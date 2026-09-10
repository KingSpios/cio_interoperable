package com.cio.createinteroperable;

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
 * produces the 4-tier HEAT_LEVEL display's real behavior: a fully open valve
 * with no steam coming in still shows COLD, same as a real heater whose
 * valve is open but starved of fuel.
 * <p>
 * {@link #getHeatFraction()} is the intended hook point for a future Cold
 * Sweat BlockTemp registration (see the block-level javadoc and the
 * conversation this was designed in) — deferred pending Cold Sweat being
 * added as a real build dependency; nothing here depends on Cold Sweat being
 * present.
 */
public class BrassHeaterBlockEntity extends KineticBlockEntity {
    /** mB of steam drawn per tick while the valve is open and supply allows it. Tunable. */
    private static final int MAX_STEAM_CONSUMPTION_PER_TICK = 20;
    private static final int TANK_CAPACITY_MB = 2000;

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
            .startWithValue(0)
            .chase(0, 0, Chaser.LINEAR);

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

        float newHeatFraction = 0;
        if (desired > 0) {
            FluidStack drained = steamTank.drain(desired, IFluidHandler.FluidAction.EXECUTE);
            newHeatFraction = (float) drained.getAmount() / MAX_STEAM_CONSUMPTION_PER_TICK;
        }

        if (Math.abs(newHeatFraction - heatFraction) > 0.001f) {
            heatFraction = newHeatFraction;
            updateHeatLevelState();
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
        BrassHeaterBlock.HeatLevel newTier = quantizeHeatTier(heatFraction);
        BlockState state = getBlockState();
        if (state.getValue(BrassHeaterBlock.HEAT_LEVEL) != newTier) {
            level.setBlockAndUpdate(worldPosition, state.setValue(BrassHeaterBlock.HEAT_LEVEL, newTier));
        }
    }

    private static BrassHeaterBlock.HeatLevel quantizeHeatTier(float fraction) {
        if (fraction >= 0.75f) {
            return BrassHeaterBlock.HeatLevel.BLAZING;
        }
        if (fraction >= 0.45f) {
            return BrassHeaterBlock.HeatLevel.HOT;
        }
        if (fraction >= 0.15f) {
            return BrassHeaterBlock.HeatLevel.WARM;
        }
        return BrassHeaterBlock.HeatLevel.COLD;
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
