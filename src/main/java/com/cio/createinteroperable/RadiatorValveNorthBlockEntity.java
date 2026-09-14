package com.cio.createinteroperable;

import com.cio.createinteroperable.compat.ColdSweatCompat;
import com.cio.createinteroperable.compat.ColdSweatWarmthEffect;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The steam-consuming, heat-producing half of a Multi Radiator — see
 * {@link RadiatorValveNorthBlock} for the shared FACING/shaft/capability
 * reasoning, and {@link RadiatorValveSouthBlockEntity} for the water-output
 * partner this hands condensate to every tick once assembled.
 * <p>
 * Deliberately reuses the exact valve/tank pattern already proven out on
 * {@link BrassHeaterBlockEntity}/{@link SteamOutletBlockEntity} — a
 * shaft-driven {@link #pointer} chasing 0..1, gating how much of the
 * buffered steam is actually burned per tick — rather than inventing a new
 * mechanic. What's genuinely new here:
 * <ul>
 *     <li>Does nothing at all until assembled (see {@link RadiatorAssembly})
 *     — a lone North block just sits inert.</li>
 *     <li>Whatever steam it actually burns each tick is forwarded 1:1 (mB
 *     steam -> mB water — a placeholder ratio, see TODO below) to the cached
 *     South partner via {@link RadiatorValveSouthBlockEntity#receiveCondensate},
 *     instead of just disappearing like Brass Heater's own consumption does.</li>
 * </ul>
 * <p>
 * TODO(design, scaffold-only): capacity (tank size / max consumption) is
 * still a flat placeholder, not yet scaled by {@code middlePositions.size()}
 * — more segments presumably should mean more throughput, not implemented.
 * HEAT_LEVEL tiering (pipe texture AND the actual Cold Sweat effect on the
 * player — see {@code data/createinteroperable/block/block_temp/multi_radiator_*.json},
 * same zero-dependency datapack mechanism as Brass Heater's own) reuses
 * Brass Heater's exact thresholds via {@link BrassHeaterBlockEntity#quantizeHeatTier}.
 */
public class RadiatorValveNorthBlockEntity extends KineticBlockEntity {
    /**
     * A bare 2x1 run (North+South, no middles) tops out at 24 mB/t at a fully
     * open valve. Deliberately scaled to real Create pipe throughput rather
     * than an arbitrary round number: a Mechanical Pump's actual mB/t is
     * {@code max(1, pumpRPM / 2)} (confirmed by reading
     * {@code FluidNetwork#tick}'s real {@code transferSpeed} calculation —
     * uncapped, but linear in RPM), so a typical early-game pump at 16 RPM
     * only ever delivers 8 mB/t no matter how much steam the boiler has
     * sitting available. The original 256/128 numbers assumed pipe
     * throughput that only shows up around 512+ RPM — this now lands
     * 16 RPM at a comfortable WARM (8/24 ≈ 33%), ~32 RPM at HOT, and
     * ~64 RPM (a modest early windmill/water wheel setup, not lategame
     * gearing) at BLAZING on a bare 2x1 run.
     */
    private static final int BASE_MAX_STEAM_CONSUMPTION_PER_TICK = 24;
    /** Each middle segment adds half the base amount, same 2:1 ratio as before, just at the new scale. */
    private static final int MAX_STEAM_CONSUMPTION_PER_MIDDLE = 12;
    private static final int TANK_CAPACITY_MB = 2000;
    /** How fast the displayed heat tier settles toward the real rate each tick — see #tick's use of it for why this exists at all. 0.05 ≈ mostly converged within a second. */
    private static final float HEAT_FRACTION_SMOOTHING = 0.05f;
    /** The radiator turns steam into water at a quarter the rate — 4 mB of steam makes 1 mB of water. */
    private static final float STEAM_TO_WATER_RATIO = 0.25f;

    private final FluidTank steamTank = new FluidTank(TANK_CAPACITY_MB) {
        @Override
        public boolean isFluidValid(FluidStack stack) {
            return stack.getFluid() == CIOFluids.STEAM_STILL.get();
        }
    };

    /** 0 = closed, 1 = open — see BrassHeaterBlockEntity#pointer for the exact chase mechanic. */
    private final LerpedFloat pointer = LerpedFloat.linear()
            .startWithValue(0)
            .chase(0, 0, Chaser.LINEAR);

    private float heatFraction = 0;

    @Nullable
    private BlockPos southPos;
    private List<BlockPos> middlePositions = List.of();

    /** Cached "trapped room" set — see {@link #tickHearthEffect}. Empty until first computed. */
    private Set<BlockPos> roomCache = Set.of();
    /**
     * Deliberately NOT {@code Long.MIN_VALUE} — {@code gameTime -
     * Long.MIN_VALUE} overflows a signed 64-bit long (wraps to a huge
     * NEGATIVE number instead of a huge positive one), which made the
     * "recompute due" check in {@link #tickHearthEffect} false on every
     * server start, forever: {@code computeRoom()} never ran even once, and
     * {@code roomCache} silently stayed empty for the block's entire
     * lifetime. The only thing that ever actually granted warmth was the
     * fallback 3-block radius around the Inlet/Outlet endpoints — which
     * looked exactly like "warmth only within a couple blocks," the bug this
     * fixes. {@code -ROOM_RECOMPUTE_INTERVAL_TICKS} makes the very first
     * check ({@code gameTime - (-100) >= 100}) true for any non-negative
     * gameTime, with no overflow risk since both operands stay small.
     */
    private long roomCacheComputedAtTick = -ROOM_RECOMPUTE_INTERVAL_TICKS;

    public RadiatorValveNorthBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Called once by {@link RadiatorAssembly#tryAssemble} on a successful assembly. */
    public void setAssembledPartner(BlockPos southPos, List<BlockPos> middlePositions) {
        this.southPos = southPos.immutable();
        this.middlePositions = List.copyOf(middlePositions);
        // Force the very next tickHearthEffect call to recompute the room
        // immediately instead of waiting up to ROOM_RECOMPUTE_INTERVAL_TICKS
        // — the seed set (self + middles + South) just changed, so the old
        // cache is stale, and a freshly-assembled Hearth shouldn't sit for up
        // to 5 seconds looking like it isn't working.
        roomCacheComputedAtTick = -ROOM_RECOMPUTE_INTERVAL_TICKS;
        setChanged();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // Purely speed-driven, same as Brass Heater — no manual UI needed.
    }

    @Override
    public void onSpeedChanged(float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        pointer.chase(getSpeed() > 0 ? 1 : 0, getChaseSpeed(), Chaser.LINEAR);
        pointer.forceNextSync();
        // Same real Steam Engine puff as SteamOutletBlockEntity's own valve —
        // see its onSpeedChanged for why this is #play (server-broadcast),
        // not #playAt (client-local no-op on a real server).
        if (level != null && !level.isClientSide) {
            AllSoundEvents.STEAM.play(level, null, worldPosition, 0.6f,
                    0.9f + level.random.nextFloat() * 0.2f);
        }
    }

    private float getChaseSpeed() {
        return Mth.clamp(Math.abs(getSpeed()) / 16 / 20, 0, 1);
    }

    /** @return this run's max throughput at a fully open valve — 256 mB/t base, +128 mB/t per assembled middle segment. */
    private int maxSteamConsumptionPerTick() {
        return BASE_MAX_STEAM_CONSUMPTION_PER_TICK + middlePositions.size() * MAX_STEAM_CONSUMPTION_PER_MIDDLE;
    }

    /**
     * @return whether South's water buffer is completely full right now —
     * the "clogged" condition: with nowhere for fresh condensate to go, the
     * whole run stalls (see #tick, which forces heatFraction to 0 whenever
     * this is true) and reads as COLD/FREEZING via the usual
     * {@link BrassHeaterBlockEntity#quantizeHeatTier} threshold, exactly like
     * an idle or unpowered radiator — until the water is drained, either by
     * South's own dripstone leak (see RadiatorValveSouthBlockEntity#drip) or
     * by piping it back to a real consumer.
     */
    private boolean isSouthWaterFull() {
        return southPos != null && level.getBlockEntity(southPos) instanceof RadiatorValveSouthBlockEntity southBe
                && southBe.isWaterFull();
    }

    @Override
    public void tick() {
        super.tick();
        pointer.tickChaser();

        if (level == null || level.isClientSide) {
            return;
        }

        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof RadiatorValveNorthBlock) || !state.getValue(RadiatorValveNorthBlock.ASSEMBLED)) {
            return;
        }

        int maxConsumption = maxSteamConsumptionPerTick();
        float valvePosition = pointer.getValue();
        int desired = Math.round(valvePosition * maxConsumption);
        boolean clogged = isSouthWaterFull();

        float instantFraction = 0;
        if (desired > 0 && !clogged) {
            FluidStack drained = steamTank.drain(desired, IFluidHandler.FluidAction.EXECUTE);
            int consumed = drained.getAmount();
            instantFraction = (float) consumed / maxConsumption;

            // Water production stays tied to the real, instant amount consumed
            // this tick — that's genuine mass conservation, not something to
            // smooth. Only the DISPLAYED heat tier gets smoothed below.
            if (consumed > 0 && southPos != null
                    && level.getBlockEntity(southPos) instanceof RadiatorValveSouthBlockEntity southBe) {
                southBe.receiveCondensate(Math.round(consumed * STEAM_TO_WATER_RATIO));
            }
        }
        // Smoothed rather than assigned outright — a raw single-tick
        // consumed/max ratio flickers hard now that supply and demand sit
        // close together (see the RPM-based rescale): a pipe's flow landing
        // one tick late, or Create's own flow-establishment jitter, would
        // otherwise read as "zero steam this tick" and snap the whole radiator
        // to COLD, then back up next tick — the "flashing" symptom. This
        // settles toward the real rate over about a second instead of
        // reacting to single-tick noise, mirroring why Create's own BoilerData
        // averages water supply over a rolling window rather than trusting one
        // tick's fill() amount.
        heatFraction += (instantFraction - heatFraction) * HEAT_FRACTION_SMOOTHING;

        // Reuses Brass Heater's exact thresholds/ambient formula (widened to
        // package-visible on that class for this purpose) rather than
        // re-deriving them — single source of truth for both blocks. Applied
        // to the whole assembled run (self + every middle + South), not just
        // this block, since the tiered pipe texture (see
        // multi_radiator_*_warm/hot/blazing/freezing.json) is meant to read
        // as one continuous radiator glowing together.
        BrassHeaterBlock.HeatLevel newTier = BrassHeaterBlockEntity.quantizeHeatTier(
                heatFraction, BrassHeaterBlockEntity.ambientTemperatureC(level, worldPosition),
                BrassHeaterBlockEntity.isCold(level, worldPosition));
        applyHeatLevel(newTier);

        if (level instanceof ServerLevel serverLevel) {
            tickSmokeParticles(serverLevel, newTier);
            if (ColdSweatCompat.present()) {
                // Same smoothed tier as the visible model/pipe texture, not
                // the raw instant one — this is meant to be a continuous
                // status, exactly like the real Cold Sweat Hearth's own
                // isUsingHotFuel (a stable on/off, never a flickering
                // per-tick value): the real Hearth's own reapplication check
                // only runs every 20 ticks too (see its tick(),
                // ticksExisted % 20 == 0), which only gives reliable,
                // gap-free coverage because its OWN gating condition is
                // stable between samples. Feeding this the noisy instant
                // tier meant the 1-in-20-ticks sample would just as often
                // land on a momentary dip, so the effect kept lapsing
                // instead of running continuously.
                tickHearthEffect(serverLevel, newTier);
            }
        }
    }

    /** How often (in ticks) the "trapped room" flood-fill is recomputed — matches Cold Sweat's own Hearth (every 200 ticks), used here as a plain periodic recompute rather than an incremental spreader. */
    private static final int ROOM_RECOMPUTE_INTERVAL_TICKS = 100;
    /**
     * Caps the flood-fill so an unenclosed outdoor build (or a huge
     * warehouse) can't make this unbounded — matches real Cold Sweat's own
     * default {@code hearth_max_volume} (1000) exactly, per
     * {@code ConfigSettings.HEARTH_MAX_VOLUME}. The old 400 was well under
     * half that, which for anything but a small room could truncate well
     * before covering the whole enclosed space — reading as "warmth only
     * within some distance," not "warmth throughout the sealed room."
     */
    private static final int ROOM_MAX_SIZE = 1000;
    /** Cube (Chebyshev) distance from this block beyond which the room never extends, even through open space — already more generous than real Cold Sweat's own default {@code hearth_max_range} (16). */
    private static final int ROOM_MAX_RANGE = 20;
    /** Matches Cold Sweat's own Hearth cadence for re-applying WARMTH to everyone still inside. */
    private static final int WARMTH_APPLY_INTERVAL_TICKS = 20;
    /** Reapplied every {@link #WARMTH_APPLY_INTERVAL_TICKS}, so this only needs to outlast one interval. */
    private static final int WARMTH_DURATION_TICKS = 60;

    /**
     * Makes the assembled radiator behave like Cold Sweat's own Hearth
     * (warmth side only — no fuel, no cooling) rather than a plain
     * distance-based {@code BlockTemp} radius: a flood-fill through open,
     * non-solid, non-sky-exposed space starting from every segment of the
     * run, capped in size and range — the same "heat stays trapped in a
     * sealed room, blocked by walls, escapes through any opening to the sky"
     * idea as the real Hearth's {@code SpreadPath} system, just as a single
     * capped BFS recomputed periodically instead of an incremental per-tick
     * spreader (Cold Sweat needs that complexity because a Hearth can run
     * for the entire time a chunk is loaded; recomputing a capped, cheap BFS
     * every 100 ticks is simpler and plenty responsive here).
     * <p>
     * Anyone standing inside that reachable room gets Cold Sweat's own real
     * {@code WARMTH} mob effect directly (see {@link ColdSweatWarmthEffect}) —
     * exactly how the real Hearth grants warmth (see its
     * {@code insulateEntity}) — rather than contributing to the world's
     * ambient temperature via a {@code BlockTemp} lookup. Only reachable at
     * all when Cold Sweat is present (checked by the caller).
     */
    private void tickHearthEffect(ServerLevel serverLevel, BrassHeaterBlock.HeatLevel tier) {
        if (tier != BrassHeaterBlock.HeatLevel.WARM && tier != BrassHeaterBlock.HeatLevel.HOT
                && tier != BrassHeaterBlock.HeatLevel.BLAZING) {
            return;
        }
        long gameTime = serverLevel.getGameTime();
        if (gameTime - roomCacheComputedAtTick >= ROOM_RECOMPUTE_INTERVAL_TICKS) {
            roomCache = computeRoom(serverLevel);
            roomCacheComputedAtTick = gameTime;
        }
        tickHearthAirParticles(serverLevel);
        if (gameTime % WARMTH_APPLY_INTERVAL_TICKS != 0) {
            return;
        }
        int amplifier = switch (tier) {
            case BLAZING -> 2;
            case HOT -> 1;
            default -> 0;
        };
        AABB searchArea = new AABB(worldPosition).inflate(ROOM_MAX_RANGE);
        if (southPos != null) {
            searchArea = searchArea.minmax(new AABB(southPos).inflate(ROOM_MAX_RANGE));
        }
        for (LivingEntity entity : serverLevel.getEntitiesOfClass(LivingEntity.class, searchArea)) {
            // Standing right at the Inlet or Outlet always counts, even if
            // that exact spot didn't pass the room flood-fill's sky-exposure
            // check (e.g. an Inlet/Outlet poking through an exterior wall for
            // its pipe hookup) — a real heater radiates at its own surface
            // regardless of whether the room around it is fully sealed.
            // Anywhere else, only the trapped room itself counts.
            boolean nearInletOrOutlet = isNear(entity, worldPosition) || (southPos != null && isNear(entity, southPos));
            boolean inRoom = roomCache.contains(entity.blockPosition()) || roomCache.contains(entity.blockPosition().above());
            if (nearInletOrOutlet || inRoom) {
                ColdSweatWarmthEffect.apply(entity, amplifier, WARMTH_DURATION_TICKS);
            }
        }
    }

    /** How close counts as "standing right at" the Inlet/Outlet for the unconditional warmth guarantee above. */
    private static final double NEAR_ENDPOINT_RADIUS = 3.0;

    /**
     * @return the Cold Sweat {@code WarmthTempModifier} "strength" (1/2/3 for
     * WARM/HOT/BLAZING, matching {@link ColdSweatWarmthEffect#apply}'s own
     * {@code strength = amplifier + 1}) this Hearth's currently-computed room
     * grants at {@code pos} right now, or 0 if {@code pos} isn't inside it (or
     * this Hearth isn't at least warm). Mirrors the exact same
     * near-endpoint-or-in-room test {@link #tickHearthEffect} uses for living
     * entities, but as a pure position query with no entity involved.
     * <p>
     * Called from {@code WorldHelperMixin} (a Cold-Sweat-only mixin, isolated
     * the same way as everything under {@code compat}) so that ANY
     * position-based Cold Sweat temperature query — a real Cold Sweat
     * Thermometer, PG's own {@code ThermalBehaviour} ambient baseline, our own
     * {@code isCold} freezing-texture check — sees the same room-wide warmth a
     * living entity standing there already receives via the per-entity
     * modifier above, instead of that warmth being invisible to every query
     * that isn't "is a specific LivingEntity inside the room" (see
     * {@code WorldHelper.getInsulationAt}'s real source: it only ever
     * recognizes Cold Sweat's own native {@code HearthBlockEntity}, by a
     * literal {@code instanceof} check with nothing to hook into otherwise —
     * confirmed by reading it directly).
     */
    public int hearthStrengthAt(BlockPos pos) {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof RadiatorValveNorthBlock)) {
            return 0;
        }
        BrassHeaterBlock.HeatLevel tier = state.getValue(RadiatorValveNorthBlock.HEAT_LEVEL);
        if (tier != BrassHeaterBlock.HeatLevel.WARM && tier != BrassHeaterBlock.HeatLevel.HOT
                && tier != BrassHeaterBlock.HeatLevel.BLAZING) {
            return 0;
        }
        boolean nearInletOrOutlet = pos.closerThan(worldPosition, NEAR_ENDPOINT_RADIUS)
                || (southPos != null && pos.closerThan(southPos, NEAR_ENDPOINT_RADIUS));
        boolean inRoom = roomCache.contains(pos) || roomCache.contains(pos.above());
        if (!nearInletOrOutlet && !inRoom) {
            return 0;
        }
        return switch (tier) {
            case BLAZING -> 3;
            case HOT -> 2;
            default -> 1;
        };
    }

    private static boolean isNear(LivingEntity entity, BlockPos pos) {
        return entity.position().closerThan(net.minecraft.world.phys.Vec3.atCenterOf(pos), NEAR_ENDPOINT_RADIUS);
    }

    /**
     * Same {@code warm_air} ambient particle the real Cold Sweat Hearth
     * drifts through its own trapped room, at the same density formula (see
     * {@code HearthBlockEntity#spawnRandomAirParticles}: {@code count =
     * max(1, roomSize / 100)}, one random cell per particle, small random
     * offset within the cell plus a gentle horizontal drift) — this is what
     * makes the room read as "the whole space is warm," not just wherever the
     * player happens to be standing. Runs every tick (like the real Hearth's
     * own client-tick spawner) rather than on the 20-tick WARMTH_APPLY
     * cadence, since it's cosmetic and should feel continuous.
     */
    private void tickHearthAirParticles(ServerLevel serverLevel) {
        if (roomCache.isEmpty()) {
            return;
        }
        int count = Math.max(1, roomCache.size() / 100);
        List<BlockPos> cells = new ArrayList<>(roomCache);
        for (int i = 0; i < count; i++) {
            BlockPos pos = cells.get(serverLevel.random.nextInt(cells.size()));
            double x = pos.getX() + serverLevel.random.nextDouble();
            double y = pos.getY() + serverLevel.random.nextDouble();
            double z = pos.getZ() + serverLevel.random.nextDouble();
            double xMotion = serverLevel.random.nextDouble() / 20 - 0.025;
            double zMotion = serverLevel.random.nextDouble() / 20 - 0.025;
            ColdSweatWarmthEffect.spawnAirParticle(serverLevel, x, y, z, xMotion, zMotion);
        }
    }

    /**
     * Breadth-first flood-fill from every segment of the assembled run
     * through open, passable, non-sky-exposed space — a cell counts as
     * "trapped room" only if it's reachable this way; a solid wall blocks
     * expansion entirely, and a cell open to the sky is excluded and never
     * expanded from (heat escapes there instead of spreading further),
     * mirroring the real Hearth's own skylight-pruning behavior without
     * needing its full incremental SpreadPath machinery.
     */
    private Set<BlockPos> computeRoom(ServerLevel serverLevel) {
        Set<BlockPos> room = new HashSet<>();
        Deque<BlockPos> frontier = new ArrayDeque<>();

        List<BlockPos> seeds = new ArrayList<>(middlePositions.size() + 2);
        seeds.add(worldPosition);
        seeds.addAll(middlePositions);
        if (southPos != null) {
            seeds.add(southPos);
        }
        for (BlockPos seed : seeds) {
            if (room.add(seed)) {
                frontier.add(seed);
            }
        }

        while (!frontier.isEmpty() && room.size() < ROOM_MAX_SIZE) {
            BlockPos current = frontier.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (room.contains(next) || !withinRoomRange(next)) {
                    continue;
                }
                BlockState state = serverLevel.getBlockState(next);
                if (state.canOcclude() && !state.isAir()) {
                    continue;
                }
                if (serverLevel.canSeeSky(next)) {
                    continue;
                }
                room.add(next);
                frontier.add(next);
            }
        }
        return room;
    }

    private boolean withinRoomRange(BlockPos pos) {
        int dx = Math.abs(pos.getX() - worldPosition.getX());
        int dy = Math.abs(pos.getY() - worldPosition.getY());
        int dz = Math.abs(pos.getZ() - worldPosition.getZ());
        return Math.max(dx, Math.max(dy, dz)) <= ROOM_MAX_RANGE;
    }

    /**
     * Pushes {@code tier} onto this block's own HEAT_LEVEL (+ the 3
     * Cold-Sweat-only mirror booleans, see RadiatorValveNorthBlock#HEAT_WARM)
     * plus every cached middle position and the South partner — each is a
     * cheap no-op if already at that tier (same "always recheck, no-op
     * internally" pattern BrassHeaterBlockEntity#updateHeatLevelState uses).
     */
    private void applyHeatLevel(BrassHeaterBlock.HeatLevel tier) {
        boolean warm = tier == BrassHeaterBlock.HeatLevel.WARM;
        boolean hot = tier == BrassHeaterBlock.HeatLevel.HOT;
        boolean blazing = tier == BrassHeaterBlock.HeatLevel.BLAZING;

        BlockState selfState = getBlockState();
        if (selfState.getBlock() instanceof RadiatorValveNorthBlock
                && selfState.getValue(RadiatorValveNorthBlock.HEAT_LEVEL) != tier) {
            level.setBlockAndUpdate(worldPosition, selfState.setValue(RadiatorValveNorthBlock.HEAT_LEVEL, tier)
                    .setValue(RadiatorValveNorthBlock.HEAT_WARM, warm)
                    .setValue(RadiatorValveNorthBlock.HEAT_HOT, hot)
                    .setValue(RadiatorValveNorthBlock.HEAT_BLAZING, blazing));
        }
        for (BlockPos middlePos : middlePositions) {
            BlockState middleState = level.getBlockState(middlePos);
            if (middleState.getBlock() instanceof RadiatorMiddleBlock
                    && middleState.getValue(RadiatorMiddleBlock.HEAT_LEVEL) != tier) {
                level.setBlockAndUpdate(middlePos, middleState.setValue(RadiatorMiddleBlock.HEAT_LEVEL, tier)
                        .setValue(RadiatorMiddleBlock.HEAT_WARM, warm)
                        .setValue(RadiatorMiddleBlock.HEAT_HOT, hot)
                        .setValue(RadiatorMiddleBlock.HEAT_BLAZING, blazing));
            }
        }
        if (southPos != null) {
            BlockState southState = level.getBlockState(southPos);
            if (southState.getBlock() instanceof RadiatorValveSouthBlock
                    && southState.getValue(RadiatorValveSouthBlock.HEAT_LEVEL) != tier) {
                level.setBlockAndUpdate(southPos, southState.setValue(RadiatorValveSouthBlock.HEAT_LEVEL, tier)
                        .setValue(RadiatorValveSouthBlock.HEAT_WARM, warm)
                        .setValue(RadiatorValveSouthBlock.HEAT_HOT, hot)
                        .setValue(RadiatorValveSouthBlock.HEAT_BLAZING, blazing));
            }
        }
    }

    /** @return the fraction (0..1) of maximum steam throughput actually being burned right now. */
    public float getHeatFraction() {
        return heatFraction;
    }

    /** How often (in ticks) a HOT run puffs — a lazier pace than BLAZING's. */
    private static final int SMOKE_INTERVAL_HOT_TICKS = 30;
    /** BLAZING puffs roughly 4x as often as HOT, matching the "even more, faster" ask. */
    private static final int SMOKE_INTERVAL_BLAZING_TICKS = 8;

    /**
     * Server-broadcast visual (see {@code ServerLevel#sendParticles}, same
     * mechanism {@link RadiatorValveSouthBlockEntity#drip} already uses for
     * its own dripstone particle) rather than a client-only
     * {@code level.addParticle} — this end is the only block in the run with
     * a BlockEntity that ticks at all (Middle has none, see
     * {@link RadiatorMiddleBlock}), but the whole assembled radiator should
     * visibly steam along its full length, not just this one block. Only the
     * server actually knows {@link #middlePositions}/{@link #southPos}, so it
     * broadcasts one puff per segment directly instead of relying on any of
     * that data being client-synced.
     * <p>
     * Same white {@code CIOParticles#RADIATOR_SMOKE} technique as before — a
     * steady trickle at HOT, thicker and faster-rising at BLAZING — just fired
     * at self + every middle + South instead of only self.
     */
    private void tickSmokeParticles(ServerLevel serverLevel, BrassHeaterBlock.HeatLevel tier) {
        if (tier != BrassHeaterBlock.HeatLevel.HOT && tier != BrassHeaterBlock.HeatLevel.BLAZING) {
            return;
        }
        boolean blazing = tier == BrassHeaterBlock.HeatLevel.BLAZING;
        long ticksSinceEpoch = serverLevel.getGameTime() + worldPosition.hashCode();
        int interval = blazing ? SMOKE_INTERVAL_BLAZING_TICKS : SMOKE_INTERVAL_HOT_TICKS;
        if (ticksSinceEpoch % interval != 0) {
            return;
        }
        int countPerSegment = blazing ? 2 + serverLevel.random.nextInt(3) : 1;
        double riseSpeed = blazing ? 0.02 : 0.01;

        puffAt(serverLevel, worldPosition, countPerSegment, riseSpeed);
        for (BlockPos middlePos : middlePositions) {
            puffAt(serverLevel, middlePos, countPerSegment, riseSpeed);
        }
        if (southPos != null) {
            puffAt(serverLevel, southPos, countPerSegment, riseSpeed);
        }
    }

    /**
     * count=0 on {@code sendParticles} means the xDist/yDist/zDist/speed
     * quartet is used as an exact velocity, not a random spread (confirmed by
     * reading {@code ClientPacketListener#handleParticleEvent}'s real source)
     * — so each call here spawns exactly one particle with a controlled
     * upward drift, same as the old client-side {@code addParticle} did, just
     * with the position jitter computed server-side instead.
     */
    private void puffAt(ServerLevel serverLevel, BlockPos pos, int count, double riseSpeed) {
        for (int i = 0; i < count; i++) {
            double x = pos.getX() + 0.5 + (serverLevel.random.nextDouble() - 0.5) * 0.4;
            double y = pos.getY() + 0.9;
            double z = pos.getZ() + 0.5 + (serverLevel.random.nextDouble() - 0.5) * 0.4;
            serverLevel.sendParticles(CIOParticles.RADIATOR_SMOKE.get(), x, y, z, 0, 0.0, riseSpeed, 0.0, 1.0);
        }
    }

    /**
     * @return this block's internal steam buffer when queried from directly
     * below — same convention BrassHeaterBlockEntity uses, and deliberately
     * NOT the FACING face (that's shaft/valve only): a pipe has to run in
     * from under the radiator, not out its exposed end or top.
     */
    @Nullable
    public IFluidHandler getSteamHandler(@Nullable Direction side) {
        if (side == Direction.DOWN) {
            return steamTank;
        }
        return null;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        CreateLang.text("Steam Hearth Inlet")
                .style(ChatFormatting.WHITE)
                .forGoggles(tooltip);
        containedFluidTooltip(tooltip, isPlayerSneaking, steamTank);

        BlockState state = getBlockState();
        boolean assembled = state.getBlock() instanceof RadiatorValveNorthBlock && state.getValue(RadiatorValveNorthBlock.ASSEMBLED);

        // Steam has somewhere to go (this end's own tank) but no South
        // partner to hand condensate to at all — the tank just fills up and
        // backs up the supplying pipe, same as any full Create tank. This
        // flags that specific "intake with no outlet" case rather than
        // leaving it to be inferred from "Assembled: No" alone.
        if (!assembled && pointer.getValue() > 0) {
            CreateLang.text("Clogged: ")
                    .style(ChatFormatting.RED)
                    .add(CreateLang.text("No Water Outlet attached").style(ChatFormatting.RED))
                    .forGoggles(tooltip, 1);
        } else if (assembled && isSouthWaterFull()) {
            // Named explicitly rather than just "water tank full" — the water
            // itself is buffered at the Water Outlet (South), not here; this
            // is one shared water level for the whole assembled body, and
            // North stalling is a downstream symptom, not a local one.
            CreateLang.text("Clogged: ")
                    .style(ChatFormatting.RED)
                    .add(CreateLang.text("Water Outlet is full — drain it to resume").style(ChatFormatting.RED))
                    .forGoggles(tooltip, 1);
        }

        CreateLang.text("Assembled: ")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.text(assembled ? "Yes (" + middlePositions.size() + " middle)" : "No")
                        .style(assembled ? ChatFormatting.GREEN : ChatFormatting.RED))
                .forGoggles(tooltip, 1);

        // Shown here too (not just on the Water Outlet itself) since it's a
        // single shared level for the whole assembled body, not something
        // local to either end — see class doc.
        if (assembled && southPos != null && level.getBlockEntity(southPos) instanceof RadiatorValveSouthBlockEntity southBe) {
            CreateLang.text("Water: ")
                    .style(ChatFormatting.GRAY)
                    .add(CreateLang.number(Math.round(southBe.getWaterFraction() * 100))
                            .text("%")
                            .style(ChatFormatting.BLUE))
                    .forGoggles(tooltip, 1);
        }

        CreateLang.text("Valve: ")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.number(Math.round(pointer.getValue() * 100))
                        .text("%")
                        .style(ChatFormatting.AQUA))
                .forGoggles(tooltip, 1);

        CreateLang.text("Heat: ")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.number(Math.round(heatFraction * 100))
                        .text("% (" + state.getValue(RadiatorValveNorthBlock.HEAT_LEVEL).getSerializedName() + ")")
                        .style(ChatFormatting.GOLD))
                .forGoggles(tooltip, 1);

        // Directly exposes what #computeRoom actually found — otherwise the
        // only way to tell "sealed room found, N blocks" apart from "open air,
        // no room, warmth is local-only" is by feel, which is exactly how the
        // roomCacheComputedAtTick overflow bug (see its field doc) went
        // unnoticed for this long: the effect LOOKED like it was working
        // (goggles showed Heat: BLAZING) while the room it was supposed to be
        // warming silently stayed empty the entire time.
        if (ColdSweatCompat.present()) {
            int roomSize = roomCache.size();
            int seedCount = middlePositions.size() + (southPos != null ? 2 : 1);
            if (roomSize > seedCount) {
                CreateLang.text("Warmth: ")
                        .style(ChatFormatting.GRAY)
                        .add(CreateLang.text("enclosed room, " + roomSize + " blocks").style(ChatFormatting.LIGHT_PURPLE))
                        .forGoggles(tooltip, 1);
            } else {
                CreateLang.text("Warmth: ")
                        .style(ChatFormatting.GRAY)
                        .add(CreateLang.text("open air — local heat only").style(ChatFormatting.LIGHT_PURPLE))
                        .forGoggles(tooltip, 1);
            }
        }

        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("SteamTank", steamTank.writeToNBT(registries, new CompoundTag()));
        tag.putFloat("HeatFraction", heatFraction);
        tag.put("Pointer", pointer.writeNBT());
        if (southPos != null) {
            tag.putLong("SouthPos", southPos.asLong());
            long[] middleLongs = new long[middlePositions.size()];
            for (int i = 0; i < middleLongs.length; i++) {
                middleLongs[i] = middlePositions.get(i).asLong();
            }
            tag.putLongArray("MiddlePositions", middleLongs);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        steamTank.readFromNBT(registries, tag.getCompound("SteamTank"));
        heatFraction = tag.getFloat("HeatFraction");
        pointer.readNBT(tag.getCompound("Pointer"), clientPacket);
        if (tag.contains("SouthPos")) {
            southPos = BlockPos.of(tag.getLong("SouthPos"));
            long[] middleLongs = tag.getLongArray("MiddlePositions");
            List<BlockPos> positions = new ArrayList<>(middleLongs.length);
            for (long middleLong : middleLongs) {
                positions.add(BlockPos.of(middleLong));
            }
            middlePositions = positions;
        }
    }
}
