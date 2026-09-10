package com.cio.createinteroperable.letsdo;

/**
 * Duck interface implemented (by {@code AlpineWhispersBathtubEntityMixin}) on
 * Alpine Whispers' {@code BathtubBlockEntity}, so the block-side mixin can drive
 * its private fill counter without a compile-time dependency on Alpine Whispers.
 */
public interface LetsDoBathtub {

    /**
     * Advance the tub's fill by {@code progressTicks} of Alpine Whispers' own
     * {@code 0..total} counter (total is 900), capped at full. Marks the block
     * {@code full} and stops when it reaches the top. Does not set the
     * {@code filling} flag — CIO drives the fill from right-clicks, so Alpine
     * Whispers' timed auto-advance stays dormant.
     */
    void cio$feed(int progressTicks);

    /** Current fill fraction, {@code 0f}..{@code 1f}. */
    float cio$ratio();
}
