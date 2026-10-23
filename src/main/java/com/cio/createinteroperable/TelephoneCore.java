package com.cio.createinteroperable;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Everything CIO's own three telephones (Interoperable, CPG, CEE) know and do,
 * independent of how they are wired: settings, call state, dialling /
 * ringing / answering / hanging up, power and overheat handling, redstone,
 * particles, the goggle readout and the saved NBT. Each block entity holds one
 * and supplies only the electrical side through {@link CioTelephone}: where the
 * line voltage comes from, its breakers, its ring sound, its tap-reachability.
 * Iden's telephones have their own core ({@code IdenPhoneCore}): different
 * handset and ring behaviour, same {@link TelephoneRegistry} call protocol.
 *
 * <p><b>Must never reference a Power Grid or Electro Energetics type</b> (the
 * CEE phone carries it without PG installed, the CPG phone without CEE);
 * {@code LoadSafetyTest} enforces that. NBT keys are exactly the ones the
 * three block entities always wrote, so existing phones load unchanged.</p>
 */
final class TelephoneCore {
    /** At or above this across the line, the phone counts as powered. */
    private static final float POWERED_THRESHOLD = 10.5f;
    /** Above this, the phone starts humming and its hover reads "Overheating" — below EXPLODE_THRESHOLD it's just a warning. */
    private static final float OVERHEAT_THRESHOLD = 20f;
    /** At or above this, the phone actually explodes (see #explode). */
    private static final float EXPLODE_THRESHOLD = 24f;
    /** Ticks a call rings before an Auto-Answer phone auto-picks-up. Also applies while waiting for a manual answer. */
    private static final int RING_DELAY_TICKS = 40;
    private static final int RING_SOUND_PERIOD_TICKS = 20;

    private final SmartBlockEntity be;
    private final CioTelephone phone;

    /** Own area code (0-999), set from the telephone settings screen. */
    private int areaCode = 0;
    /** Up to 6 characters — digits, #, and * (see TelephoneNumbers#sanitizeNumberText). Empty until set. */
    private String ownNumberText = "";
    private String label = "";
    private String dialingTarget = "";

    /**
     * "Pulse (3s)", a caller-side setting: calls placed FROM this phone make the
     * answering phone's call outputs cycle on/off every {@link TelephoneNode#PULSE_TICKS}.
     */
    private boolean pulseMode = false;
    /** Answering end only: this call came from a phone with Pulse on. */
    private boolean pulsedCall = false;
    /** Answering end only: ticks since the call was answered (drives the pulse phase). */
    private int callTicks = 0;

    private boolean powered = false;
    private boolean overheating = false;
    private boolean exploded = false;
    private boolean busy = false;
    private boolean ringing = false;
    /** True only for the phone that was dialed (not the caller) — gates the "In call"/"Incoming call" readout. */
    private boolean receivingCall = false;
    /** True once ringing has actually been picked up (auto or manual) — distinct from busy, which also covers "still ringing." */
    private boolean answered = false;
    private int ringingTicks = 0;
    @Nullable
    private BlockPos callPartner;

    <T extends SmartBlockEntity & CioTelephone> TelephoneCore(T phone) {
        this.be = phone;
        this.phone = phone;
    }

    private Level level() {
        return be.getLevel();
    }

    BlockPos pos() {
        return be.getBlockPos();
    }

    private void changed() {
        be.setChanged();
        be.notifyUpdate();
    }

    // --- settings -------------------------------------------------------

    int areaCode() {
        return areaCode;
    }

    String ownNumberText() {
        return ownNumberText;
    }

    String label() {
        return label;
    }

    String dialingTarget() {
        return dialingTarget;
    }

    String ownNumber() {
        return TelephoneNumbers.formatNumber(areaCode, ownNumberText);
    }

    boolean autoAnswer() {
        return phone.autoAnswerBehaviour().isOn();
    }

    void setAutoAnswer(boolean autoAnswer) {
        phone.autoAnswerBehaviour().setOn(autoAnswer);
    }

    boolean pulseMode() {
        return pulseMode;
    }

    void setPulse(boolean pulse) {
        pulseMode = pulse;
        changed();
    }

    void setLabel(String newLabel) {
        this.label = newLabel;
        changed();
    }

    void setDialingTarget(String target) {
        this.dialingTarget = target;
        changed();
    }

    /**
     * Set from the back_plate settings screen. Rejects the change outright
     * (nothing applied, returns false) if that exact Area Code + Number combo
     * already belongs to another loaded phone; the caller
     * (TelephoneSettingsPacket) handles the deny feedback.
     */
    boolean setOwnNumber(int newAreaCode, String newNumber) {
        String sanitized = TelephoneNumbers.sanitizeNumberText(newNumber);
        if (TelephoneRegistry.isNumberTaken(phone, newAreaCode, sanitized)) {
            return false;
        }
        ownNumberText = sanitized;
        areaCode = newAreaCode;
        changed();
        return true;
    }

    /**
     * Same deny sound + smoke as a rejected self/busy dial (see the blocks'
     * useWithoutItem), for rejections with no direct client-dispatched
     * interaction to piggyback on — a rejected slider scroll or a rejected
     * Number-screen submission. No player to exclude from the sound broadcast
     * here, unlike the click-driven cases.
     */
    void denyFeedback() {
        Level level = level();
        if (level == null) {
            return;
        }
        AllSoundEvents.DENY.play(level, null, pos());
        if (level instanceof ServerLevel serverLevel) {
            BlockPos pos = pos();
            serverLevel.sendParticles(ParticleTypes.WHITE_SMOKE,
                    pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5,
                    6, 0.2, 0.15, 0.2, 0.01);
        }
    }

    // --- state ------------------------------------------------------------

    boolean isPowered() {
        return powered;
    }

    boolean isOverheating() {
        return overheating;
    }

    boolean isExploded() {
        return exploded;
    }

    boolean isBusy() {
        return busy;
    }

    boolean isCallAnswered() {
        return busy && answered;
    }

    @Nullable
    BlockPos callPartner() {
        return callPartner;
    }

    /**
     * Whether this phone's call outputs (Call Breaker, Call Feed +/-, redstone)
     * are on: only on the answering end of an answered call and, if the caller
     * has Pulse on, only in the "on" half of each {@link TelephoneNode#PULSE_TICKS} cycle.
     */
    boolean callOutputsClosed() {
        return answered && receivingCall && (!pulsedCall || TelephoneNode.pulsePhaseOn(callTicks));
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
     * {@link TelephoneRegistry#findByNumber} {@link #dial()} uses — that
     * filter is exactly why a plain findByNumber-based check would silently
     * treat "exists but busy" the same as "doesn't exist at all."
     */
    boolean isDialingBusyNumber() {
        if (dialingTarget.isEmpty()) {
            return false;
        }
        TelephoneNode target = TelephoneRegistry.findAnyByNumber(phone, dialingTarget);
        return target != null && target != phone && target.isBusy();
    }

    // --- ticking ------------------------------------------------------------

    /** Advances the pulse phase on the answering end of a pulsed call; tells the phone when its outputs flip. */
    void advancePulse() {
        if (answered && receivingCall && pulsedCall) {
            boolean wasClosed = callOutputsClosed();
            callTicks++;
            if (callOutputsClosed() != wasClosed) {
                phone.onCallOutputsChanged();
                be.setChanged();
            }
        }
    }

    /**
     * One server tick of everything that follows from the line voltage: power
     * and overheat state, exploding at {@link #EXPLODE_THRESHOLD}, ringing and
     * Auto-Answer, dropping a call whose partner is gone or unreachable, and
     * the redstone output. Call {@link #advancePulse()} first.
     */
    void serverTick(double voltage) {
        if (exploded) {
            return;
        }
        boolean nowPowered = voltage >= POWERED_THRESHOLD;
        if (nowPowered != powered) {
            powered = nowPowered;
            changed();
        }

        if (voltage >= EXPLODE_THRESHOLD) {
            explode();
            return;
        }

        boolean nowOverheating = voltage > OVERHEAT_THRESHOLD;
        if (nowOverheating != overheating) {
            overheating = nowOverheating;
            changed();
        }

        Level level = level();
        if (ringing) {
            ringingTicks++;
            if (level.getGameTime() % RING_SOUND_PERIOD_TICKS == 0) {
                phone.playRingSound();
            }
            if (ringingTicks >= RING_DELAY_TICKS && autoAnswer()) {
                answer();
            }
        }

        checkCallStillConnected();

        // Emit a redstone signal to the neighbours (strongly into the block
        // behind the phone) while this is the receiving end of an answered,
        // ongoing call — the phone that dialled out never emits. All three
        // telephone blocks use the vanilla horizontal FACING property.
        TelephoneRedstone.sync(level, pos(), be.getBlockState(), BlockStateProperties.HORIZONTAL_FACING,
                callOutputsClosed());
    }

    /**
     * 24V+ across the line — a real vanilla explosion (sound + particles come
     * from Level#explode itself), Level.ExplosionInteraction.NONE so nothing
     * around it takes block or entity damage, plus Create's own desk bell
     * layered on top, then the phone itself is destroyed (no item drop — it
     * blew up).
     */
    private void explode() {
        exploded = true;
        Level level = level();
        BlockPos pos = pos();
        level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                2f, false, Level.ExplosionInteraction.NONE);
        AllSoundEvents.DESK_BELL_USE.play(level, null, pos);
        level.removeBlock(pos, false);
    }

    /** Ring / busy particles; call from the block entity's client tick. */
    void clientTick() {
        if (ringing) {
            spawnRingParticles();
        } else if (busy) {
            spawnBusyParticles();
        }
    }

    private void spawnRingParticles() {
        Level level = level();
        if (level.random.nextInt(4) != 0) {
            return;
        }
        BlockPos pos = pos();
        double x = pos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.5;
        double y = pos.getY() + 0.5 + level.random.nextDouble() * 0.5;
        double z = pos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.5;
        level.addParticle(ParticleTypes.PORTAL, x, y, z, 0, 0.05, 0);
    }

    /** Slow, sparse redstone dust while an active (answered, non-ringing) call is in progress. */
    private void spawnBusyParticles() {
        Level level = level();
        if (level.random.nextInt(20) != 0) {
            return;
        }
        BlockPos pos = pos();
        double x = pos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.5;
        double y = pos.getY() + 0.5 + level.random.nextDouble() * 0.5;
        double z = pos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.5;
        level.addParticle(DustParticleOptions.REDSTONE, x, y, z, 0, 0.01, 0);
    }

    // --- interactions ---------------------------------------------------------

    /** Front face — open the dial-out screen. */
    InteractionResult onFrontUsed(Player player) {
        if (level().isClientSide) {
            TelephoneClient.openDialScreen(pos(), dialingTarget);
        }
        return InteractionResult.SUCCESS;
    }

    /** Top face — open the label screen. */
    InteractionResult onTopUsed(Player player) {
        if (level().isClientSide) {
            TelephoneClient.openLabelScreen(pos(), label);
        }
        return InteractionResult.SUCCESS;
    }

    /** back_plate — open the full settings screen (own number, area code, label, number to call, Auto-Answer, Pulse). */
    InteractionResult onBackPlateUsed(Player player) {
        if (level().isClientSide) {
            TelephoneClient.openSettings(pos(), areaCode, ownNumberText, label, dialingTarget, autoAnswer(),
                    true, pulseMode);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * The handset (phone_part) — dispatches by call state. ringing must
     * resolve to answer() first (ringing implies busy is already true), or
     * manually answering an incoming call would hang it up instead.
     */
    InteractionResult onHandsetUsed(Player player) {
        if (level().isClientSide) {
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

    // --- call protocol -------------------------------------------------------

    private void dial() {
        if (!powered || dialingTarget.isEmpty()) {
            return;
        }
        TelephoneNode target = TelephoneRegistry.findByNumber(phone, dialingTarget);
        if (target == null || target == phone || target.isBusy()) {
            return;
        }

        busy = true;
        receivingCall = false;
        answered = false;
        callPartner = target.telephonePos();

        target.receiveCall(pos());

        changed();
    }

    void receiveCall(BlockPos callerPos) {
        TelephoneNode caller = TelephoneRegistry.get(level(), callerPos);
        pulsedCall = caller != null && caller.isPulse();
        callTicks = 0;
        busy = true;
        ringing = true;
        ringingTicks = 0;
        receivingCall = true;
        answered = false;
        callPartner = callerPos;
        changed();
    }

    private void answer() {
        ringing = false;
        answered = true;
        callTicks = 0;
        phone.onCallOutputsChanged();
        changed();
        TelephoneNode partner = TelephoneRegistry.get(level(), callPartner);
        if (partner != null) {
            // Mirror onto the caller so both ends read "In call" rather than
            // the caller being stuck on "Calling…" — the caller's own
            // receivingCall stays false for the whole call (set in #dial()),
            // so its call outputs never close; this mirror only ever affects
            // the readout.
            partner.notifyAnswered();
        }
    }

    void notifyAnswered() {
        answered = true;
        phone.onCallOutputsChanged();
        changed();
    }

    /** Resets only this phone's own call state — used both for a player-initiated hang-up and a detected disconnect. */
    void resetCallState() {
        busy = false;
        ringing = false;
        receivingCall = false;
        answered = false;
        ringingTicks = 0;
        callPartner = null;
        pulsedCall = false;
        callTicks = 0;
        phone.onCallOutputsChanged();
        changed();
    }

    private void hangUp() {
        BlockPos partnerPos = callPartner;
        resetCallState();

        TelephoneNode partner = TelephoneRegistry.get(level(), partnerPos);
        if (partner != null) {
            partner.resetCallState();
        }
    }

    /**
     * Cutting the tap wire mid-call used to leave both phones stuck "busy"
     * forever — nothing ever re-checked whether the call could still
     * physically be sustained. Called every tick while busy: if the partner is
     * gone or no longer reachable (the wire got cut), silently resets just
     * this phone's own state. Symmetric by construction — the partner's own
     * tick independently detects the same condition and resets itself, so no
     * cross-entity mutation is needed here.
     */
    private void checkCallStillConnected() {
        if (!busy || callPartner == null) {
            return;
        }
        TelephoneNode partner = TelephoneRegistry.get(level(), callPartner);
        if (partner == null || !phone.canReach(partner)) {
            resetCallState();
        }
    }

    // --- readout + NBT ---------------------------------------------------------

    void addToTooltip(List<Component> tooltip) {
        tooltip.add(Component.literal("    " + phone.telephoneName()).withStyle(ChatFormatting.WHITE));
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
        if (pulseMode) {
            tooltip.add(Component.literal("Pulse (3s): On for calls from here").withStyle(ChatFormatting.GRAY));
        }
        if (pulsedCall && receivingCall) {
            tooltip.add(Component.literal("Caller is pulsing this line").withStyle(ChatFormatting.GOLD));
        }

        if (overheating) {
            tooltip.add(Component.literal("Overheating").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }
        tooltip.add(Component.literal("Wiring: " + phone.wiringLabel()).withStyle(ChatFormatting.GRAY));
    }

    /** {@code ": <label-or-number>"} of the connected/ringing peer, or {@code ""} if it can't be resolved. */
    private String partnerSuffix() {
        if (callPartner == null || level() == null) {
            return "";
        }
        TelephoneNode peer = TelephoneRegistry.get(level(), callPartner);
        if (peer == null) {
            return "";
        }
        return ": " + (peer.getLabel().isEmpty() ? peer.ownNumber() : peer.getLabel());
    }

    /** The number typed into the dial screen, shown as its target's label when that resolves. */
    private String dialDisplay() {
        TelephoneNode target = TelephoneRegistry.findAnyByNumber(phone, dialingTarget);
        return (target != null && !target.getLabel().isEmpty()) ? target.getLabel() : dialingTarget;
    }

    void read(CompoundTag tag) {
        label = tag.getString("Label");
        dialingTarget = tag.getString("DialingTarget");
        ownNumberText = TelephoneNumbers.sanitizeNumberText(tag.getString("OwnNumberText"));
        pulseMode = tag.getBoolean("PulseMode");
        pulsedCall = tag.getBoolean("PulsedCall");
        callTicks = tag.getInt("CallTicks");
        areaCode = tag.contains("AreaCode") ? tag.getInt("AreaCode") : TelephoneAutoAnswerBehaviour.legacyAreaCode(tag);
        powered = tag.getBoolean("Powered");
        overheating = tag.getBoolean("Overheating");
        busy = tag.getBoolean("Busy");
        ringing = tag.getBoolean("Ringing");
        receivingCall = tag.getBoolean("ReceivingCall");
        answered = tag.getBoolean("Answered");
        ringingTicks = tag.getInt("RingingTicks");
        callPartner = tag.contains("CallPartner") ? BlockPos.of(tag.getLong("CallPartner")) : null;
    }

    void write(CompoundTag tag) {
        tag.putString("Label", label);
        tag.putString("DialingTarget", dialingTarget);
        tag.putString("OwnNumberText", ownNumberText);
        tag.putBoolean("PulseMode", pulseMode);
        tag.putBoolean("PulsedCall", pulsedCall);
        tag.putInt("CallTicks", callTicks);
        tag.putInt("AreaCode", areaCode);
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
