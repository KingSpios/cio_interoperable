package com.cio.createinteroperable.telephone;

import com.cio.createinteroperable.Lang;
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
        return Lang.component("telephone.telephone_positive_12_v_in");
    }

    public static MutableComponent negative() {
        return Lang.component("telephone.telephone_negative_12_v_in");
    }

    /** The call line: phones whose taps are wired together can call each other. */
    public static MutableComponent tap() {
        return Lang.component("telephone.telephone_tap_call_line");
    }

    /** Switch to the negative rail, closed on the answering phone while a call is up. */
    public static MutableComponent callBreaker() {
        return Lang.component("telephone.call_breaker_closes_while_answered");
    }

    /** Pass-through of the positive rail, live on the answering phone while a call is up. */
    public static MutableComponent callFeedPositive() {
        return Lang.component("telephone.call_feed_live_while_answered");
    }

    public static MutableComponent callFeedNegative() {
        return Lang.component("telephone.call_feed_live_while_answered_2");
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
            default -> Lang.component("telephone.telephone");
        };
    }
}
