package com.cio.createinteroperable.deb;

/**
 * Ratings for the Redstone Switch, calibrated against {@link DebTier#TIER_2}
 * (the Domestic Power Kit) — the same 120&nbsp;V house circuit it is expected
 * to gate, so its throughput caps mirror that kit's medium-voltage pool and
 * its overheat point is identical.
 *
 * <p>The thermal ceiling is deliberately <em>not</em> copied from the kit: a
 * rectifier dumps step-down conversion loss on top of I&sup2;R, while a bare
 * switch only has contact I&sup2;R, so a smaller ceiling is what keeps
 * "rated load runs warm, sustained overload cooks it" true — the same intent
 * {@code TIER_2}'s own 3.2&nbsp;kW figure was picked for.</p>
 *
 * <p>Protocol-neutral: referenced from both the Power Grid and the Electro
 * Energetics switch families, so it holds no PG/CEE import.</p>
 */
final class RedstoneSwitchStats {
    private RedstoneSwitchStats() {}

    /** Per-pole contact resistance (&Omega;) while closed — small, but the sole source of the switch's own heat. */
    static final double CONTACT_RESISTANCE = 0.05;
    /** Per-pole resistance (&Omega;) while open — an effectively broken circuit (matches the DEB's own {@code R_OPEN}). */
    static final double OPEN_RESISTANCE = 1.0e7;

    /** Throughput (W) the switch carries indefinitely &mdash; {@code TIER_2}'s 120&nbsp;V pool soft cap. */
    static final double RATED_WATTS = 4_800.0;
    /** Gross overload (W): instant detonation, same ratio to the rating as {@code TIER_2}'s own hard cap. */
    static final double HARD_WATTS = 7_500.0;

    /** Contacts weld / arc over: overheat point (&deg;C). Identical to {@code TIER_2}. */
    static final double OVERHEAT_CELSIUS = 300.0;
    /**
     * Steady dissipation (W) whose equilibrium temperature is the overheat
     * point. At the rated 4.8&nbsp;kW / 120&nbsp;V &asymp; 40&nbsp;A per pole
     * the two contacts shed 2&nbsp;&times;&nbsp;40&sup2;&nbsp;&times;&nbsp;0.05
     * &asymp; 160&nbsp;W and settle well under; from roughly 1.3&times; rated
     * up it climbs to the limit.
     */
    static final double THERMAL_MAX_POWER_WATTS = 220.0;

    /** Ticks a throughput past {@link #RATED_WATTS} is tolerated before detonation. Matches the DEB fault fuse. */
    static final int FAULT_GRACE_TICKS = 200;
    /** Load fraction (throughput / rating) at which the switch starts venting a haze. */
    static final float HAZE_FRACTION = 0.85f;

    /** Guard rail on the computed throughput so a solver blip can't fake an instant detonation. */
    static final double MAX_PLAUSIBLE_WATTS = 1_000_000.0;
}
