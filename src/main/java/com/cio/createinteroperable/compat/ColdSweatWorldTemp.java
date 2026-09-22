package com.cio.createinteroperable.compat;

import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.util.world.WorldHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * The ONLY reference in this codebase to Cold Sweat's own real ambient-world-
 * temperature calculation ({@link WorldHelper#getRoughTemperatureAt}) — kept
 * isolated in its own class, with no Cold-Sweat type in this class's own
 * public method signature, so that always-loaded classes (BrassHeaterBlockEntity,
 * RadiatorValveNorthBlockEntity) can safely call {@link #getWorldTemperatureC}
 * without ever forcing the JVM to resolve/link a Cold-Sweat-only type — the
 * same class-loading isolation this project already relies on for
 * {@link ColdSweatIntegration}/{@link ColdSweatHeatTemp} (see
 * {@link ColdSweatCompat}'s doc). Callers MUST check
 * {@link ColdSweatCompat#present()} first; this class is only ever actually
 * loaded (by the JVM resolving the first real call into it) once that's true.
 * <p>
 * Replaces an earlier, wrong approach that approximated ambient °C straight
 * from the raw Minecraft biome temperature attribute — that's exactly what
 * this method deliberately avoids: {@code getRoughTemperatureAt} is Cold
 * Sweat's own real, cached-per-8-block-segment computation, which accounts
 * for biome, altitude, dimension, time of day, and nearby hearth/campfire
 * insulation — not just the biome's raw number.
 * <p>
 * <b>Bug fixed here:</b> this used to call the bare 2-arg
 * {@code getRoughTemperatureAt(level, pos)} overload, which defaults its
 * {@code flags} to 0 — Cold Sweat's real source shows that's the SLOW,
 * non-"sensitive" cache path ({@code interval = sensitive ? 200 : 1000}
 * ticks, i.e. up to 50 real seconds stale), even though this class's own doc
 * comment already (wrongly) claimed "~10s". Every reader of a position-based
 * query built on this — a PG Thermometer, PG's own {@code ThermalBehaviour}
 * ambient baseline — could sit on a reading up to 50 seconds out of date
 * after a real change (a Steam Hearth igniting, day turning to night),
 * which is exactly the kind of "doesn't always match" a live, per-tick
 * source like Cold Sweat's own HUD world-temp gauge (built from the
 * player's own continuously-ticking capability, not this cache at all)
 * would expose. Now passes {@code flags = 1} (the {@code sensitive} bit) for
 * the real 200-tick/10s window instead — the fastest real Cold Sweat ever
 * refreshes a position query, whether or not asked for it.
 */
public final class ColdSweatWorldTemp {
    /** Cold Sweat's own {@code sensitive} flag bit — see class doc. */
    private static final int SENSITIVE = 1;

    private ColdSweatWorldTemp() {
    }

    /** @return Cold Sweat's own real ambient world temperature at {@code pos}, in °C. */
    public static double getWorldTemperatureC(Level level, BlockPos pos) {
        double mcUnits = WorldHelper.getRoughTemperatureAt(level, pos, SENSITIVE);
        return Temperature.convert(mcUnits, Temperature.Units.MC, Temperature.Units.C, true);
    }

    /**
     * The UNCACHED counterpart to {@link #getWorldTemperatureC} — wraps
     * {@code WorldHelper.getTemperatureAt}, which real Cold Sweat itself uses
     * for its own exact-position consumers (the real Thermometer ITEM,
     * Thermolith, Waterskin — see that method's own real source) instead of
     * {@code getRoughTemperatureAt}'s per-8-block-segment cache (up to a real
     * 50-second staleness window even on its "sensitive" setting — see this
     * class's own doc). {@code AirconClimateAmbientTempMixin} patches BOTH
     * real methods identically, so this still carries every nearby
     * radiator/venter/Hearth's own additive delta — the only difference from
     * {@link #getWorldTemperatureC} is that the BASELINE portion (biome,
     * weather, altitude) is never stale here, which is exactly what
     * {@code ThermalBehaviourMixin} switched to (2026-09-20) after a real
     * report that Power Grid's own Thermometer read as "dissociated" from
     * live flood-fill changes — Cold Sweat's own segment cache was the most
     * likely remaining explanation once every other hop in the chain checked
     * out correct by direct code inspection. Costs a real (if fairly cheap)
     * {@code Temperature.apply} call every invocation with no memoization of
     * its own — acceptable for how infrequently this is actually called (see
     * {@code ThermalBehaviourMixin}'s own throttling), not something to call
     * from a genuinely hot per-tick-per-entity path.
     */
    public static double getExactWorldTemperatureC(Level level, BlockPos pos) {
        double mcUnits = WorldHelper.getTemperatureAt(level, pos);
        return Temperature.convert(mcUnits, Temperature.Units.MC, Temperature.Units.C, true);
    }

    /**
     * The inverse of {@link #getWorldTemperatureC} — converts a plain °C
     * value into Cold Sweat's own internal "MC" temperature unit, e.g. for
     * {@code AirconVenterBlockEntity}/{@code RadiatorValveNorthBlockEntity}
     * to compute the additive delta {@code AirconClimateAmbientTempMixin}
     * sums into {@code getRoughTemperatureAt}'s real return value. Kept in
     * this same isolated class (not called from the mixin's own
     * always-loaded package directly) for the identical class-loading reason
     * documented above — {@link com.momosoftworks.coldsweat.api.util.Temperature}
     * must never be referenced from a class that loads regardless of whether
     * Cold Sweat is installed.
     * <p>
     * Safe to call on a plain DELTA (not just an absolute temperature),
     * despite the name: confirmed by reading Cold Sweat's real
     * {@code Temperature.convert} source — the C↔MC conversion is a pure
     * linear scale ({@code MC = C / 25}) with no additive offset at all
     * (unlike F, which adds/subtracts 32°); the {@code absolute} parameter
     * this passes {@code true} for only ever matters for that F offset, so
     * converting a delta and converting an absolute value produce identically
     * scaled results here.
     */
    public static double celsiusToMc(double celsiusC) {
        return Temperature.convert(celsiusC, Temperature.Units.C, Temperature.Units.MC, true);
    }
}
