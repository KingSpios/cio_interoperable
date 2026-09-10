package com.cio.createinteroperable.deb;

/**
 * Marks a Crayfish-networked appliance whose metered draw depends on live
 * activity, not merely on being linked or switched on.
 *
 * <p>{@link ApplianceLoads#isDrawingPower} checks this before the default
 * {@code IHomeControlDevice} switch test: a device implementing this only bills
 * its rated load while {@link #cio$isConsuming()} is true. It is still energised
 * whenever a live rail reaches it, so it can start the moment it has something
 * to do.
 */
public interface MeteredAppliance {

    /** True only while this appliance is actually consuming its rated load right now. */
    boolean cio$isConsuming();
}
