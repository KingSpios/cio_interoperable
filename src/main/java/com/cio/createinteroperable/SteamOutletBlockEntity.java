package com.cio.createinteroperable;

import com.cio.createinteroperable.compat.ColdSweatCompat;
import com.cio.createinteroperable.compat.ColdSweatWarmthEffect;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.api.stress.BlockStressValues;
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
import net.neoforged.neoforge.capabilities.Capabilities;
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
            .startWithValue(0)
            .chase(0, 0, Chaser.LINEAR);

    private WeakReference<FluidTankBlockEntity> tankRef = new WeakReference<>(null);

    public SteamOutletBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // No extra Create behaviour-system hooks needed beyond the kinetic
        // ones KineticBlockEntity itself already registers.
    }

    @Override
    public void onSpeedChanged(float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        pointer.chase(getSpeed() > 0 ? 1 : 0, getChaseSpeed(), Chaser.LINEAR);
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

    /** Same tuning Create's own FluidValveBlockEntity uses: faster spin flips the valve faster. */
    private float getChaseSpeed() {
        return Mth.clamp(Math.abs(getSpeed()) / 16 / 20, 0, 1);
    }

    @Override
    public void tick() {
        super.tick();
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
            tickLeakParticles(serverLevel, shouldBeOpen);
        }
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

    /** Same cadence/density as the Multi Radiator's BLAZING puffs (see RadiatorValveNorthBlockEntity), 25% faster rise — this is just the visual throttle; the actual steam LOSS below runs every tick. */
    private static final int LEAK_INTERVAL_TICKS = 8;
    private static final double LEAK_RISE_SPEED = 0.025;
    /**
     * A leaking, unconnected outlet genuinely bleeds its buffer at this rate
     * — not just a cosmetic puff. Kept at the same 2:1 ratio over the
     * Radiator's own new base throughput (24 mB/t, see
     * RadiatorValveNorthBlockEntity — both were rescaled together to match
     * real Create pipe throughput, {@code max(1, pumpRPM / 2)} mB/t, instead
     * of the original numbers which assumed 512+ RPM pumps).
     */
    private static final int LEAK_DRAIN_MB_PER_TICK = 48;

    /** How strongly (and how long) a leak point warms anyone standing right on it — reapplied every {@link #LEAK_INTERVAL_TICKS}, so the duration only needs a little slack over that. */
    private static final int LEAK_WARMTH_AMPLIFIER = 1;
    private static final int LEAK_WARMTH_DURATION_TICKS = 20;
    /** How far from the leak point "standing on it" reaches. */
    private static final double LEAK_WARMTH_RADIUS = 2.0;

    /**
     * Runs every tick (not just on the particle cadence) while the valve is
     * open, there's actually buffered steam, AND nothing on the output face
     * (UP — see {@link #getSteamHandler}) will take it: high-pressure steam
     * venting straight to atmosphere with nowhere to go actually drains this
     * block's own buffer at {@link #LEAK_DRAIN_MB_PER_TICK} — previously this
     * was purely cosmetic (the tank just silently discarded whatever
     * overflowed past its capacity with no real consequence). The particle
     * puff (same {@code CIOParticles#RADIATOR_SMOKE} white campfire-style
     * particle as the Radiator's HOT/BLAZING tiers, at BLAZING's exact
     * density but 25% faster rise) and the warmth effect stay on the slower
     * {@link #LEAK_INTERVAL_TICKS} cadence purely for visual/entity-scan cost.
     */
    private void tickLeakParticles(ServerLevel serverLevel, boolean valveOpen) {
        if (!valveOpen || steamTank.getFluidAmount() <= 0 || isConnectedAbove()) {
            return;
        }
        steamTank.drain(LEAK_DRAIN_MB_PER_TICK, IFluidHandler.FluidAction.EXECUTE);
        if ((serverLevel.getGameTime() + worldPosition.hashCode()) % LEAK_INTERVAL_TICKS != 0) {
            return;
        }
        // Steam leaking into a water-filled space bubbles instead of smoking —
        // checked at the block directly above (the output face — see
        // getSteamHandler/isConnectedAbove), which is where it's actually
        // escaping to.
        BlockPos leakPos = worldPosition.above();
        boolean underwater = serverLevel.getFluidState(leakPos).is(FluidTags.WATER);
        int count = 2 + serverLevel.random.nextInt(3);
        for (int i = 0; i < count; i++) {
            double x = worldPosition.getX() + 0.5 + (serverLevel.random.nextDouble() - 0.5) * 0.4;
            double y = worldPosition.getY() + 0.9;
            double z = worldPosition.getZ() + 0.5 + (serverLevel.random.nextDouble() - 0.5) * 0.4;
            serverLevel.sendParticles(underwater ? ParticleTypes.BUBBLE : CIOParticles.RADIATOR_SMOKE.get(),
                    x, y, z, 0, 0.0, LEAK_RISE_SPEED, 0.0, 1.0);
        }
        if (ColdSweatCompat.present()) {
            applyLeakWarmth(serverLevel, leakPos);
        }
    }

    /** Warms whoever's standing right on the leak — same real Cold Sweat WARMTH effect the Radiator's Hearth-style mechanic uses (see ColdSweatWarmthEffect), just localized to this one spot instead of a whole room. */
    private static void applyLeakWarmth(ServerLevel serverLevel, BlockPos leakPos) {
        AABB area = new AABB(leakPos).inflate(LEAK_WARMTH_RADIUS);
        for (LivingEntity entity : serverLevel.getEntitiesOfClass(LivingEntity.class, area)) {
            ColdSweatWarmthEffect.apply(entity, LEAK_WARMTH_AMPLIFIER, LEAK_WARMTH_DURATION_TICKS);
        }
    }

    /** @return whether something on the UP face (a pipe, a pump) would actually accept this block's steam. */
    private boolean isConnectedAbove() {
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, worldPosition.above(), Direction.DOWN);
        return handler != null;
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
