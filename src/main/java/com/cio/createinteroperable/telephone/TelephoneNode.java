package com.cio.createinteroperable.telephone;

import net.minecraft.core.BlockPos;

/**
 * Shared contract implemented by all three Telephone BlockEntity variants —
 * Interoperable (needs both PG and CEE), CPG-only, and CEE-only — so
 * {@link TelephoneRegistry}, the dial/label/number packets, and
 * {@link TelephoneRenderer} can work with any of them without knowing which
 * one they've got.
 *
 * <p>Deliberately 100% protocol-neutral in its own method signatures (no
 * Power Grid or Electro Energetics type anywhere here) — this interface is
 * implemented by all three concrete classes, including the CEE-only one,
 * which must never force Power Grid to load. {@link #pgTapNetwork()} returns
 * a plain {@code Object} for exactly this reason: a CPG-having node returns
 * its real (PG-typed) tap network upcast to {@code Object}, a CEE-only node
 * just returns {@code null} — neither side needs to reference the other's
 * type. {@link #canReach} builds cross-protocol reachability out of these two
 * primitives without either protocol's type ever appearing here, matching
 * the original Interoperable Telephone's "PG network identity OR CEE
 * tap-graph walk, checked independently, never bridged" semantics.</p>
 */
public interface TelephoneNode {

    BlockPos telephonePos();

    boolean isBusy();

    String ownNumber();

    int getAreaCode();

    String getOwnNumberText();

    /** True while this phone is on an answered, ongoing call (ringing / dialling doesn't count) — what call audio relaying keys off. */
    default boolean isCallAnswered() {
        return false;
    }

    /**
     * True while this phone's call (ringing, placed or answered) has a computer at the far
     * end instead of another phone, e.g. a radio link: see {@code TelephoneCore#computerRingExternal}.
     * {@link #callPartnerPos()} is then null.
     */
    default boolean isExternalCall() {
        return false;
    }

    /**
     * On an external call: the phone at the far end, when the call's carriers told this
     * phone where it is (two Radio Telephones joined through TeleLink computers). Both
     * ends naming each other get their voice relayed directly. Null otherwise.
     */
    @org.jetbrains.annotations.Nullable
    default BlockPos voicePartnerPos() {
        return null;
    }

    /** The other end of this phone's current call, or {@code null} when there isn't one. */
    @org.jetbrains.annotations.Nullable
    default BlockPos callPartnerPos() {
        return null;
    }

    String getLabel();

    boolean isPowered();

    void setDialingTarget(String target);

    /** The number this phone calls (formatted {@code AAA-NNNNNN}), or empty. */
    String getDialingTarget();

    boolean isAutoAnswer();

    void setAutoAnswer(boolean autoAnswer);

    /** Half-period of a "Pulse (3s)" call, in ticks: the answering phone's call outputs are on this long, then off this long. */
    int PULSE_TICKS = 60;

    /** Pulse phase for an answered call {@code ticksSinceAnswered} old: on for the first {@link #PULSE_TICKS}, off for the next, and so on. */
    static boolean pulsePhaseOn(int ticksSinceAnswered) {
        return (ticksSinceAnswered / PULSE_TICKS) % 2 == 0;
    }

    /** Whether this phone offers the Pulse (3s) setting (CIO's three telephones; not Iden's). */
    default boolean supportsPulse() {
        return false;
    }

    /**
     * "Pulse (3s)", a caller-side setting: while a call placed FROM this phone is
     * answered, the answering phone's call outputs (Call Breaker, Call Feed +/-,
     * redstone) cycle on/off every {@link #PULSE_TICKS} instead of staying on.
     * Its own power input and tap line are unaffected. Read by the answering
     * phone in {@link #receiveCall}.
     */
    default boolean isPulse() {
        return false;
    }

    default void setPulse(boolean pulse) {
    }

    /** A radio telephone's channel and transmit level; null for a phone that only works on wires. */
    @org.jetbrains.annotations.Nullable
    default RadioTuning radioTuning() {
        return null;
    }

    /**
     * How much louder than the configured line noise this phone's earpiece hisses: 1 on a
     * wire; a Radio Telephone on a radio call raises it as the signal weakens or another
     * transmitter interferes.
     */
    default float lineNoiseFactor() {
        return 1f;
    }

    /** Retunes a radio telephone; ignored by every other phone. */
    default void setRadioTuning(RadioTuning tuning) {
    }

    /** A radio telephone set to keep its chunk loaded (so it works with nobody nearby); false for every other phone. */
    default boolean keepsChunkLoaded() {
        return false;
    }

    /** Sets a radio telephone's "keep chunk loaded"; ignored by every other phone. */
    default void setKeepChunkLoaded(boolean keep) {
    }

    void setLabel(String label);

    /** Returns false (no change applied) when the combination is already taken by another loaded phone. */
    boolean setOwnNumber(int areaCode, String number);

    void denyFeedback();

    /** Callee-side half of {@code dial()}: mark this phone as ringing, called by {@code callerPos}. */
    void receiveCall(BlockPos callerPos);

    /** Caller-side mirror of the callee answering, so both ends read "In call" rather than "Calling…". */
    void notifyAnswered();

    /** Clears all call state (busy/ringing/receivingCall/answered/callPartner) on just this phone. */
    void resetCallState();

    /** This node's live Power Grid tap-network identity (an opaque, {@code equals()}-comparable key), or {@code null} if it has no PG tap at all (no PG wire, or Power Grid absent). */
    Object pgTapNetwork();

    /** True if this node's own CEE tap-wire graph reaches the node at {@code otherPos}. False if this node has no CEE tap at all. */
    boolean ceeTapReaches(BlockPos otherPos);

    /**
     * Real dialing/busy/call-still-connected reachability: the wire
     * ({@link #canReachByWire}), or a bridge between two kinds of line
     * ({@link TelephoneBridges}: a Radio Telephone Interconnect joining a radio
     * phone to the wired phones on its tap).
     */
    default boolean canReach(TelephoneNode other) {
        return canReachByWire(other) || TelephoneBridges.reaches(this, other);
    }

    /**
     * Reachability over the tap wire alone: true for self, true if both sides share a live
     * PG tap network, else whatever this side's own CEE tap-graph walk finds. PG and CEE are
     * deliberately never bridged — either protocol's own tap connectivity is independently
     * sufficient.
     */
    default boolean canReachByWire(TelephoneNode other) {
        if (other == this) {
            return true;
        }
        Object myPg = pgTapNetwork();
        if (myPg != null && myPg.equals(other.pgTapNetwork())) {
            return true;
        }
        return ceeTapReaches(other.telephonePos());
    }

    /**
     * Whether a call from {@code caller} can't reach this phone because its radio channel
     * is taken (a Radio Telephone called from a wired phone through an interconnect whose
     * band is full, or a wired phone called over an interconnect trunk with no channel
     * free). Called by the caller's dial, after its own check; the caller then hears a busy
     * tone.
     */
    default boolean channelBusyForIncoming(TelephoneNode caller) {
        return !canReachByWire(caller) && TelephoneBridges.channelBusy(caller, this);
    }
}
