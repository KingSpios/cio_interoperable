package com.cio.createinteroperable;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.api.equipment.goggles.IHaveHoveringInformation;
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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.utility.sound.SoundScapes;

import java.util.List;

/**
 * "CPG Telephone" BlockEntity — Power-Grid-only twin of the Interoperable
 * {@link TelephoneBlockEntity}. Same electrical model (positive/negative
 * form a resistive load, tap has no internal connection so its network
 * identity reflects only real external tap wiring), but the positive/negative
 * pair is always live — no WIRE_LOCK arbitration, since there's no second
 * protocol competing for it.
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
public class CpgTelephoneBlockEntity extends ElectricBlockEntity implements IHaveHoveringInformation, TelephoneNode {
    static final float COIL_RESISTANCE = 200f;
    private static final float DELIVERY_RESISTANCE = 0.05f;
    /** Near-zero pass-through resistance for the bottom Feed +/- terminals — same convention as the Power Kit's Power Feed. */
    private static final float FEED_PASSTHROUGH_R = 0.001f;
    private static final float POWERED_THRESHOLD = 10.5f;
    private static final float OVERHEAT_THRESHOLD = 20f;
    private static final float EXPLODE_THRESHOLD = 24f;
    private static final int RING_DELAY_TICKS = 40;
    private static final int RING_SOUND_PERIOD_TICKS = 20;

    private IElectricNode positiveNode, negativeNode, tapNode;
    private SwitchedWire listenerBreaker, outletPositiveBreaker, outletNegativeBreaker;

    private ScrollValueBehaviour autoAnswerValue;
    private ScrollValueBehaviour areaCodeValue;

    private String ownNumberText = "";
    private int lastValidAreaCode = 0;

    private boolean powered = false;
    private boolean overheating = false;
    private boolean busy = false;
    private boolean ringing = false;
    private boolean receivingCall = false;
    private boolean answered = false;
    private int ringingTicks = 0;
    @Nullable
    private BlockPos callPartner;

    private String label = "";
    private String dialingTarget = "";

    public CpgTelephoneBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
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
                return label;
            }
        };
    }

    private static class LabeledScrollValueBehaviour extends ScrollValueBehaviour {
        private final BehaviourType<ScrollValueBehaviour> type;
        private final String rowLabel;
        private final java.util.function.IntFunction<String> boardFormatter;
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
        public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
            if (iconOptions != null)
                return new ValueSettingsBoard(label, max, 1, ImmutableList.of(Component.literal(rowLabel)),
                        new ScrollOptionSettingsFormatter(iconOptions));
            return new ValueSettingsBoard(label, max, 10, ImmutableList.of(Component.literal(rowLabel)),
                    new ValueSettingsFormatter(vs -> Component.literal(boardFormatter.apply(vs.value()))));
        }
    }

    private static final BehaviourType<ScrollValueBehaviour> AUTO_ANSWER_TYPE = new BehaviourType<>();
    private static final BehaviourType<ScrollValueBehaviour> AREA_CODE_TYPE = new BehaviourType<>();

    private static class Slot extends ValueBoxTransform {
        private final Vec3 base;
        private final int baseAngle;

        Slot(double x, double y, double z, int baseAngle) {
            this.base = VecHelper.voxelSpace(x, y, z);
            this.baseAngle = baseAngle;
        }

        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            return CpgTelephoneBlock.rotateY(base, CpgTelephoneBlock.angleFor(state));
        }

        @Override
        public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
            TransformStack.of(ms).rotateYDegrees(baseAngle + CpgTelephoneBlock.angleFor(state));
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

    private void onAreaCodeScrolled(int newAreaCode) {
        if (TelephoneRegistry.isNumberTaken(this, newAreaCode, ownNumberText)) {
            areaCodeValue.setValue(lastValidAreaCode);
            denyFeedback();
        } else {
            lastValidAreaCode = newAreaCode;
        }
    }

    @Override
    public void denyFeedback() {
        if (level == null) {
            return;
        }
        AllSoundEvents.DENY.play(level, null, worldPosition);
        if (level instanceof ServerLevel serverLevel) {
            CpgTelephoneBlock.spawnDenySmokeServer(serverLevel, worldPosition);
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
    public boolean isPowered() {
        return powered;
    }

    @Override
    public BlockPos telephonePos() {
        return worldPosition;
    }

    @Override
    public Object pgTapNetwork() {
        return tapNode == null ? null : tapNode.getNetwork();
    }

    @Override
    public boolean ceeTapReaches(BlockPos otherPos) {
        return false;
    }

    @Override
    public boolean isBusy() {
        return busy;
    }

    boolean isDialingOwnNumber() {
        return !dialingTarget.isEmpty() && dialingTarget.equals(ownNumber());
    }

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

        double voltage = Math.abs(positiveNode.getVoltage() - negativeNode.getVoltage());
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
        TelephoneRedstone.sync(level, worldPosition, getBlockState(), CpgTelephoneBlock.FACING,
                answered && receivingCall);
    }

    private void explode() {
        level.explode(null, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
                2f, false, Level.ExplosionInteraction.NONE);
        AllSoundEvents.DESK_BELL_USE.play(level, null, worldPosition);
        level.removeBlock(worldPosition, false);
    }

    @Override
    public void tickAudio() {
        if (overheating) {
            SoundScapes.play(SoundScapes.AmbienceGroup.HUM, worldPosition, 1f, 1f);
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

    private void spawnBusyParticles() {
        if (level.random.nextInt(20) != 0) {
            return;
        }
        double x = worldPosition.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.5;
        double y = worldPosition.getY() + 0.5 + level.random.nextDouble() * 0.5;
        double z = worldPosition.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.5;
        level.addParticle(DustParticleOptions.REDSTONE, x, y, z, 0, 0.01, 0);
    }

    InteractionResult onFrontUsed(Player player) {
        if (level.isClientSide) {
            TelephoneClient.openDialScreen(worldPosition, dialingTarget);
        }
        return InteractionResult.SUCCESS;
    }

    InteractionResult onTopUsed(Player player) {
        if (level.isClientSide) {
            TelephoneClient.openLabelScreen(worldPosition, label);
        }
        return InteractionResult.SUCCESS;
    }

    InteractionResult onBackPlateUsed(Player player) {
        if (level.isClientSide) {
            TelephoneClient.openNumberScreen(worldPosition, getAreaCode(), ownNumberText);
        }
        return InteractionResult.SUCCESS;
    }

    InteractionResult onHandsetUsed(Player player) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (ringing) {
            answer();
        } else if (busy) {
            hangUp();
        } else {
            dial();
        }
        return InteractionResult.SUCCESS;
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
        tooltip.add(Component.literal("    CPG Telephone").withStyle(ChatFormatting.WHITE));
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
        tooltip.add(Component.literal("Wiring: CPG").withStyle(ChatFormatting.GRAY));
        return true;
    }

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
