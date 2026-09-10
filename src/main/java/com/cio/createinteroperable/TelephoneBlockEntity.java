package com.cio.createinteroperable;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter.ScrollOptionSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import com.simibubi.create.api.equipment.goggles.IHaveHoveringInformation;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
 * real external tap-to-tap wiring, not the shared power rail.
 *
 * <p>The listener terminal and the two bottom Feed +/- terminals are all
 * breakers ({@link SwitchedWire}, near-zero resistance closed / open
 * otherwise) — not voltage sources, and not merely gated on the phone being
 * powered. They close only on the <b>receiving</b> end of an answered,
 * ongoing call: the phone that dialed out never closes its own (see
 * {@link #updateCallBreakers()}). The listener completes (or breaks)
 * whatever external, independently-powered circuit the player wires between
 * it and the negative rail, up to PG's own higher voltage tiers — the phone
 * doesn't supply that circuit's own power, it only gates it, like a real
 * breaker. Same design as {@link CpgTelephoneBlockEntity}.</p>
 *
 * <p>See {@link CpgTelephoneBlockEntity}/{@link CeeTelephoneBlockEntity} for
 * the single-protocol siblings — those need no WIRE_LOCK arbitration at all,
 * since there's only ever one protocol competing for the terminals.</p>
 */
public class TelephoneBlockEntity extends ElectricBlockEntity implements IHaveHoveringInformation, TelephoneNode {
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
    private static final float POWERED_THRESHOLD = 10.5f;
    /** Above this, the phone starts humming and its hover reads "Overheating" — below EXPLODE_THRESHOLD it's just a warning. */
    private static final float OVERHEAT_THRESHOLD = 20f;
    /** At or above this, the phone actually explodes (see #explode). */
    private static final float EXPLODE_THRESHOLD = 24f;
    /** Ticks a call rings before an Auto-Answer phone auto-picks-up. Also applies while waiting for a manual answer. */
    private static final int RING_DELAY_TICKS = 40;
    private static final int RING_SOUND_PERIOD_TICKS = 20;

    private IElectricNode positiveNode, negativeNode, tapNode;
    private SwitchedWire listenerBreaker, outletPositiveBreaker, outletNegativeBreaker;
    /** CEE's side of positive/negative, only actually delivering while WIRE_LOCK == CEE — see #electricalTick/#updateWireLock. */
    @Nullable
    private TelephoneDevice ceeDevice;

    private ScrollValueBehaviour autoAnswerValue;
    private ScrollValueBehaviour areaCodeValue;

    /** Up to 6 characters — digits, #, and * (see TelephoneNumbers#sanitizeNumberText). Empty until set. */
    private String ownNumberText = "";

    /**
     * Mirrors areaCodeValue's last successfully-applied value, so a rejected
     * in-world scroll (see #onAreaCodeScrolled) has something concrete to
     * revert to. Kept in sync in read() (after super.read() loads the real
     * persisted value) and in setOwnNumber().
     */
    private int lastValidAreaCode = 0;

    private boolean powered = false;

    @Override
    public boolean isPowered() {
        return powered;
    }
    private boolean overheating = false;
    private boolean busy = false;
    private boolean ringing = false;
    /** True only for the phone that was dialed (not the caller) — gates the "In call"/"Incoming call" readout. */
    private boolean receivingCall = false;
    /** True once ringing has actually been picked up (auto or manual) — distinct from busy, which also covers "still ringing." */
    private boolean answered = false;
    private int ringingTicks = 0;
    @Nullable
    private BlockPos callPartner;

    private String label = "";
    private String dialingTarget = "";

    public TelephoneBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);

        // Own Number is still set via TelephoneNumberScreen (right-click
        // back_plate) — the in-world attempt for it was abandoned earlier
        // for repeatedly landing on overlapping model geometry. Area Code
        // is different: it lives on back_plate's own real, unobstructed
        // surface with nothing else nearby, so it's safe as a real in-world
        // slider — same mechanism as auto_lever.
        autoAnswerValue = new LabeledScrollValueBehaviour(Component.literal("Auto-Answer"), this,
                new Slot(11.5, 10.5, 12.5, 90), AUTO_ANSWER_TYPE, "Auto", v -> v == 0 ? "OFF" : "ON",
                AUTO_ANSWER_OPTIONS)
                .between(0, 1).withFormatter(i -> i == 0 ? "OFF" : "ON");
        behaviours.add(autoAnswerValue);

        areaCodeValue = new LabeledScrollValueBehaviour(Component.literal("Set Area Code"), this,
                new Slot(8, 8, 13.9, 180), AREA_CODE_TYPE, "Area Code", String::valueOf, null)
                .between(0, 999).withFormatter(String::valueOf)
                .withCallback(this::onAreaCodeScrolled);
        behaviours.add(areaCodeValue);
    }

    /** Two-state toggle -> icon-mode board (auto-sized panel, one scroll step per state). */
    private static final INamedIconOptions[] AUTO_ANSWER_OPTIONS = {
            iconOption(AllIcons.I_DISABLE, "OFF"),
            iconOption(AllIcons.I_ACTIVE, "ON"),
    };

    private static INamedIconOptions iconOption(AllIcons icon, String label) {
        return new INamedIconOptions() {
            @Override
            public AllIcons getIcon() {
                return icon;
            }

            @Override
            public String getTranslationKey() {
                return label; // rendered verbatim by MC when not a registered key
            }
        };
    }

    /**
     * Create's own ScrollValueBehaviour#createBoard hardcodes the on-screen
     * row label to the literal string "Value" and ignores whatever
     * .withFormatter(...) was set (confirmed by reading its real source —
     * same fix InteroperableSmallBlockEntity's own DirectionScrollValueBehaviour
     * already needed once). Overriding createBoard() is the only way to
     * change either the row label or the displayed value text.
     * <p>
     * getType() is ALSO overridden here, each with its OWN distinct
     * BehaviourType — SmartBlockEntity stores behaviours in a
     * {@code Map<BehaviourType<?>, BlockEntityBehaviour>} keyed by
     * {@code getType()} (confirmed by reading its real constructor), and
     * ScrollValueBehaviour's own TYPE field is a single static shared by the
     * whole class. Two instances sharing that same TYPE — Auto-Answer and
     * Area Code — meant the second one silently evicted the first from that
     * map the moment both were added, even though both were passed to
     * addBehaviours()'s list: not a rendering glitch or a position conflict,
     * Auto-Answer was never actually gone from the world, it just stopped
     * being tracked by the block entity at all. A fresh BehaviourType per
     * distinct slider avoids the collision.
     */
    static class LabeledScrollValueBehaviour extends ScrollValueBehaviour {
        private final BehaviourType<ScrollValueBehaviour> type;
        private final String rowLabel;
        private final java.util.function.IntFunction<String> boardFormatter;
        /** Non-null => discrete icon-mode board (auto-sized labels, 1 scroll step/option). */
        private final INamedIconOptions[] iconOptions;

        LabeledScrollValueBehaviour(Component label, SmartBlockEntity be, ValueBoxTransform slot,
                                     BehaviourType<ScrollValueBehaviour> type,
                                     String rowLabel, java.util.function.IntFunction<String> boardFormatter,
                                     INamedIconOptions[] iconOptions) {
            super(label, be, slot);
            this.type = type;
            this.rowLabel = rowLabel;
            this.boardFormatter = boardFormatter;
            this.iconOptions = iconOptions;
        }

        @Override
        public BehaviourType<?> getType() {
            return type;
        }

        @Override
        public ValueSettingsBoard createBoard(net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hitResult) {
            if (iconOptions != null)
                return new ValueSettingsBoard(label, max, 1, ImmutableList.of(Component.literal(rowLabel)),
                        new ScrollOptionSettingsFormatter(iconOptions));
            return new ValueSettingsBoard(label, max, 10, ImmutableList.of(Component.literal(rowLabel)),
                    new ValueSettingsFormatter(vs -> Component.literal(boardFormatter.apply(vs.value()))));
        }
    }

    private static final BehaviourType<ScrollValueBehaviour> AUTO_ANSWER_TYPE = new BehaviourType<>();
    private static final BehaviourType<ScrollValueBehaviour> AREA_CODE_TYPE = new BehaviourType<>();

    static class Slot extends ValueBoxTransform {
        private final Vec3 base;
        private final int baseAngle;

        Slot(double x, double y, double z, int baseAngle) {
            this.base = VecHelper.voxelSpace(x, y, z);
            this.baseAngle = baseAngle;
        }

        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            return TelephoneBlock.rotateY(base, TelephoneBlock.angleFor(state));
        }

        @Override
        public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
            TransformStack.of(ms).rotateYDegrees(baseAngle + TelephoneBlock.angleFor(state));
        }

        @Override
        public float getScale() {
            return 3 / 16f;
        }
    }

    private boolean autoAnswer() {
        return autoAnswerValue.getValue() == 1;
    }

    @Override
    public String ownNumber() {
        return TelephoneNumbers.formatNumber(areaCodeValue.getValue(), ownNumberText);
    }

    /**
     * The Number screen (back_plate) sets both fields together now — Area
     * Code can still also be scrolled directly via its own in-world slider
     * (areaCodeValue), both paths converge on the same underlying value.
     * Rejects the change outright — nothing applied, returns false — if
     * that exact Area Code + Number combo already belongs to another loaded
     * phone. The number is sanitized and applied BEFORE the area code slider
     * is touched, so if onAreaCodeScrolled's own redundant check also fires
     * from the setValue() call below, it sees the real new number, not the
     * stale one. Caller (TelephoneNumberPacket) handles the deny feedback.
     */
    @Override
    public boolean setOwnNumber(int newAreaCode, String newNumber) {
        String sanitized = TelephoneNumbers.sanitizeNumberText(newNumber);
        if (TelephoneRegistry.isNumberTaken(this, newAreaCode, sanitized)) {
            return false;
        }
        ownNumberText = sanitized;
        areaCodeValue.setValue(newAreaCode);
        lastValidAreaCode = newAreaCode;
        setChanged();
        notifyUpdate();
        return true;
    }

    /**
     * The in-world Area Code slider has no vetoable pre-change hook — only
     * this after-the-fact callback (Create's ScrollValueBehaviour already
     * applied the new value by the time it fires) — so a conflicting scroll
     * is reverted straight back to lastValidAreaCode instead of prevented
     * upfront. That revert calls setValue() again, re-invoking this same
     * callback with lastValidAreaCode itself; since that value can't
     * conflict with itself, it just re-confirms and returns — no infinite
     * loop, just one harmless extra reentrant call.
     */
    private void onAreaCodeScrolled(int newAreaCode) {
        if (TelephoneRegistry.isNumberTaken(this, newAreaCode, ownNumberText)) {
            areaCodeValue.setValue(lastValidAreaCode);
            denyFeedback();
        } else {
            lastValidAreaCode = newAreaCode;
        }
    }

    /**
     * Same deny sound + smoke as a rejected self/busy dial (see
     * TelephoneBlock#useWithoutItem), for rejections with no direct
     * client-dispatched interaction to piggyback on — a rejected slider
     * scroll or a rejected Number-screen submission. No player to exclude
     * from the sound broadcast here, unlike the click-driven cases.
     */
    @Override
    public void denyFeedback() {
        if (level == null) {
            return;
        }
        AllSoundEvents.DENY.play(level, null, worldPosition);
        if (level instanceof ServerLevel serverLevel) {
            TelephoneBlock.spawnDenySmokeServer(serverLevel, worldPosition);
        }
    }

    @Override
    public int getAreaCode() {
        return areaCodeValue.getValue();
    }

    @Override
    public String getOwnNumberText() {
        return ownNumberText;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public BlockPos telephonePos() {
        return worldPosition;
    }

    @Override
    public Object pgTapNetwork() {
        return tapNode == null ? null : tapNode.getNetwork();
    }

    /**
     * CEE has no PG-ElectricalNetwork-style "which network is this node in"
     * query (confirmed: Network/CircuitGraph are internal solver machinery,
     * not a public API) — so this walks InfrastructureSavedData's own
     * connection list outward from this phone's own tap node (id 2, see
     * TelephoneBlock#CEE_TAP_BASE) to see if otherPos's tap node is reachable
     * through any chain of CEE wires. MAX_TAP_HOPS bounds the walk against
     * a pathological/cyclic wire layout; a real telephone network is never
     * going to be anywhere close to that deep.
     */
    private static final int MAX_TAP_HOPS = 256;

    @Override
    public boolean ceeTapReaches(BlockPos otherPos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        InWorldNode start = new InWorldNode(2, worldPosition);
        InWorldNode goal = new InWorldNode(2, otherPos);
        if (start.equals(goal)) {
            return true;
        }
        InfrastructureSavedData sd = InfrastructureSavedData.load(serverLevel);
        Set<InWorldNode> visited = new HashSet<>();
        Deque<InWorldNode> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);
        int hops = 0;
        while (!queue.isEmpty() && hops++ < MAX_TAP_HOPS) {
            InWorldNode current = queue.poll();
            for (InWorldNodeConnection connection : sd.getConnections(current)) {
                InWorldNode next = connection.node1().equals(current) ? connection.node2() : connection.node1();
                if (next.equals(goal)) {
                    return true;
                }
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        return false;
    }

    @Override
    public boolean isBusy() {
        return busy;
    }

    /** Whether the number currently typed into the dial screen is this phone's own — used to deny self-dialing. */
    boolean isDialingOwnNumber() {
        return !dialingTarget.isEmpty() && dialingTarget.equals(ownNumber());
    }

    /**
     * Whether the number currently typed into the dial screen belongs to a
     * real, reachable phone that's currently busy — used to deny dialing it,
     * same as self-dialing. Deliberately uses the unfiltered
     * {@link TelephoneRegistry#findAnyByNumber}, not the busy-filtered
     * {@link TelephoneRegistry#findByNumber} dial() itself uses — that
     * filter is exactly why a plain findByNumber-based check would silently
     * treat "exists but busy" the same as "doesn't exist at all."
     */
    boolean isDialingBusyNumber() {
        if (dialingTarget.isEmpty()) {
            return false;
        }
        TelephoneNode target = TelephoneRegistry.findAnyByNumber(this, dialingTarget);
        return target != null && target != this && target.isBusy();
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
        // answered, ongoing call — see #updateCallBreakers.
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
     * (it's still just {@code answered}, but {@code receivingCall} is false
     * for the whole call, set in {@link #dial()}). Open otherwise.
     */
    private void updateCallBreakers() {
        boolean closed = answered && receivingCall;
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

        updateWireLock();
        boolean ceeActive = getBlockState().getValue(TelephoneBlock.WIRE_LOCK) == TelephoneBlock.WireLock.CEE;
        if (level instanceof ServerLevel serverLevel && (ceeDevice == null || !ceeDevice.isValid())) {
            ceeDevice = DevicesSavedData.load(serverLevel).getDevice(worldPosition, TelephoneDevice.class);
        }

        double voltage = ceeActive && ceeDevice != null
                ? Math.abs(ceeDevice.getLastVoltage())
                : Math.abs(positiveNode.getVoltage() - negativeNode.getVoltage());
        boolean nowPowered = voltage >= POWERED_THRESHOLD;
        if (nowPowered != powered) {
            powered = nowPowered;
            setChanged();
            notifyUpdate();
        }

        if (voltage >= EXPLODE_THRESHOLD) {
            explode();
            return;
        }

        boolean nowOverheating = voltage > OVERHEAT_THRESHOLD;
        if (nowOverheating != overheating) {
            overheating = nowOverheating;
            setChanged();
            notifyUpdate();
        }

        if (ringing) {
            ringingTicks++;
            if (level.getGameTime() % RING_SOUND_PERIOD_TICKS == 0) {
                level.playSound(null, worldPosition, org.patryk3211.powergrid.collections.ModdedSoundEvents.ALARM_BELL.getMainEvent(),
                        net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1f);
            }
            if (ringingTicks >= RING_DELAY_TICKS && autoAnswer()) {
                answer();
            }
        }

        checkCallStillConnected();

        // Emit a redstone signal to the neighbours (strongly into the block
        // behind the phone) while this is the receiving end of an answered,
        // ongoing call — the phone that dialled out never emits (same
        // condition as #updateCallBreakers).
        TelephoneRedstone.sync(level, worldPosition, getBlockState(), TelephoneBlock.FACING,
                answered && receivingCall);
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
        return connections.containsKey(new org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint(worldPosition, 0))
                || connections.containsKey(new org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint(worldPosition, 1));
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
     * the same periodic-recheck idiom checkCallStillConnected() already uses
     * elsewhere in this class. Known, accepted gap: a wire from the
     * currently-losing protocol landing in the same ~1-tick window before
     * this catches up could theoretically slip through — CEE's own
     * isNodeAccessible (TelephoneBlock) is instant/synchronous, but PG has
     * no equivalent, only this poll.
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

    /**
     * 24V+ across positive/negative — a real vanilla explosion (sound +
     * particles come from Level#explode itself), Level.ExplosionInteraction.NONE
     * so nothing around it takes block or entity damage, plus Create's own
     * desk bell layered on top, then the phone itself is destroyed (no item
     * drop — it blew up).
     */
    private void explode() {
        level.explode(null, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
                2f, false, net.minecraft.world.level.Level.ExplosionInteraction.NONE);
        AllSoundEvents.DESK_BELL_USE.play(level, null, worldPosition);
        level.removeBlock(worldPosition, false);
    }

    /** Low hum while overheating (20V-24V), same real sound/mechanism as PG's own Transformer — see InteroperableSmallBlockEntity#tickAudio. */
    @Override
    public void tickAudio() {
        if (overheating) {
            org.patryk3211.powergrid.utility.sound.SoundScapes.play(
                    org.patryk3211.powergrid.utility.sound.SoundScapes.AmbienceGroup.HUM, worldPosition, 1f, 1f);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level != null && level.isClientSide) {
            if (ringing) {
                spawnRingParticles();
            } else if (busy) {
                spawnBusyParticles();
            }
        }
    }

    private void spawnRingParticles() {
        if (level.random.nextInt(4) != 0) {
            return;
        }
        double x = worldPosition.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.5;
        double y = worldPosition.getY() + 0.5 + level.random.nextDouble() * 0.5;
        double z = worldPosition.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.5;
        level.addParticle(ParticleTypes.PORTAL, x, y, z, 0, 0.05, 0);
    }

    /** Slow, sparse redstone dust while an active (answered, non-ringing) call is in progress. */
    private void spawnBusyParticles() {
        if (level.random.nextInt(20) != 0) {
            return;
        }
        double x = worldPosition.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.5;
        double y = worldPosition.getY() + 0.5 + level.random.nextDouble() * 0.5;
        double z = worldPosition.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.5;
        level.addParticle(DustParticleOptions.REDSTONE, x, y, z, 0, 0.01, 0);
    }

    /** Front face — open the dial-out screen. */
    net.minecraft.world.InteractionResult onFrontUsed(net.minecraft.world.entity.player.Player player) {
        if (level.isClientSide) {
            TelephoneClient.openDialScreen(worldPosition, dialingTarget);
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    /** Top face — open the label screen. */
    net.minecraft.world.InteractionResult onTopUsed(net.minecraft.world.entity.player.Player player) {
        if (level.isClientSide) {
            TelephoneClient.openLabelScreen(worldPosition, label);
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    /** back_plate — open the Number screen (Area Code + Number, same layout as the dial screen). */
    net.minecraft.world.InteractionResult onBackPlateUsed(net.minecraft.world.entity.player.Player player) {
        if (level.isClientSide) {
            TelephoneClient.openNumberScreen(worldPosition, getAreaCode(), ownNumberText);
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    /**
     * The handset (phone_part) — dispatches by call state. ringing must
     * resolve to answer() first (ringing implies busy is already true), or
     * manually answering an incoming call would hang it up instead.
     */
    net.minecraft.world.InteractionResult onHandsetUsed(net.minecraft.world.entity.player.Player player) {
        if (level.isClientSide) {
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        if (ringing) {
            answer();
        } else if (busy) {
            hangUp();
        } else {
            dial();
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    @Override
    public void setDialingTarget(String target) {
        this.dialingTarget = target;
        setChanged();
        notifyUpdate();
    }

    @Override
    public void setLabel(String newLabel) {
        this.label = newLabel;
        setChanged();
        notifyUpdate();
    }

    private void dial() {
        if (!powered || dialingTarget.isEmpty()) {
            return;
        }
        TelephoneNode target = TelephoneRegistry.findByNumber(this, dialingTarget);
        if (target == null || target == this || target.isBusy()) {
            return;
        }

        busy = true;
        receivingCall = false;
        answered = false;
        callPartner = target.telephonePos();

        target.receiveCall(worldPosition);

        setChanged();
        notifyUpdate();
    }

    @Override
    public void receiveCall(BlockPos callerPos) {
        busy = true;
        ringing = true;
        ringingTicks = 0;
        receivingCall = true;
        answered = false;
        callPartner = callerPos;
        setChanged();
        notifyUpdate();
    }

    private void answer() {
        ringing = false;
        answered = true;
        updateCallBreakers();
        setChanged();
        notifyUpdate();
        TelephoneNode partner = TelephoneRegistry.get(level, callPartner);
        if (partner != null) {
            // Mirror onto the caller so both ends read "In call" rather than
            // the caller being stuck on "Calling…" — the caller's own
            // receivingCall stays false for the whole call (set in #dial()),
            // so updateCallBreakers() on that end never closes its breakers;
            // this mirror only ever affects the readout.
            partner.notifyAnswered();
        }
    }

    @Override
    public void notifyAnswered() {
        answered = true;
        updateCallBreakers();
        setChanged();
        notifyUpdate();
    }

    /** Resets only this phone's own call state — used both for a player-initiated hang-up and a detected disconnect. */
    @Override
    public void resetCallState() {
        busy = false;
        ringing = false;
        receivingCall = false;
        answered = false;
        ringingTicks = 0;
        callPartner = null;
        updateCallBreakers();
        setChanged();
        notifyUpdate();
    }

    private void hangUp() {
        BlockPos partnerPos = callPartner;
        resetCallState();

        TelephoneNode partner = TelephoneRegistry.get(level, partnerPos);
        if (partner != null) {
            partner.resetCallState();
        }
    }

    /**
     * Cutting the tap wire mid-call previously left both phones stuck "busy"
     * forever — nothing ever re-checked whether the call could still
     * physically be sustained. Called every electricalTick while busy: if
     * the partner is gone or no longer reachable (the wire got cut),
     * silently resets just this phone's own state. Symmetric by
     * construction — the partner's own tick independently detects the same
     * condition and resets itself, so no cross-entity mutation is needed
     * here.
     */
    private void checkCallStillConnected() {
        if (!busy || callPartner == null) {
            return;
        }
        TelephoneNode partner = TelephoneRegistry.get(level, callPartner);
        if (partner == null || !canReach(partner)) {
            resetCallState();
        }
    }

    @Override
    public void setLevel(net.minecraft.world.level.Level level) {
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
        tooltip.add(Component.literal("    Interoperable Telephone").withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.literal("Number: " + ownNumber()).withStyle(ChatFormatting.GRAY));
        if (!label.isEmpty()) {
            tooltip.add(Component.literal(label).withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
        }
        tooltip.add(Component.literal("Powered: " + (powered ? "Yes" : "No"))
                .withStyle(powered ? ChatFormatting.GREEN : ChatFormatting.RED));

        if (ringing && receivingCall) {
            tooltip.add(Component.literal("Incoming call" + partnerSuffix()).withStyle(ChatFormatting.YELLOW));
        } else if (busy && answered) {
            tooltip.add(Component.literal("In call" + partnerSuffix()).withStyle(ChatFormatting.GREEN));
        } else if (busy) {
            tooltip.add(Component.literal("Calling" + partnerSuffix() + "…").withStyle(ChatFormatting.YELLOW));
        } else if (!dialingTarget.isEmpty()) {
            tooltip.add(Component.literal("Dialling: " + dialDisplay()).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("Idle").withStyle(ChatFormatting.GREEN));
        }

        tooltip.add(Component.literal("Auto-Answer: " + (autoAnswer() ? "On" : "Off"))
                .withStyle(autoAnswer() ? ChatFormatting.GREEN : ChatFormatting.GRAY));

        if (overheating) {
            tooltip.add(Component.literal("Overheating").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }
        String wiring = switch (getBlockState().getValue(TelephoneBlock.WIRE_LOCK)) {
            case PG -> "CPG";
            case CEE -> "CEE";
            case NONE -> "Free";
        };
        tooltip.add(Component.literal("Wiring: " + wiring).withStyle(ChatFormatting.GRAY));
        return true;
    }

    /** {@code ": <label-or-number>"} of the connected/ringing peer, or {@code ""} if it can't be resolved. */
    private String partnerSuffix() {
        if (callPartner == null || level == null) {
            return "";
        }
        TelephoneNode peer = TelephoneRegistry.get(level, callPartner);
        if (peer == null) {
            return "";
        }
        return ": " + (peer.getLabel().isEmpty() ? peer.ownNumber() : peer.getLabel());
    }

    /** The number typed into the dial screen, shown as its target's label when that resolves. */
    private String dialDisplay() {
        TelephoneNode target = TelephoneRegistry.findAnyByNumber(this, dialingTarget);
        return (target != null && !target.getLabel().isEmpty()) ? target.getLabel() : dialingTarget;
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        label = tag.getString("Label");
        dialingTarget = tag.getString("DialingTarget");
        ownNumberText = TelephoneNumbers.sanitizeNumberText(tag.getString("OwnNumberText"));
        // areaCodeValue is already loaded by super.read() above (behaviours
        // read their own NBT there) — mirror it so a later rejected scroll
        // has the real persisted value to revert to, not the field default.
        lastValidAreaCode = areaCodeValue.getValue();
        powered = tag.getBoolean("Powered");
        overheating = tag.getBoolean("Overheating");
        busy = tag.getBoolean("Busy");
        ringing = tag.getBoolean("Ringing");
        receivingCall = tag.getBoolean("ReceivingCall");
        answered = tag.getBoolean("Answered");
        ringingTicks = tag.getInt("RingingTicks");
        callPartner = tag.contains("CallPartner") ? BlockPos.of(tag.getLong("CallPartner")) : null;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putString("Label", label);
        tag.putString("DialingTarget", dialingTarget);
        tag.putString("OwnNumberText", ownNumberText);
        tag.putBoolean("Powered", powered);
        tag.putBoolean("Overheating", overheating);
        tag.putBoolean("Busy", busy);
        tag.putBoolean("Ringing", ringing);
        tag.putBoolean("ReceivingCall", receivingCall);
        tag.putBoolean("Answered", answered);
        tag.putInt("RingingTicks", ringingTicks);
        if (callPartner != null) {
            tag.putLong("CallPartner", callPartner.asLong());
        }
    }
}
