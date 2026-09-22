package com.cio.createinteroperable.mixin.coldsweat;

import com.cio.createinteroperable.AirconVenterBlockEntity;
import com.cio.createinteroperable.CreateInteroperable;
import com.cio.createinteroperable.RadiatorValveNorthBlockEntity;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.common.blockentity.HearthBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

/**
 * Supersedes the earlier separate {@code AirconVenterAmbientTempMixin} and
 * {@code RadiatorAmbientTempMixin} — those each independently OVERRODE
 * {@code WorldHelper.getRoughTemperatureAt}'s return value (the venter to a
 * flat computed target, the radiator to a floor). That's fine in isolation,
 * but breaks the moment a radiator and a venter can ever affect the same
 * position: Mixin chains multiple {@code @Inject(at = "RETURN", cancellable
 * = true)} handlers on the same method in a fixed but incidental order (this
 * project's own mixin config list order, not a deliberate design choice),
 * and whichever handler happens to run LAST would simply clobber whatever
 * the other one already wrote — the radiator would win 100% of the time in
 * one build and lose 100% of the time in another, purely by accident of
 * registration order, never a genuine "which is stronger" resolution.
 * <p>
 * Fixed by making all three effects ADDITIVE deltas (see
 * {@link AirconVenterBlockEntity#getCoolingOffsetMc}/
 * {@link RadiatorValveNorthBlockEntity#getWarmingOffsetMc}/
 * {@link #createinteroperable$hearthOffsetMc} — negative, positive, and
 * either respectively) summed here, ONCE, in a single injection: order no
 * longer matters because addition is commutative — a room with several
 * working sources genuinely nets out to whichever pulls harder, exactly like
 * real, independent climate sources sharing one space, rather than any one
 * unconditionally overriding the others. Multiple overlapping sources of the
 * same kind also simply stack (2 BLAZING radiators warm a shared room more
 * than 1) — a deliberate, honest simulation choice, not capped, since
 * nothing in the design brief asked for a cap and a real room really would
 * run hotter with two heaters in it.
 * <p>
 * <b>Cold Sweat's own real Hearth</b> is folded in too, as a deliberately
 * nerfed early-game alternative to our own radiator/venter — it reuses the
 * real Hearth's OWN room tracking ({@link HearthBlockEntity#getPathLookup()},
 * a public, real API, confirmed by reading its actual source — a genuine
 * incremental {@code SpreadPath} system, not a periodic BFS like ours) and
 * OWN live strength ({@link HearthBlockEntity#getHeatingLevel()}/
 * {@link HearthBlockEntity#getCoolingLevel()}, already 0 whenever that fuel
 * type isn't actively in use — confirmed by reading their real
 * implementation, {@code usingHotFuel ? insulationLevel : 0}, so no separate
 * "is it on" check is needed), scaled down to a flat
 * {@link #HEARTH_NERF_MAX_C} ceiling that never reaches even our own
 * weakest tier (the venter's Low, 6°C) — this only touches the ambient
 * READING (what a thermometer/PG's ThermalBehaviour sees), not the real
 * Hearth's own native particles or WARMTH/FRIGIDNESS application, which are
 * Cold Sweat's own business and untouched here.
 * <p>
 * <b>Patches BOTH real ambient-read entry points, not just one</b> —
 * {@code getRoughTemperatureAt} (cached per 8-block segment, up to 50s TTL;
 * this is what {@link com.cio.createinteroperable.compat.ColdSweatWorldTemp}
 * and, through it, PG's own {@code ThermalBehaviour} ambient baseline, use)
 * AND {@code getTemperatureAt} (uncached/exact; this is what the REAL Cold
 * Sweat Thermometer item, {@code Thermolith}, {@code Waterskin}, and the
 * item-frame label mixin all call directly). Originally only the first was
 * patched here, and the second was covered separately by a now-deleted
 * {@code WorldHelperMixin} that instead patched Cold Sweat's own internal
 * {@code getInsulationAt} (which both real methods call on their own, on a
 * cache miss, to build a {@code WarmthTempModifier}/{@code FrigidnessTempModifier}
 * baked directly into the CACHED value) — confirmed by a real in-game report
 * ("cold corner problem": rooms read hottest right next to the radiator and
 * fell off with distance, the opposite of the intended flat, room-wide
 * effect). Root cause, confirmed by reading {@code getRoughTemperatureAt}'s
 * real source: every call site — including this class's OWN periodic
 * self-sampling of {@code insideTempC} at the block's own position — always
 * added ITS full delta again via the RETURN injection below, REGARDLESS of
 * whether the segment's cached base already baked in an equal delta from
 * {@code getInsulationAt} moments earlier. A segment queried often (e.g. the
 * one containing the radiator itself, self-sampled every 5s) stayed freshly
 * cache-missed and so got double-counted; a segment queried rarely (a far
 * corner nobody stands in) kept an OLDER cached base — computed before the
 * room reached its current tier, or with zero contribution at all — and so
 * read correctly single-counted, i.e. COLDER relative to the double-counted
 * center. Not a distance formula anywhere in this codebase, but a genuine,
 * distance-correlated artifact of two independent injection points feeding
 * the same underlying Cold-Sweat cache. Fixed by making the RETURN-based
 * additive injection (always live, always current-tier, immune to Cold
 * Sweat's own segment-cache staleness by construction, since it re-adds the
 * correct delta on literally every call regardless of what the cached base
 * already contains) the ONLY integration point, covering both real methods
 * directly instead of leaning on the internal {@code getInsulationAt} call
 * either of them happens to make on a cache miss.
 * <p>
 * <b>Targeted by string, not {@code WorldHelper.class}</b> — this config is
 * optional/{@code required: false} (Cold Sweat is a soft dependency), and a
 * direct class-literal {@code @Mixin} value forces the JVM to resolve that
 * type just to read this mixin's own annotation, bypassing the config's
 * {@code required: false} soft-skip and throwing {@code NoClassDefFoundError}
 * on a Cold-Sweat-absent install — the exact same failure mode confirmed (and
 * fixed) on {@code ThermalBehaviourMixin}, see that class's own doc.
 */
@Mixin(targets = "com.momosoftworks.coldsweat.util.world.WorldHelper")
public abstract class AirconClimateAmbientTempMixin {
    /** How many chunks out (from the queried position) to scan for an active radiator/venter/Hearth — generous relative to any of their own room ranges (20 blocks). */
    private static final int CHUNK_SEARCH_RADIUS = 2;

    /** Never reaches even our own weakest tier (the venter's Low setting, 6°C) — deliberately a permanent early-game ceiling, not something upgrades should ever let catch up to our own blocks. */
    private static final double HEARTH_NERF_MAX_C = 4.0;

    @Inject(
            method = "getRoughTemperatureAt(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;I)D",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void createinteroperable$climateOffsetRough(Level level, BlockPos pos, int flags,
                                                                 CallbackInfoReturnable<Double> cir) {
        if (!createinteroperable$logged) {
            createinteroperable$logged = true;
            CreateInteroperable.LOGGER.info(
                    "[Create: Interoperable] Cold Sweat WorldHelper#getRoughTemperatureAt mixin active (radiator/venter/Hearth ambient deltas now compose additively)");
        }
        double totalOffset = createinteroperable$totalOffsetAt(level, pos);
        if (totalOffset != 0.0) {
            cir.setReturnValue(cir.getReturnValue() + totalOffset);
        }
    }

    /**
     * Same additive delta, applied to the UNCACHED/exact query — see this
     * class's own doc for why both real entry points need this independently
     * rather than leaning on {@code getInsulationAt} for one of them.
     */
    @Inject(
            method = "getTemperatureAt(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)D",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void createinteroperable$climateOffsetExact(Level level, BlockPos pos,
                                                                 CallbackInfoReturnable<Double> cir) {
        double totalOffset = createinteroperable$totalOffsetAt(level, pos);
        if (totalOffset != 0.0) {
            cir.setReturnValue(cir.getReturnValue() + totalOffset);
        }
    }

    /**
     * Per-(level, game tick) memoization — this method runs on the RETURN of
     * BOTH real Cold Sweat ambient-query methods, so it's reached from every
     * caller in the whole running game that ever asks "what's the
     * temperature here or near here", not just this project's own code: Cold
     * Sweat's own per-entity temperature ticking for every loaded mob and
     * player, real Cold Sweat blocks/items, and (via
     * {@code ThermalBehaviourMixin}) every PG electrical component. A real
     * reported performance regression traced to exactly this — the same
     * position (or nearby ones) gets asked about many times within a single
     * tick, and each ask redid a full nearby-chunk block-entity scan from
     * scratch. Caching by exact {@code BlockPos} for the CURRENT tick only
     * (cleared the instant the tick — or the level instance, guarding
     * against two dimensions sharing a tick count — changes) costs nothing
     * on correctness (never reused across ticks, so it's exactly as live as
     * before) but turns "N callers asking about the same spot this tick"
     * into one real scan instead of N.
     */
    @Unique
    private static Level createinteroperable$cachedLevel = null;
    @Unique
    private static long createinteroperable$cachedTick = Long.MIN_VALUE;
    @Unique
    private static final Map<BlockPos, Double> createinteroperable$cache = new HashMap<>();

    @Unique
    private static double createinteroperable$totalOffsetAt(Level level, BlockPos pos) {
        long gameTime = level.getGameTime();
        if (level != createinteroperable$cachedLevel || gameTime != createinteroperable$cachedTick) {
            createinteroperable$cachedLevel = level;
            createinteroperable$cachedTick = gameTime;
            createinteroperable$cache.clear();
        }
        Double cached = createinteroperable$cache.get(pos);
        if (cached != null) {
            return cached;
        }
        double totalOffset = createinteroperable$computeOffsetAt(level, pos);
        createinteroperable$cache.put(pos.immutable(), totalOffset);
        return totalOffset;
    }

    @Unique
    private static double createinteroperable$computeOffsetAt(Level level, BlockPos pos) {
        double totalOffset = 0.0;
        ChunkPos centerChunk = new ChunkPos(pos);
        for (int x = -CHUNK_SEARCH_RADIUS; x <= CHUNK_SEARCH_RADIUS; x++) {
            for (int z = -CHUNK_SEARCH_RADIUS; z <= CHUNK_SEARCH_RADIUS; z++) {
                int chunkX = centerChunk.x + x;
                int chunkZ = centerChunk.z + z;
                if (!level.hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                ChunkAccess chunk = level.getChunk(chunkX, chunkZ);
                for (BlockPos bePos : chunk.getBlockEntitiesPos()) {
                    BlockEntity be = chunk.getBlockEntity(bePos);
                    double delta;
                    if (be instanceof AirconVenterBlockEntity venter) {
                        delta = venter.getCoolingOffsetMc(pos);
                    } else if (be instanceof RadiatorValveNorthBlockEntity radiator) {
                        delta = radiator.getWarmingOffsetMc(pos);
                    } else if (be instanceof HearthBlockEntity hearth) {
                        delta = createinteroperable$hearthOffsetMc(hearth, pos);
                    } else {
                        continue;
                    }
                    totalOffset += delta;
                }
            }
        }
        return totalOffset;
    }

    /**
     * @return the (nerfed) ambient delta the real Hearth {@code hearth}
     * currently contributes at {@code pos}, in Cold Sweat's own "MC" unit —
     * 0 if {@code pos} isn't in its real path lookup, or it isn't actively
     * heating/cooling right now. Scaled by the Hearth's own live level
     * against its own configured max ({@code getMaxInsulationLevel()}, real
     * Cold Sweat config, respects upgrades/config changes) so a freshly-lit
     * Hearth contributes less than a fully warmed-up one, same "ramps up,
     * doesn't just snap on" feel the real Hearth already has for its own
     * native effects — just capped at {@link #HEARTH_NERF_MAX_C} instead of
     * whatever the real Hearth's own (much larger) insulation numbers reach.
     */
    @Unique
    private static double createinteroperable$hearthOffsetMc(HearthBlockEntity hearth, BlockPos pos) {
        if (!hearth.getPathLookup().containsKey(pos)) {
            return 0.0;
        }
        int max = hearth.getMaxInsulationLevel();
        if (max <= 0) {
            return 0.0;
        }
        int heating = hearth.getHeatingLevel();
        if (heating > 0) {
            double fraction = Mth.clamp(heating / (double) max, 0.0, 1.0);
            return Temperature.convert(fraction * HEARTH_NERF_MAX_C, Temperature.Units.C, Temperature.Units.MC, true);
        }
        int cooling = hearth.getCoolingLevel();
        if (cooling > 0) {
            double fraction = Mth.clamp(cooling / (double) max, 0.0, 1.0);
            return -Temperature.convert(fraction * HEARTH_NERF_MAX_C, Temperature.Units.C, Temperature.Units.MC, true);
        }
        return 0.0;
    }

    @Unique
    private static boolean createinteroperable$logged = false;
}
