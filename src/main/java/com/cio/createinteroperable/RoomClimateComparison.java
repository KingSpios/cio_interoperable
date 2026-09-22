package com.cio.createinteroperable;

/**
 * Shared decision logic for "is this flood-filled room's net climate
 * actually worth showing/feeling", used identically by
 * {@link RadiatorValveNorthBlockEntity} and {@link AirconVenterBlockEntity}
 * — both independently compare their own room's interior reading against a
 * reference point just past their own flood-fill boundary (see each class's
 * own {@code computeRoom}), then ask this class what that comparison means.
 * <p>
 * Deliberately a plain utility with no Cold-Sweat type anywhere in its
 * signature (both callers are always-loaded classes) — the two temperatures
 * it compares are just {@code double}s in °C, already resolved by the
 * caller via the isolated {@code ColdSweatWorldTemp}.
 * <p>
 * Two independent gates, NOT the same rule at two different numbers:
 * <ul>
 *     <li>{@link #particleFor} — cosmetic, wide/lenient bands
 *     ({@link #PARTICLE_HOT_FLOOR_C}/{@link #PARTICLE_COLD_CEILING_C}), just
 *     enough to stop a still-freezing "relatively warmer" room from showing
 *     falsely cozy hot particles.</li>
 *     <li>{@link #warmthLivable}/{@link #chillLivable} — a mechanical bonus
 *     (WARMTH / the venter's own chill effect), gated by the tighter
 *     "genuinely comfortable" band ({@link #STATUS_WARM_FLOOR_C}/
 *     {@link #STATUS_CHILL_CEILING_C}) — a radiator on WARM inside a
 *     blizzard cabin that's still only 5°C should never grant "you feel
 *     warm", even though 5°C is relatively warmer than the blizzard outside.</li>
 * </ul>
 * Both also require a minimum real gap ({@link #MIN_DELTA_C}) between inside
 * and outside, so a room that isn't meaningfully different from its
 * surroundings doesn't flicker between HOT/COLD/none on ambient noise.
 */
public final class RoomClimateComparison {
    /** Minimum real difference (°C) between inside and outside before either gate below even considers firing — a dead-band against flicker. */
    public static final double MIN_DELTA_C = 2.0;

    /** Below this, "relatively warmer than outside" still isn't warm enough to show hot particles at all. */
    public static final double PARTICLE_HOT_FLOOR_C = 0.0;
    /** Above this, "relatively cooler than outside" still isn't cool enough to show cold particles at all. */
    public static final double PARTICLE_COLD_CEILING_C = 35.0;

    /** Below this, a room reading "relatively warmer than outside" still isn't warm enough for WARMTH — the blizzard-cabin case. */
    public static final double STATUS_WARM_FLOOR_C = 17.0;
    /** Above this, a room reading "relatively cooler than outside" still isn't cool enough for the chill effect. */
    public static final double STATUS_CHILL_CEILING_C = 22.0;

    public enum RoomParticle {
        NONE, HOT, COLD
    }

    private RoomClimateComparison() {
    }

    /** @return which ambient air particle (if any) this room's interior should drift, purely for atmosphere. */
    public static RoomParticle particleFor(double insideC, double outsideC) {
        if (insideC - outsideC >= MIN_DELTA_C && insideC > PARTICLE_HOT_FLOOR_C) {
            return RoomParticle.HOT;
        }
        if (outsideC - insideC >= MIN_DELTA_C && insideC < PARTICLE_COLD_CEILING_C) {
            return RoomParticle.COLD;
        }
        return RoomParticle.NONE;
    }

    /** @return whether this room is genuinely warm enough, and genuinely warmer than outside, to grant WARMTH right now. */
    public static boolean warmthLivable(double insideC, double outsideC) {
        return insideC - outsideC >= MIN_DELTA_C && insideC > STATUS_WARM_FLOOR_C;
    }

    /** @return whether this room is genuinely cool enough, and genuinely cooler than outside, to grant the chill effect right now. */
    public static boolean chillLivable(double insideC, double outsideC) {
        return outsideC - insideC >= MIN_DELTA_C && insideC < STATUS_CHILL_CEILING_C;
    }
}
