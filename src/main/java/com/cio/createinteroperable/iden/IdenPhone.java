package com.cio.createinteroperable.iden;

import com.cio.createinteroperable.TelephoneNode;
import com.cio.createinteroperable.deb.MeteredAppliance;
import com.cio.createinteroperable.grid.ApplianceNode;
import com.cio.createinteroperable.grid.GridConnection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.Set;

/**
 * An Iden's Decor telephone, whichever block entity backs it (Power Grid's
 * {@link IdenPgTelephoneBlockEntity} or the CEE-only
 * {@link IdenCeeTelephoneBlockEntity}). It is at once a CIO
 * {@link TelephoneNode} (so it's in {@code TelephoneRegistry} and can call, and
 * be called by, CIO's own telephones) and an appliance-grid node (power gates
 * everything it does). Every method here forwards to the shared,
 * protocol-neutral {@link IdenPhoneCore}; each block entity supplies only its
 * own tap wiring.
 */
public interface IdenPhone extends TelephoneNode, ApplianceNode, MeteredAppliance {

    IdenPhoneCore phoneCore();

    /** True while a Power Grid wire is on this phone's CPG tap. */
    boolean pgTapConnected();

    /** True while an Electro Energetics wire is on this phone's CEE tap (node 2). */
    boolean ceeTapConnected();

    // --- TelephoneNode ---------------------------------------------------

    @Override
    default BlockPos telephonePos() {
        return applianceOwner().getBlockPos();
    }

    @Override
    default boolean isBusy() {
        return phoneCore().isUnavailable();
    }

    @Override
    default boolean isCallAnswered() {
        return phoneCore().callAnswered();
    }

    @Override
    default BlockPos callPartnerPos() {
        return phoneCore().callPartner();
    }

    @Override
    default String ownNumber() {
        return phoneCore().ownNumber();
    }

    @Override
    default int getAreaCode() {
        return phoneCore().areaCode();
    }

    @Override
    default String getOwnNumberText() {
        return phoneCore().ownNumberText();
    }

    @Override
    default String getLabel() {
        return phoneCore().label();
    }

    @Override
    default boolean isPowered() {
        return phoneCore().powered();
    }

    @Override
    default void setDialingTarget(String target) {
        phoneCore().setDialTarget(target);
    }

    @Override
    default String getDialingTarget() {
        return phoneCore().dialTarget();
    }

    @Override
    default boolean isAutoAnswer() {
        return phoneCore().autoAnswer();
    }

    @Override
    default void setAutoAnswer(boolean autoAnswer) {
        phoneCore().setAutoAnswer(autoAnswer);
    }

    @Override
    default void setLabel(String label) {
        phoneCore().setLabel(label);
    }

    @Override
    default boolean setOwnNumber(int areaCode, String number) {
        return phoneCore().setOwnNumber(areaCode, number);
    }

    @Override
    default void denyFeedback() {
        phoneCore().denyFeedback();
    }

    @Override
    default void receiveCall(BlockPos callerPos) {
        phoneCore().receiveCall(callerPos);
    }

    @Override
    default void notifyAnswered() {
        phoneCore().notifyAnswered();
    }

    @Override
    default void resetCallState() {
        phoneCore().resetCallState();
    }

    // --- ApplianceNode ---------------------------------------------------

    @Override
    default Set<GridConnection> applianceConnections() {
        return phoneCore().gridConnections();
    }

    @Override
    default int applianceConnectionLimit() {
        return 6;
    }

    /** Above the handset, clear of the phone's own body (the cell centre is inside the cradle). */
    @Override
    default AABB applianceNodeBox() {
        return IdenPhoneBlocks.NODE_BOX;
    }

    @Override
    default boolean appliancePowered() {
        return phoneCore().powered();
    }

    @Override
    default void setAppliancePowered(boolean powered) {
        phoneCore().setPowered(powered);
    }

    @Override
    default boolean applianceReceivingPower() {
        return phoneCore().receivingPower();
    }

    @Override
    default void setApplianceReceivingPower(boolean receiving) {
        phoneCore().setReceivingPower(receiving);
    }

    // --- MeteredAppliance: a phone draws loop current only while a call is up

    @Override
    default boolean cio$isConsuming() {
        return phoneCore().inCallOrRinging();
    }
}
