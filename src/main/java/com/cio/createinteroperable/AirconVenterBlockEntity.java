package com.cio.createinteroperable;

import com.cio.createinteroperable.compat.ColdSweatChillEffect;
import com.cio.createinteroperable.compat.ColdSweatCompat;
import com.cio.createinteroperable.compat.ColdSweatWarmthEffect;
import com.cio.createinteroperable.compat.ColdSweatWorldTemp;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.IAirCurrentSource;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every venter, chained or solo, draws from its own {@link #coldAirIntake}
 * buffer every tick for TWO separate purposes (see {@link #VENTED_COLD_AIR_PER_CYCLE}'s
 * own doc for why these are kept as two independent consumptions rather than
 * one bigger number): converting {@link #COLD_AIR_CONSUMED_PER_CYCLE} of it
 * into {@link #hotAirOutput} (the "transforms cold_air into hot_air"
 * hot_air-return loop), AND separately venting {@link #VENTED_COLD_AIR_PER_CYCLE}
 * more straight out the BOTTOM face — genuinely destroyed, no output fluid
 * produced, representing the actual cold air blown into the room. Both only
 * happen while the BOTTOM face is unobstructed (see {@link #isBottomObstructed});
 * a blocked bottom "clogs" it, stopping both consumptions, chain relay, and
 * airflow entirely until cleared. Faces (remapped through {@link AirconVenterBlock#rotate}):
 * <ul>
 *     <li>EAST-relative — the motor-facing side: a {@link DualFluidFaceHandler}
 *     exposing {@link #coldAirIntake} (fill) and {@link #hotAirOutput} (drain)
 *     through the same face.</li>
 *     <li>WEST-relative — plain handler pointing at {@link #hotAirOutput}
 *     (where a further-west neighbor's relayed hot_air lands, whether pushed
 *     by a real Create pipe on a solo venter, or {@link #receiveHotAirPush}
 *     from an active chain neighbor).</li>
 * </ul>
 * Chain membership (see {@link AirconVenterAssembly}) is fully automatic —
 * recomputed fresh every tick from nothing but live world state, not a
 * wrench-triggered event — so any run of up to {@link AirconVenterAssembly#MAX_CHAIN}
 * same-FACING venters placed side by side just works. While active in a
 * chain, each tick also directly pushes cold_air surplus to the WEST
 * neighbor and hot_air surplus to the EAST neighbor — bypassing the public
 * capability entirely, same "no pipe needed between multiblock segments"
 * precedent as {@code RadiatorValveNorthBlockEntity} pushing condensate
 * straight into its cross-linked partner.
 * <p>
 * Genuinely implements Create's own {@link IAirCurrentSource} (backed by a
 * real {@link AirCurrent}, ticked the same way {@code EncasedFanBlockEntity}
 * does) — the BOTTOM face is always the vent (regardless of the block's own
 * horizontal FACING), blowing a real Create air current straight down:
 * entities caught in it get pushed/blown and hear Create's own real
 * "caught in the wind" sound, all for free from that one interface. On top
 * of Create's own (fixed-rate) particle spawn, {@link #tickExtraParticles}
 * adds explicitly {@link #activity}- and setting-scaled extra particles
 * (a plain vanilla {@link net.minecraft.core.particles.ParticleTypes#CLOUD},
 * NOT Create's own {@code AirFlowParticleData} — see that method's own doc
 * for why), so the stream reads visibly faster/denser the harder this
 * venter is currently working.
 */
public class AirconVenterBlockEntity extends SmartBlockEntity implements IAirCurrentSource, IHaveGoggleInformation {
    /**
     * mB of cold_air consumed, and produced ({@link #HOT_AIR_PRODUCED_PER_CYCLE})
     * of hot_air, per tick — a deliberate 1:2 ratio, not 1:1: per the user's
     * own numbers, a fixed mass of cold, dense air becomes roughly double
     * the volume once heated (the same real-world reason a fixed mass of gas
     * expands when its temperature rises), and it's also what makes the
     * whole loop self-sustaining once a chain is running — the returning
     * hot_air stream alone is enough to meaningfully register at the motor
     * without needing a 1:1 mass-conserving assumption.
     */
    private static final int COLD_AIR_CONSUMED_PER_CYCLE = 25;
    private static final int HOT_AIR_PRODUCED_PER_CYCLE = 50;
    /**
     * mB of cold_air consumed AND DESTROYED per cycle, on top of
     * {@link #COLD_AIR_CONSUMED_PER_CYCLE}'s own hot_air-return conversion —
     * represents the cold_air actually blown out the BOTTOM vent into the
     * room (the real cooling effect), as opposed to the portion that's
     * recirculated back to the motor as hot_air. No output fluid is produced
     * from this — it's genuinely destroyed, "to symbolize the cold air going
     * out" per the design request. Same 25 mB figure as the conversion side,
     * so a fully-supplied venter's real total per-cycle demand is 50 mB, not
     * 25.
     */
    private static final int VENTED_COLD_AIR_PER_CYCLE = 25;
    /**
     * Sized off the larger of the two flows ({@link #HOT_AIR_PRODUCED_PER_CYCLE})
     * at a flat 4 cycles' worth, applied to both tanks for simplicity — see
     * this class's own "leak, not tank" doc history (shrunk twice already at
     * the user's own request) for why this stays intentionally tiny rather
     * than scaling with the motor's own much larger production rate.
     */
    private static final int TANK_CAPACITY_MB = HOT_AIR_PRODUCED_PER_CYCLE * 4;
    /** EMA smoothing factor for {@link #activity} — same anti-flicker reasoning as AirconMotorBottomBlockEntity#EFFICIENCY_SMOOTHING. */
    private static final float ACTIVITY_SMOOTHING = 0.08f;
    /**
     * Below this, {@link #activity} is treated as fully stopped — matches
     * {@code AirconMotorTopBlockEntity#PERFORMANCE_EPSILON} (see its own doc
     * for why): {@link #activity} is a smoothed EMA that only asymptotically
     * approaches 0 after the upstream motor loses power, so an exact-zero
     * check on {@link #getSpeed()} let Create's own native
     * {@code AirCurrent#tick} (gated on {@code getSpeed() != 0}, a check this
     * class doesn't control) keep spawning its own particles for several
     * extra seconds after this class's own hum/particle gate (already using
     * this same threshold in {@link #tick()}) had gone quiet.
     */
    private static final float ACTIVITY_EPSILON = 0.01f;
    /** Cosmetic RPM-like ceiling fed into Create's IAirCurrentSource#getMaxDistance formula — full throttle at activity = 1. */
    private static final float MAX_FAN_SPEED = 128f;
    private static final int AIR_CURRENT_REBUILD_INTERVAL = 8;
    private static final int ENTITY_SEARCH_INTERVAL = 5;
    private static final int EXTRA_PARTICLE_BASE_INTERVAL = 10;

    /**
     * How stale {@link #lastColdAirFillTick} may be before {@link #isReceivingColdAirSupply}
     * calls the supply dead. Goggle-display only now (see that method's own
     * doc) — loosened from 10 to 40 to match Create: Pipes n Physics' own
     * real, bursty (not smoothly per-tick) delivery cadence, so the goggle
     * line itself doesn't flicker "no supply" on a genuinely working, if
     * irregular, pipe.
     */
    private static final int SUPPLY_GRACE_TICKS = 40;
    /** Set by {@link #coldAirIntake}'s own overridden {@code fill()} on every tick real cold_air is actually received — see {@link #isReceivingColdAirSupply}. */
    private long lastColdAirFillTick = Long.MIN_VALUE / 2;

    private final FluidTank coldAirIntake = new FluidTank(TANK_CAPACITY_MB) {
        @Override
        public boolean isFluidValid(FluidStack stack) {
            return stack.getFluid() == CIOFluids.COLD_AIR_STILL.get();
        }

        /**
         * Tracks genuine incoming supply, not just "do I have a buffer" —
         * see {@link #isReceivingColdAirSupply}'s own doc for the real bug
         * this fixes: without this, a venter kept converting (and showing
         * full activity/particles) off its own up-to-4000mB buffer for a
         * real ~200 ticks/10s after the actual supply was fully cut,
         * because the buffer alone doesn't distinguish "still supplied"
         * from "coasting on what's left".
         */
        @Override
        public int fill(FluidStack resource, FluidAction action) {
            int filled = super.fill(resource, action);
            if (filled > 0 && action.execute() && level != null) {
                lastColdAirFillTick = level.getGameTime();
            }
            return filled;
        }
    };
    private final FluidTank hotAirOutput = new FluidTank(TANK_CAPACITY_MB) {
        @Override
        public boolean isFluidValid(FluidStack stack) {
            return stack.getFluid() == CIOFluids.HOT_AIR_STILL.get();
        }
    };
    private final DualFluidFaceHandler eastFace = new DualFluidFaceHandler(coldAirIntake, hotAirOutput);

    private final AirCurrent airCurrent = new AirCurrent(this);
    private int airCurrentCooldown = 0;
    private int entitySearchCooldown = 0;

    private boolean clogged = false;
    /** 0..1 — how much of {@link #COLD_AIR_CONSUMED_PER_CYCLE} this venter actually used this tick, smoothed. Drives hum/particle intensity and the fake "fan speed" fed to Create's own AirCurrent. */
    private float activity = 0f;
    /** This row's total length (including self) and this venter's own 0-based position within it, recomputed every tick — see {@link AirconVenterAssembly#computeChainInfo}. */
    private int chainLength = 1;
    private int chainIndexFromWest = 0;

    public AirconVenterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // Purely fluid-tick driven — no Create behaviours needed.
    }

    /** @return whether the block directly below has any real collision — a solid (or even partial) block "clogs" the vent, per the design conversation. */
    private boolean isBottomObstructed() {
        BlockPos belowPos = worldPosition.below();
        BlockState belowState = level.getBlockState(belowPos);
        return !belowState.getCollisionShape(level, belowPos).isEmpty();
    }

    /**
     * @return whether cold_air has genuinely been received — a real pipe
     * fill, or a chain push from an active neighbor, see
     * {@link #coldAirIntake}'s own overridden {@code fill()} — within the
     * last {@link #SUPPLY_GRACE_TICKS}.
     * <p>
     * Goggle-display information ONLY now — NOT read by {@link #tick()}'s
     * own conversion math anymore. It originally gated real conversion too
     * (added when the tank was still 4000mB and could coast on a stale
     * buffer for a real ~10 seconds after supply genuinely stopped), but a
     * real report found this gate refusing to convert an ACTIVELY,
     * continuously piped venter: Create: Pipes n Physics delivers fluid in
     * periodic bursts, not smoothly every tick, and this gate's grace
     * window could land between two real deliveries, reading "no supply"
     * for a genuinely working setup. Since the tank is now only a few
     * ticks' worth ({@link #TANK_CAPACITY_MB}), it can no longer coast
     * meaningfully on its own — so the honest fix was to let the tiny
     * buffer do that job by construction, and demote this to what it's
     * actually reliable for: telling the player, via goggles, whether a
     * pipe has delivered anything recently.
     */
    private boolean isReceivingColdAirSupply() {
        return level != null && level.getGameTime() - lastColdAirFillTick <= SUPPLY_GRACE_TICKS;
    }

    /**
     * Writes {@code active} into the {@code ASSEMBLED} blockstate ONLY when
     * it actually changed — same "always recheck, no-op internally" pattern
     * used elsewhere in this codebase (e.g. RadiatorValveNorthBlockEntity's
     * own heat-tier state) — a per-tick recompute (see {@link #tick()})
     * would otherwise call {@code setBlockAndUpdate} 20x/second per venter,
     * spamming neighbor updates for a value that's usually stable tick to
     * tick. Has no visual effect (the blockstate JSON doesn't key off it at
     * all), it exists purely so goggle info and the push gate below both
     * read the same settled value.
     */
    private void applyChainedState(boolean active) {
        BlockState state = getBlockState();
        if (state.getBlock() instanceof AirconVenterBlock && state.getValue(AirconVenterBlock.ASSEMBLED) != active) {
            level.setBlockAndUpdate(worldPosition, state.setValue(AirconVenterBlock.ASSEMBLED, active));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) {
            return;
        }

        // AirCurrent needs both sides ticking independently off their own
        // synced state (client for particles/entity-caught sound, server for
        // the real entity push) — mirrors EncasedFanBlockEntity's own tick(),
        // not gated behind a single isClientSide check. But NEVER call
        // airCurrent.tick() while getSpeed() == 0: Create's own
        // AirCurrent#rebuild only assigns `direction` inside its speed != 0
        // branch, so at speed 0 it stays null (forever, on a freshly placed
        // or still-unpowered venter) — and AirCurrent#tick's client-side
        // particle code dereferences it unconditionally
        // (direction.getNormal()), a guaranteed NPE. Confirmed by a real
        // crash log. Create's own EncasedFanBlockEntity dodges this the same
        // way — never calls airCurrent.tick() at all while getSpeed() == 0.
        if (airCurrentCooldown-- <= 0) {
            airCurrentCooldown = AIR_CURRENT_REBUILD_INTERVAL;
            airCurrent.rebuild();
        }
        if (getSpeed() != 0) {
            airCurrent.tick();
        }

        if (level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof AirconVenterBlock)) {
            return;
        }

        Direction facing = state.getValue(AirconVenterBlock.FACING);
        AirconVenterAssembly.ChainInfo chainInfo = AirconVenterAssembly.computeChainInfo(level, worldPosition, facing);
        chainLength = chainInfo.length();
        chainIndexFromWest = chainInfo.indexFromWest();
        boolean chainActive = chainInfo.isActive();
        applyChainedState(chainActive);

        clogged = isBottomObstructed();

        float instantActivity = 0f;
        if (!clogged) {
            // Buffer + headroom alone gate real conversion — NOT
            // isReceivingColdAirSupply (kept only for goggle display, see
            // that method's own doc): a real, reported bug traced this
            // exact gate to refusing to convert a genuinely, continuously
            // supplied venter, because Create: Pipes n Physics delivers
            // fluid in periodic bursts, not smoothly every single tick, and
            // this gate's grace window could land between two real
            // deliveries. The tank is now small enough on its own (a few
            // ticks' worth, see TANK_CAPACITY_MB's own doc) that it can no
            // longer meaningfully "coast" the way the old 4000 mB tank did
            // — so the extra liveness gate stopped paying for itself and
            // started actively causing false negatives instead.
            // 1:2 mass-to-volume ratio, not 1:1 — see COLD_AIR_CONSUMED_PER_CYCLE's
            // own doc. Headroom is checked in HOT_AIR terms (each mB of cold
            // consumed needs 2 mB of hot_air headroom), so this never
            // overfills hotAirOutput.
            int headroom = hotAirOutput.getCapacity() - hotAirOutput.getFluidAmount();
            int coldAvailable = Math.min(COLD_AIR_CONSUMED_PER_CYCLE, coldAirIntake.getFluidAmount());
            int coldConsumed = Math.min(coldAvailable, headroom / 2);
            int hotProduced = coldConsumed * (HOT_AIR_PRODUCED_PER_CYCLE / COLD_AIR_CONSUMED_PER_CYCLE);
            if (coldConsumed > 0) {
                coldAirIntake.drain(coldConsumed, IFluidHandler.FluidAction.EXECUTE);
                hotAirOutput.fill(new FluidStack(CIOFluids.HOT_AIR_STILL.get(), hotProduced), IFluidHandler.FluidAction.EXECUTE);
            }
            // A SEPARATE consumption, on top of the hot_air-return loop above
            // — represents the actual cold_air blown out the BOTTOM vent into
            // the room, gone for good (no output fluid produced from it,
            // unlike the conversion above). Drained from whatever's left in
            // the buffer after the conversion's own draw, gated purely by
            // availability (nothing downstream to overfill, so no headroom
            // check needed the way the conversion has one).
            int ventedConsumed = Math.min(VENTED_COLD_AIR_PER_CYCLE, coldAirIntake.getFluidAmount());
            if (ventedConsumed > 0) {
                coldAirIntake.drain(ventedConsumed, IFluidHandler.FluidAction.EXECUTE);
            }
            // Both halves of the venter's own total per-cycle demand feed
            // activity now (50 mB total, not 25) — a venter that can only
            // keep the conversion loop fed but starves the actual room-facing
            // vent (or vice versa) isn't genuinely at full strength either
            // way.
            instantActivity = (coldConsumed + ventedConsumed) / (float) (COLD_AIR_CONSUMED_PER_CYCLE + VENTED_COLD_AIR_PER_CYCLE);

            if (chainActive) {
                Direction westDir = AirconVenterBlock.rotate(Direction.WEST, facing);
                Direction eastDir = westDir.getOpposite();
                pushColdAirTo(worldPosition.relative(westDir), facing);
                pushHotAirTo(worldPosition.relative(eastDir), facing);
            }
        }
        activity += (instantActivity - activity) * ACTIVITY_SMOOTHING;

        // Same cadence as AirconMotorBottomBlockEntity's own periodic
        // notifyUpdate() call — without this, `activity` only ever reaches
        // the client via whatever incidental sync happens to fire (chunk
        // load, block placement, etc.), never on every real change. That's
        // a real, confirmed bug: getSpeed()/getAirFlowDirection() (read on
        // BOTH sides — see #tick's own airCurrent.tick() call above, which
        // runs before the isClientSide return below) end up gating Create's
        // own native AIR_FLOW particle spawn off the CLIENT's stale copy of
        // `activity`, which can freeze at whatever nonzero value it last
        // happened to sync at and simply never decay back down once the
        // upstream motor turns off or is removed — the venter keeps visibly
        // "blowing" forever even though the server's own activity (and
        // every server-authoritative effect gated on it, e.g. tickExtraParticles
        // below) has already correctly dropped to 0.
        if ((level.getGameTime() & 3) == 0) {
            notifyUpdate();
        }

        if (activity > ACTIVITY_EPSILON && level instanceof ServerLevel serverLevel) {
            if (entitySearchCooldown-- <= 0) {
                entitySearchCooldown = ENTITY_SEARCH_INTERVAL;
                airCurrent.findEntities();
            }
            tickHum(activity);
            tickExtraParticles(serverLevel, activity);
            if (ColdSweatCompat.present()) {
                tickHearthEffect(serverLevel);
            }
        }
    }

    /**
     * Plain vanilla-server-broadcast sound, NOT
     * {@code org.patryk3211.powergrid.utility.sound.SoundScapes} — this
     * class registers unconditionally and must work whether the nearby
     * motor is Power Grid- or Electro Energetics-wired (or PG is entirely
     * absent), so it can never reference a PG type at all. See
     * {@code AirconMotorTopBlockEntity#tickHum}'s own doc for the full
     * reasoning and why this is throttled rather than called every tick.
     * Represents the venter's own running noise, on top of whatever Create's
     * real AirCurrent sound does for anyone standing in the stream.
     */
    private void tickHum(float activity) {
        if (level == null || (level.getGameTime() & 19) != 0) {
            return;
        }
        float pitch = 0.8f + 0.2f * activity;
        float volume = 0.2f + 0.5f * activity;
        level.playSound(null, worldPosition, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, volume, pitch);
    }

    /**
     * Create's own AirCurrent#tick already spawns its real AIR_FLOW particle
     * client-side at a fixed, config-driven rate — that alone doesn't scale
     * with THIS source's own activity. This adds explicit, activity- AND
     * setting-scaled extra particles, denser (via {@code activity}, real
     * throughput) and FASTER via {@link #currentSettingFraction()} (Low/Mid/
     * Max), so the stream visibly blows harder on Max than Low even at
     * identical real throughput — same "setting is a separate cosmetic
     * dimension from real activity" idea
     * {@code AirconMotorBottomBlockEntity#smoothedHumIntensity} already uses
     * for the motor's own hum.
     * <p>
     * <b>Deliberately a plain vanilla {@link ParticleTypes#CLOUD}, NOT
     * Create's own {@code AirFlowParticleData}</b> (what this used before,
     * and what {@code AirconMotorTopBlockEntity}'s own base air-flow layer
     * still uses) — confirmed by reading Create's real
     * {@code AirFlowParticle} source: its {@code tick()} completely IGNORES
     * whatever velocity is passed to {@code sendParticles} and instead
     * recomputes its own motion every tick from
     * {@code source.getAirCurrent()}'s real {@code direction}/{@code maxDistance}/
     * {@code pushing} — so the {@code fallSpeed} below was never actually
     * doing anything through that particle type. Worse, that same
     * {@code tick()} calls {@code remove()} outright the moment the
     * particle's position isn't inside {@code airCurrent.bounds.inflate(0.25f)},
     * which this class's own spawn point (Y+0.05, still technically inside
     * the block's own cell, not below its bottom face) doesn't reliably
     * satisfy — a real, confirmed cause of "no particles visible at all,"
     * not just "wrong speed." A plain {@code SimpleParticleType} like CLOUD
     * has no such self-driving logic — it genuinely obeys the count=0/
     * literal-velocity convention (same as {@code SteamOutletBlockEntity#tickSteamParticles}
     * elsewhere in this codebase), so {@code fallSpeed} now actually controls
     * how fast it falls.
     */
    private void tickExtraParticles(ServerLevel serverLevel, float activity) {
        long ticksSinceEpoch = serverLevel.getGameTime() + worldPosition.hashCode();
        int interval = Math.max(1, Math.round(EXTRA_PARTICLE_BASE_INTERVAL / Math.max(activity, 0.05f)));
        if (ticksSinceEpoch % interval != 0) {
            return;
        }
        int count = 1 + Math.round(activity * 3);
        double fallSpeed = 0.03 + activity * 0.08 * currentSettingFraction();
        for (int i = 0; i < count; i++) {
            double x = worldPosition.getX() + 0.5 + (serverLevel.random.nextDouble() - 0.5) * 0.4;
            double y = worldPosition.getY() + 0.05;
            double z = worldPosition.getZ() + 0.5 + (serverLevel.random.nextDouble() - 0.5) * 0.4;
            serverLevel.sendParticles(ParticleTypes.CLOUD, x, y, z, 0, 0.0, -fallSpeed, 0.0, 1.0);
        }
    }

    /**
     * @return whether {@code neighborPos} is a genuine chain neighbor to
     * relay into — just "a venter, same FACING" now, no ASSEMBLED check of
     * its own: the CALLER already gates on its own {@code chainActive}
     * before ever reaching here (see {@link #tick()}), and deliberately
     * doesn't also require the neighbor to be active — an over-long row's
     * boundary member (index {@link AirconVenterAssembly#MAX_CHAIN} - 1)
     * still hands off into the first excluded member beyond it, which then
     * simply doesn't relay any further itself (it just never calls this push
     * again on its own turn, since ITS OWN {@code chainActive} is false).
     */
    private boolean isChainNeighbor(BlockPos neighborPos, Direction facing) {
        BlockState neighborState = level.getBlockState(neighborPos);
        return neighborState.getBlock() instanceof AirconVenterBlock
                && neighborState.getValue(AirconVenterBlock.FACING) == facing;
    }

    private void pushColdAirTo(BlockPos neighborPos, Direction facing) {
        if (!isChainNeighbor(neighborPos, facing) || !(level.getBlockEntity(neighborPos) instanceof AirconVenterBlockEntity neighbor)) {
            return;
        }
        int amount = Math.min(COLD_AIR_CONSUMED_PER_CYCLE, coldAirIntake.getFluidAmount());
        FluidStack drained = coldAirIntake.drain(amount, IFluidHandler.FluidAction.EXECUTE);
        if (!drained.isEmpty()) {
            neighbor.receiveColdAirPush(drained.getAmount());
        }
    }

    private void pushHotAirTo(BlockPos neighborPos, Direction facing) {
        if (!isChainNeighbor(neighborPos, facing) || !(level.getBlockEntity(neighborPos) instanceof AirconVenterBlockEntity neighbor)) {
            return;
        }
        int amount = Math.min(HOT_AIR_PRODUCED_PER_CYCLE, hotAirOutput.getFluidAmount());
        FluidStack drained = hotAirOutput.drain(amount, IFluidHandler.FluidAction.EXECUTE);
        if (!drained.isEmpty()) {
            neighbor.receiveHotAirPush(drained.getAmount());
        }
    }

    /** Called by an assembled EAST neighbor's own tick (its "west push") — see {@link #pushColdAirTo}. */
    void receiveColdAirPush(int mb) {
        if (mb > 0) {
            coldAirIntake.fill(new FluidStack(CIOFluids.COLD_AIR_STILL.get(), mb), IFluidHandler.FluidAction.EXECUTE);
        }
    }

    /** Called by an assembled WEST neighbor's own tick (its "east push") — see {@link #pushHotAirTo}. */
    void receiveHotAirPush(int mb) {
        if (mb > 0) {
            hotAirOutput.fill(new FluidStack(CIOFluids.HOT_AIR_STILL.get(), mb), IFluidHandler.FluidAction.EXECUTE);
        }
    }

    /** How often (in ticks) the "trapped room" flood-fill is recomputed — matches RadiatorValveNorthBlockEntity's own Steam Hearth cadence. */
    private static final int ROOM_RECOMPUTE_INTERVAL_TICKS = 100;
    /** Same cap as the Steam Hearth — matches real Cold Sweat's own default {@code hearth_max_volume}. */
    private static final int ROOM_MAX_SIZE = 1000;
    /** Cube (Chebyshev) distance beyond which the room never extends. */
    private static final int ROOM_MAX_RANGE = 20;
    /** Matches Cold Sweat's own Hearth cadence for re-applying its mob effect to everyone still inside. */
    private static final int CHILL_APPLY_INTERVAL_TICKS = 20;
    /** Reapplied every {@link #CHILL_APPLY_INTERVAL_TICKS}, so this only needs to outlast one interval — once the venter stops reapplying (clogged, or activity drops to 0), the effect simply expires within this window on its own, matching "if the venter is no longer working, the effect vanishes." */
    private static final int CHILL_DURATION_TICKS = 60;
    /** Standing this close to the venter always counts, even outside the flood-filled room (e.g. right at its BOTTOM vent). */
    private static final double NEAR_VENTER_RADIUS = 3.0;
    /** Below this {@link #activity}, the venter isn't meaningfully "working" for Hearth purposes — matches the same gate #tick already uses for hum/particles. */
    private static final float WORKING_ACTIVITY_THRESHOLD = 0.01f;
    /**
     * How often (in ticks) {@link #currentDropC()} re-scans for a nearby
     * motor's current Low/Mid/Max setting. Doesn't need to be instant —
     * matches the general "settle within about a second" feel every other
     * periodic scan in this feature already uses.
     */
    private static final int DROP_C_SCAN_INTERVAL_TICKS = 20;
    /** How many chunks out to look for a motor whose setting should drive this venter's own cooling degree — same emergent, non-hardcoded "no stored link" approach {@code AirconMotorBottomBlockEntity#totalCooledRoomSize} already uses in the opposite direction. */
    private static final int MOTOR_SCAN_CHUNK_RADIUS = 2;
    /** Used when no motor is found within {@link #MOTOR_SCAN_CHUNK_RADIUS} chunks — a fair, non-extreme middle value (matches AirconMotorBottomBlockEntity's own Mid setting) rather than silently defaulting to either "barely cooling" or "always maximum". */
    private static final float DEFAULT_DROP_C = 12f;
    /** Used when no motor is found — matches {@link #DEFAULT_DROP_C}'s own "fair Mid-like middle" reasoning, same value as {@code AirconMotorBottomBlockEntity}'s own Mid entry in {@code SETTING_HUM_FRACTION}. */
    private static final float DEFAULT_SETTING_FRACTION = 0.5f;
    /** Cold sinks — the cooled room never extends more than this many blocks ABOVE the venter's own Y (no cap going down at all), the deliberate opposite of the Steam Hearth's "never below the radiator's own floor" rule. Tightened from 10 to 2 per user feedback — a venter's own cold output shouldn't reach nearly as far up as a full extra floor. */
    private static final int MAX_RISE_ABOVE_VENTER = 2;

    private float cachedDropC = DEFAULT_DROP_C;
    private float cachedSettingFraction = DEFAULT_SETTING_FRACTION;
    private long dropCComputedAtTick = -DROP_C_SCAN_INTERVAL_TICKS;

    /**
     * How often (in ticks) {@link #tickWaterFreeze} scans the room for water
     * to progress toward ice — deliberately much shorter than
     * {@link #ROOM_RECOMPUTE_INTERVAL_TICKS} (the room's own shape barely
     * changes tick to tick, but "-25°C onwards means near-instant freezing"
     * needs a genuinely short response time, not one gated behind a 5s
     * structural recompute). Reuses the existing {@link #roomCache} between
     * its own recomputes — this only re-checks fluid state at already-known
     * room positions, not a fresh flood-fill.
     */
    private static final int FREEZE_SCAN_INTERVAL_TICKS = 5;
    /** Real seconds of continuous sub-0°C exposure a water source needs right at (just below) 0°C — "10-ish seconds of exposure" per the design request. */
    private static final double FREEZE_SECONDS_AT_ZERO_C = 10.0;
    /** Real seconds required at {@link #FREEZE_COLDEST_C} and colder — "near-instant" without being a literal same-tick snap. */
    private static final double FREEZE_SECONDS_AT_MIN = 0.5;
    /** °C at/below which freezing is already at its fastest ({@link #FREEZE_SECONDS_AT_MIN}) — matches "-25°C onwards" from the design request exactly. */
    private static final double FREEZE_COLDEST_C = -25.0;

    /** Accumulated exposure ticks per water-source position currently mid-freeze — see {@link #tickWaterFreeze}. Positions are pruned the instant they stop being a water source (removed by hand, already frozen, room reshaped) or the room warms back to/above 0°C. */
    private final Map<BlockPos, Integer> freezeExposureTicks = new HashMap<>();
    private long freezeScanComputedAtTick = -FREEZE_SCAN_INTERVAL_TICKS;

    /**
     * @return how many ticks of continuous sub-0°C exposure a water source
     * needs at {@code roomC} before turning to ice — {@link #FREEZE_SECONDS_AT_ZERO_C}
     * right at 0°C, scaling linearly down to {@link #FREEZE_SECONDS_AT_MIN}
     * by {@link #FREEZE_COLDEST_C} and colder. {@code roomC >= 0} never
     * freezes at all (returns {@link Integer#MAX_VALUE}).
     */
    private static int freezeRequiredTicks(double roomC) {
        if (roomC >= 0.0) {
            return Integer.MAX_VALUE;
        }
        double coldness = Mth.clamp((float) (roomC / FREEZE_COLDEST_C), 0f, 1f);
        double requiredSeconds = FREEZE_SECONDS_AT_ZERO_C
                - coldness * (FREEZE_SECONDS_AT_ZERO_C - FREEZE_SECONDS_AT_MIN);
        return Math.max(1, (int) Math.round(requiredSeconds * 20.0));
    }

    /**
     * Turns water source blocks in {@link #roomCache} to ice once they've
     * accumulated enough sub-0°C exposure (see {@link #freezeRequiredTicks}).
     * Reuses the room shape as-is between its own {@link #ROOM_RECOMPUTE_INTERVAL_TICKS}
     * recomputes — this only re-checks each cached position's CURRENT fluid
     * state, so a position that already froze (or was drained/filled by a
     * player) is picked up on this method's own next pass, not stale until
     * the next structural flood-fill. Called from {@link #tickHearthEffect},
     * so it inherits that method's own {@link #isWorking()} gate — no
     * separate check needed here.
     */
    private void tickWaterFreeze(ServerLevel serverLevel) {
        long gameTime = serverLevel.getGameTime();
        if (gameTime - freezeScanComputedAtTick < FREEZE_SCAN_INTERVAL_TICKS) {
            return;
        }
        int elapsedTicks = freezeScanComputedAtTick < 0 ? FREEZE_SCAN_INTERVAL_TICKS
                : (int) (gameTime - freezeScanComputedAtTick);
        freezeScanComputedAtTick = gameTime;

        if (insideTempC >= 0.0 || roomCache.isEmpty()) {
            if (!freezeExposureTicks.isEmpty()) {
                freezeExposureTicks.clear();
            }
            return;
        }
        int requiredTicks = freezeRequiredTicks(insideTempC);
        Set<BlockPos> stillWater = new HashSet<>();
        for (BlockPos pos : roomCache) {
            FluidState fluid = serverLevel.getFluidState(pos);
            if (!fluid.is(FluidTags.WATER) || !fluid.isSource()) {
                continue;
            }
            stillWater.add(pos);
            int exposure = freezeExposureTicks.getOrDefault(pos, 0) + elapsedTicks;
            if (exposure >= requiredTicks) {
                serverLevel.setBlockAndUpdate(pos, Blocks.ICE.defaultBlockState());
            } else {
                freezeExposureTicks.put(pos, exposure);
            }
        }
        freezeExposureTicks.keySet().retainAll(stillWater);
    }

    /**
     * @return how many °C this venter's own cooling counts as "full
     * strength" right now — no longer a flat constant. Per the user's own
     * redesign: the motor's Low/Mid/Max setting no longer throttles HOW MUCH
     * cold_air it produces (that's now a flat rate regardless of setting,
     * specifically so a venter's own buffer never runs dry and the room's
     * temperature stops "bouncing" from starvation) — instead the setting
     * now determines HOW COLD that cold_air effectively is once a venter
     * processes it. Looked up from a nearby {@link AirconMotorBottomInfo}'s
     * own {@code getSettingDropC()} — checked via that interface, not either
     * concrete bottom class, so a Power Grid AND an Electro Energetics motor
     * both drive this identically (see {@link AirconMotorBottomInfo}'s own
     * doc) — same "no stored link, just scan nearby chunks" approach used
     * throughout this feature, cached and refreshed only every
     * {@link #DROP_C_SCAN_INTERVAL_TICKS}. Falls back to {@link #DEFAULT_DROP_C}
     * if no motor is found nearby at all, rather than silently cooling not
     * at all.
     */
    private float currentDropC() {
        if (level == null) {
            return cachedDropC;
        }
        long gameTime = level.getGameTime();
        if (gameTime - dropCComputedAtTick < DROP_C_SCAN_INTERVAL_TICKS) {
            return cachedDropC;
        }
        dropCComputedAtTick = gameTime;
        ChunkPos centerChunk = new ChunkPos(worldPosition);
        for (int x = -MOTOR_SCAN_CHUNK_RADIUS; x <= MOTOR_SCAN_CHUNK_RADIUS; x++) {
            for (int z = -MOTOR_SCAN_CHUNK_RADIUS; z <= MOTOR_SCAN_CHUNK_RADIUS; z++) {
                int chunkX = centerChunk.x + x;
                int chunkZ = centerChunk.z + z;
                if (!level.hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                ChunkAccess chunk = level.getChunk(chunkX, chunkZ);
                for (BlockPos bePos : chunk.getBlockEntitiesPos()) {
                    if (chunk.getBlockEntity(bePos) instanceof AirconMotorBottomInfo motor) {
                        cachedDropC = motor.getSettingDropC();
                        cachedSettingFraction = motor.getSettingIntensityFraction();
                        return cachedDropC;
                    }
                }
            }
        }
        cachedDropC = DEFAULT_DROP_C;
        cachedSettingFraction = DEFAULT_SETTING_FRACTION;
        return cachedDropC;
    }

    /**
     * @return the nearby motor's own {@code getSettingIntensityFraction()}
     * (Off=0, Low=0.25, Mid=0.5, Max=1) — refreshed on the exact same cadence
     * and by the exact same scan as {@link #currentDropC()} (calling this
     * first as a side effect, rather than re-scanning independently), used
     * to scale {@link #tickExtraParticles}'s own particle speed so Low/Mid/
     * Max visibly changes how hard the vent appears to be blowing, not just
     * how cold the room ends up.
     */
    private float currentSettingFraction() {
        currentDropC();
        return cachedSettingFraction;
    }

    /** Cached "trapped room" set — see {@link #tickHearthEffect}. Empty until first computed. Seeded from just this one block — unlike the Steam Hearth's multi-segment run, a venter chain is at most 5 adjacent blocks, well within reach of a single flood-fill seed. */
    private Set<BlockPos> roomCache = Set.of();
    private long roomCacheComputedAtTick = -ROOM_RECOMPUTE_INTERVAL_TICKS;
    /** This tick's cooling delta, in Cold Sweat's own "MC" temperature unit (always &lt;= 0) — see {@link #tickHearthEffect} for how it's derived and {@code AirconClimateAmbientTempMixin} for where it's summed with every other nearby source. 0 by default (never read while {@link #isWorking()} is false, which gates every reader). */
    private double coolingOffsetMc = 0.0;
    /** A point just past the room's own boundary — see {@link #computeRoom}'s own doc for how it's picked. Null if no rejected boundary cell was found at all. */
    @Nullable
    private BlockPos outsideReferencePos = null;
    /** This tick's real interior/exterior readings (°C), refreshed on the same cadence as {@link #roomCache} — see {@link RoomClimateComparison}. NaN until first computed. */
    private double insideTempC = Double.NaN;
    private double outsideTempC = Double.NaN;

    /** @return whether this venter is currently doing real work — gates both the Hearth-style cooling below and {@link #isCoolingAt}. */
    private boolean isWorking() {
        return !clogged && activity > WORKING_ACTIVITY_THRESHOLD;
    }

    /**
     * The cooling mirror of {@code RadiatorValveNorthBlockEntity#tickHearthEffect}
     * — same flood-fill-through-open-space "trapped room" mechanic (heat/cold
     * stays trapped by walls, escapes through any opening to the sky), same
     * periodic recompute, same real Cold Sweat mob effect applied directly to
     * every entity found inside (see {@link ColdSweatChillEffect}) rather than
     * a plain distance-based {@code BlockTemp} radius — genuinely "refrigerates
     * the player" the way the real Hearth warms one, just negated. Only
     * reachable at all while {@link #isWorking()} (checked by the caller too,
     * redundantly, on purpose — this method is also the one place the
     * per-tick reapplication actually happens).
     */
    private void tickHearthEffect(ServerLevel serverLevel) {
        if (!isWorking()) {
            return;
        }
        long gameTime = serverLevel.getGameTime();
        if (gameTime - roomCacheComputedAtTick >= ROOM_RECOMPUTE_INTERVAL_TICKS) {
            roomCache = computeRoom(serverLevel);
            roomCacheComputedAtTick = gameTime;
            // Same cadence as the room itself — Cold Sweat's own
            // getRoughTemperatureAt is already internally cached (~10s), so
            // there's no benefit to re-sampling more often than the room
            // shape itself gets re-walked. Only meaningful once Cold Sweat
            // is present (this whole method is only ever called from #tick
            // behind that same check).
            insideTempC = ColdSweatWorldTemp.getWorldTemperatureC(serverLevel, worldPosition);
            outsideTempC = outsideReferencePos != null
                    ? ColdSweatWorldTemp.getWorldTemperatureC(serverLevel, outsideReferencePos)
                    : insideTempC;
        }
        // A pure SUBTRACTIVE delta, not a computed absolute target: "at Low
        // it drops the current world temperature by 6°C" is applied as -6°C
        // on top of whatever the ambient reading already is at the moment
        // AirconClimateAmbientTempMixin runs. This matters once a radiator
        // and a venter can ever overlap the same position — two independent,
        // order-independent deltas (this one negative, the radiator's
        // positive) compose additively into one real number instead of one
        // flatly overwriting whatever the other already wrote. See
        // AirconClimateAmbientTempMixin's own doc.
        // ColdSweatWorldTemp.celsiusToMc (not a direct Temperature.convert
        // call here) — this class loads unconditionally regardless of Cold
        // Sweat being installed, so it can never reference a Cold-Sweat-only
        // type directly; see that class's own doc for the isolation
        // reasoning. Harmless to compute even without Cold Sweat present —
        // nothing reads coolingOffsetMc unless a Cold-Sweat-only mixin asks.
        // (C<->MC conversion has no additive offset — see celsiusToMc's own
        // doc — so calling it on a plain delta is exactly correct, not an
        // approximation.)
        // <p>
        // Deliberately NOT scaled by `activity` anymore (fixed 2026-09-21,
        // a real reported bug: "Low/Mid settings show zero temperature
        // change, only Max works"). Root cause: `activity` reflects REAL
        // pipe throughput, which can genuinely sit well below 1.0 even with
        // ample production, if the actual pump/pipe setup can't quite
        // deliver a full 25 mB/t to this exact venter. At Max's 24°C, even
        // a suppressed activity (say 0.15) still clears
        // RoomClimateComparison's fixed 2°C minimum gap (24 x 0.15 = 3.6°C)
        // — but the SAME suppression on Low's 6°C (6 x 0.15 = 0.9°C) or
        // Mid's 12°C (1.8°C) falls under that same fixed threshold, making
        // weaker settings silently invisible for a reason that has nothing
        // to do with the setting itself. Matches
        // RadiatorValveNorthBlockEntity's own precedent — its own tier-based
        // °C add is likewise NOT scaled by heatFraction, just gated by
        // isWorking() — once a venter is genuinely working at all, it
        // delivers its full rated setting, the same way a real AC compressor
        // that's cycling delivers its set temperature rather than a
        // fraction of it proportional to how saturated the refrigerant line
        // happens to be.
        coolingOffsetMc = ColdSweatWorldTemp.celsiusToMc(-(double) currentDropC());

        // Genuinely below 0°C, not just "colder than outside" — a real
        // freezing effect on the room's own water, independent of the
        // MIN_DELTA_C/chillLivable-style comparisons below (those gate the
        // player-felt chill status; this is a real physical consequence of
        // the room's own absolute temperature).
        tickWaterFreeze(serverLevel);

        tickRoomAirParticles(serverLevel);
        if (gameTime % CHILL_APPLY_INTERVAL_TICKS != 0) {
            return;
        }
        // "A venter cooling an already-scorching desert room down a few
        // degrees shouldn't grant a chill effect if it's still 35°C" — see
        // RoomClimateComparison's own doc — still true, but this is now ONLY
        // consulted for the cosmetic status icon below (showStatusIcon), not
        // for whether the real temperature change happens at all. Gating the
        // real change itself here was the actual bug (see ColdSweatChillEffect's
        // own doc, "third fix"): the SimpleTempModifier this call ends up
        // adding is the ONLY thing that ever touches a player's own felt/HUD
        // temperature (Cold Sweat's position-ambient cache, patched by
        // AirconClimateAmbientTempMixin, is a completely separate system that
        // Cold Sweat's own per-player trait computation never reads) — so
        // skipping this call at Low/Mid in a room that hadn't reached the
        // "comfortable" band yet meant the player's own number never moved,
        // not just that it didn't also feel refreshing.
        boolean livable = RoomClimateComparison.chillLivable(insideTempC, outsideTempC);
        // The REAL delta (already computed above, negative), not an
        // abstract tier number — see ColdSweatChillEffect's own doc for why
        // this replaced an abstract amplifier. Applied to every entity found
        // (players AND mobs alike, whichever happen to currently be in the
        // room) — Cold Sweat stores each entity's own modifier on that
        // entity's own capability (confirmed by reading its real
        // EntityTempManager source), so this is already correct with any
        // number of concurrent players: each one gets their own independent
        // modifier from their own presence in the room, nothing shared or
        // keyed globally.
        AABB searchArea = new AABB(worldPosition).inflate(ROOM_MAX_RANGE);
        for (LivingEntity entity : serverLevel.getEntitiesOfClass(LivingEntity.class, searchArea)) {
            boolean nearVenter = entity.position().closerThan(Vec3.atCenterOf(worldPosition), NEAR_VENTER_RADIUS);
            boolean inRoom = roomCache.contains(entity.blockPosition()) || roomCache.contains(entity.blockPosition().above());
            if (nearVenter || inRoom) {
                ColdSweatChillEffect.apply(entity, coolingOffsetMc, CHILL_DURATION_TICKS, livable);
            }
        }
    }

    /**
     * @return whether {@code pos} is inside this venter's currently-cooled
     * room right now — used by {@code AirconClimateAmbientTempMixin} to
     * decide whether {@link #getCoolingOffsetMc} should contribute to that
     * position's combined delta at all.
     */
    public boolean isCoolingAt(BlockPos pos) {
        if (!isWorking()) {
            return false;
        }
        boolean nearVenter = pos.closerThan(worldPosition, NEAR_VENTER_RADIUS);
        boolean inRoom = roomCache.contains(pos) || roomCache.contains(pos.above());
        return nearVenter || inRoom;
    }

    /**
     * @return the (always &lt;= 0) ambient-temperature delta, in Cold
     * Sweat's "MC" unit, this venter currently subtracts at {@code pos} —
     * 0 if {@link #isCoolingAt} is false. Deliberately a pure additive
     * delta, not an absolute target — see {@link #tickHearthEffect}'s own
     * doc for why, and {@code AirconClimateAmbientTempMixin} for where every
     * nearby venter's and radiator's own delta gets summed into one real,
     * order-independent result.
     */
    public double getCoolingOffsetMc(BlockPos pos) {
        return isCoolingAt(pos) ? coolingOffsetMc : 0.0;
    }

    /**
     * @return how many blocks this venter is currently cooling (0 while not
     * working) — fed into {@code AirconMotorBottomBlockEntity}'s own wattage
     * formula ("even more wattage depending on how many blocks are being
     * cooled"), same nearby-scan approach that class already uses, not a
     * stored link.
     */
    public int getCooledRoomSize() {
        return isWorking() ? roomCache.size() : 0;
    }

    /**
     * Same ambient particle drift as the Steam Hearth's own trapped room (see
     * {@code RadiatorValveNorthBlockEntity#tickHearthAirParticles}), just the
     * real {@code cold_air} particle instead of {@code warm_air} — makes the
     * whole cooled room read as cold, not just wherever the player happens to
     * be standing.
     */
    private void tickRoomAirParticles(ServerLevel serverLevel) {
        if (roomCache.isEmpty()) {
            return;
        }
        RoomClimateComparison.RoomParticle particle = RoomClimateComparison.particleFor(insideTempC, outsideTempC);
        if (particle == RoomClimateComparison.RoomParticle.NONE) {
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
            if (particle == RoomClimateComparison.RoomParticle.COLD) {
                ColdSweatChillEffect.spawnAirParticle(serverLevel, x, y, z, xMotion, zMotion);
            } else {
                ColdSweatWarmthEffect.spawnAirParticle(serverLevel, x, y, z, xMotion, zMotion);
            }
        }
    }

    /**
     * Breadth-first flood-fill through open, passable, non-sky-exposed space
     * — see {@code RadiatorValveNorthBlockEntity#computeRoom}, this is the
     * same algorithm, seeded from just this block. Cold sinks: never expands
     * above {@code worldPosition.getY() + MAX_RISE_ABOVE_VENTER} (no cap
     * downward at all) — the deliberate opposite of the Steam Hearth's own
     * "never below the radiator's floor" rule, so a ground-floor venter
     * cools its own floor (and everything below, in a multi-story build)
     * without reaching more than 10 blocks up, while a top-floor venter
     * reaches every floor beneath it with no limit.
     * <p>
     * Also picks {@link #outsideReferencePos} as a side effect — see
     * {@code RadiatorValveNorthBlockEntity#computeRoom}'s own doc for the
     * exact mechanism (every cell the BFS rejects right at the boundary is
     * recorded for free, one is picked at random, and the reference point is
     * 5 more blocks further in that same outward direction).
     */
    private Set<BlockPos> computeRoom(ServerLevel serverLevel) {
        Set<BlockPos> room = new HashSet<>();
        Deque<BlockPos> frontier = new ArrayDeque<>();
        List<BoundaryStep> boundarySteps = new ArrayList<>();
        room.add(worldPosition);
        frontier.add(worldPosition);
        int ceilingY = worldPosition.getY() + MAX_RISE_ABOVE_VENTER;

        while (!frontier.isEmpty() && room.size() < ROOM_MAX_SIZE) {
            BlockPos current = frontier.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (room.contains(next)) {
                    continue;
                }
                if (!withinRoomRange(next) || next.getY() > ceilingY) {
                    boundarySteps.add(new BoundaryStep(next, direction));
                    continue;
                }
                BlockState state = serverLevel.getBlockState(next);
                if (state.canOcclude() && !state.isAir()) {
                    boundarySteps.add(new BoundaryStep(next, direction));
                    continue;
                }
                if (serverLevel.canSeeSky(next)) {
                    boundarySteps.add(new BoundaryStep(next, direction));
                    continue;
                }
                room.add(next);
                frontier.add(next);
            }
        }

        outsideReferencePos = boundarySteps.isEmpty() ? null
                : boundarySteps.get(serverLevel.random.nextInt(boundarySteps.size())).outward(OUTSIDE_REFERENCE_DISTANCE);
        return room;
    }

    /** How far past a rejected boundary cell {@link #outsideReferencePos} sits — "5 blocks outside the limits" per the design conversation. */
    private static final int OUTSIDE_REFERENCE_DISTANCE = 5;

    /** A boundary cell the flood-fill rejected, plus the direction it was reached from — see {@link #computeRoom}'s own doc. */
    private record BoundaryStep(BlockPos pos, Direction direction) {
        BlockPos outward(int extraDistance) {
            return pos.relative(direction, extraDistance);
        }
    }

    private boolean withinRoomRange(BlockPos pos) {
        int dx = Math.abs(pos.getX() - worldPosition.getX());
        int dy = Math.abs(pos.getY() - worldPosition.getY());
        int dz = Math.abs(pos.getZ() - worldPosition.getZ());
        return Math.max(dx, Math.max(dy, dz)) <= ROOM_MAX_RANGE;
    }

    public boolean isClogged() {
        return clogged;
    }

    @Nullable
    public IFluidHandler getFluidHandler(@Nullable Direction side) {
        if (side == null) {
            return null;
        }
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof AirconVenterBlock)) {
            return null;
        }
        Direction facing = state.getValue(AirconVenterBlock.FACING);
        if (side == AirconVenterBlock.rotate(Direction.EAST, facing)) {
            return eastFace;
        }
        if (side == AirconVenterBlock.rotate(Direction.WEST, facing)) {
            return hotAirOutput;
        }
        return null;
    }

    // --- IAirCurrentSource — the BOTTOM face is always the vent, regardless of this block's own horizontal FACING. ---

    @Override
    @Nullable
    public AirCurrent getAirCurrent() {
        return airCurrent;
    }

    @Override
    @Nullable
    public Level getAirCurrentWorld() {
        return level;
    }

    @Override
    public BlockPos getAirCurrentPos() {
        return worldPosition;
    }

    @Override
    public float getSpeed() {
        // Snapped to exactly 0 below the epsilon (see ACTIVITY_EPSILON's own
        // doc) so Create's own native AirCurrent#tick stops spawning its own
        // particles on the same timeline as this class's own hum/particles,
        // instead of trailing for several extra seconds on a
        // barely-nonzero activity value.
        return activity <= ACTIVITY_EPSILON ? 0f : activity * MAX_FAN_SPEED;
    }

    @Override
    public Direction getAirflowOriginSide() {
        return Direction.DOWN;
    }

    @Override
    @Nullable
    public Direction getAirFlowDirection() {
        return getSpeed() > 0 ? Direction.DOWN : null;
    }

    @Override
    public boolean isSourceRemoved() {
        return isRemoved();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.text("AC Vent (venter)")
                .style(ChatFormatting.WHITE)
                .forGoggles(tooltip);

        boolean overLimit = chainLength > AirconVenterAssembly.MAX_CHAIN;
        boolean relaying = chainLength >= 2 && chainIndexFromWest < AirconVenterAssembly.MAX_CHAIN;
        CreateLang.text("Chain: ")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.number(chainLength)
                        .text(chainLength == 1 ? " (working alone)"
                                : overLimit ? " (exceeds " + AirconVenterAssembly.MAX_CHAIN + ")" : " (relaying)")
                        .style(overLimit ? ChatFormatting.RED : chainLength >= 2 ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                .forGoggles(tooltip, 1);

        if (overLimit && !relaying) {
            CreateLang.text("Warning: ")
                    .style(ChatFormatting.RED)
                    .add(CreateLang.text("Chain longer than " + AirconVenterAssembly.MAX_CHAIN
                            + " — only the first " + AirconVenterAssembly.MAX_CHAIN
                            + " (from the west end) relay; this one is idle for relay purposes")
                            .style(ChatFormatting.RED))
                    .forGoggles(tooltip, 1);
        }

        CreateLang.text("Function: ")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.text("cold_air in → hot_air out (1:2)").style(ChatFormatting.DARK_AQUA))
                .forGoggles(tooltip, 1);

        CreateLang.text("Clogged: ")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.text(clogged ? "Yes — bottom face obstructed" : "No")
                        .style(clogged ? ChatFormatting.RED : ChatFormatting.GREEN))
                .forGoggles(tooltip, 1);

        CreateLang.text("Activity: ")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.number(Math.round(activity * 100)).text("%").style(ChatFormatting.GREEN))
                .forGoggles(tooltip, 1);

        int totalColdDemand = COLD_AIR_CONSUMED_PER_CYCLE + VENTED_COLD_AIR_PER_CYCLE;
        CreateLang.text("Cold Air In: ")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.number(Math.round(totalColdDemand * activity))
                        .text(" / " + totalColdDemand + " mB/t")
                        .style(ChatFormatting.BLUE))
                .forGoggles(tooltip, 1);
        CreateLang.builder()
                .add(CreateLang.text("(" + COLD_AIR_CONSUMED_PER_CYCLE + " -> hot air, " + VENTED_COLD_AIR_PER_CYCLE + " vented)")
                        .style(ChatFormatting.DARK_GRAY))
                .forGoggles(tooltip, 1);
        CreateLang.builder()
                .add(CreateLang.number(coldAirIntake.getFluidAmount())
                        .text(" / " + coldAirIntake.getCapacity() + " mB buffered")
                        .style(ChatFormatting.DARK_GRAY))
                .forGoggles(tooltip, 1);

        CreateLang.text("Hot Air Out: ")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.number(Math.round(HOT_AIR_PRODUCED_PER_CYCLE * activity))
                        .text(" / " + HOT_AIR_PRODUCED_PER_CYCLE + " mB/t")
                        .style(ChatFormatting.GOLD))
                .forGoggles(tooltip, 1);
        CreateLang.builder()
                .add(CreateLang.number(hotAirOutput.getFluidAmount())
                        .text(" / " + hotAirOutput.getCapacity() + " mB buffered")
                        .style(ChatFormatting.DARK_GRAY))
                .forGoggles(tooltip, 1);

        boolean receivingSupply = isReceivingColdAirSupply();
        CreateLang.text("Cold Air Pipe: ")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.text(receivingSupply ? "Delivering" : "Nothing recently received")
                        .style(receivingSupply ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY))
                .forGoggles(tooltip, 1);

        if (ColdSweatCompat.present()) {
            CreateLang.text("Cooling Strength: ")
                    .style(ChatFormatting.GRAY)
                    .add(CreateLang.number(currentDropC()).text("°C max (from nearby motor setting)").style(ChatFormatting.LIGHT_PURPLE))
                    .forGoggles(tooltip, 1);
            if (isWorking()) {
                CreateLang.text("Cooling: ")
                        .style(ChatFormatting.GRAY)
                        .add(CreateLang.text(getCooledRoomSize() + " blocks").style(ChatFormatting.LIGHT_PURPLE))
                        .forGoggles(tooltip, 1);
            }
        }

        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("ColdAirIntake", coldAirIntake.writeToNBT(registries, new CompoundTag()));
        tag.put("HotAirOutput", hotAirOutput.writeToNBT(registries, new CompoundTag()));
        tag.putBoolean("Clogged", clogged);
        tag.putFloat("Activity", activity);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        coldAirIntake.readFromNBT(registries, tag.getCompound("ColdAirIntake"));
        hotAirOutput.readFromNBT(registries, tag.getCompound("HotAirOutput"));
        clogged = tag.getBoolean("Clogged");
        activity = tag.getFloat("Activity");
    }
}
