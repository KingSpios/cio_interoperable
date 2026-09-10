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

    String getLabel();

    boolean isPowered();

    void setDialingTarget(String target);

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
