package com.cio.createinteroperable;

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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
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
 * The shaft on the back face (see SteamOutletBlock#hasShaftTowards) drives a
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
    /** mB of steam produced per tick at 100% engine efficiency while the valve is open. Tunable. */
    private static final int STEAM_PER_TICK_AT_FULL_EFFICIENCY = 25;
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
            tickVentParticles();
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
        if (valvePosition <= 0) {
            return;
        }

        FluidTankBlockEntity controller = getBoilerController();
        if (controller == null || controller.boiler == null || !controller.boiler.isActive()) {
            return;
        }

        float efficiency = Mth.clamp(controller.boiler.getEngineEfficiency(controller.getTotalTankSize()), 0, 1);
        if (efficiency <= 0) {
            return;
        }

        int produced = Math.round(efficiency * valvePosition * STEAM_PER_TICK_AT_FULL_EFFICIENCY);
        if (produced <= 0) {
            return;
        }

        steamTank.fill(new FluidStack(CIOFluids.STEAM_STILL.get(), produced), IFluidHandler.FluidAction.EXECUTE);
    }

    /**
     * Client-only visual: puffs of white smoke while the valve is open and
     * there's actually steam being vented — both the OPEN blockstate and the
     * tank's contents are already synced to the client via normal block
     * update/BE-sync packets, so no extra networking is needed. Loosely
     * modeled on how Create's own OpenEndedPipe reacts to fluid escaping into
     * open air, but done as a simple standalone puff rather than hooking
     * Create's OpenPipeEffectHandler registry (that registry is for
     * server-side world effects like extinguishing fire, not a particle
     * system, and Create's real fluid-particle helpers are a much heavier
     * dependency than a plain vanilla smoke puff needs).
     */
    private void tickVentParticles() {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof SteamOutletBlock)) {
            return;
        }
        if (!state.getValue(SteamOutletBlock.OPEN) || steamTank.getFluidAmount() <= 0) {
            return;
        }
        if (level.random.nextInt(6) != 0) {
            return;
        }
        double x = worldPosition.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.4;
        double y = worldPosition.getY() + 0.9;
        double z = worldPosition.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.4;
        level.addParticle(ParticleTypes.WHITE_SMOKE, x, y, z, 0, 0.02, 0);
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

            int output = Math.round(efficiency * pointer.getValue() * STEAM_PER_TICK_AT_FULL_EFFICIENCY);
            boolean bufferFull = steamTank.getFluidAmount() >= steamTank.getCapacity();
            CreateLang.text("Output: ")
                    .style(ChatFormatting.GRAY)
                    .add(CreateLang.number(output)
                            .text(" mB/t" + (bufferFull ? "  (buffer full)" : ""))
                            .style(bufferFull ? ChatFormatting.RED : ChatFormatting.AQUA))
                    .forGoggles(tooltip, 1);
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
