package com.cio.createinteroperable.deb;

/**
 * Marks an appliance whose rated load isn't a fixed number but scales with its
 * own current size or state &mdash; e.g. a multi-tile screen wall that draws
 * more the bigger it's grown.
 *
 * <p>{@link ApplianceLoads#effectiveWatts} multiplies the appliance's base
 * {@link ApplianceLoads.Spec#watts()} by {@link #cio$loadScale()} before
 * billing it. An appliance that doesn't implement this always bills its flat
 * rated watts, unchanged.</p>
 */
public interface ScalableAppliance {

    /** Multiplier applied to the appliance's base rated watts. Expected &gt;= 1. */
    double cio$loadScale();
}
