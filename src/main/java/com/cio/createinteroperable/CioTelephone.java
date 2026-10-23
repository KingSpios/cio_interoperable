package com.cio.createinteroperable;

import net.minecraft.core.BlockPos;

/**
 * One of CIO's own telephones (Interoperable, CPG, CEE): a {@link TelephoneNode}
 * whose whole call side lives in a {@link TelephoneCore}. The defaults here
 * forward the node's methods to that core, so a telephone block entity only
 * supplies its core, its Auto-Answer slider and the electrical specifics below
 * (plus {@link #pgTapNetwork()} / {@link #ceeTapReaches} for reachability).
 *
 * <p>Like the core, this must never reference a Power Grid or Electro
 * Energetics type. Verified when this was introduced: no superclass of the
 * three block entities (Create's SmartBlockEntity chain, PG's
 * ElectricBlockEntity) declares a method with any of these names, which would
 * otherwise silently win over the defaults.</p>
 */
interface CioTelephone extends TelephoneNode {

    TelephoneCore telephoneCore();

    /** The in-world Auto-Answer slider (created in the block entity's addBehaviours). */
    TelephoneAutoAnswerBehaviour autoAnswerBehaviour();

    /** The call outputs just changed state; phones with real breakers re-set them here. */
    default void onCallOutputsChanged() {
    }

    /** One ring of the bell, every second while ringing. */
    void playRingSound();

    /** Goggle readout header, e.g. "CPG Telephone". */
    String telephoneName();

    /** Goggle readout "Wiring:" value. */
    String wiringLabel();

    // --- TelephoneNode, forwarded to the core ---

    @Override
    default BlockPos telephonePos() {
        return telephoneCore().pos();
    }

    @Override
    default boolean isBusy() {
        return telephoneCore().isBusy();
    }

    @Override
    default String ownNumber() {
        return telephoneCore().ownNumber();
    }

    @Override
    default int getAreaCode() {
        return telephoneCore().areaCode();
    }

    @Override
    default String getOwnNumberText() {
        return telephoneCore().ownNumberText();
    }

    @Override
    default boolean isCallAnswered() {
        return telephoneCore().isCallAnswered();
    }

    @Override
    default BlockPos callPartnerPos() {
        return telephoneCore().callPartner();
    }

    @Override
    default String getLabel() {
        return telephoneCore().label();
    }

    @Override
    default boolean isPowered() {
        return telephoneCore().isPowered();
    }

    @Override
    default void setDialingTarget(String target) {
        telephoneCore().setDialingTarget(target);
    }

    @Override
    default String getDialingTarget() {
        return telephoneCore().dialingTarget();
    }

    @Override
    default boolean isAutoAnswer() {
        return telephoneCore().autoAnswer();
    }

    @Override
    default void setAutoAnswer(boolean autoAnswer) {
        telephoneCore().setAutoAnswer(autoAnswer);
    }

    @Override
    default boolean supportsPulse() {
        return true;
    }

    @Override
    default boolean isPulse() {
        return telephoneCore().pulseMode();
    }

    @Override
    default void setPulse(boolean pulse) {
        telephoneCore().setPulse(pulse);
    }

    @Override
    default void setLabel(String label) {
        telephoneCore().setLabel(label);
    }

    @Override
    default boolean setOwnNumber(int areaCode, String number) {
        return telephoneCore().setOwnNumber(areaCode, number);
    }

    @Override
    default void denyFeedback() {
        telephoneCore().denyFeedback();
    }

    @Override
    default void receiveCall(BlockPos callerPos) {
        telephoneCore().receiveCall(callerPos);
    }

    @Override
    default void notifyAnswered() {
        telephoneCore().notifyAnswered();
    }

    @Override
    default void resetCallState() {
        telephoneCore().resetCallState();
    }
}
