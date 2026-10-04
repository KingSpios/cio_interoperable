package com.cio.createinteroperable;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * What each telephone nub is called when hovered with a wire: Power Grid
 * terminal names and Electro Energetics node labels, shared by CIO's
 * Interoperable, CPG and CEE telephones and Iden's Decor's, so one nub reads
 * the same whichever grid it's wired with. Each name says what the nub does.
 */
public final class TelephoneLabels {

    private TelephoneLabels() {
    }

    /** Powers the phone: ~12 V across positive/negative (it overheats above 20 V). */
    public static MutableComponent positive() {
        return Component.literal("Telephone Positive (12 V in)");
    }

    public static MutableComponent negative() {
        return Component.literal("Telephone Negative (12 V in)");
    }

    /** The call line: phones whose taps are wired together can call each other. */
    public static MutableComponent tap() {
        return Component.literal("Telephone Tap (call line)");
    }

    /** Switch to the negative rail, closed on the answering phone while a call is up. */
    public static MutableComponent callBreaker() {
        return Component.literal("Call Breaker (closes while answered)");
    }

    /** Pass-through of the positive rail, live on the answering phone while a call is up. */
    public static MutableComponent callFeedPositive() {
        return Component.literal("Call Feed + (live while answered)");
    }

    public static MutableComponent callFeedNegative() {
        return Component.literal("Call Feed − (live while answered)");
    }

    /**
     * CEE node id &rarr; label, per the ids every CIO telephone uses (0/1 power,
     * 2 tap, 3/4 call feed, 5 call breaker &mdash; see {@code TelephoneDevice}).
     */
    public static MutableComponent ceeNode(int id) {
        return switch (id) {
            case 0 -> positive();
            case 1 -> negative();
            case 2 -> tap();
            case 3 -> callFeedPositive();
            case 4 -> callFeedNegative();
            case 5 -> callBreaker();
            default -> Component.literal("Telephone");
        };
    }
}
