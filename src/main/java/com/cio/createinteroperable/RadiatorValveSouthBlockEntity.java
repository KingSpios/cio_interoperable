package com.cio.createinteroperable;

import com.simibubi.create.AllSoundEvents;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The water-output half of a Multi Radiator — see {@link RadiatorValveSouthBlock}
 * for the shared FACING/shaft/capability reasoning and
 * {@link RadiatorValveNorthBlockEntity} for the steam-consuming partner that
 * feeds this end via {@link #receiveCondensate}.
 * <p>
 * Runs its own independent shaft-driven {@link #pointer} (0..1) — the two
 * ends are deliberately separate valves, not one shared control: North's
 * valve decides how much steam is burned (and therefore how much condensate
 * is produced); South's own valve is currently a plain open/shut gate on
 * whether the buffer is extractable AT ALL (see {@link #getWaterHandler}),
 * not yet a proportional rate limiter — see TODO.
 * <p>
 * If the valve is open but nothing on the DOWN face will actually take the
 * water (no pipe, no pump, no tank — see {@link #isConnectedBelow}), this end
 * drips it away instead of just letting the buffer cap out silently: a
 * vanilla-dripstone-style particle + sound, and a real attempt to fill an
 * actual cauldron underneath if one is in range (see {@link #fillCauldronBelow}) —
 * not purely cosmetic, the water is genuinely gone. That's deliberate and is
 * the whole point: whatever boiler supplied the steam this condensate came
 * from never gets it back, so that boiler's own water level — and therefore
 * its real, already-existing Create-simulated efficiency — degrades on its
 * own over time. No boiler-provenance tracking needed here at all; that
 * consequence falls straight out of Create's own water/steam simulation once
 * the water is actually lost instead of sitting inertly in a full tank.
 * <p>
 * TODO(design, scaffold-only): South's valve should probably throttle
 * extraction proportionally (mirroring how North's valve throttles
 * consumption), not just gate it open/shut — deferred because that needs a
 * small stateful IFluidHandler wrapper (a per-tick release budget reset in
 * {@link #tick()}), which felt like more mechanism than this scaffold pass
 * should commit to before the rate is even decided.
 */
public class RadiatorValveSouthBlockEntity extends KineticBlockEntity {
    private static final int TANK_CAPACITY_MB = 2048;

    private final FluidTank waterTank = new FluidTank(TANK_CAPACITY_MB) {
        @Override
        public boolean isFluidValid(FluidStack stack) {
            return stack.getFluid() == Fluids.WATER;
        }
    };

    private final LerpedFloat pointer = LerpedFloat.linear()
            .startWithValue(0)
            .chase(0, 0, Chaser.LINEAR);

    @Nullable
    private BlockPos northPos;
    private int middleCount;

    public RadiatorValveSouthBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Called once by {@link RadiatorAssembly#tryAssemble} on a successful assembly. */
    public void setAssembledPartner(BlockPos northPos, int middleCount) {
        this.northPos = northPos.immutable();
        this.middleCount = middleCount;
        setChanged();
    }

    /**
     * Called by the assembled North partner's own tick once it's actually
     * burned steam this tick — see RadiatorValveNorthBlockEntity#tick.
     * Gains nothing beyond {@link #waterTank}'s own capacity (excess is
     * silently discarded — see class TODO).
     */
    public void receiveCondensate(int mb) {
        if (mb > 0) {
            waterTank.fill(new FluidStack(Fluids.WATER, mb), IFluidHandler.FluidAction.EXECUTE);
        }
    }

    /** @return whether the water buffer is completely full — see RadiatorValveNorthBlockEntity#isSouthWaterFull, the "clogged" gate. */
    public boolean isWaterFull() {
        return waterTank.getFluidAmount() >= waterTank.getCapacity();
    }

    /** @return 0..1 fill fraction of the shared body-wide water buffer — also surfaced on the Inlet's own goggle tooltip, see RadiatorValveNorthBlockEntity#addToGoggleTooltip. */
    public float getWaterFraction() {
        int capacity = waterTank.getCapacity();
        return capacity > 0 ? (float) waterTank.getFluidAmount() / capacity : 0f;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // Purely speed-driven, same as the North end.
    }

    @Override
    public void onSpeedChanged(float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        pointer.chase(getSpeed() > 0 ? 1 : 0, getChaseSpeed(), Chaser.LINEAR);
        pointer.forceNextSync();
        // Same real Steam Engine puff as SteamOutletBlockEntity's own valve —
        // see its onSpeedChanged for why this is #play (server-broadcast),
        // not #playAt (client-local no-op on a real server).
        if (level != null && !level.isClientSide) {
            AllSoundEvents.STEAM.play(level, null, worldPosition, 0.6f,
                    0.9f + level.random.nextFloat() * 0.2f);
        }
    }

    private float getChaseSpeed() {
        return Mth.clamp(Math.abs(getSpeed()) / 16 / 20, 0, 1);
    }

    /** How often (in ticks) an unconnected, open outlet checks whether it should drip. Matches a slow, lazy dripstone pace. */
    private static final int DRIP_INTERVAL_TICKS = 40;
    /** mB lost per drip event — deliberately small; a fully-open, fully-supplied radiator still overflows the tank faster than this trickles it away. */
    private static final int DRIP_AMOUNT_MB = 50;
    /** Matches vanilla's own PointedDripstoneBlock -> cauldron search depth. */
    private static final int CAULDRON_SEARCH_RANGE = 11;

    @Override
    public void tick() {
        super.tick();
        pointer.tickChaser();
        // Actual release to the outside world otherwise happens on demand,
        // when something downstream (a pipe, a boiler) drains
        // getWaterHandler — this end doesn't need to actively push anything
        // itself in that case, same as how SteamOutletBlockEntity's own
        // buffer works. The valve instead gates capability EXTRACT below
        // (see getWaterHandler). This dripping path is the one case where
        // this end DOES act on its own: an open valve with nothing plumbed
        // in below has to lose its condensate somewhere visible, or a closed
        // loop and a "forgot to pipe it back" loop would be indistinguishable
        // to the player — see class doc.
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // getSpeed() == 0 means this end's own shaft is unpowered right now —
        // requested explicitly so a radiator sitting idle (no kinetic input)
        // never drips, even if the valve was left cracked open from when it
        // last had power (the valve position itself only chases back to
        // closed gradually, see onSpeedChanged, so without this a freshly
        // depowered radiator could keep leaking for a moment after).
        if (getSpeed() == 0 || pointer.getValue() <= 0 || waterTank.getFluidAmount() <= 0 || isConnectedBelow()) {
            return;
        }
        if ((serverLevel.getGameTime() + worldPosition.hashCode()) % DRIP_INTERVAL_TICKS != 0) {
            return;
        }
        drip(serverLevel);
    }

    /** @return whether something on the DOWN face would actually accept this block's water (a pipe, a tank, a pump intake). */
    private boolean isConnectedBelow() {
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, worldPosition.below(), Direction.UP);
        return handler != null;
    }

    /**
     * Drains a small amount from {@link #waterTank}, plays the same drip
     * particle/sound vanilla's own dripstone uses, and — exactly like a real
     * dripstone stalactite — tries to actually fill a cauldron underneath if
     * one is in range, rather than the loss being purely cosmetic. Water lost
     * this way never makes it back to whatever boiler supplied the steam, so
     * that boiler's own water level (and therefore its real Create-simulated
     * efficiency) genuinely suffers over time — no extra bookkeeping needed
     * on this end for that consequence, it falls straight out of Create's own
     * boiler/water simulation once the water is actually gone instead of
     * sitting inertly capped in a full buffer.
     */
    private void drip(ServerLevel serverLevel) {
        FluidStack drained = waterTank.drain(DRIP_AMOUNT_MB, IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) {
            return;
        }
        serverLevel.sendParticles(ParticleTypes.DRIPPING_WATER,
                worldPosition.getX() + 0.5, worldPosition.getY() - 0.05, worldPosition.getZ() + 0.5,
                1, 0.0, 0.0, 0.0, 0.0);
        serverLevel.playSound(null, worldPosition, SoundEvents.POINTED_DRIPSTONE_DRIP_WATER, SoundSource.BLOCKS,
                0.3f, 1.0f + (serverLevel.random.nextFloat() - 0.5f) * 0.2f);
        fillCauldronBelow(serverLevel);
    }

    /** Same search-down-through-air, fill-if-found idea as vanilla's own dripstone -> cauldron mechanic, reimplemented directly since that method isn't public. */
    private void fillCauldronBelow(ServerLevel serverLevel) {
        BlockPos pos = worldPosition.below();
        for (int i = 0; i < CAULDRON_SEARCH_RANGE; i++) {
            BlockState state = serverLevel.getBlockState(pos);
            if (state.is(Blocks.CAULDRON)) {
                serverLevel.setBlockAndUpdate(pos, Blocks.WATER_CAULDRON.defaultBlockState());
                return;
            }
            if (state.is(Blocks.WATER_CAULDRON)) {
                int fillLevel = state.getValue(LayeredCauldronBlock.LEVEL);
                if (fillLevel < LayeredCauldronBlock.MAX_FILL_LEVEL) {
                    serverLevel.setBlockAndUpdate(pos, state.setValue(LayeredCauldronBlock.LEVEL, fillLevel + 1));
                }
                return;
            }
            if (!state.isAir()) {
                return;
            }
            pos = pos.below();
        }
    }

    /**
     * @return this block's internal water buffer when queried from directly
     * below — same convention as {@link RadiatorValveNorthBlockEntity#getSteamHandler}
     * (and NOT the FACING face, which is shaft/valve only) — gated on this
     * end's own valve position (fully closed = nothing extractable,
     * regardless of how much condensate is buffered).
     */
    @Nullable
    public IFluidHandler getWaterHandler(@Nullable Direction side) {
        if (side != Direction.DOWN) {
            return null;
        }
        // Plain open/shut gate for now — see class TODO for the intended
        // proportional-rate-limit upgrade.
        return pointer.getValue() > 0 ? waterTank : null;
    }

    /** @return 0..1, how open this end's own release valve currently is. */
    public float getValvePosition() {
        return pointer.getValue();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        CreateLang.text("Water Outlet")
                .style(ChatFormatting.WHITE)
                .forGoggles(tooltip);
        containedFluidTooltip(tooltip, isPlayerSneaking, waterTank);

        BlockState state = getBlockState();
        boolean assembled = state.getBlock() instanceof RadiatorValveSouthBlock && state.getValue(RadiatorValveSouthBlock.ASSEMBLED);
        CreateLang.text("Assembled: ")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.text(assembled ? "Yes (" + middleCount + " middle)" : "No")
                        .style(assembled ? ChatFormatting.GREEN : ChatFormatting.RED))
                .forGoggles(tooltip, 1);

        CreateLang.text("Valve: ")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.number(Math.round(pointer.getValue() * 100))
                        .text("%")
                        .style(ChatFormatting.AQUA))
                .forGoggles(tooltip, 1);

        if (pointer.getValue() > 0) {
            boolean connected = level != null && isConnectedBelow();
            CreateLang.text("Status: ")
                    .style(ChatFormatting.GRAY)
                    .add(CreateLang.text(connected ? "Connected" : "Dripping (wasting water)")
                            .style(connected ? ChatFormatting.GREEN : ChatFormatting.GOLD))
                    .forGoggles(tooltip, 1);
        }

        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("WaterTank", waterTank.writeToNBT(registries, new CompoundTag()));
        tag.put("Pointer", pointer.writeNBT());
        if (northPos != null) {
            tag.putLong("NorthPos", northPos.asLong());
            tag.putInt("MiddleCount", middleCount);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        waterTank.readFromNBT(registries, tag.getCompound("WaterTank"));
        pointer.readNBT(tag.getCompound("Pointer"), clientPacket);
        if (tag.contains("NorthPos")) {
            northPos = BlockPos.of(tag.getLong("NorthPos"));
            middleCount = tag.getInt("MiddleCount");
        }
    }
}
