package com.cio.createinteroperable;

import com.cio.createinteroperable.compat.ColdSweatCompat;
import com.cio.createinteroperable.compat.ColdSweatWorldTemp;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Consumes "steam" fed into its bottom face (via Create's pipe network) — the
 * shaft on the back face drives a real analog valve, not a binary on/off:
 * spin it one direction and {@link #pointer} chases toward 1 (fully open),
 * the other direction and it chases toward 0 (fully closed). Stop spinning
 * (or cut power) partway and it just stops there — {@code pointer}'s current
 * value, wherever it happens to be sitting, IS the 0-100% valve position, and
 * it directly scales how many mB of steam are drawn per tick (see #tick).
 * {@link BrassHeaterBlock#OPEN} is kept in sync purely as a coarse "is this
 * fully shut or not" flag for the blockstate/model — it plays no part in the
 * actual math.
 * <p>
 * {@code heatFraction} is how much of the REQUESTED draw (valve position ×
 * max rate) the internal tank could actually supply — so it can fall below
 * the valve's own position if upstream steam is scarce, which is what
 * produces the 5-tier HEAT_LEVEL display's real behavior: a fully open valve
 * with no steam coming in still shows COLD, same as a real heater whose
 * valve is open but starved of fuel. See {@link #quantizeHeatTier(float, float, boolean)}
 * for the exact valve-position bands (WARM is a deliberately wide band
 * straddling the "ideal" 50% opening; BLAZING gets harder to reach the
 * colder the room is).
 * <p>
 * Cold Sweat itself is never a build dependency here — the actual "this
 * heater warms the room" effect is a pure datapack integration (see
 * {@code data/createinteroperable/block/block_temp/}), keyed entirely off
 * the {@link BrassHeaterBlock#HEAT_LEVEL} blockstate this class drives, with
 * Cold Sweat's own {@code max_temp} clamp enforcing the "never above 30°C
 * standing next to it" requirement — nothing here reads or depends on
 * whether Cold Sweat is even installed.
 */
public class BrassHeaterBlockEntity extends KineticBlockEntity {
    /** mB of steam drawn per tick while the valve is open and supply allows it. Tunable. */
    private static final int MAX_STEAM_CONSUMPTION_PER_TICK = 20;
    private static final int TANK_CAPACITY_MB = 2000;
    /** How fast the displayed heat tier settles toward the real rate each tick — see #tick's use of it for why this exists at all. 0.05 ≈ mostly converged within a second. */
    private static final float HEAT_FRACTION_SMOOTHING = 0.05f;

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
     * {@link BrassHeaterBlock#OPEN} is flipped to match (see #tick).
     */
    private final LerpedFloat pointer = LerpedFloat.linear()
            .startWithValue(1)
            .chase(1, 0, Chaser.LINEAR);

    /** Fraction (0..1) of MAX_STEAM_CONSUMPTION_PER_TICK actually burned last tick. */
    private float heatFraction = 0;

    public BrassHeaterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // Purely speed-driven — no ScrollValueBehaviour/manual control needed.
    }

    @Override
    public void onSpeedChanged(float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        pointer.chase(getSpeed() > 0 ? 1 : 0, getChaseSpeed(), Chaser.LINEAR);
        pointer.forceNextSync();
    }

    /** Same tuning Create's own FluidValveBlockEntity uses: faster spin flips the valve faster. */
    private float getChaseSpeed() {
        return Mth.clamp(Math.abs(getSpeed()) / 16 / 20, 0, 1);
    }

    @Override
    public void tick() {
        super.tick();
        pointer.tickChaser();

        if (level == null) {
            return;
        }

        if (level.isClientSide) {
            tickMaxCapacityParticles();
            return;
        }

        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof BrassHeaterBlock)) {
            return;
        }

        // Purely cosmetic bookkeeping — see class javadoc. Not read anywhere
        // that affects the actual consumption math below.
        boolean shouldBeOpen = pointer.getValue() > 0;
        if (state.getValue(BrassHeaterBlock.OPEN) != shouldBeOpen) {
            level.setBlockAndUpdate(worldPosition, state.setValue(BrassHeaterBlock.OPEN, shouldBeOpen));
        }

        float valvePosition = pointer.getValue();
        int desired = Math.round(valvePosition * MAX_STEAM_CONSUMPTION_PER_TICK);

        float instantFraction = 0;
        if (desired > 0) {
            FluidStack drained = steamTank.drain(desired, IFluidHandler.FluidAction.EXECUTE);
            instantFraction = (float) drained.getAmount() / MAX_STEAM_CONSUMPTION_PER_TICK;
        }

        // Smoothed rather than assigned outright — see
        // RadiatorValveNorthBlockEntity's own HEAT_FRACTION_SMOOTHING for the
        // full reasoning: a raw single-tick consumed/max ratio flickers
        // whenever a pipe's actual per-tick delivery doesn't land in lockstep
        // with this block's own tick (real Create pipe throughput is
        // RPM-based and not guaranteed to line up tick-for-tick), snapping the
        // heat tier down and back up every tick instead of settling.
        float newHeatFraction = heatFraction + (instantFraction - heatFraction) * HEAT_FRACTION_SMOOTHING;

        // Always re-check the tier (cheap — updateHeatLevelState() itself
        // no-ops unless the computed tier actually differs from the current
        // blockstate), not just when heatFraction moves: a heater that's
        // never received any steam at all sits at newHeatFraction == 0 from
        // the moment it's placed, so the old "only on change" gate here
        // never fired even once and left it stuck on whatever
        // registerDefaultState() set, regardless of the actual environment.
        boolean fractionChanged = Math.abs(newHeatFraction - heatFraction) > 0.001f;
        heatFraction = newHeatFraction;
        updateHeatLevelState();
        if (fractionChanged) {
            notifyUpdate();
        }
    }

    /**
     * Client-only visual: a rare, slow puff of white smoke — only while
     * running at the top heat tier — as a quiet "working at max capacity"
     * confirmation rather than a constant effect. The BlockPos-based offset
     * on the timing keeps every Brass Heater in the world from puffing on
     * the exact same tick.
     */
    private void tickMaxCapacityParticles() {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof BrassHeaterBlock)) {
            return;
        }
        if (state.getValue(BrassHeaterBlock.HEAT_LEVEL) != BrassHeaterBlock.HeatLevel.BLAZING) {
            return;
        }
        long ticksSinceEpoch = level.getGameTime() + worldPosition.hashCode();
        if (ticksSinceEpoch % 200 != 0) {
            return;
        }
        int count = 1 + level.random.nextInt(2);
        for (int i = 0; i < count; i++) {
            double x = worldPosition.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.4;
            double y = worldPosition.getY() + 0.9;
            double z = worldPosition.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.4;
            level.addParticle(ParticleTypes.WHITE_SMOKE, x, y, z, 0, 0.008, 0);
        }
    }

    private void updateHeatLevelState() {
        BrassHeaterBlock.HeatLevel newTier = quantizeHeatTier(heatFraction, getAmbientTemperatureC(), isCold(level, worldPosition));
        BlockState state = getBlockState();
        if (state.getValue(BrassHeaterBlock.HEAT_LEVEL) != newTier) {
            // The 3 HEAT_WARM/HOT/BLAZING booleans are set here too, not just
            // HEAT_LEVEL — see BrassHeaterBlock's doc on why Cold Sweat needs
            // them (its own state-predicate matcher can't reliably compare
            // against an enum-valued property).
            level.setBlockAndUpdate(worldPosition, state.setValue(BrassHeaterBlock.HEAT_LEVEL, newTier)
                    .setValue(BrassHeaterBlock.HEAT_WARM, newTier == BrassHeaterBlock.HeatLevel.WARM)
                    .setValue(BrassHeaterBlock.HEAT_HOT, newTier == BrassHeaterBlock.HeatLevel.HOT)
                    .setValue(BrassHeaterBlock.HEAT_BLAZING, newTier == BrassHeaterBlock.HeatLevel.BLAZING));
        }
    }

    /**
     * Same rough biome-based approximation of real-world °C Power Grid's own
     * ThermalBehaviour uses for its Cold Sweat ambient reading — kept
     * consistent with that formula rather than invented fresh, even though
     * this block has no Cold Sweat dependency of its own (no gradle
     * dependency, no BlockTemp registered anywhere in this project yet).
     */
    private float getAmbientTemperatureC() {
        if (level == null) {
            return 0f;
        }
        return ambientTemperatureC(level, worldPosition);
    }

    /**
     * Package-visible so other steam-heating blocks (see
     * RadiatorValveNorthBlockEntity) can reuse the exact same formula instead
     * of re-deriving it — single source of truth for "what ambient °C looks
     * like here" across this whole feature family.
     */
    static float ambientTemperatureC(net.minecraft.world.level.Level level, BlockPos pos) {
        return level.getBiome(pos).value().getBaseTemperature() * 13.65f + 7.1f;
    }

    /**
     * Whether this position counts as "cold" for the FREEZING gate below.
     * <p>
     * When Cold Sweat is installed, this asks Cold Sweat's OWN real ambient
     * temperature calculation for this exact coordinate
     * ({@link ColdSweatWorldTemp#getWorldTemperatureC}, backed by its
     * {@code WorldHelper#getRoughTemperatureAt}) — the same computation that
     * decides the player's own world-temperature trait, accounting for
     * biome, altitude, dimension, time of day, and nearby hearth/campfire
     * insulation. That's a deliberate correction: an earlier version of this
     * check approximated ambient °C straight from the raw Minecraft biome
     * temperature attribute, which is exactly the kind of home-grown
     * approximation Cold Sweat's own real number should be used instead of
     * whenever Cold Sweat is actually present.
     * <p>
     * Without Cold Sweat installed there's no such calculation to query, so
     * this falls back to the raw biome temperature attribute being negative
     * — cruder, but keeps the FREEZING texture meaningful even on a
     * Cold-Sweat-less install.
     */
    static boolean isCold(net.minecraft.world.level.Level level, BlockPos pos) {
        if (ColdSweatCompat.present()) {
            return ColdSweatWorldTemp.getWorldTemperatureC(level, pos) < 0;
        }
        return level.getBiome(pos).value().getBaseTemperature() < 0f;
    }

    /** Below this valve/throughput fraction the heater isn't doing anything useful — COLD (or FREEZING, see below). */
    private static final float COLD_TO_WARM_THRESHOLD = 0.25f;
    /** WARM is a deliberately wide, forgiving band straddling the "ideal" 50% valve opening. */
    private static final float WARM_TO_HOT_THRESHOLD = 0.65f;
    /** Base HOT->BLAZING cutover in a neutral-or-warm environment. */
    private static final float BLAZING_BASE_THRESHOLD = 0.88f;
    /** Extra fraction required per °C of ambient below 0°C — a heater loses more to a freezing room. */
    private static final float BLAZING_COLD_PENALTY_PER_DEGREE_C = 0.005f;
    /** However cold it gets, BLAZING must stay reachable short of a fully-open valve. */
    private static final float BLAZING_MAX_THRESHOLD = 0.97f;

    /**
     * Below the "actually producing warmth" threshold (fraction <
     * {@link #COLD_TO_WARM_THRESHOLD}), a starved/closed heater only reads as
     * Freezing in a genuinely cold environment (ambient below 0°C — snowy
     * biomes, roughly) — an idle heater sitting in a temperate or warm biome
     * is just Cold, not actively making anything worse. WARM is intentionally
     * wide (25%-65%) so the "ideal" 50% valve position doesn't need to be hit
     * precisely; reaching BLAZING gets harder the colder the room is, via
     * {@link #blazingThreshold(float)}.
     */
    static BrassHeaterBlock.HeatLevel quantizeHeatTier(float fraction, float ambientC, boolean coldBiome) {
        if (fraction < COLD_TO_WARM_THRESHOLD) {
            return coldBiome ? BrassHeaterBlock.HeatLevel.FREEZING : BrassHeaterBlock.HeatLevel.COLD;
        }
        if (fraction < WARM_TO_HOT_THRESHOLD) {
            return BrassHeaterBlock.HeatLevel.WARM;
        }
        if (fraction < blazingThreshold(ambientC)) {
            return BrassHeaterBlock.HeatLevel.HOT;
        }
        return BrassHeaterBlock.HeatLevel.BLAZING;
    }

    /** How much valve opening BLAZING needs, scaling up as the room gets colder than 0°C (capped, never unreachable). */
    private static float blazingThreshold(float ambientC) {
        if (ambientC >= 0f) {
            return BLAZING_BASE_THRESHOLD;
        }
        return Math.min(BLAZING_BASE_THRESHOLD + (-ambientC * BLAZING_COLD_PENALTY_PER_DEGREE_C), BLAZING_MAX_THRESHOLD);
    }

    /**
     * @return the fraction (0..1) of maximum steam throughput actually being
     * burned right now — the live, continuous value a future Cold Sweat
     * BlockTemp#getTemperature should read (via the BlockEntity at its
     * queried BlockPos) rather than the coarse 4-tier HEAT_LEVEL blockstate.
     */
    public float getHeatFraction() {
        return heatFraction;
    }

    @Nullable
    public IFluidHandler getSteamHandler(@Nullable Direction side) {
        return side == Direction.DOWN ? steamTank : null;
    }

    /**
     * Goggle-only readout (no native Create system surfaces any of this on
     * its own, since this block deliberately declares no stress impact — see
     * KineticBlockEntity#addToGoggleTooltip): internal steam buffer via the
     * same containedFluidTooltip helper Create's own FluidTankBlockEntity
     * uses, plus valve state and heat as a live readout for testing without a UI.
     */
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        added |= containedFluidTooltip(tooltip, isPlayerSneaking, steamTank);

        CreateLang.text("Valve: ")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.number(Math.round(pointer.getValue() * 100))
                        .text("%")
                        .style(ChatFormatting.AQUA))
                .forGoggles(tooltip, 1);

        int desired = Math.round(pointer.getValue() * MAX_STEAM_CONSUMPTION_PER_TICK);
        int drawn = Math.round(heatFraction * MAX_STEAM_CONSUMPTION_PER_TICK);
        boolean starved = desired - drawn >= 1;
        CreateLang.text("Draw: ")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.number(drawn)
                        .text(" mB/t" + (starved ? "  (starved, wants " + desired + ")" : ""))
                        .style(starved ? ChatFormatting.RED : ChatFormatting.AQUA))
                .forGoggles(tooltip, 1);

        CreateLang.text("Heat: ")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.number(Math.round(heatFraction * 100))
                        .text("% (" + getBlockState().getValue(BrassHeaterBlock.HEAT_LEVEL).getSerializedName() + ")")
                        .style(ChatFormatting.GOLD))
                .forGoggles(tooltip, 1);

        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("SteamTank", steamTank.writeToNBT(registries, new CompoundTag()));
        tag.putFloat("HeatFraction", heatFraction);
        tag.put("Pointer", pointer.writeNBT());
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        steamTank.readFromNBT(registries, tag.getCompound("SteamTank"));
        heatFraction = tag.getFloat("HeatFraction");
        pointer.readNBT(tag.getCompound("Pointer"), clientPacket);
    }
}
