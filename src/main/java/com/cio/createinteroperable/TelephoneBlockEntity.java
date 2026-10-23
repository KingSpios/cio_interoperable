package com.cio.createinteroperable;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.simibubi.create.api.equipment.goggles.IHaveHoveringInformation;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.collections.ModdedSoundEvents;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.utility.sound.SoundScapes;

import java.util.List;

/**
 * The Interoperable Telephone — needs both Power Grid and Electro Energetics.
 * See TelephoneBlock's own class doc for the model-derived terminal/interaction
 * layout. Electrically: real PG terminals (positive/negative/tap/listener),
 * with a live WIRE_LOCK arbitrating whether a PG or a CEE wire currently owns
 * the shared positive/negative nubs (see TelephoneBlock#WIRE_LOCK). Positive/
 * negative form a fixed-resistance load (powered = ~12V detected across
 * them). Tap is deliberately NOT a live/pulsing EMF — CIO already hit a real
 * solver-stability bug doing that kind of bidirectional live-voltage bridging
 * for the Interoperable Transformer (see InteroperableDevice's class doc) —
 * instead reachability is purely {@link IElectricNode#getNetwork()} identity
 * comparison between phones' tap nodes. tapNode has NO internal circuit
 * connection (see the comment in buildCircuit) so that identity reflects only
 * real external tap-to-tap wiring, not the shared power rail. Call logic,
 * settings and NBT are shared with the other two phones in
 * {@link TelephoneCore}.
 *
 * <p>The listener terminal and the two bottom Feed +/- terminals are all
 * breakers ({@link SwitchedWire}, near-zero resistance closed / open
 * otherwise) — not voltage sources, and not merely gated on the phone being
 * powered. They close only on the <b>receiving</b> end of an answered,
 * ongoing call: the phone that dialed out never closes its own (see
 * {@link #onCallOutputsChanged()}). The listener completes (or breaks)
 * whatever external, independently-powered circuit the player wires between
 * it and the negative rail, up to PG's own higher voltage tiers — the phone
 * doesn't supply that circuit's own power, it only gates it, like a real
 * breaker. Same design as {@link CpgTelephoneBlockEntity}.</p>
 *
 * <p>See {@link CpgTelephoneBlockEntity}/{@link CeeTelephoneBlockEntity} for
 * the single-protocol siblings — those need no WIRE_LOCK arbitration at all,
 * since there's only ever one protocol competing for the terminals.</p>
 */
public class TelephoneBlockEntity extends ElectricBlockEntity implements IHaveHoveringInformation, CioTelephone {
    /**
     * Package-visible so TelephoneBlock can disclose it in the item tooltip
     * (see TelephoneBlock#appendProperties). 200 ohms — the middle of a real
     * analog telephone's actual off-hook DC loop resistance range
     * (~150-600 ohms; the commonly-cited "600 ohm" figure is really the AC
     * impedance-matching target, not raw DC resistance), and noticeably
     * higher than PG's own LV Light Bulb's 20-48 ohm range since a phone
     * doesn't need to draw enough current to visibly glow.
     */
    static final float COIL_RESISTANCE = 200f;
    private static final float DELIVERY_RESISTANCE = 0.05f;
    /** Near-zero pass-through resistance for the bottom Feed +/- terminals — same convention as the Power Kit's Power Feed. */
    private static final float FEED_PASSTHROUGH_R = 0.001f;

    private final TelephoneCore core = new TelephoneCore(this);
    private IElectricNode positiveNode, negativeNode, tapNode;
    private SwitchedWire listenerBreaker, outletPositiveBreaker, outletNegativeBreaker;
    /** CEE's side of positive/negative, only actually delivering while WIRE_LOCK == CEE — see #electricalTick/#updateWireLock. */
    @Nullable
    private TelephoneDevice ceeDevice;
    private TelephoneAutoAnswerBehaviour autoAnswerValue;

    public TelephoneBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);

        // Only Auto-Answer is an in-world slider (on auto_lever); own number,
        // area code, label and the number to call are all set from the
        // back_plate settings screen (see TelephoneAutoAnswerBehaviour for why
        // the old Area Code slider went).
        autoAnswerValue = new TelephoneAutoAnswerBehaviour(this, slot(12.05, 10.5, 12.5, 270));
        behaviours.add(autoAnswerValue);
    }

    /** Slider box on the face a legacy {@code baseAngle} pointed at, carried through the block's own facing (see {@link CIOValueBox}). */
    static CIOValueBox slot(double x, double y, double z, int baseAngle) {
        return new CIOValueBox(x, y, z, CIOValueBox.faceForBaseAngle(baseAngle), 5 / 16f,
                (state, v) -> TelephoneBlock.rotateY(v, TelephoneBlock.angleFor(state))).fromAnySide();
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
    public String telephoneName() {
        return "Interoperable Telephone";
    }

    @Override
    public String wiringLabel() {
        return switch (getBlockState().getValue(TelephoneBlock.WIRE_LOCK)) {
            case PG -> "CPG";
            case CEE -> "CEE";
            case NONE -> "Free";
        };
    }

    @Override
    public void playRingSound() {
        level.playSound(null, worldPosition, ModdedSoundEvents.ALARM_BELL.getMainEvent(), SoundSource.BLOCKS, 1f, 1f);
    }

    /** A live PG wire actually attached to the tap terminal (index 2); a cut wire must not leave a stale network match. */
    private boolean isTapWired() {
        return electricBehaviour != null && electricBehaviour.getConnections()
                .containsKey(new BlockWireEndpoint(worldPosition, 2));
    }

    @Override
    public Object pgTapNetwork() {
        return tapNode == null || !isTapWired() ? null : tapNode.getNetwork();
    }

    /** Walks CEE wires from this phone's tap node (id 2, see TelephoneBlock#CEE_TAP_BASE) — see {@link TelephoneDevice#tapReaches}. */
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
    public void buildCircuit(CircuitBuilder builder) {
        builder.setTerminalCount(6);
        positiveNode = builder.terminalNode(0);
        negativeNode = builder.terminalNode(1);
        tapNode = builder.terminalNode(2);
        IElectricNode listenerNode = builder.terminalNode(3);
        IElectricNode outletPositiveNode = builder.terminalNode(4);
        IElectricNode outletNegativeNode = builder.terminalNode(5);

        builder.connect(COIL_RESISTANCE, positiveNode, negativeNode);
        // tapNode is deliberately left with NO internal connection. It used
        // to be tied to negativeNode through SENSE_RESISTANCE just to give
        // it a valid node identity — but negativeNode is externally wired to
        // a shared power rail across every phone, and PG's solver merges
        // any conductively-linked nodes (internal connections included) into
        // one ElectricalNetwork. That let tapNode#getNetwork() leak through
        // the shared power wiring: any two phones sharing power were always
        // "on the same network" for dialing purposes, even with their TAP
        // terminals never wired together or after that wire was cut. Left
        // fully unconnected, tapNode's network identity is determined
        // purely by real external tap-to-tap wiring — null until an actual
        // tap wire is placed, exactly matching dial()'s existing null check.

        // Bottom Feed +/- terminals: a breaker onto the same positive/negative
        // rail, closed (near-zero resistance) only on the receiving end of an
        // answered, ongoing call — see #onCallOutputsChanged.
        outletPositiveBreaker = builder.connectSwitch(FEED_PASSTHROUGH_R, positiveNode, outletPositiveNode, false);
        outletNegativeBreaker = builder.connectSwitch(FEED_PASSTHROUGH_R, negativeNode, outletNegativeNode, false);

        // Middle bottom output (listener): a breaker completing/breaking
        // whatever external, independently-powered circuit the player wires
        // between it and the negative rail — up to PG's own higher voltage
        // tiers. Not a voltage source: the phone doesn't power that circuit,
        // it only gates it, closed exactly while the receiving end of a call
        // is answered.
        listenerBreaker = builder.connectSwitch(DELIVERY_RESISTANCE, listenerNode, negativeNode, false);
    }

    /**
     * Closes the listener/Feed breakers only on the receiving end of an
     * answered, ongoing call — the phone that dialed out never closes its own
     * (see {@link TelephoneCore#callOutputsClosed()}). Open otherwise.
     */
    @Override
    public void onCallOutputsChanged() {
        boolean closed = core.callOutputsClosed();
        if (listenerBreaker != null) {
            listenerBreaker.setState(closed);
        }
        if (outletPositiveBreaker != null) {
            outletPositiveBreaker.setState(closed);
        }
        if (outletNegativeBreaker != null) {
            outletNegativeBreaker.setState(closed);
        }
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    @Override
    public void electricalTick() {
        super.electricalTick();
        applyPower(null);
        core.advancePulse();

        updateWireLock();
        boolean ceeActive = getBlockState().getValue(TelephoneBlock.WIRE_LOCK) == TelephoneBlock.WireLock.CEE;
        if (level instanceof ServerLevel serverLevel && (ceeDevice == null || !ceeDevice.isValid())) {
            ceeDevice = DevicesSavedData.load(serverLevel).getDevice(worldPosition, TelephoneDevice.class);
        }

        double voltage = ceeActive && ceeDevice != null
                ? Math.abs(ceeDevice.getLastVoltage())
                : Math.abs(positiveNode.getVoltage() - negativeNode.getVoltage());
        core.serverTick(voltage);
    }

    /**
     * A live, active PG wire on terminal 0 or 1 (positive/negative) — not
     * just "a PG network is present," an actual attached wire.
     * electricBehaviour (protected, inherited from ElectricBlockEntity)
     * tracks every real connection per BlockWireEndpoint; a present, non-
     * empty entry means a wire is genuinely plugged in there.
     */
    private boolean isPgWired() {
        if (electricBehaviour == null) {
            return false;
        }
        var connections = electricBehaviour.getConnections();
        return connections.containsKey(new BlockWireEndpoint(worldPosition, 0))
                || connections.containsKey(new BlockWireEndpoint(worldPosition, 1));
    }

    /**
     * Same idea, CEE side, node ids 0/1 (matching TelephoneBlock's
     * getNodePositions). InfrastructureSavedData#getConnections gracefully
     * returns an empty list for an unregistered/unwired node — confirmed
     * via its real bytecode, never null, never throws — so this is safe to
     * call unconditionally.
     */
    private boolean isCeeWired(ServerLevel serverLevel) {
        InfrastructureSavedData sd = InfrastructureSavedData.load(serverLevel);
        return !sd.getConnections(new InWorldNode(0, worldPosition)).isEmpty()
                || !sd.getConnections(new InWorldNode(1, worldPosition)).isEmpty();
    }

    /**
     * Polled every electrical tick rather than event-driven — PG exposes no
     * "a wire just connected/disconnected" callback to hook, so this mirrors
     * the same periodic-recheck idiom the call-connection check already uses.
     * Known, accepted gap: a wire from the currently-losing protocol landing
     * in the same ~1-tick window before this catches up could theoretically
     * slip through — CEE's own isNodeAccessible (TelephoneBlock) is
     * instant/synchronous, but PG has no equivalent, only this poll.
     */
    private void updateWireLock() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        boolean pg = isPgWired();
        boolean cee = isCeeWired(serverLevel);
        TelephoneBlock.WireLock desired = pg ? TelephoneBlock.WireLock.PG
                : cee ? TelephoneBlock.WireLock.CEE : TelephoneBlock.WireLock.NONE;
        BlockState state = getBlockState();
        if (state.getValue(TelephoneBlock.WIRE_LOCK) != desired) {
            level.setBlock(worldPosition, state.setValue(TelephoneBlock.WIRE_LOCK, desired), Block.UPDATE_ALL);
        }
    }

    /** Low hum while overheating (20V-24V), same real sound/mechanism as PG's own Transformer — see InteroperableSmallBlockEntity#tickAudio. */
    @Override
    public void tickAudio() {
        if (core.isOverheating()) {
            SoundScapes.play(SoundScapes.AmbienceGroup.HUM, worldPosition, 1f, 1f);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level != null && level.isClientSide) {
            core.clientTick();
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
