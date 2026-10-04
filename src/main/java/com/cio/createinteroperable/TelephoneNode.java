package com.cio.createinteroperable;

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
     * Real dialing/busy/call-still-connected reachability: true for self,
     * true if both sides share a live PG tap network, else whatever this
     * side's own CEE tap-graph walk finds. PG and CEE are deliberately never
     * bridged — either protocol's own tap connectivity is independently
     * sufficient.
     */
    default boolean canReach(TelephoneNode other) {
        if (other == this) {
            return true;
        }
        Object myPg = pgTapNetwork();
        if (myPg != null && myPg.equals(other.pgTapNetwork())) {
            return true;
        }
        return ceeTapReaches(other.telephonePos());
    }
}
