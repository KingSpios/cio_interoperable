package com.cio.createinteroperable.deb;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * What each Power Kit nub is called when hovered with a wire &mdash; the PG
 * terminal names and the CEE node labels, so one nub reads the same on either
 * grid. Bottom nubs are the "Power Kit Intake", top nubs the outlets
 * ("+12V Outlet" / "−12V Outlet", …).
 *
 * <p>PG hands a terminal's name out with only the blockstate (no position, so
 * no BlockEntity), so on the multi-tap tiers the PG intake label carries no
 * voltage. CEE's {@code getNodeLabel} does get the position, so the CEE intake
 * label follows the selected tap. Carries no PG or CEE import, so both sides
 * can share it.</p>
 */
final class PowerKitLabels {

    private PowerKitLabels() {
    }

    /** Intake nub; {@code volts} is null when the tap can't be known (PG, multi-tap tiers). */
    static MutableComponent intake(String volts, boolean positive) {
        String sign = positive ? "+" : "−";
        return Component.literal(volts == null
                ? "Power Kit Intake " + sign
                : "Power Kit Intake " + volts + " " + sign);
    }

    /** Outlet nub, e.g. {@code "+12V Outlet"}. */
    static MutableComponent outlet(String volts, boolean positive) {
        return Component.literal((positive ? "+" : "−") + volts + " Outlet");
    }

    /** "120V", "240V", "1kV" &mdash; the intake tap as a nub label reads it. */
    static String volts(double v) {
        return v >= 1000.0
                ? (v % 1000.0 == 0 ? String.valueOf((int) (v / 1000.0)) : String.valueOf(v / 1000.0)) + "kV"
                : (int) Math.round(v) + "V";
    }

    /**
     * CEE node id &rarr; label. Ids 0/1 are always the intake; from id 2 on,
     * each +/- pair is the next entry of {@code outlets} (e.g. {@code {"120V",
     * "12V"}} for tier 2), matching {@code DebRectifierBlockEntity#buildCircuit}'s
     * terminal order.
     */
    static MutableComponent ceeNode(int id, String intakeVolts, String[] outlets) {
        boolean positive = id % 2 == 0;
        if (id < 2) {
            return intake(intakeVolts, positive);
        }
        int pair = (id - 2) / 2;
        return pair < outlets.length
                ? outlet(outlets[pair], positive)
                : Component.literal("Power Kit");
    }
}
