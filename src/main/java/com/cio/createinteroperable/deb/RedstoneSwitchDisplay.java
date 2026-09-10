package com.cio.createinteroperable.deb;

/**
 * The client-readable slice of a Redstone Switch's state that the shared
 * {@link RedstoneSwitchRenderer} needs. Implemented by both
 * {@link RedstoneSwitchBlockEntity} (Power Grid) and
 * {@link CeeRedstoneSwitchBlockEntity} (Electro Energetics), which is what lets
 * one generic renderer serve both.
 */
public interface RedstoneSwitchDisplay {
    /** 0 = fully OFF (contact up), 1 = fully ON (contact dropped toward the bottom viewer). Eased, client-smoothed. */
    float switchSlide();

    float switchSlidePrev();

    /** Power passing through the switch this tick (W). */
    float switchThroughputWatts();

    /** Circuit voltage the switch is handling (V). */
    float switchAcrossVolts();

    /** True while the throughput is past its rating (contacts arcing, overload fuse burning). */
    boolean switchFaulted();

    /** True while there is enough voltage on either side to bother drawing the readouts. */
    boolean switchHasReadout();
}
