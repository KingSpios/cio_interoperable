package com.cio.createinteroperable;

import com.cio.createinteroperable.compat.ColdSweatCompat;
import com.cio.createinteroperable.compat.ColdSweatWarmthEffect;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.tank.BoilerData;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Reads a Create Fluid Tank/Boiler's real activeHeat/waterSupply — made
 * reachable without a real Steam Engine by BoilerDataMixin, which counts
 * this block into BoilerData#attachedEngines exactly like a Steam Engine —
 * and converts it into "steam" fluid, buffered internally and exposed to
 * Create's pipe network on the top face only (see CIOCapabilities) — this
 * block only ever sits directly on top of the tank (see
 * SteamOutletBlock#canSurvive), so the bottom face is permanently occupied.
 * <p>
 * Deliberately mirrors what a real Steam Engine itself reads
 * (SteamEngineBlockEntity#tick: {@code Mth.clamp(tank.boiler.getEngineEfficiency(tank.getTotalTankSize()), 0, 1)})
 * rather than inventing a separate formula.
 * <p>
 * Important: the SU dilution effect on real Steam Engines is NOT produced
 * here and is NOT gated by {@link #pointer}/{@link SteamOutletBlock#OPEN} at
 * all — see BoilerDataMixin, which counts this block into
 * BoilerData#attachedEngines purely from being physically attached to the
 * tank. That is the actual "diverting steam makes the boiler less capable"
 * mechanic: a closed valve stops steam from leaving this block, but the
 * boiler still treats the outlet as a tap on its heat/water budget, exactly
 * like a real Steam Engine bolted on and idling. What the valve DOES gate is
 * purely local: whether this block's own internal buffer actually receives
 * newly produced steam and can hand it to the pipe network below.
 * <p>
 * The shaft on the FACING face (see SteamOutletBlock#hasShaftTowards) drives a
 * real analog valve, not a binary on/off: spinning it one direction chases
 * {@link #pointer} toward 1 (fully open), the other direction chases it
 * toward 0 (fully closed). Stop spinning partway and it just stops there —
 * {@code pointer}'s current value IS the 0-100% valve position, and it
 * directly scales how much of the produced steam actually reaches the
 * internal buffer each tick (see #tick). {@link SteamOutletBlock#OPEN} is
 * kept in sync purely as a coarse "is this fully shut or not" flag for the
 * blockstate/model — it plays no part in the actual math.
 */
public class SteamOutletBlockEntity extends KineticBlockEntity {
    private static final int TANK_CAPACITY_MB = 4000;

    private final FluidTank steamTank = new FluidTank(TANK_CAPACITY_MB) {
        @Override
        public boolean isFluidValid(FluidStack stack) {
            return stack.getFluid() == CIOFluids.STEAM_STILL.get();
        }
    };

    /**
     * 0 = closed, 1 = open — chases toward whichever end matches the shaft's
     * current spin direction, exactly like Create's real
     * FluidValveBlockEntity#pointer. Once it fully settles at either end,
     * {@link SteamOutletBlock#OPEN} is flipped to match (see #tick).
     */
    private final LerpedFloat pointer = LerpedFloat.linear()
            .startWithValue(1)
            .chase(1, 0, Chaser.LINEAR);

    private WeakReference<FluidTankBlockEntity> tankRef = new WeakReference<>(null);

    public SteamOutletBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // No extra Create behaviour-system hooks needed beyond the kinetic
        // ones KineticBlockEntity itself already registers.
        //
        // A real Create FluidTransportBehaviour (+ Pipes n Physics' own
        // FluidPump interface) was tried here to give this block real
        // hydraulic "pressure" — reverted. Pipes n Physics' pump model is a
        // genuine two-flank PASSTHROUGH (Pumps.isPump nodes split a pipe RUN
        // into two edges, exactly like Create's real Mechanical Pump wedged
        // inline between two pipe runs, conserving mass through both sides).
        // This block is the opposite shape: a dead end with real internal
        // storage and exactly one exposed face (UP) — there is no valid
        // "back" flank for an EMF pump to draw real network flow from, so
        // the pump node never conducted anything (an unpowered/broken pump
        // rather than a source), which is exactly the "pipes stopped
        // accepting steam, as if it were no longer a pump" regression this
        // caused. A plain fluid-handler RESERVOIR (see PipesNPhysicsIntegration)
        // is the topologically correct classification for a dead-end
        // source/tank — the same thing a real Create Fluid Tank is.
    }

    /**
     * Boiler-derived steam pressure available right now, scaled by the
     * valve's own open fraction ({@link #pointer}) — the exact same number
     * {@link #tick()} uses to fill the internal buffer. Used purely as the
     * intensity input for the vent particle visual (see
     * {@link #tickSteamParticles}); a closed valve or an inactive boiler
     * both read 0.
     */
    public double currentPressure() {
        FluidTankBlockEntity controller = getBoilerController();
        if (controller == null) {
            return 0;
        }
        return availableSteamPerTick(controller) * pointer.getValue();
    }

    @Override
    public void onSpeedChanged(float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        // The actual valve-position update moved to #tick (recomputed every
        // tick from the LIVE speed, not just re-armed here on transitions —
        // see #tick's own comment on why). This override still exists purely
        // to force an immediate sync + play the puff below on a genuine
        // network speed change, rather than waiting for the next tick's sync
        // cadence.
        pointer.forceNextSync();
        // A real Steam Engine puff, played once per genuine speed change (this
        // is only ever called when Create's own kinetic network actually
        // recalculates a different speed for this shaft — not every tick) —
        // server-broadcast via AllSoundEvents#play (NOT #playAt, which calls
        // Level#playLocalSound and is a client-only no-op on a real server)
        // so it's heard once by everyone nearby, not double-played by both
        // sides independently predicting the same event.
        if (level != null && !level.isClientSide) {
            AllSoundEvents.STEAM.play(level, null, worldPosition, 0.6f,
                    0.9f + level.random.nextFloat() * 0.2f);
        }
    }

    /**
     * How many degrees of real shaft rotation correspond to a full 0→100%
     * valve sweep. NOT a literal 1-configured-degree-to-1% mapping — that
     * was tried (100.0 here) and is structurally impossible to achieve via
     * Create's kinetic network, confirmed by reading both
     * {@code HandCrankBlockEntity} (the Valve Handle's real parent class)
     * and {@code ValveHandleBlockEntity} directly:
     * <ul>
     *     <li>{@code HandCrankBlockEntity#getGeneratedSpeed()} outputs the
     *     block's FULL nominal {@code rotationSpeed} (32 RPM for the Copper
     *     Valve Handle) for every tick {@code inUse > 0} — there is no
     *     partial-speed/fractional-tick signal; the shaft is either at full
     *     speed or stopped, nothing in between.</li>
     *     <li>{@code ValveHandleBlockEntity#activate()} sets
     *     {@code inUse = ceil(targetDegrees / degreesPerTick) + 2} — that
     *     flat {@code + 2} padding means EVERY activation, even the
     *     UI-minimum 1° target ({@code ValveHandleScrollValueBehaviour}
     *     clamps to {@code max(1, value)}), still drives the shaft at full
     *     speed for a MINIMUM of 3 ticks.</li>
     * </ul>
     * At the Copper Valve Handle's 32 RPM (9.6°/tick via
     * {@code convertToAngular}), that minimum floor is 3 × 9.6 ≈ 28.8° of
     * REAL shaft rotation per click, REGARDLESS of how small the configured
     * target is — the handle's own "1°" turn is only honest for its OWN
     * cosmetic model interpolation ({@code getIndependentAngle}), not for
     * what it actually broadcasts to the kinetic network every other
     * attached block reads via {@link #getSpeed()}. This is the exact,
     * sourced cause of a real reported bug: "even at 1º turn... wildly
     * bigger, ~30%, changes" — 28.8/100 ≈ 29%, matching almost exactly.
     * <p>
     * Since there is no way to distinguish "a genuinely tiny click" from
     * "the engine's own unavoidable minimum pulse" from here, this constant
     * is instead picked so that pulse floor itself reads as a small,
     * reasonable step: 2880° (8 full shaft rotations) makes even the
     * smallest possible real click land at roughly 28.8/2880 ≈ 1% — the
     * closest honest approximation of "the smallest turn barely nudges it"
     * actually achievable, while sustained rotation (a windmill, water
     * wheel, etc. left spinning) still reaches 100% in a reasonable ~10-30
     * real seconds at typical early RPMs.
     */
    private static final double DEGREES_PER_FULL_SWEEP = 2880.0;

    @Override
    public void tick() {
        super.tick();
        // Direct accumulation of the shaft's REAL rotation this tick, not a
        // chase toward a fixed 0/1 target — see DEGREES_PER_FULL_SWEEP's own
        // doc for why. getSpeed() is Create's internal RPM-like unit;
        // KineticBlockEntity#convertToAngular is Create's own real
        // rpm->degrees/tick conversion (speed * 360 / 60 / 20), the same one
        // ValveHandleBlockEntity itself uses to know how many ticks its own
        // bounded turn needs — reused here rather than re-deriving it.
        float speed = getSpeed();
        if (speed != 0) {
            double degreesThisTick = convertToAngular(Math.abs(speed));
            double deltaFraction = degreesThisTick / DEGREES_PER_FULL_SWEEP;
            double next = Mth.clamp(pointer.getValue() + Math.signum(speed) * deltaFraction, 0.0, 1.0);
            // Chase speed 1.0 = can move the full 0..1 range in a single
            // tick, so this always lands exactly on `next` this same tick —
            // LerpedFloat is kept only for its existing NBT/sync plumbing,
            // not for multi-tick interpolation toward a target.
            pointer.chase(next, 1.0, Chaser.LINEAR);
        }
        pointer.tickChaser();

        if (level == null || level.isClientSide) {
            return;
        }

        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof SteamOutletBlock)) {
            return;
        }

        // Purely cosmetic bookkeeping — see class javadoc. Not read anywhere
        // that affects the actual production math below.
        boolean shouldBeOpen = pointer.getValue() > 0;
        if (state.getValue(SteamOutletBlock.OPEN) != shouldBeOpen) {
            level.setBlockAndUpdate(worldPosition, state.setValue(SteamOutletBlock.OPEN, shouldBeOpen));
        }

        float valvePosition = pointer.getValue();
        if (valvePosition > 0) {
            FluidTankBlockEntity controller = getBoilerController();
            if (controller != null) {
                double availablePerTick = availableSteamPerTick(controller);
                int produced = (int) Math.round(availablePerTick * valvePosition);
                if (produced > 0) {
                    steamTank.fill(new FluidStack(CIOFluids.STEAM_STILL.get(), produced), IFluidHandler.FluidAction.EXECUTE);
                }
            }
        }

        if (level instanceof ServerLevel serverLevel) {
            tickSteamParticles(serverLevel);
            tickOpenEndScan(serverLevel);
        }
    }

    /** How often (in ticks) a connected Outlet re-walks its pipe network for genuinely open ends — see {@link SteamOpenEndScanner}. */
    private static final int OPEN_END_SCAN_INTERVAL_TICKS = 5;

    /**
     * Only while genuinely piped (a disconnected Outlet already has its own
     * local leak visual, see {@link #tickSteamParticles}) AND this Outlet's
     * own buffer actually holds real steam right now — periodically re-walks
     * the attached pipe network for real open ends and vents
     * {@link CIOOpenPipeEffects} there directly, scaled by this Outlet's own
     * exact valve fraction. Staggered per-position (same hashCode trick used
     * elsewhere in this class) rather than every tick — the walk itself is
     * bounded (see SteamOpenEndScanner's own caps) but still real work, no
     * need to repeat it 20 times a second for a cosmetic effect.
     * <p>
     * The {@code steamTank.getFluidAmount() > 0} check is load-bearing, not
     * an optimization: without it, this fired purely off pipe TOPOLOGY and a
     * timer — a real reported bug (particles kept showing at open ends with
     * a completely dry, inactive boiler and an empty buffer, as long as a
     * pipe merely happened to be connected). This buffer is the one real
     * thing we can honestly check: {@link #getSteamHandler} exposes it
     * regardless of valve position, so as long as it holds anything, the
     * pipe network genuinely CAN still be pulling real steam from it right
     * now (a closed valve only gates further PRODUCTION into the buffer —
     * see this class's own doc — not the pipe network's ability to drain
     * whatever's already sitting in it). Once the buffer actually reads
     * empty, no real steam can reach any open end from this Outlet, and
     * this stops — matching {@link #tickSteamParticles}'s own identical gate
     * for the disconnected case.
     */
    private void tickOpenEndScan(ServerLevel serverLevel) {
        if (!isConnectedAbove() || steamTank.getFluidAmount() <= 0) {
            return;
        }
        if ((serverLevel.getGameTime() + worldPosition.hashCode()) % OPEN_END_SCAN_INTERVAL_TICKS != 0) {
            return;
        }
        SteamOpenEndScanner.scanAndVent(serverLevel, worldPosition.above(), pointer.getValue());
    }

    /**
     * The real total SU a Steam Engine attached to this exact boiler would
     * generate right now, mapped 1:1 to mB of steam per tick at 100% valve —
     * literally the same formula Create's own boiler goggle tooltip shows as
     * "Capacity Provided" (see {@code BoilerData#addToGoggleTooltip}, read
     * directly rather than re-derived by guesswork):
     * <pre>
     * totalSU = efficiency * 16 * max(boilerLevel, attachedEngines) * steamEngineCapacity
     * </pre>
     * where {@code boilerLevel} is the boiler's actual (heat/water/size-capped)
     * heat tier and {@code steamEngineCapacity} is
     * {@link BlockStressValues#getCapacity} for the real Steam Engine block —
     * queried live, not hardcoded, so this tracks any datapack change to that
     * value automatically. Divided by {@code attachedEngines} (which counts
     * THIS block too, via BoilerDataMixin) so multiple Outlets/Engines sharing
     * one boiler split its total output instead of each independently
     * claiming the whole thing.
     */
    /**
     * Looked up by resource location straight from the vanilla block registry
     * rather than Create's own {@code AllBlocks.STEAM_ENGINE} (a Registrate
     * {@code BlockEntry}) — {@code create-slim} (this project's compile-time
     * Create dependency, see build.gradle) strips Registrate's own classes
     * out entirely, confirmed by a real compile failure ("class file for
     * com.tterrag.registrate.util.entry.BlockEntry not found") the moment
     * this called {@code AllBlocks.STEAM_ENGINE.get()} directly. A plain
     * registry lookup needs nothing beyond vanilla's own {@code Block} type,
     * sidestepping the whole issue instead of adding Registrate as a new
     * dependency just for one field access.
     */
    private static final ResourceLocation STEAM_ENGINE_ID = ResourceLocation.fromNamespaceAndPath("create", "steam_engine");

    private static double availableSteamPerTick(FluidTankBlockEntity controller) {
        BoilerData boiler = controller.boiler;
        if (boiler == null || !boiler.isActive() || boiler.attachedEngines <= 0) {
            return 0;
        }
        int boilerSize = controller.getTotalTankSize();
        float efficiency = Mth.clamp(boiler.getEngineEfficiency(boilerSize), 0, 1);
        if (efficiency <= 0) {
            return 0;
        }
        int boilerLevel = Math.min(boiler.activeHeat,
                Math.min(boiler.getMaxHeatLevelForWaterSupply(), boiler.getMaxHeatLevelForBoilerSize(boilerSize)));
        Block steamEngine = BuiltInRegistries.BLOCK.get(STEAM_ENGINE_ID);
        double totalSU = efficiency * 16 * Math.max(boilerLevel, boiler.attachedEngines)
                * BlockStressValues.getCapacity(steamEngine);
        return totalSU / boiler.attachedEngines;
    }

    /**
     * @return whether more Outlets/Engines are attached to this boiler than
     * its current heat tier can fully power at once — the real Create
     * condition behind {@code getEngineEfficiency} falling below 100% (each
     * one only gets an {@code actualHeat / attachedEngines} share instead of
     * the full amount) — i.e. demand for engine "slots" exceeding what the
     * boiler's heat can supply, the steam-side equivalent of an overstressed
     * kinetic network.
     */
    private static boolean isOverstressed(FluidTankBlockEntity controller) {
        BoilerData boiler = controller.boiler;
        if (boiler == null || !boiler.isActive() || boiler.activeHeat <= 0) {
            return false;
        }
        int boilerSize = controller.getTotalTankSize();
        int actualHeat = Math.min(boiler.activeHeat,
                Math.min(boiler.getMaxHeatLevelForWaterSupply(), boiler.getMaxHeatLevelForBoilerSize(boilerSize)));
        return boiler.attachedEngines > actualHeat;
    }

    /** Rise speed in blocks/tick — height traveled is roughly this times the particle's ~80-130 tick lifetime (see RadiatorSmokeParticle), so this pair alone is what makes a high-pressure vent read "a lot higher", not a separate height mechanic. */
    private static final double LOW_RISE_SPEED = 0.012;
    private static final double HIGH_RISE_SPEED = 0.07;
    /** Particles spawned per puff. */
    private static final int LOW_PARTICLE_COUNT = 1;
    private static final int HIGH_PARTICLE_COUNT = 8;
    /** How often a puff fires, in ticks — lower = more continuous. Bounds match the Multi Radiator's own HOT/BLAZING cadence (see RadiatorValveNorthBlockEntity) at the low/high ends respectively. */
    private static final int LOW_INTERVAL_TICKS = 14;
    private static final int HIGH_INTERVAL_TICKS = 4;

    private static double lerp(double low, double high, double fraction) {
        return low + (high - low) * fraction;
    }

    /**
     * A leaking, unconnected outlet genuinely bleeds its buffer at this rate
     * — not just a cosmetic puff. Kept at the same 2:1 ratio over the
     * Radiator's own new base throughput (24 mB/t, see
     * RadiatorValveNorthBlockEntity — both were rescaled together to match
     * real Create pipe throughput, {@code max(1, pumpRPM / 2)} mB/t, instead
     * of the original numbers which assumed 512+ RPM pumps).
     */
    private static final int LEAK_DRAIN_MB_PER_TICK = 48;

    /** How strongly (and how long) a leak point warms anyone standing right on it — reapplied every puff (see the pressure-scaled interval in {@link #tickSteamParticles}), so the duration only needs a little slack over the slowest (LOW_INTERVAL_TICKS) cadence. */
    private static final int LEAK_WARMTH_AMPLIFIER = 1;
    private static final int LEAK_WARMTH_DURATION_TICKS = 20;
    /** How far from the leak point "standing on it" reaches. */
    private static final double LEAK_WARMTH_RADIUS = 2.0;

    /**
     * The floor on the visual's fraction: even with the valve fully shut
     * (valve fraction 0), a disconnected outlet still genuinely drains its
     * buffer every tick below (see {@link #LEAK_DRAIN_MB_PER_TICK}) — the
     * visual has to keep pace at a low, unmistakably "low pressure" rate
     * rather than vanish the instant the valve closes, or a real, ongoing
     * loss would read as nothing happening at all.
     */
    private static final double MIN_LEAK_FRACTION = 0.15;

    /**
     * Only ever runs while nothing on the output face (UP — see
     * {@link #getSteamHandler}) will accept this block's steam: a properly
     * piped Steam Outlet shows NOTHING here — a real pipe run's own open
     * (unconnected) ends are where PnP's own network actually vents to
     * atmosphere, and that's where any "is this thing under pressure"
     * visual belongs, not retroactively painted onto this block regardless
     * of what's plumbed into it.
     * <p>
     * When genuinely disconnected, the buffer drains at the flat
     * {@link #LEAK_DRAIN_MB_PER_TICK} regardless of valve position — the
     * visual's intensity (rise speed/height, particle count, how often it
     * puffs) tracks the valve's own open fraction ({@link #pointer}) directly
     * on top of the {@link #MIN_LEAK_FRACTION} floor, the same "signal fire"
     * idea vanilla's hay-bale-boosted Campfire smoke uses: a bare residual
     * drip reads as a lazy low puff, a fully open valve reads as a real vent
     * stack. Deliberately NOT normalized against {@link #currentPressure()}
     * (this boiler's absolute mB/t output) — that made the visual saturate
     * to max intensity for most of the valve's range on any boiler whose
     * output happened to run well above whatever fixed ceiling was chosen,
     * masking valve position instead of showing it. The valve's own 0..1
     * fraction is already exactly proportional across its whole range by
     * construction (see the degree-accumulation in {@link #tick}), so using
     * it directly is both simpler and correct regardless of boiler size.
     */
    private void tickSteamParticles(ServerLevel serverLevel) {
        if (isConnectedAbove() || steamTank.getFluidAmount() <= 0) {
            return;
        }
        steamTank.drain(LEAK_DRAIN_MB_PER_TICK, IFluidHandler.FluidAction.EXECUTE);

        double fraction = Mth.clamp(Math.max(MIN_LEAK_FRACTION, pointer.getValue()), 0.0, 1.0);
        int interval = (int) Math.round(lerp(LOW_INTERVAL_TICKS, HIGH_INTERVAL_TICKS, fraction));
        if ((serverLevel.getGameTime() + worldPosition.hashCode()) % Math.max(1, interval) != 0) {
            return;
        }

        double riseSpeed = lerp(LOW_RISE_SPEED, HIGH_RISE_SPEED, fraction);
        int count = (int) Math.round(lerp(LOW_PARTICLE_COUNT, HIGH_PARTICLE_COUNT, fraction));

        // Steam venting into a water-filled space bubbles instead of smoking —
        // checked at the block directly above (the output face — see
        // getSteamHandler/isConnectedAbove), which is where it's actually
        // escaping to.
        BlockPos leakPos = worldPosition.above();
        boolean underwater = serverLevel.getFluidState(leakPos).is(FluidTags.WATER);
        for (int i = 0; i < count; i++) {
            double x = worldPosition.getX() + 0.5 + (serverLevel.random.nextDouble() - 0.5) * 0.4;
            double y = worldPosition.getY() + 0.9;
            double z = worldPosition.getZ() + 0.5 + (serverLevel.random.nextDouble() - 0.5) * 0.4;
            serverLevel.sendParticles(underwater ? ParticleTypes.BUBBLE : CIOParticles.RADIATOR_SMOKE.get(),
                    x, y, z, 0, 0.0, riseSpeed, 0.0, 1.0);
        }
        if (ColdSweatCompat.present()) {
            applyLeakWarmth(serverLevel, leakPos);
        }
    }

    /** Warms whoever's standing right on the leak — same real Cold Sweat WARMTH effect the Radiator's Hearth-style mechanic uses (see ColdSweatWarmthEffect), just localized to this one spot instead of a whole room. */
    private static void applyLeakWarmth(ServerLevel serverLevel, BlockPos leakPos) {
        AABB area = new AABB(leakPos).inflate(LEAK_WARMTH_RADIUS);
        for (LivingEntity entity : serverLevel.getEntitiesOfClass(LivingEntity.class, area)) {
            ColdSweatWarmthEffect.apply(entity, LEAK_WARMTH_AMPLIFIER, LEAK_WARMTH_DURATION_TICKS, true);
        }
    }

    /**
     * @return whether something on the UP face (a pipe, a pump, an external
     * tank) would actually accept this block's steam.
     * <p>
     * Deliberately NOT a plain {@code Capabilities.FluidHandler.BLOCK} query
     * on the neighbor — confirmed by reading Create's real pipe classes
     * ({@code FluidPipeBlockEntity}, {@code EncasedPipeBlock}, etc.) that
     * NONE of them register themselves as a NeoForge fluid handler capability
     * at all; pipe-to-pipe transport runs entirely through Create's own
     * internal {@code FluidTransportBehaviour}, not capabilities (a plain
     * external tank/handler is the only thing that DOES show up that way).
     * A capability-only check therefore always read "not connected" for a
     * real Create pipe (vanilla or Pipes n Physics-transported — PnP reuses
     * these exact classes, see CIOOpenPipeEffects' own doc) or any of its
     * cosmetic/encased variants sitting directly above, which is exactly the
     * reported bug: the Outlet kept rendering its own leak particles even
     * with a pipe genuinely connected and accepting steam.
     * <p>
     * {@link FluidPropagator#isOpenEnd} is Create's own real answer to this
     * exact question (used internally to decide where a pipe run's own open
     * end sits) — it checks the neighbor's {@code FluidTransportBehaviour}
     * first (a real pipe), then a pump, then a plain fluid-handler capability
     * (an external tank), then whether a solid block simply blocks flow
     * outright. Reusing it here instead of re-deriving a partial version of
     * the same logic.
     */
    private boolean isConnectedAbove() {
        return !FluidPropagator.isOpenEnd(level, worldPosition, Direction.UP);
    }

    @Nullable
    private FluidTankBlockEntity getBoilerController() {
        FluidTankBlockEntity tank = tankRef.get();
        if (tank == null || tank.isRemoved()) {
            // Tank is always directly below now — see SteamOutletBlock#canSurvive.
            if (level.getBlockEntity(worldPosition.below()) instanceof FluidTankBlockEntity tankBe) {
                tankRef = new WeakReference<>(tank = tankBe);
            } else {
                return null;
            }
        }
        return tank.getControllerBE();
    }

    /**
     * @return this block's internal steam buffer when queried from its top
     * face (the only face Create's pipe network is meant to attach to — see
     * CIOCapabilities). The bottom face is permanently occupied by the tank
     * this block sits on, so output moved to the top.
     */
    @Nullable
    public IFluidHandler getSteamHandler(@Nullable Direction side) {
        return side == Direction.UP ? steamTank : null;
    }

    /**
     * Goggle-only readout (no native Create system surfaces any of this on
     * its own, since this block deliberately declares no stress impact — see
     * KineticBlockEntity#addToGoggleTooltip): whether the attached tank sees
     * an active boiler at all (the core thing the Mixin is responsible for),
     * its efficiency, this Outlet's own valve state, and the internal steam
     * buffer via the same containedFluidTooltip helper Create's own
     * FluidTankBlockEntity uses. Hovering the Tank itself with goggles also
     * shows Create's native boiler tooltip (heat/water/pressure) for free,
     * once the Mixin has made it active — this is just the Outlet's own side.
     */
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        // A proper title line: Create's own containedFluidTooltip() unconditionally
        // opens with its generic "Fluid Container Info:" header (create.gui.goggles
        // .fluid_container — no parameter to override or suppress it), which is all
        // a player would otherwise see, reading as an unnamed generic tank rather
        // than a Steam Outlet. Create's goggle overlay has no automatic block-name
        // header of its own (GoggleOverlayRenderer renders exactly and only what
        // addToGoggleTooltip populates) — so the name has to be added explicitly.
        CreateLang.text("Steam Outlet")
                .style(ChatFormatting.WHITE)
                .forGoggles(tooltip);
        added |= containedFluidTooltip(tooltip, isPlayerSneaking, steamTank);

        FluidTankBlockEntity controller = getBoilerController();
        boolean boilerActive = controller != null && controller.boiler != null && controller.boiler.isActive();
        CreateLang.text("Boiler: ")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.text(boilerActive ? "Active" : "Inactive")
                        .style(boilerActive ? ChatFormatting.GREEN : ChatFormatting.RED))
                .forGoggles(tooltip, 1);

        if (boilerActive) {
            float efficiency = Mth.clamp(controller.boiler.getEngineEfficiency(controller.getTotalTankSize()), 0, 1);
            CreateLang.text("Efficiency: ")
                    .style(ChatFormatting.GRAY)
                    .add(CreateLang.number(Math.round(efficiency * 100))
                            .text("%")
                            .style(ChatFormatting.GOLD))
                    .forGoggles(tooltip, 1);

            double availablePerTick = availableSteamPerTick(controller);
            CreateLang.text("Available: ")
                    .style(ChatFormatting.GRAY)
                    .add(CreateLang.number(Math.round(availablePerTick))
                            .text(" SU / mB/t")
                            .style(ChatFormatting.AQUA))
                    .forGoggles(tooltip, 1);

            int output = (int) Math.round(availablePerTick * pointer.getValue());
            boolean bufferFull = steamTank.getFluidAmount() >= steamTank.getCapacity();
            CreateLang.text("Output: ")
                    .style(ChatFormatting.GRAY)
                    .add(CreateLang.number(output)
                            .text(" mB/t" + (bufferFull ? "  (buffer full)" : ""))
                            .style(bufferFull ? ChatFormatting.RED : ChatFormatting.AQUA))
                    .forGoggles(tooltip, 1);

            if (isOverstressed(controller)) {
                CreateLang.text("Overstressed: ")
                        .style(ChatFormatting.RED)
                        .add(CreateLang.text("too many engines for this boiler's heat").style(ChatFormatting.RED))
                        .forGoggles(tooltip, 1);
            }
        }

        CreateLang.text("Valve: ")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.number(Math.round(pointer.getValue() * 100))
                        .text("%")
                        .style(ChatFormatting.AQUA))
                .forGoggles(tooltip, 1);

        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("SteamTank", steamTank.writeToNBT(registries, new CompoundTag()));
        tag.put("Pointer", pointer.writeNBT());
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        steamTank.readFromNBT(registries, tag.getCompound("SteamTank"));
        pointer.readNBT(tag.getCompound("Pointer"), clientPacket);
    }
}
