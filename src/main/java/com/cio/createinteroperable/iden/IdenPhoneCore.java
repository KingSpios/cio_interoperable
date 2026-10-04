package com.cio.createinteroperable.iden;

import com.cio.createinteroperable.TelephoneNode;
import com.cio.createinteroperable.TelephoneNumbers;
import com.cio.createinteroperable.TelephoneRedstone;
import com.cio.createinteroperable.TelephoneRegistry;
import com.cio.createinteroperable.compat.ElectroEnergeticsCompat;
import com.cio.createinteroperable.compat.PowerGridCompat;
import com.cio.createinteroperable.grid.GridConnection;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Everything an Iden's Decor telephone knows and does, independent of which
 * block entity class carries it (see {@link IdenPhone}). Call semantics follow
 * CIO's own telephones, so the two interoperate through {@code TelephoneRegistry}:
 *
 * <ul>
 *   <li><b>Power</b> (appliance grid / Crayfish) gates everything: an unpowered
 *       phone can't dial, reads as busy to callers, and drops a call in progress.</li>
 *   <li><b>Dialling</b>: lifting the handset, or a rising redstone edge while
 *       idle, calls {@link #dialTarget} &mdash; if some phone with that number is
 *       reachable over the tap wires (CPG network identity or CEE tap-graph walk,
 *       exactly {@link TelephoneNode#canReach}). A redstone-placed call is
 *       withdrawn again if the signal drops before it is answered.</li>
 *   <li><b>Receiving</b>: rings (Iden's own ring sound) until answered by lifting
 *       the handset, or by itself after a short delay with Auto-Answer on.</li>
 *   <li><b>Hanging up</b>: returning the handset to the cradle ends the call on
 *       both ends; so does power loss or a cut tap wire.</li>
 *   <li><b>Redstone</b>: the receiving end emits a lever-like signal while the call
 *       is answered ({@code TelephoneRedstone}, as on CIO's phones) &mdash; cycling
 *       3 s on / 3 s off if the caller is a CIO phone with Pulse (3s) on.</li>
 *   <li>A phone whose handset is off the cradle while idle is off-hook: busy to callers.</li>
 * </ul>
 */
public final class IdenPhoneCore {

    /** Iden's ring clip is ~2.8 s; ring, pause, ring. */
    private static final int RING_PERIOD_TICKS = 80;
    /** How long an Auto-Answer phone rings before picking up (same as CIO's phones). */
    private static final int AUTO_ANSWER_DELAY_TICKS = 40;
    private static final int TAP_POLL_TICKS = 20;

    private final SmartBlockEntity be;
    private final IdenPhone phone;

    // --- settings (the sneak-click screen) ---
    private int areaCode;
    private String ownNumberText = "";
    private String label = "";
    /** Full formatted number to call ({@code AAA-NNNNNN}), or empty. */
    private String dialTarget = "";
    private boolean autoAnswer;

    // --- call state (as CIO's TelephoneBlockEntity) ---
    private boolean busy;
    private boolean ringing;
    /** True only on the phone that was called. */
    private boolean receivingCall;
    private boolean answered;
    private int ringingTicks;
    /** This (incoming) call came from a phone with Pulse (3s) on: the redstone output cycles. */
    private boolean pulsedCall;
    /** Ticks since the incoming call was answered (drives the pulse phase). */
    private int callTicks;
    @Nullable
    private BlockPos callPartner;

    // --- appliance-grid power ---
    private boolean powered;
    private boolean receivingPower;
    /** Native-grid links; the Crayfish adapter keeps its own set when Crayfish is installed. */
    private final Set<GridConnection> connections = new HashSet<>();

    // --- tap wiring, server-polled and synced for the goggle readout ---
    private boolean pgTap;
    private boolean ceeTap;

    public IdenPhoneCore(SmartBlockEntity be, IdenPhone phone) {
        this.be = be;
        this.phone = phone;
    }

    // --- accessors -------------------------------------------------------

    public int areaCode() { return areaCode; }
    public String ownNumberText() { return ownNumberText; }
    public String label() { return label; }
    public String dialTarget() { return dialTarget; }
    public boolean autoAnswer() { return autoAnswer; }
    public boolean powered() { return powered; }
    public boolean receivingPower() { return receivingPower; }
    public Set<GridConnection> gridConnections() { return connections; }
    public boolean inCallOrRinging() { return busy; }
    public boolean callAnswered() { return busy && answered; }

    @Nullable
    public BlockPos callPartner() { return callPartner; }

    public String ownNumber() {
        return TelephoneNumbers.formatNumber(areaCode, ownNumberText);
    }

    /** What CIO's registry treats as "busy": on a call, unpowered, or off-hook. */
    public boolean isUnavailable() {
        return busy || !powered || !handsetOnCradle();
    }

    private boolean handsetOnCradle() {
        return be.getBlockState().hasProperty(IdenPhoneBlocks.PHONE) && be.getBlockState().getValue(IdenPhoneBlocks.PHONE);
    }

    @Nullable
    private Level level() {
        return be.getLevel();
    }

    private BlockPos pos() {
        return be.getBlockPos();
    }

    private void changed() {
        be.setChanged();
        Level level = level();
        if (level != null && !level.isClientSide) {
            be.notifyUpdate();
        }
    }

    // --- power -----------------------------------------------------------

    public void setPowered(boolean powered) {
        if (this.powered != powered) {
            this.powered = powered;
            changed();
        }
    }

    public void setReceivingPower(boolean receiving) {
        this.receivingPower = receiving;
    }

    // --- settings --------------------------------------------------------

    public void setLabel(String label) {
        this.label = label.length() > 32 ? label.substring(0, 32) : label;
        changed();
    }

    public void setDialTarget(String target) {
        this.dialTarget = target.length() > 16 ? target.substring(0, 16) : target;
        changed();
    }

    /** Rejected (nothing applied, false) if another loaded phone already owns that number. */
    public boolean setOwnNumber(int areaCode, String number) {
        int area = Math.max(0, Math.min(999, areaCode));
        String sanitized = TelephoneNumbers.sanitizeNumberText(number);
        if (area == this.areaCode && sanitized.equals(this.ownNumberText)) {
            return true;
        }
        if (TelephoneRegistry.isNumberTaken(phone, area, sanitized)) {
            return false;
        }
        this.areaCode = area;
        this.ownNumberText = sanitized;
        changed();
        return true;
    }

    public void setAutoAnswer(boolean autoAnswer) {
        this.autoAnswer = autoAnswer;
        changed();
    }

    public void denyFeedback() {
        Level level = level();
        if (level == null) {
            return;
        }
        AllSoundEvents.DENY.play(level, null, pos());
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.WHITE_SMOKE, pos().getX() + 0.5, pos().getY() + 0.4,
                    pos().getZ() + 0.5, 6, 0.2, 0.15, 0.2, 0.01);
        }
    }

    // --- player / redstone triggers --------------------------------------

    /** The handset was just lifted off the cradle by {@code player}. */
    public void onHandsetLifted(Player player) {
        if (ringing) {
            answer();
            return;
        }
        if (busy || dialTarget.isEmpty()) {
            // Already on a call (auto-answered, or redstone-dialled): picking up
            // just joins it. No number set: a plain decorative phone.
            return;
        }
        String failure = dial();
        if (failure != null) {
            denyFeedback();
            player.displayClientMessage(Component.literal(failure).withStyle(ChatFormatting.RED), true);
        }
    }

    /** The handset was put back on the cradle. */
    public void onHandsetReturned() {
        if (busy) {
            hangUp();
        }
    }

    /** Rising redstone edge: an idle, powered phone calls its configured number. */
    public void onRedstoneRise() {
        if (busy || !powered || dialTarget.isEmpty()) {
            return;
        }
        if (dial() != null) {
            denyFeedback();
        }
    }

    /**
     * Falling redstone edge: an idle phone (handset on the cradle) stops trying
     * to call &mdash; an outgoing call that hasn't been answered yet is withdrawn,
     * so the called phone stops ringing. An answered call, an incoming call, or
     * one placed by lifting the handset is left alone.
     */
    public void onRedstoneFall() {
        if (busy && !receivingCall && !answered && handsetOnCradle()) {
            hangUp();
        }
    }

    // --- call state machine ----------------------------------------------

    /** @return {@code null} once the call is placed, else a player-facing reason it wasn't. */
    @Nullable
    private String dial() {
        if (!powered) {
            return "No power.";
        }
        if (dialTarget.equals(ownNumber())) {
            return "That's this phone's own number.";
        }
        TelephoneNode target = TelephoneRegistry.findAnyByNumber(phone, dialTarget);
        if (target == null || target == phone) {
            return "No phone with that number is connected to this one.";
        }
        if (target.isBusy()) {
            return "That number is busy.";
        }
        busy = true;
        ringing = false;
        receivingCall = false;
        answered = false;
        callPartner = target.telephonePos();
        target.receiveCall(pos());
        changed();
        return null;
    }

    public void receiveCall(BlockPos callerPos) {
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
        changed();
        TelephoneNode partner = TelephoneRegistry.get(level(), callPartner);
        if (partner != null) {
            partner.notifyAnswered();
        }
    }

    public void notifyAnswered() {
        answered = true;
        changed();
    }

    public void resetCallState() {
        busy = false;
        ringing = false;
        receivingCall = false;
        answered = false;
        ringingTicks = 0;
        callPartner = null;
        pulsedCall = false;
        callTicks = 0;
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

    // --- ticking ---------------------------------------------------------

    public void tick() {
        Level level = level();
        if (level == null) {
            return;
        }
        if (level.isClientSide) {
            if (ringing && level.random.nextInt(6) == 0) {
                level.addParticle(ParticleTypes.NOTE, pos().getX() + 0.5, pos().getY() + 0.7, pos().getZ() + 0.5,
                        level.random.nextDouble(), 0, 0);
            }
            return;
        }

        if (busy && !powered) {
            hangUp();
        }
        if (ringing) {
            ringingTicks++;
            if ((ringingTicks - 1) % RING_PERIOD_TICKS == 0) {
                IdenPhoneBlocks.playIdenSound(level, pos(), "phone_ring");
            }
            if (autoAnswer && ringingTicks >= AUTO_ANSWER_DELAY_TICKS) {
                answer();
            }
        }
        if (busy && callPartner != null) {
            TelephoneNode partner = TelephoneRegistry.get(level, callPartner);
            if (partner == null || !phone.canReach(partner)) {
                resetCallState();
            }
        }
        if (answered && receivingCall && pulsedCall) {
            callTicks++;
        }
        TelephoneRedstone.sync(level, pos(), be.getBlockState(), IdenPhoneBlocks.FACING,
                answered && receivingCall && (!pulsedCall || TelephoneNode.pulsePhaseOn(callTicks)));

        if ((level.getGameTime() + pos().hashCode()) % TAP_POLL_TICKS == 0) {
            boolean pg = phone.pgTapConnected();
            boolean cee = phone.ceeTapConnected();
            if (pg != pgTap || cee != ceeTap) {
                pgTap = pg;
                ceeTap = cee;
                changed();
            }
        }
    }

    // --- NBT (world save and client sync) --------------------------------

    public void write(CompoundTag tag) {
        tag.putInt("AreaCode", areaCode);
        tag.putString("OwnNumberText", ownNumberText);
        tag.putString("Label", label);
        tag.putString("DialTarget", dialTarget);
        tag.putBoolean("AutoAnswer", autoAnswer);
        tag.putBoolean("Busy", busy);
        tag.putBoolean("Ringing", ringing);
        tag.putBoolean("ReceivingCall", receivingCall);
        tag.putBoolean("Answered", answered);
        tag.putInt("RingingTicks", ringingTicks);
        tag.putBoolean("PulsedCall", pulsedCall);
        tag.putInt("CallTicks", callTicks);
        if (callPartner != null) {
            tag.putLong("CallPartner", callPartner.asLong());
        }
        tag.putBoolean("CioPowered", powered);
        tag.putBoolean("PgTap", pgTap);
        tag.putBoolean("CeeTap", ceeTap);
    }

    public void read(CompoundTag tag) {
        areaCode = tag.getInt("AreaCode");
        ownNumberText = TelephoneNumbers.sanitizeNumberText(tag.getString("OwnNumberText"));
        label = tag.getString("Label");
        dialTarget = tag.getString("DialTarget");
        autoAnswer = tag.getBoolean("AutoAnswer");
        busy = tag.getBoolean("Busy");
        ringing = tag.getBoolean("Ringing");
        receivingCall = tag.getBoolean("ReceivingCall");
        answered = tag.getBoolean("Answered");
        ringingTicks = tag.getInt("RingingTicks");
        pulsedCall = tag.getBoolean("PulsedCall");
        callTicks = tag.getInt("CallTicks");
        callPartner = tag.contains("CallPartner") ? BlockPos.of(tag.getLong("CallPartner")) : null;
        powered = tag.getBoolean("CioPowered");
        pgTap = tag.getBoolean("PgTap");
        ceeTap = tag.getBoolean("CeeTap");
    }

    // --- goggles ---------------------------------------------------------

    public void addGoggleTooltip(List<Component> tooltip) {
        tooltip.add(Component.literal("    Telephone").withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.literal("Number: " + (ownNumberText.isEmpty() ? "not set" : ownNumber()))
                .withStyle(ChatFormatting.GRAY));
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
        } else if (!handsetOnCradle()) {
            tooltip.add(Component.literal("Off the hook").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("Idle").withStyle(ChatFormatting.GREEN));
        }

        tooltip.add(Component.literal("Calls: " + (dialTarget.isEmpty() ? "not set" : dialDisplay()))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Auto-Answer: " + (autoAnswer ? "On" : "Off"))
                .withStyle(autoAnswer ? ChatFormatting.GREEN : ChatFormatting.GRAY));

        StringBuilder taps = new StringBuilder("Tap: ");
        if (PowerGridCompat.present()) {
            taps.append("CPG ").append(pgTap ? "✔" : "✘");
        }
        if (ElectroEnergeticsCompat.present()) {
            if (PowerGridCompat.present()) {
                taps.append("  ");
            }
            taps.append("CEE ").append(ceeTap ? "✔" : "✘");
        }
        tooltip.add(Component.literal(taps.toString())
                .withStyle(pgTap || ceeTap ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
    }

    private String partnerSuffix() {
        TelephoneNode peer = TelephoneRegistry.get(level(), callPartner);
        if (peer == null) {
            return "";
        }
        return ": " + (peer.getLabel().isEmpty() ? peer.ownNumber() : peer.getLabel());
    }

    private String dialDisplay() {
        TelephoneNode target = TelephoneRegistry.findAnyByNumber(phone, dialTarget);
        return target != null && !target.getLabel().isEmpty()
                ? target.getLabel() + " (" + dialTarget + ")"
                : dialTarget;
    }
}
