package com.cio.createinteroperable.mixin.powergrid;

import com.cio.createinteroperable.CreateInteroperable;
import com.cio.createinteroperable.compat.ColdSweatCompat;
import com.cio.createinteroperable.compat.ColdSweatWorldTemp;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes Power Grid's own thermal simulation (the overheat/fault mechanics
 * backing our Redstone Switch, Power Kits, and Rectifiers, all built on PG's
 * real {@code ThermalBehaviour}) cool toward Cold Sweat's real ambient
 * temperature instead of PG's own crude approximation, when Cold Sweat is
 * present — refreshed periodically, not just once at placement.
 * <p>
 * Confirmed by reading PG's real source: {@code ThermalBehaviour} caches one
 * "ambient" baseline per component on its very first tick
 * ({@code firstTick} branch: {@code cachedAmbientTemperature =
 * getAmbientTemperature(getWorld(), getPos()); firstTick = false; return;})
 * and every later tick dissipates heat toward exactly that number
 * ({@code dissipatedPower = dissipationFactor * (temperature -
 * cachedAmbientTemperature)}). {@code getAmbientTemperature(Level, BlockPos)}
 * itself is just:
 * <pre>{@code
 * return 13.65f * level.getBiome(pos).value().getBaseTemperature() + 7.1f;
 * }</pre>
 * — raw Minecraft biome temperature only, with no altitude, time-of-day, or
 * awareness of anything actually warming/cooling the room (our own Steam
 * Hearth/Aircon included). The HEAD injection below redirects PG's one
 * baseline read to Cold Sweat's own real calculation instead, so a component
 * sitting in a genuinely hot or cold — or radiator/venter-affected — spot
 * heats up and cools down accordingly. Falls through to PG's own real
 * formula, completely unmodified, whenever Cold Sweat isn't present.
 * <p>
 * <b>Reverted 2026-09-21 back to {@link ColdSweatWorldTemp#getWorldTemperatureC}
 * (the CACHED read) at a 100-tick interval — a real, reported performance
 * regression.</b> The previous revision switched to
 * {@link ColdSweatWorldTemp#getExactWorldTemperatureC} (Cold Sweat's
 * genuinely uncached {@code getTemperatureAt}, which itself does a real
 * {@code Temperature.apply} over a full modifier list plus its own
 * {@code getInsulationAt} chunk scan) AND tightened the refresh interval to
 * 20 ticks — for EVERY PG electrical component with a {@code ThermalBehaviour}
 * in the loaded world, not just ones near an active radiator/venter. That
 * combination is genuinely expensive at scale, and directly caused the
 * reported periodic stutter (worsened further by
 * {@code AirconClimateAmbientTempMixin}'s own chunk scan running on TOP of
 * Cold Sweat's, on every single call, from every caller — not just this
 * one). The original motivation (a report that PG's Thermometer looked
 * completely dissociated from live changes) turned out to be explained by a
 * genuinely different, unrelated bug — {@code ColdSweatWarmthEffect}/
 * {@code ColdSweatChillEffect} using the wrong Cold Sweat {@code TempModifier}
 * class (see those classes' own doc) — so bypassing Cold Sweat's cache here
 * was never actually load-bearing for that fix; it just cost real
 * performance for comparatively little correctness benefit. Cold Sweat's own
 * segment cache (up to 50s per 8-block segment) means repeat calls to the
 * same area are cheap lookups after the first, and
 * {@code AirconClimateAmbientTempMixin}'s own additive delta is ALWAYS
 * live regardless of whether the underlying cache was a hit or a miss (see
 * that mixin's own doc) — so the number this returns is still current for
 * anything CIO itself controls; only the raw biome/weather baseline can lag
 * up to Cold Sweat's own cache window, same as it does for every other
 * consumer of that real method in the game.
 * <p>
 * <b>Periodic refresh, and why it's built this way:</b> an earlier version of
 * this class re-derived the refresh independently — its own
 * {@code @Inject(at = "TAIL")} shadowing the INHERITED
 * {@code getWorld()}/{@code getPos()} (declared on Create's
 * {@code BlockEntityBehaviour}, not on {@code ThermalBehaviour} itself) plus
 * the private {@code cachedAmbientTemperature} field, then re-invoking
 * {@code getAmbientTemperature} and writing the field itself. A
 * {@code @Shadow} mismatch fails a mixin's ENTIRE application silently (this
 * project has hit that exact failure mode before, on the Vista TV
 * integration) — if that happened here, it would have taken the HEAD
 * redirect above down with it too, since both live in the same
 * {@code @Mixin} class, reverting components to PG's raw un-redirected
 * formula and explaining a real regression report ("accurate before this
 * change, wrong after"). Rebuilt below to remove that risk entirely: instead
 * of re-deriving the refresh, it just flips PG's OWN {@code firstTick} flag
 * back to {@code true} periodically and lets PG's real, already-correct
 * {@code tick()} body do the actual recompute — same one line PG already
 * runs on genuine placement, just re-triggered. That needs exactly one
 * {@code @Shadow}, of a {@code private boolean} declared directly on
 * {@code ThermalBehaviour} itself (the exact same low-risk category as this
 * project's other proven shadow, {@code BoilerDataMixin}'s
 * {@code attachedEngines}) — no inherited methods, no re-implemented logic.
 * <p>
 * <b>Targeted by string, not {@code ThermalBehaviour.class}</b> — this config
 * is optional/{@code required: false} (Power Grid is a soft dependency, see
 * {@code cio-context}), and unlike every other optional-mod mixin in this
 * codebase (see {@code mixin.letsdo}/{@code mixin.crayfish}/{@code mixin.vista}/
 * {@code mixin.crn}, all string-targeted), this one used a direct
 * {@code @Mixin(ThermalBehaviour.class)} class literal — confirmed the real
 * cause of a reported {@code NoClassDefFoundError: .../ThermalBehaviour} mod
 * load failure on a CEE-only (no Power Grid) install: a class-literal
 * annotation value forces the JVM to resolve that type when this mixin
 * class's own annotations are read, even though the config's own
 * {@code required: false} correctly no-ops the mixin APPLICATION itself when
 * Power Grid is absent. String-targeting avoids ever needing the type
 * resolved at all unless the target genuinely exists.
 */
@Mixin(targets = "org.patryk3211.powergrid.electricity.base.ThermalBehaviour")
public abstract class ThermalBehaviourMixin {
    @Inject(
            method = "getAmbientTemperature(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)F",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void createinteroperable$realAmbientTemperature(Level level, BlockPos pos,
                                                                     CallbackInfoReturnable<Float> cir) {
        if (!createinteroperable$logged) {
            createinteroperable$logged = true;
            // Unconditional, logged ONCE on the very first call regardless
            // of outcome — this line existing at all in the log is proof
            // the mixin actually applied. This is a one-time cost, not a
            // per-tick one — kept deliberately, unlike the removed
            // per-call diagnostic that used to sit here (see class doc:
            // that path's own uncached computation, not the log write
            // itself, was the real cost, but removing both together keeps
            // this method doing the minimum necessary).
            CreateInteroperable.LOGGER.info(
                    "[Create: Interoperable] PG ThermalBehaviour ambient-temperature mixin active (Cold Sweat present: {})",
                    ColdSweatCompat.present());
        }
        if (!ColdSweatCompat.present()) {
            return;
        }
        cir.setReturnValue((float) ColdSweatWorldTemp.getWorldTemperatureC(level, pos));
    }

    @Unique
    private static boolean createinteroperable$logged = false;

    /**
     * How often (in ticks) the ambient baseline is re-sampled. 100 ticks
     * (5s) — a deliberate middle ground after the 20-tick revision above
     * caused a real reported performance regression (every PG electrical
     * component in the loaded world re-triggering a real Cold Sweat query
     * that often), and the original 200-tick value predated this class using
     * a cached Cold Sweat read at all. PG's own dissipation toward this
     * baseline is already a slow multi-second convergence, so resampling
     * faster than this wouldn't make a component's OWN displayed temperature
     * track any quicker regardless.
     */
    @Unique
    private static final int createinteroperable$AMBIENT_REFRESH_INTERVAL_TICKS = 100;

    @Unique
    private int createinteroperable$ambientRefreshTicks = 0;

    @Shadow
    private boolean firstTick;

    /**
     * Runs before PG's own real tick() body every tick. Every 100 ticks, sets
     * {@code firstTick} back to true so the ORIGINAL method's own existing
     * branch — unmodified, using its own real getWorld()/getPos() calls —
     * recomputes cachedAmbientTemperature exactly as it does on genuine
     * placement. On every other tick this is a single cheap int compare.
     */
    @Inject(method = "tick()V", at = @At("HEAD"))
    private void createinteroperable$periodicAmbientRefresh(CallbackInfo ci) {
        if (++createinteroperable$ambientRefreshTicks >= createinteroperable$AMBIENT_REFRESH_INTERVAL_TICKS) {
            createinteroperable$ambientRefreshTicks = 0;
            firstTick = true;
        }
    }
}
