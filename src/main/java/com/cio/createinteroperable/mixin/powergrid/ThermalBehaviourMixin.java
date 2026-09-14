package com.cio.createinteroperable.mixin.powergrid;

import com.cio.createinteroperable.CreateInteroperable;
import com.cio.createinteroperable.compat.ColdSweatCompat;
import com.cio.createinteroperable.compat.ColdSweatWorldTemp;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.patryk3211.powergrid.electricity.base.ThermalBehaviour;
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
 * real {@link ThermalBehaviour}) cool toward Cold Sweat's real ambient
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
 * Hearth included). Cold Sweat's own {@code WorldHelper.getRoughTemperatureAt}
 * (already wrapped, with zero Cold-Sweat types in its own signature, by
 * {@link ColdSweatWorldTemp#getWorldTemperatureC}) is the real, fuller
 * calculation this project already trusts everywhere else — the HEAD
 * injection below redirects PG's one baseline read to that instead, so a
 * component sitting in a genuinely hot or cold — or Hearth-warmed — spot
 * heats up and cools down accordingly. Falls through to PG's own real
 * formula, completely unmodified, whenever Cold Sweat isn't present.
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
 */
@Mixin(ThermalBehaviour.class)
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
            // Unconditional, logged on the very FIRST call regardless of
            // outcome — this line existing at all in the log is proof the
            // mixin actually applied. Its absence, after this mixin's own
            // recent silent-failure history (see class doc), is the single
            // fastest way to tell "not applying" apart from "applying but
            // Cold Sweat legitimately returned a biome-dominated number."
            CreateInteroperable.LOGGER.info(
                    "[Create: Interoperable] PG ThermalBehaviour ambient-temperature mixin active (Cold Sweat present: {})",
                    ColdSweatCompat.present());
        }
        if (ColdSweatCompat.present()) {
            cir.setReturnValue((float) ColdSweatWorldTemp.getWorldTemperatureC(level, pos));
        }
    }

    @Unique
    private static boolean createinteroperable$logged = false;

    /** How often (in ticks) the ambient baseline is re-sampled. 200 ticks = 10s, matching Cold Sweat's own "sensitive" getRoughTemperatureAt cache window, so this never asks fresher than Cold Sweat can actually answer. */
    @Unique
    private static final int createinteroperable$AMBIENT_REFRESH_INTERVAL_TICKS = 200;

    @Unique
    private int createinteroperable$ambientRefreshTicks = 0;

    @Shadow
    private boolean firstTick;

    /**
     * Runs before PG's own real tick() body every tick. Every 200 ticks, sets
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
