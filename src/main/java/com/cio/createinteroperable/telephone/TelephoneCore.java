package com.cio.createinteroperable.telephone;

import com.cio.createinteroperable.CIOSounds;
import com.cio.createinteroperable.Lang;
import com.cio.createinteroperable.computer.ComputerLinkBehaviour;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
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
public final class TelephoneCore {
    /**
     * The line voltages a phone works on: powered at or above {@code powered}, its hover
     * reads "Overheating" above {@code overheat} (only a warning), and it explodes at or
     * above {@code explode} (see #explode).
     */
    public record Rating(float powered, float overheat, float explode) {
        /** CIO's wired telephones: a 12 V line. */
        public static final Rating LINE_12V = new Rating(10.5f, 20f, 24f);
        /** The Radio Telephone: anything from a 12 V to a 240 V supply. */
        public static final Rating WIDE_12_240V = new Rating(10.5f, 252f, 264f);
    }

    /** Ticks a call rings before an Auto-Answer phone auto-picks-up. Also applies while waiting for a manual answer. */
    private static final int RING_DELAY_TICKS = 40;

    private final SmartBlockEntity be;
    private final CioTelephone phone;
    private final Rating rating;

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

    /**
     * The current call's far end is a computer, not a phone (e.g. a radio link): rung by
     * {@link #computerRingExternal}, or placed by dialling a number no wired phone has while
     * a computer holds external routing. {@link #callPartner} is then null.
     */
    private boolean externalCall = false;
    private String externalNumber = "";
    private String externalLabel = "";
    /**
     * Game time until which a computer handles calls to numbers not on the wire
     * ({@link #holdExternalRouting}). A lease, renewed by the computer, so a crashed or
     * removed computer can't leave a phone stuck on an external call. Not saved.
     */
    private long externalRoutingUntil = Long.MIN_VALUE;

    /**
     * A call that won't go through, played out like one that might: the number dials out on
     * the keypad, the line holds a moment ({@link #ATTEMPT_LINE_TICKS}), then the busy tone
     * (or, for a number nobody answers, nothing) and a few words on the action bar. Ticks
     * left (synced: the client dials while it runs), what it will come to, and who lifted the
     * handset (not saved).
     */
    private int attemptTicks;
    @Nullable
    private DialResult attemptResult;
    @Nullable
    private java.util.UUID attemptPlayer;

    /**
     * Synced for the visors: the network the phone is on ({@link TelephoneNetworks}; "" for a
     * local line), and the far end of its call as the status visor shows it - number and label.
     */
    private String networkName = "";
    private String partnerNumber = "";
    private String partnerLabel = "";
    /** How often the network's name is looked up again. */
    private static final int NETWORK_NAME_EVERY_TICKS = 40;

    /** What the status visor shows: the line free, a call coming in, one going out, one in progress. */
    public enum VisorStatus {
        FREE, INCOMING, CALLING, IN_CALL;

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public <T extends SmartBlockEntity & CioTelephone> TelephoneCore(T phone) {
        this(phone, Rating.LINE_12V);
    }

    public <T extends SmartBlockEntity & CioTelephone> TelephoneCore(T phone, Rating rating) {
        this.be = phone;
        this.phone = phone;
        this.rating = rating;
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

    public boolean isOverheating() {
        return overheating;
    }

    public boolean isExploded() {
        return exploded;
    }

    boolean isBusy() {
        return busy;
    }

    boolean isCallAnswered() {
        return busy && answered;
    }

    /** True while this phone has placed a call nobody has answered yet: what the dial tone plays for. */
    public VisorStatus visorStatus() {
        if (ringing && receivingCall) {
            return VisorStatus.INCOMING;
        }
        if (busy && answered) {
            return VisorStatus.IN_CALL;
        }
        return busy || attemptTicks > 0 ? VisorStatus.CALLING : VisorStatus.FREE;
    }

    /** The network's name, "" for a local line (client: as synced). */
    public String networkName() {
        return networkName;
    }

    /** The far end's number ({@code AAA-NNNNNN}) for the status visor (client: as synced): the call's partner, or the number being dialled. */
    public String visorPartnerNumber() {
        return partnerNumber.isEmpty() && attemptTicks > 0 ? dialingTarget : partnerNumber;
    }

    public String visorPartnerLabel() {
        return partnerLabel;
    }

    /** Server: the network name now and then, the call's far end every tick (synced when they change). */
    private void syncVisor(Level level) {
        if ((level.getGameTime() + pos().asLong()) % NETWORK_NAME_EVERY_TICKS == 0) {
            String name = TelephoneNetworks.nameOf(phone);
            if (!name.equals(networkName)) {
                networkName = name;
                changed();
            }
        }
        String number = "", label = "";
        if (busy && externalCall) {
            number = externalNumber;
            label = externalLabel;
        } else if (busy && callPartner != null) {
            TelephoneNode partner = TelephoneRegistry.get(level, callPartner);
            if (partner != null) {
                number = partner.ownNumber();
                label = partner.getLabel();
            }
        }
        if (!number.equals(partnerNumber) || !label.equals(partnerLabel)) {
            partnerNumber = number;
            partnerLabel = label;
            changed();
        }
    }

    /** The line is in use: placing, ringing, on a call, or a dial attempt playing out (the dial screens' Dial key lit). */
    public boolean isLineInUse() {
        return busy || attemptTicks > 0;
    }

    public boolean isPlacingCall() {
        return (busy && !answered && !receivingCall) || attemptTicks > 0;
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
    public boolean callOutputsClosed() {
        return answered && receivingCall && (!pulsedCall || TelephoneNode.pulsePhaseOn(callTicks));
    }

    /** Whether the number currently typed into the dial screen is this phone's own — used to deny self-dialing. */
    public boolean isDialingOwnNumber() {
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
    public boolean isDialingBusyNumber() {
        if (dialingTarget.isEmpty()) {
            return false;
        }
        TelephoneNode target = TelephoneRegistry.findAnyByNumber(phone, dialingTarget);
        return target != null && target != phone && target.isBusy();
    }

    // --- ticking ------------------------------------------------------------

    /** Advances the pulse phase on the answering end of a pulsed call; tells the phone when its outputs flip. */
    public void advancePulse() {
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
     * and overheat state, exploding at the {@link Rating}'s limit, ringing and
     * Auto-Answer, dropping a call whose partner is gone or unreachable, and
     * the redstone output. Call {@link #advancePulse()} first.
     */
    public void serverTick(double voltage) {
        if (exploded) {
            return;
        }
        boolean nowPowered = voltage >= rating.powered();
        if (nowPowered != powered) {
            powered = nowPowered;
            changed();
        }

        if (voltage >= rating.explode()) {
            explode();
            return;
        }

        boolean nowOverheating = voltage > rating.overheat();
        if (nowOverheating != overheating) {
            overheating = nowOverheating;
            changed();
        }

        Level level = level();
        if (ringing) {
            ringingTicks++;
            if (level.getGameTime() % phone.ringSoundPeriodTicks() == 0) {
                phone.playRingSound();
            }
            if (ringingTicks >= RING_DELAY_TICKS && autoAnswer()) {
                answer();
            }
        }

        checkCallStillConnected();
        syncVisor(level);

        if (attemptTicks > 0) {
            if (busy || !powered) {
                endAttempt(false); // a call came in, or the line went dead
            } else if (--attemptTicks == 0) {
                endAttempt(true);
            }
        }

        // Emit a redstone signal to the neighbours (strongly into the block
        // behind the phone) while this is the receiving end of an answered,
        // ongoing call — the phone that dialled out never emits. All three
        // telephone blocks use the vanilla horizontal FACING property.
        TelephoneRedstone.sync(level, pos(), be.getBlockState(), BlockStateProperties.HORIZONTAL_FACING,
                callOutputsClosed());
    }

    /**
     * The rating's limit across the line (24 V on the wired phones) — a real vanilla explosion (sound + particles come
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

    /** Ring / busy particles and the dial tone; call from the block entity's client tick. */
    public void clientTick() {
        TelephoneClient.syncDialTone(be, isPlacingCall(), dialingTarget);
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
    public InteractionResult onFrontUsed(Player player) {
        if (level().isClientSide) {
            TelephoneClient.openDialScreen(pos(), dialingTarget, phone.radioTuning() != null);
        }
        return InteractionResult.SUCCESS;
    }

    /** back_plate — open the full settings screen (own number, area code, label, number to call, Auto-Answer, Pulse). */
    public InteractionResult onBackPlateUsed(Player player) {
        if (level().isClientSide) {
            TelephoneClient.openSettings(pos(), areaCode, ownNumberText, label, dialingTarget, autoAnswer(),
                    true, pulseMode, phone.radioTuning(), phone.keepsChunkLoaded());
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * The handset (phone_part) — dispatches by call state. ringing must
     * resolve to answer() first (ringing implies busy is already true), or
     * manually answering an incoming call would hang it up instead.
     */
    public InteractionResult onHandsetUsed(Player player) {
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (ringing) {
            answer();
        } else if (busy) {
            hangUp();
        } else if (attemptTicks > 0) {
            endAttempt(false); // handset back down before the line answered
        } else {
            boolean ownNumber = isDialingOwnNumber();
            DialResult result = dial();
            if (ownNumber && result != DialResult.CALLING) {
                result = DialResult.OWN_NUMBER;
            }
            switch (result) {
                case CALLING -> {
                }
                case BUSY, CHANNEL_BUSY, NOT_FOUND, OWN_NUMBER -> startAttempt(result, player);
                default -> player.displayClientMessage(Lang.component(failureKey(result)).withStyle(ChatFormatting.GRAY), true);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Ticks the line holds after the number has dialled out, before the outcome. */
    private static final int ATTEMPT_LINE_TICKS = 24;
    /** The keypad's pace ({@code TelephoneKeypad}), so the outcome waits for the last digit. */
    private static final int ATTEMPT_TICKS_PER_DIGIT = 3;
    /** How near the player who dialled must still be to read the outcome. */
    private static final double ATTEMPT_HEARING = 24;

    private void startAttempt(DialResult result, Player player) {
        int digits = (int) dialingTarget.chars().filter(Character::isDigit).count();
        attemptTicks = digits * ATTEMPT_TICKS_PER_DIGIT + ATTEMPT_LINE_TICKS;
        attemptResult = result;
        attemptPlayer = player.getUUID();
        changed();
    }

    /** The attempt is over: with {@code outcome}, its busy tone and words; without, it just stops. */
    private void endAttempt(boolean outcome) {
        DialResult result = attemptResult;
        java.util.UUID who = attemptPlayer;
        attemptTicks = 0;
        attemptResult = null;
        attemptPlayer = null;
        changed();
        if (!outcome || result == null) {
            return;
        }
        if (result != DialResult.NOT_FOUND) {
            playBusyTone();
        }
        Player player = who == null ? null : level().getPlayerByUUID(who);
        if (player != null && player.distanceToSqr(pos().getCenter()) <= ATTEMPT_HEARING * ATTEMPT_HEARING) {
            player.displayClientMessage(Lang.component(failureKey(result)).withStyle(ChatFormatting.GRAY), true);
        }
    }

    private void playBusyTone() {
        level().playSound(null, pos(), CIOSounds.TELEPHONE_BUSY_TONE.get(), SoundSource.BLOCKS, 0.6f, 1f);
    }

    /** The action-bar words for a call that didn't go through. */
    private String failureKey(DialResult result) {
        boolean radio = phone.radioTuning() != null;
        return switch (result) {
            case UNPOWERED -> "telephone.err.unpowered";
            case NO_NUMBER -> "telephone.err.no_number";
            case OWN_NUMBER -> "telephone.err.own_number";
            case BUSY -> "telephone.err.busy";
            case CHANNEL_BUSY -> "telephone.radio.channel_busy";
            case IN_CALL -> "telephone.err.in_call";
            default -> radio ? "telephone.err.not_found_radio" : "telephone.err.not_found";
        };
    }

    // --- call protocol -------------------------------------------------------

    /** Places a call to the dial target; what came of it (CALLING once it rings). */
    private DialResult dial() {
        if (!powered || dialingTarget.isEmpty()) {
            return powered ? DialResult.NO_NUMBER : DialResult.UNPOWERED;
        }
        TelephoneNode target = TelephoneRegistry.findByNumber(phone, dialingTarget);
        if (target == null && externalRoutingHeld() && !isDialingOwnNumber()
                && TelephoneRegistry.findAnyByNumber(phone, dialingTarget) == null) {
            dialExternal(dialingTarget);
            return DialResult.CALLING;
        }
        if (target == null || target == phone) {
            return DialResult.NOT_FOUND;
        }
        if (target.isBusy()) {
            return DialResult.BUSY;
        }
        if (phone.channelBusyFor(target) || target.channelBusyForIncoming(phone)) {
            // A radio call whose channel already carries someone else nearby: the caller's
            // busy tone comes from the attempt (handset) or computerDial.
            return DialResult.CHANNEL_BUSY;
        }

        busy = true;
        receivingCall = false;
        answered = false;
        callPartner = target.telephonePos();

        target.receiveCall(pos());

        changed();
        return DialResult.CALLING;
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
        clearExternal();
        changed();
        partnerEvent("telephone_ring");
    }

    private void answer() {
        ringing = false;
        answered = true;
        callTicks = 0;
        phone.onCallOutputsChanged();
        changed();
        partnerEvent("telephone_answer");
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
        partnerEvent("telephone_answer");
    }

    /** Resets only this phone's own call state — used both for a player-initiated hang-up and a detected disconnect. */
    void resetCallState() {
        boolean wasBusy = busy;
        busy = false;
        ringing = false;
        receivingCall = false;
        answered = false;
        ringingTicks = 0;
        callPartner = null;
        pulsedCall = false;
        callTicks = 0;
        clearExternal();
        phone.onCallOutputsChanged();
        changed();
        if (wasBusy) {
            event("telephone_hangup");
        }
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
     * gone or no longer reachable (the wire got cut), silently resets this
     * phone, and the partner too while its call is still with this one. The
     * partner can't be left to notice by itself: over an interconnect trunk it
     * may be far off, loaded but not ticking, and would stay "in call".
     */
    private void checkCallStillConnected() {
        if (!busy) {
            return;
        }
        if (externalCall) {
            // The computer at the far end stopped renewing its lease: it is gone.
            if (!externalRoutingHeld()) {
                hangUp();
            }
            return;
        }
        if (callPartner == null) {
            // Busy with nobody: a half-loaded or stale state. Free the line.
            resetCallState();
            return;
        }
        TelephoneNode partner = TelephoneRegistry.get(level(), callPartner);
        if (partner == null || !phone.canReach(partner)) {
            dropLine(partner, pos());
            resetCallState();
        }
    }

    /** The line to {@code partner} is cut: its end of the call drops too, if that call is still with {@code self}. */
    public static void dropLine(@Nullable TelephoneNode partner, BlockPos self) {
        if (partner != null && partner.isBusy() && self.equals(partner.callPartnerPos())) {
            partner.resetCallState();
        }
    }

    // --- computers ---------------------------------------------------------------

    /** What a computer's dial attempt came to; the name, lower-cased, is what Lua sees. */
    public enum DialResult {
        CALLING, IN_CALL, UNPOWERED, NO_NUMBER, OWN_NUMBER, NOT_FOUND, BUSY,
        /** Radio only: another transmitter already uses the channel near one of the two phones. */
        CHANNEL_BUSY
    }

    /**
     * A computer dials: like lifting the handset, with {@code number} (already
     * formatted {@code AAA-NNNNNN}) first set as the number to call when given,
     * else whatever is set. Says why when no call starts.
     */
    public DialResult computerDial(@Nullable String number) {
        if (busy) {
            return DialResult.IN_CALL;
        }
        if (number != null) {
            setDialingTarget(number);
        }
        if (!powered) {
            return DialResult.UNPOWERED;
        }
        if (dialingTarget.isEmpty()) {
            return DialResult.NO_NUMBER;
        }
        if (isDialingOwnNumber()) {
            return DialResult.OWN_NUMBER;
        }
        TelephoneNode target = TelephoneRegistry.findAnyByNumber(phone, dialingTarget);
        if (target == null && !externalRoutingHeld()) {
            return DialResult.NOT_FOUND;
        }
        if (target != null && target.isBusy()) {
            return DialResult.BUSY;
        }
        DialResult result = dial();
        if (result == DialResult.CHANNEL_BUSY) {
            playBusyTone();
            return result;
        }
        return busy ? DialResult.CALLING : DialResult.NOT_FOUND;
    }

    /** A computer picks up a ringing call. False when nothing is ringing. */
    public boolean computerAnswer() {
        if (!ringing) {
            return false;
        }
        answer();
        return true;
    }

    /** A computer hangs up: ends a call, stops a call it placed, or rejects one ringing. False when idle. */
    public boolean computerHangUp() {
        if (!busy) {
            return false;
        }
        hangUp();
        return true;
    }

    /** "idle", "calling" (placed, not yet answered), "ringing" (incoming) or "in_call". */
    public String callState() {
        if (ringing && receivingCall) {
            return "ringing";
        }
        if (busy && answered) {
            return "in_call";
        }
        return busy ? "calling" : "idle";
    }

    /** The phone at the other end of the current (or ringing) call, if it is loaded. */
    @Nullable
    public TelephoneNode partner() {
        return callPartner == null ? null : TelephoneRegistry.get(level(), callPartner);
    }

    /** The far end's number: the partner phone's, or the external caller's / callee's; empty when idle. */
    public String partnerNumber() {
        if (externalCall) {
            return externalNumber;
        }
        TelephoneNode peer = partner();
        return peer != null ? peer.ownNumber() : "";
    }

    /** The far end's label, as {@link #partnerNumber()}. */
    public String partnerLabel() {
        if (externalCall) {
            return externalLabel;
        }
        TelephoneNode peer = partner();
        return peer != null ? peer.getLabel() : "";
    }

    /** A call event: to the computers cabled to this phone, and to the phone itself (a radio phone's TeleLink). */
    private void event(String name, Object... args) {
        ComputerLinkBehaviour.event(be, name, args);
        phone.onCallEvent(name, args);
    }

    /** Tells the computers on this phone {@code name}, with the partner's number and label. */
    private void partnerEvent(String name) {
        event(name, partnerNumber(), partnerLabel());
    }

    // --- external calls (the far end is a computer) ---------------------------

    /** Ticks a {@link #holdExternalRouting} call lasts: renew it well within this. */
    public static final int EXTERNAL_ROUTING_LEASE_TICKS = 100;

    public boolean isExternalCall() {
        return externalCall;
    }

    /**
     * A computer takes (on) or gives up (off) calls to numbers no wired phone has, for
     * {@link #EXTERNAL_ROUTING_LEASE_TICKS}: dialling one then places an external call and
     * tells the computer ({@code telephone_dial}). Giving it up ends a current external call.
     */
    public void holdExternalRouting(boolean on) {
        Level level = level();
        if (level == null) {
            return;
        }
        externalRoutingUntil = on ? level.getGameTime() + EXTERNAL_ROUTING_LEASE_TICKS : Long.MIN_VALUE;
        if (!on && externalCall && busy) {
            hangUp();
        }
    }

    public boolean externalRoutingHeld() {
        Level level = level();
        return level != null && level.getGameTime() <= externalRoutingUntil;
    }

    /** An outgoing call to {@code number}, carried by the computer holding external routing. */
    private void dialExternal(String number) {
        busy = true;
        receivingCall = false;
        answered = false;
        callPartner = null;
        externalCall = true;
        externalNumber = number;
        externalLabel = "";
        changed();
        event("telephone_dial", number);
    }

    /**
     * Rings this phone with a call from {@code number} / {@code label} that a computer
     * carries (e.g. arriving by radio). Answering it works as for any call and tells the
     * computer ({@code telephone_answer}). False when the phone is busy or unpowered.
     */
    public boolean computerRingExternal(String number, String label) {
        if (busy || !powered || exploded) {
            return false;
        }
        busy = true;
        ringing = true;
        ringingTicks = 0;
        receivingCall = true;
        answered = false;
        callPartner = null;
        pulsedCall = false;
        callTicks = 0;
        externalCall = true;
        externalNumber = number;
        externalLabel = label;
        changed();
        partnerEvent("telephone_ring");
        return true;
    }

    /** The computer's far end answered an external call placed from here. False when there is none waiting. */
    public boolean computerConnectExternal() {
        if (!externalCall || !busy || receivingCall || answered) {
            return false;
        }
        answered = true;
        phone.onCallOutputsChanged();
        changed();
        partnerEvent("telephone_answer");
        return true;
    }

    /** The computer names the far end of the current external call (e.g. once the remote station replies). */
    public void setExternalLabel(String label) {
        if (externalCall) {
            externalLabel = label;
            changed();
        }
    }

    private void clearExternal() {
        externalCall = false;
        externalNumber = "";
        externalLabel = "";
    }

    // --- readout + NBT ---------------------------------------------------------

    public void addToTooltip(List<Component> tooltip) {
        tooltip.add(Component.literal("    ").append(phone.telephoneName()).withStyle(ChatFormatting.WHITE));
        tooltip.add(Lang.component("telephone.number", ownNumber()).withStyle(ChatFormatting.GRAY));
        if (!label.isEmpty()) {
            tooltip.add(Component.literal(label).withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
        }
        tooltip.add(Lang.component("telephone.powered", Lang.component(powered ? "common.yes" : "common.no"))
                .withStyle(powered ? ChatFormatting.GREEN : ChatFormatting.RED));

        if (ringing && receivingCall) {
            tooltip.add(partnerLine("telephone.incoming_call").withStyle(ChatFormatting.YELLOW));
        } else if (busy && answered) {
            tooltip.add(partnerLine("telephone.in_call").withStyle(ChatFormatting.GREEN));
        } else if (busy) {
            tooltip.add(partnerLine("telephone.calling").withStyle(ChatFormatting.YELLOW));
        } else if (!dialingTarget.isEmpty()) {
            tooltip.add(Lang.component("telephone.dialling", dialDisplay()).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Lang.component("telephone.idle").withStyle(ChatFormatting.GREEN));
        }

        tooltip.add(Lang.component("telephone.auto_answer_state", Lang.component(autoAnswer() ? "common.on" : "common.off"))
                .withStyle(autoAnswer() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        if (pulseMode) {
            tooltip.add(Lang.component("telephone.pulse_3s_on_for_calls_from_here").withStyle(ChatFormatting.GRAY));
        }
        if (pulsedCall && receivingCall) {
            tooltip.add(Lang.component("telephone.caller_is_pulsing_this_line").withStyle(ChatFormatting.GOLD));
        }

        if (overheating) {
            tooltip.add(Lang.component("telephone.overheating").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }
        tooltip.add(Lang.component("telephone.wiring", phone.wiringLabel()).withStyle(ChatFormatting.GRAY));
    }

    /**
     * A call-state line: {@code key} alone, or {@code key + "_with"} naming the
     * connected/ringing peer (its label, else its number) when that resolves.
     */
    private MutableComponent partnerLine(String key) {
        if (externalCall && !(externalLabel.isEmpty() && externalNumber.isEmpty())) {
            return Lang.component(key + "_with", externalLabel.isEmpty() ? externalNumber : externalLabel);
        }
        if (callPartner != null && level() != null) {
            TelephoneNode peer = TelephoneRegistry.get(level(), callPartner);
            if (peer != null) {
                return Lang.component(key + "_with", peer.getLabel().isEmpty() ? peer.ownNumber() : peer.getLabel());
            }
        }
        return Lang.component(key);
    }

    /** The number typed into the dial screen, shown as its target's label when that resolves. */
    private String dialDisplay() {
        TelephoneNode target = TelephoneRegistry.findAnyByNumber(phone, dialingTarget);
        return (target != null && !target.getLabel().isEmpty()) ? target.getLabel() : dialingTarget;
    }

    public void read(CompoundTag tag) {
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
        externalCall = tag.getBoolean("ExternalCall");
        externalNumber = tag.getString("ExternalNumber");
        externalLabel = tag.getString("ExternalLabel");
        attemptTicks = tag.getInt("AttemptTicks");
        networkName = tag.getString("NetworkName");
        partnerNumber = tag.getString("PartnerNumber");
        partnerLabel = tag.getString("PartnerLabel");
    }

    public void write(CompoundTag tag) {
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
        if (externalCall) {
            tag.putBoolean("ExternalCall", true);
            tag.putString("ExternalNumber", externalNumber);
            tag.putString("ExternalLabel", externalLabel);
        }
        if (attemptTicks > 0) {
            tag.putInt("AttemptTicks", attemptTicks);
        }
        tag.putString("NetworkName", networkName);
        if (!partnerNumber.isEmpty() || !partnerLabel.isEmpty()) {
            tag.putString("PartnerNumber", partnerNumber);
            tag.putString("PartnerLabel", partnerLabel);
        }
    }
}
