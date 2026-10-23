package com.cio.createinteroperable.telephone.cee;

import com.cio.createinteroperable.Lang;
import com.cio.createinteroperable.CIOValueBox;
import com.cio.createinteroperable.telephone.CioTelephone;
import com.cio.createinteroperable.telephone.TelephoneAutoAnswerBehaviour;
import com.cio.createinteroperable.telephone.TelephoneCore;
import com.cio.createinteroperable.telephone.TelephoneRegistry;
import com.cio.createinteroperable.telephone.interop.TelephoneBlockEntity;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.api.equipment.goggles.IHaveHoveringInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * "CEE Telephone" BlockEntity — Electro-Energetics-only twin of the
 * Interoperable {@link TelephoneBlockEntity}. Extends Create's own
 * {@link SmartBlockEntity} directly (no Power Grid class anywhere in this
 * hierarchy) and drives its positive/negative load entirely through
 * {@link TelephoneDevice} — the same device class the Interoperable phone
 * uses while CEE holds the wire lock, reused here unconditionally. Call
 * logic, settings and NBT are shared with the other two phones in
 * {@link TelephoneCore}.
 *
 * <p>One real capability gap versus the PG-having variants: the listener
 * terminal (a real 12V source while ringing/active, so a load genuinely
 * plugged into it draws real power) has no CEE equivalent at all —
 * {@link TelephoneDevice} only ever models the positive/negative resistive
 * load, never a listener source, even on the Interoperable phone. So this
 * variant tracks ringing/busy/answered purely for call-state purposes (the
 * particles, the auto-answer timer, the tooltip); it never energises
 * anything from the model's listener nub.</p>
 */
public class CeeTelephoneBlockEntity extends SmartBlockEntity implements IHaveHoveringInformation, CioTelephone {
    private final TelephoneCore core = new TelephoneCore(this);
    @Nullable
    private TelephoneDevice ceeDevice;
    private TelephoneAutoAnswerBehaviour autoAnswerValue;

    public CeeTelephoneBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        autoAnswerValue = new TelephoneAutoAnswerBehaviour(this, slot(12.05, 10.5, 12.5, 270));
        behaviours.add(autoAnswerValue);
    }

    /** Slider box on the face a legacy {@code baseAngle} pointed at, carried through the block's own facing (see {@link CIOValueBox}). */
    static CIOValueBox slot(double x, double y, double z, int baseAngle) {
        return new CIOValueBox(x, y, z, CIOValueBox.faceForBaseAngle(baseAngle), 5 / 16f,
                (state, v) -> CeeTelephoneBlock.rotateY(v, CeeTelephoneBlock.angleFor(state))).fromAnySide();
    }

    @Override
    public TelephoneCore telephoneCore() {
        return core;
    }

    @Override
    public TelephoneAutoAnswerBehaviour autoAnswerBehaviour() {
        return autoAnswerValue;
    }

    @Override
    public Component telephoneName() {
        return Lang.component("telephone.name.cee");
    }

    @Override
    public Component wiringLabel() {
        return Lang.component("telephone.wiring.cee");
    }

    /**
     * Create's own Desk Bell ding — the CEE-safe counterpart to the
     * CPG/Interoperable variants' PG-specific ModdedSoundEvents.ALARM_BELL
     * (this block has no PG type anywhere in its hierarchy). Thematically
     * exact too: the phone is crafted from a create:desk_bell.
     */
    @Override
    public void playRingSound() {
        AllSoundEvents.DESK_BELL_USE.play(level, null, worldPosition);
    }

    @Override
    public Object pgTapNetwork() {
        return null;
    }

    @Override
    public boolean ceeTapReaches(BlockPos otherPos) {
        return level instanceof ServerLevel serverLevel && TelephoneDevice.tapReaches(serverLevel, worldPosition, otherPos);
    }

    boolean isDialingOwnNumber() {
        return core.isDialingOwnNumber();
    }

    boolean isDialingBusyNumber() {
        return core.isDialingBusyNumber();
    }

    @Override
    public void tick() {
        if (level != null && !level.isClientSide) {
            serverTick((ServerLevel) level);
        }
        super.tick();
        if (level != null && level.isClientSide) {
            core.clientTick();
        }
    }

    private void serverTick(ServerLevel server) {
        if (core.isExploded()) {
            return;
        }
        core.advancePulse();
        if (ceeDevice == null || !ceeDevice.isValid()) {
            ceeDevice = DevicesSavedData.load(server).getDevice(worldPosition, TelephoneDevice.class);
        }
        if (ceeDevice != null) {
            // Re-synced every tick (not just on transitions) so a freshly
            // re-acquired device instance never misses the current state.
            // Only the receiving end of an answered call closes its
            // breakers — the phone that dialed out never does.
            ceeDevice.setAnswered(core.callOutputsClosed());
        }
        core.serverTick(ceeDevice != null ? Math.abs(ceeDevice.getLastVoltage()) : 0.0);
        if (core.isExploded()) {
            return;
        }

        if ((level.getGameTime() & 7L) == 0L) {
            notifyUpdate();
        }
    }

    InteractionResult onFrontUsed(Player player) {
        return core.onFrontUsed(player);
    }

    InteractionResult onTopUsed(Player player) {
        return core.onTopUsed(player);
    }

    InteractionResult onBackPlateUsed(Player player) {
        return core.onBackPlateUsed(player);
    }

    InteractionResult onHandsetUsed(Player player) {
        return core.onHandsetUsed(player);
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        TelephoneRegistry.add(this);
    }

    @Override
    public void remove() {
        super.remove();
        TelephoneRegistry.remove(this);
    }

    @Override
    public boolean addToTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        core.addToTooltip(tooltip);
        return true;
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        core.read(tag);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        core.write(tag);
    }
}
