package com.cio.createinteroperable.deb;

/**
 * Static per-model parameters for a Power Kit. One instance per tier.
 *
 * <p>Every Kit takes <b>one grid feed</b> and steps it down internally to a
 * regulated 12&nbsp;V Power Feed. Tiers 1&ndash;2 take a fixed 120&nbsp;V.
 * Tiers 3&ndash;4 are <b>substations</b>: a Create slider taps the intake for
 * one of several nominal voltages, and they also carry a high-voltage
 * pass-through and a regulated 120&nbsp;V feed. The two {@link Pool}s are load
 * buckets, not separate ports.</p>
 *
 * <p><b>Every</b> tier runs a real Power Grid {@code ThermalBehaviour}
 * ({@link #thermalOverheatCelsius}, {@link #thermalMaxPowerWatts}): the Kit's
 * own I&sup2;R + step-down conversion losses heat a core that a Create encased
 * fan can cool (PG's {@code AirCurrent} mixin cools any {@code ThermalBehaviour}
 * in a fan's path), and past the overheat point the board detonates.</p>
 *
 * <p>Capacity model, per pool: <b>soft cap</b> = every appliance on it stays
 * powered at or below; above it the pool faults and sheds, arming the
 * {@link DebRectifierBlockEntity#FAULT_GRACE_TICKS} fuse. <b>hard cap</b> =
 * grossly over &rarr; the board explodes. On a substation the MV soft/hard
 * caps and the intake resistance come from the selected {@link Substation.Mode},
 * not the record's own {@code mv*} fields (those stay as a sane fallback).</p>
 */
public record DebTier(
        double lvSoftCapWatts, double lvHardCapWatts, double lvInternalResistance,
        double mvSoftCapWatts, double mvHardCapWatts, double mvInternalResistance,
        int rangeBlocks, double brownoutFraction,
        /** True if the model carries the physical 120&nbsp;V Power Feed pass-through terminal pair. */
        boolean hasMediumVoltageFeed,
        /** {@code ThermalBehaviour} overheat point (&deg;C) &mdash; past it the board detonates unless fan-cooled. */
        double thermalOverheatCelsius,
        /** Steady dissipation (W) whose equilibrium temperature is the overheat point; feed more and the core climbs. */
        double thermalMaxPowerWatts,
        /** Substation extras (multi-tap intake, HV feed). {@code null} for tiers 1&ndash;2. */
        Substation substation) {

    /**
     * The tier-3/4 additions: a set of selectable intake taps plus one shared
     * step-down resistance.
     */
    public record Substation(
            Mode[] modes,                  // slider index -> tap; index 0 is the default
            double mvStepDownResistance) { // series R on the regulated intake->120 V feed

        /** One selectable intake tap. */
        public record Mode(
                String label, double volts,
                double softCapWatts, double hardCapWatts,
                double primaryInternalResistance,
                boolean hvFeedLive) {
        }
    }

    /**
     * "Tier 2 &mdash; Power Kit" &mdash; a whole house. 12&nbsp;V side runs the
     * lights + electronics + a run of CPG LV fixtures off the Power Feed;
     * 120&nbsp;V side runs every kitchen appliance at once (&asymp;4.8&nbsp;kW)
     * before shedding.
     */
    public static final DebTier TIER_2 = new DebTier(
            90.0, 160.0, 0.15,
            4_800.0, 7_500.0, 1.5,
            40, 0.80, true,
            300.0, 3_200.0, // overheat at 300 C; ~3.2 kW steady reaches it (a full house at rated MV sits just under)
            null);

    /**
     * "Tier 1 &mdash; Improvised" &mdash; a single room. 120&nbsp;V intake
     * stepped down to a regulated 12&nbsp;V feed; runs 120&nbsp;V appliances off
     * the Crayfish link on a tight budget. No physical 120&nbsp;V Power Feed
     * pass-through. Short range, lossy step-down, high primary loss.
     */
    public static final DebTier TIER_1 = new DebTier(
            40.0, 70.0, 0.40,
            600.0, 1_500.0, 2.0,
            16, 0.80, false,
            200.0, 120.0, // cheap parts: overheat at 200 C; ~120 W steady reaches it (rated load ~65 W, so a fan is real headroom, overload cooks it)
            null);

    /**
     * "Tier 3 &mdash; Commercial" &mdash; 8 heavy CPG appliances on the Power
     * Feeds + a full Crayfish house. Two intake taps: <b>240&nbsp;V</b>
     * (default, 20&nbsp;kW soft cap, HV feed live) and 120&nbsp;V (12&nbsp;kW,
     * HV feed off). Real thermal model on the temperature viewer.
     */
    public static final DebTier TIER_3 = new DebTier(
            300.0, 500.0, 0.06,        // 12 V pool caps; 12 V step-down R
            20_000.0, 32_000.0, 0.50,  // fallback MV caps + intake R (= default mode)
            64, 0.80, true,
            350.0, 4_000.0,            // overheat at 350 C; ~4 kW steady dissipation reaches it
            new Substation(
                    new Substation.Mode[] {
                            new Substation.Mode("240 V", 240.0, 20_000.0, 32_000.0, 0.50, true),
                            new Substation.Mode("120 V", 120.0, 12_000.0, 18_000.0, 0.13, false),
                    },
                    0.03));   // 240->120 regulated feed series R

    /**
     * "Tier 4 &mdash; Industrial" &mdash; a substation proper. Three intake
     * taps: 120&nbsp;V (16&nbsp;kW fallback, HV feed off), 240&nbsp;V
     * (28&nbsp;kW), and <b>1&nbsp;kV</b> (120&nbsp;kW &mdash; a factory floor at
     * ~120&nbsp;A, the same current band as the lower taps). Internal
     * resistance is picked so each tap loses ~15&nbsp;% to I&sup2;R at its own
     * soft cap; the 1&nbsp;kV tap runs far cooler per kilowatt, so its
     * headroom is real. Bigger thermal budget to match.
     */
    public static final DebTier TIER_4 = new DebTier(
            400.0, 700.0, 0.05,        // 12 V pool caps; 12 V step-down R
            28_000.0, 44_000.0, 0.35,  // fallback MV caps + intake R (= 240 V tap)
            96, 0.80, true,
            420.0, 20_000.0,           // overheat at 420 C; ~20 kW steady dissipation reaches it
            new Substation(
                    new Substation.Mode[] {
                            new Substation.Mode("120 V",  120.0,  16_000.0,  24_000.0, 0.13, false),
                            new Substation.Mode("240 V",  240.0,  28_000.0,  44_000.0, 0.35, true),
                            new Substation.Mode("1 kV",  1000.0, 120_000.0, 190_000.0, 1.25, true),
                    },
                    0.03));   // intake->120 regulated feed series R

    /** True if this tier energises the 120&nbsp;V appliance pool at all. */
    public boolean servesMediumVoltage() {
        return mvSoftCapWatts > 0;
    }

    /** True if this tier is a substation (multi-tap intake, HV feed, thermal, mode slider). */
    public boolean hasSubstation() {
        return substation != null;
    }

    public double softCapWatts(Pool pool) {
        return pool == Pool.LV ? lvSoftCapWatts : mvSoftCapWatts;
    }

    public double hardCapWatts(Pool pool) {
        return pool == Pool.LV ? lvHardCapWatts : mvHardCapWatts;
    }

    /** Series loss (&Omega;) on the intake &mdash; transformer + rectifier. */
    public double primaryInternalResistance() {
        return mvInternalResistance;
    }

    /** Series resistance (&Omega;) on the regulated 12&nbsp;V Power Feed output. */
    public double stepDownResistance() {
        return lvInternalResistance;
    }

    /** Bus voltage below this (V) means the pool browns out and does not energise its appliances this tick. */
    public double brownoutVolts(Pool pool) {
        return pool.nominalVoltage * brownoutFraction;
    }
}
