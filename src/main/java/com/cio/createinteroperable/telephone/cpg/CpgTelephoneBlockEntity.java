package com.cio.createinteroperable.telephone.cpg;

import com.cio.createinteroperable.Lang;
import com.cio.createinteroperable.CIOValueBox;
import com.cio.createinteroperable.telephone.CioTelephone;
import com.cio.createinteroperable.telephone.TelephoneAutoAnswerBehaviour;
import com.cio.createinteroperable.telephone.TelephoneCore;
import com.cio.createinteroperable.telephone.TelephoneRegistry;
import com.cio.createinteroperable.telephone.interop.TelephoneBlockEntity;
import com.simibubi.create.api.equipment.goggles.IHaveHoveringInformation;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.collections.ModdedSoundEvents;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.utility.sound.SoundScapes;

import java.util.List;

/**
 * "CPG Telephone" BlockEntity — Power-Grid-only twin of the Interoperable
 * {@link TelephoneBlockEntity}. Same electrical model (positive/negative
 * form a resistive load, tap has no internal connection so its network
 * identity reflects only real external tap wiring), but the positive/negative
 * pair is always live — no WIRE_LOCK arbitration, since there's no second
 * protocol competing for it. Call logic, settings and NBT are shared with the
 * other two phones in {@link TelephoneCore}.
 *
 * <p>The listener terminal and the two bottom Feed +/- terminals are all
 * breakers ({@link SwitchedWire}, near-zero resistance closed / open
 * otherwise) closed only while a call is actively answered — not a voltage
 * source, and not merely gated on the phone being powered. The listener in
 * particular completes (or breaks) whatever external, independently-powered
 * circuit the player wires between it and the negative rail, up to PG's own
 * higher voltage tiers — the phone doesn't supply that circuit's own power,
 * it just gates it, like a real breaker.</p>
 */
public class CpgTelephoneBlockEntity extends ElectricBlockEntity implements IHaveHoveringInformation, CioTelephone {
    static final float COIL_RESISTANCE = 200f;
    private static final float DELIVERY_RESISTANCE = 0.05f;
    /** Near-zero pass-through resistance for the bottom Feed +/- terminals — same convention as the Power Kit's Power Feed. */
    private static final float FEED_PASSTHROUGH_R = 0.001f;

    private final TelephoneCore core = new TelephoneCore(this);
    private IElectricNode positiveNode, negativeNode, tapNode;
    private SwitchedWire listenerBreaker, outletPositiveBreaker, outletNegativeBreaker;
    private TelephoneAutoAnswerBehaviour autoAnswerValue;

    public CpgTelephoneBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        autoAnswerValue = new TelephoneAutoAnswerBehaviour(this, slot(12.05, 10.5, 12.5, 270));
        behaviours.add(autoAnswerValue);
    }

    /** Slider box on the face a legacy {@code baseAngle} pointed at, carried through the block's own facing (see {@link CIOValueBox}). */
    static CIOValueBox slot(double x, double y, double z, int baseAngle) {
        return new CIOValueBox(x, y, z, CIOValueBox.faceForBaseAngle(baseAngle), 5 / 16f,
                (state, v) -> CpgTelephoneBlock.rotateY(v, CpgTelephoneBlock.angleFor(state))).fromAnySide();
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
        return Lang.component("telephone.name.cpg");
    }

    @Override
    public Component wiringLabel() {
        return Lang.component("telephone.wiring.cpg");
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

    @Override
    public boolean ceeTapReaches(BlockPos otherPos) {
        return false;
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
        // tapNode is deliberately left with NO internal connection — see
        // TelephoneBlockEntity#buildCircuit's own doc for why.

        // Bottom Feed +/- terminals: a breaker onto the same positive/negative
        // rail, closed (near-zero resistance) only while a call is actively
        // answered — merely being powered is not enough. Same convention as
        // the Power Kit's own Power Feed, but gated on call state instead of
        // always-on.
        outletPositiveBreaker = builder.connectSwitch(FEED_PASSTHROUGH_R, positiveNode, outletPositiveNode, false);
        outletNegativeBreaker = builder.connectSwitch(FEED_PASSTHROUGH_R, negativeNode, outletNegativeNode, false);

        // Middle bottom output (listener): a breaker completing/breaking
        // whatever external, independently-powered circuit the player wires
        // between it and the negative rail — up to PG's own higher voltage
        // tiers. Not a voltage source: the phone doesn't power that circuit,
        // it only gates it, closed exactly while a call is answered.
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
        core.serverTick(Math.abs(positiveNode.getVoltage() - negativeNode.getVoltage()));
    }

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
