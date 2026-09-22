package com.cio.createinteroperable;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.IAirCurrentSource;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

/**
 * Reads {@link AirconMotorBottomInfo#getSmoothedPerformance()} straight from
 * the block 1 below (a plain {@code SmartBlockEntity}, not an
 * {@code ElectricBlockEntity} — this half has no PG terminals or CEE nodes of
 * its own, matching {@code aircon_motor_top.json}, which has zero
 * {@code power_pin_*} elements). Electrical-backend-agnostic by design: it
 * only ever checks {@code instanceof AirconMotorBottomInfo}, never a
 * concrete bottom class, so the exact same top half sits on top of either
 * the Power Grid-wired {@link AirconMotorBottomBlockEntity} or the Electro
 * Energetics-wired {@code CeeAirconMotorBottomBlockEntity} with no
 * duplication needed. Performance (voltage ramp — see
 * {@link AirconMotorBottomInfo}'s own doc for why the setting doesn't factor
 * in here) drives blade animation speed (see {@link #getBladeAngleDegrees},
 * rendered by {@link AirconMotorTopRenderer}), a hum voice, and hot air
 * output through the TOP/UP face.
 * <p>
 * Genuinely implements Create's own {@link IAirCurrentSource} (same as
 * {@link AirconVenterBlockEntity} — see that class's doc for the general
 * shape), with UP as the fixed vent direction: entities standing above get
 * genuinely blown by a real Create air current and hear its own "caught in
 * the wind" sound, all for free from that one interface. On top of Create's
 * own particle spawn, {@link #tickExtraParticles} adds explicit,
 * performance-scaled extra particles (the same real {@code AirFlowParticleData}
 * type, plus a layered {@code ParticleTypes.LAVA} touch so this reads as
 * visibly "hot" rather than identical to the venter's own grey air current).
 */
public class AirconMotorTopBlockEntity extends SmartBlockEntity implements IAirCurrentSource, IHaveGoggleInformation {
    /** Cosmetic RPM-like ceiling fed into Create's IAirCurrentSource#getMaxDistance formula — full throttle at performance = 1. */
    private static final float MAX_FAN_SPEED = 128f;
    private static final int AIR_CURRENT_REBUILD_INTERVAL = 8;
    private static final int ENTITY_SEARCH_INTERVAL = 5;
    private static final int EXTRA_PARTICLE_BASE_INTERVAL = 10;
    /** Degrees/tick the blades sweep at performance = 1 — purely cosmetic, tuned for a fast but readable spin. */
    private static final float MAX_BLADE_DEGREES_PER_TICK = 42f;
    /**
     * Below this, treated as fully stopped — matches
     * {@code AirconMotorBottomBlockEntity#tickAudio}'s own {@code 0.01f}
     * threshold. {@link #getPerformance()} is a smoothed EMA (see
     * {@code AirconMotorBottomBlockEntity#smoothedPerformance}), which decays
     * toward 0 but, mathematically, never hits an exact float {@code 0f} for
     * many seconds — an earlier {@code performance <= 0f} check here (and the
     * equally exact {@code getSpeed() != 0} gate on Create's own native
     * {@code AirCurrent#tick}) let a barely-perceptible but genuinely nonzero
     * particle/hum/wind tail keep going for up to ~10s after the motor lost
     * power, well after the bottom half's own hum had already gone silent —
     * a real reported "doesn't stop completely" symptom. {@link #getSpeed()}
     * now snaps to exactly 0 below this threshold too, so Create's own native
     * air current (and its own particle spawning) stops on the same
     * timeline as everything else here.
     */
    private static final float PERFORMANCE_EPSILON = 0.01f;
    /** Real ticks a full flap open/close sweep takes — "2 second animation length" at 20 tps. */
    private static final int FLAP_ANIMATION_TICKS = 40;
    /** {@link #flapOpenFraction}'s own step per tick, a plain linear ramp (not an EMA — the request was for a fixed 2s duration, not an asymptotic approach). */
    private static final float FLAP_STEP_PER_TICK = 1f / FLAP_ANIMATION_TICKS;

    private final AirCurrent airCurrent = new AirCurrent(this);
    private int airCurrentCooldown = 0;
    private int entitySearchCooldown = 0;
    /** Accumulated blade angle, ticked server- and client-side alike (purely cosmetic, never synced) — see {@link #getBladeAngleDegrees}. */
    private float bladeAngleDegrees = 0f;
    /**
     * 0 (flaps flat/closed) .. 1 (flaps at their full open angle) — see
     * {@link AirconMotorTopRenderer} for the per-flap target angles. Ramped
     * linearly over {@link #FLAP_ANIMATION_TICKS} toward {@link #isPoweredOn()}'s
     * target, client-side only (same "purely cosmetic, never NBT-synced"
     * reasoning as {@link #bladeAngleDegrees} — the underlying setting is
     * already synced via the bottom's own Create behaviour, so the client can
     * derive this independently with no extra packet).
     */
    private float flapOpenFraction = 0f;

    public AirconMotorTopBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // Purely driven by the paired bottom's electrical state below — no
        // independent behaviours of its own.
    }

    /** 0 (unpowered / not assembled / bottom missing) .. 1 (running at the bottom's 120 V optimum on Max) — already smoothed by the bottom, see {@link AirconMotorBottomInfo#getSmoothedPerformance()}. Works with EITHER electrical backend's bottom half (see that interface's own doc for why this class never references either concrete bottom class). */
    public float getPerformance() {
        if (level == null) {
            return 0f;
        }
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof AirconMotorTopBlock) || !state.getValue(AirconMotorTopBlock.ASSEMBLED)) {
            return 0f;
        }
        if (level.getBlockEntity(worldPosition.below()) instanceof AirconMotorBottomInfo bottom) {
            return bottom.getSmoothedPerformance();
        }
        return 0f;
    }

    /**
     * @return whether the paired bottom half is genuinely receiving hot_air
     * back from the venter loop right now (not just spinning/powered) — see
     * {@link AirconMotorBottomBlockEntity#isReceivingHotAirSupply}. Gates
     * {@link #tickExtraParticles}'s orange ember tint: the fan itself blows
     * (and shows the plain grey air-current particle) purely from being
     * powered, matching a real fan's own mechanical function, but the HOT
     * tint specifically should only appear while there's real hot_air being
     * recirculated to actually make it hot.
     */
    private boolean isHotAirRecirculating() {
        if (level == null) {
            return false;
        }
        return level.getBlockEntity(worldPosition.below()) instanceof AirconMotorBottomInfo bottom
                && bottom.isReceivingHotAirSupply();
    }

    /**
     * @return whether the paired bottom's slider is on any setting other
     * than Off — see {@link AirconMotorBottomBlockEntity#isPoweredOn()} for
     * why this is the switch position, not live voltage/efficiency. Drives
     * {@link #flapOpenFraction}'s own target; deliberately NOT gated on
     * {@link #getPerformance()} being nonzero — the flaps open "if the motor
     * is on, in any setting, as long as it's not Off," which is exactly this,
     * not "if it's actually drawing current right now."
     */
    private boolean isPoweredOn() {
        if (level == null) {
            return false;
        }
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof AirconMotorTopBlock) || !state.getValue(AirconMotorTopBlock.ASSEMBLED)) {
            return false;
        }
        return level.getBlockEntity(worldPosition.below()) instanceof AirconMotorBottomInfo bottom
                && bottom.isPoweredOn();
    }

    /**
     * @return {@link #flapOpenFraction} as of the last committed tick (see
     * {@link #tick()}) — 0..1. Unlike {@link #getBladeAngleDegrees}, this
     * deliberately does NOT extrapolate by {@code partialTicks}: a 2-second
     * linear ramp already steps in increments too small to read as choppy
     * without it, so the renderer just reads this value directly.
     */
    public float getFlapOpenFraction() {
        return flapOpenFraction;
    }

    /**
     * @return the blade sweep angle (degrees, unbounded/accumulating) to
     * render this frame — {@link AirconMotorTopRenderer} interpolates between
     * this and the previous tick's value using {@code partialTicks}, same
     * idea as Create's own kinetic angle interpolation. Ticked every client
     * tick (see {@link #tick}) regardless of {@link #getPerformance()} being
     * server-authoritative — purely cosmetic, never NBT-synced.
     */
    public float getBladeAngleDegrees(float partialTicks) {
        return bladeAngleDegrees + MAX_BLADE_DEGREES_PER_TICK * getPerformance() * partialTicks;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) {
            return;
        }

        // AirCurrent needs both sides ticking independently — mirrors
        // EncasedFanBlockEntity's own tick(), not gated behind one
        // isClientSide check. rebuild() itself is safe to call at speed 0
        // (it just clears maxDistance/segments/bounds and returns), but
        // Create's own AirCurrent#rebuild only ever assigns `direction`
        // inside its speed != 0 branch — at speed 0 it stays null (forever,
        // if never previously assigned), and AirCurrent#tick's client-side
        // particle-position code dereferences it UNCONDITIONALLY
        // (direction.getNormal()), so calling tick() here on a still-unlit
        // motor is a guaranteed NPE. Create's own EncasedFanBlockEntity
        // dodges this by never calling airCurrent.tick() at all while
        // getSpeed() == 0 (confirmed by reading its real source) — same
        // gate applied here, not something to "fix" upstream.
        float performance = getPerformance();
        if (airCurrentCooldown-- <= 0) {
            airCurrentCooldown = AIR_CURRENT_REBUILD_INTERVAL;
            airCurrent.rebuild();
        }
        if (getSpeed() != 0) {
            airCurrent.tick();
        }

        if (level.isClientSide) {
            bladeAngleDegrees = (bladeAngleDegrees + MAX_BLADE_DEGREES_PER_TICK * performance) % 360f;
            float flapTarget = isPoweredOn() ? 1f : 0f;
            if (flapOpenFraction < flapTarget) {
                flapOpenFraction = Math.min(flapTarget, flapOpenFraction + FLAP_STEP_PER_TICK);
            } else if (flapOpenFraction > flapTarget) {
                flapOpenFraction = Math.max(flapTarget, flapOpenFraction - FLAP_STEP_PER_TICK);
            }
            return;
        }
        if (performance <= PERFORMANCE_EPSILON) {
            return;
        }
        if (entitySearchCooldown-- <= 0) {
            entitySearchCooldown = ENTITY_SEARCH_INTERVAL;
            airCurrent.findEntities();
        }
        if (level instanceof ServerLevel serverLevel) {
            tickHum(performance);
            tickExtraParticles(serverLevel, performance);
        }
    }

    /**
     * Plain vanilla-server-broadcast sound, deliberately NOT
     * {@code org.patryk3211.powergrid.utility.sound.SoundScapes} (PG's own
     * real ambient-sound-MIXING engine, confirmed by decompiling it — not a
     * simple SoundEvent wrapper, genuinely PG-only machinery) — this class
     * must work when powered by either electrical backend (see
     * {@link AirconMotorBottomInfo}'s own doc), including a CEE-only world
     * with Power Grid entirely absent, so it can never reference a PG type
     * at all. Throttled to roughly once per second rather than called every
     * tick (PG's own mixer is designed to be topped up every tick; a plain
     * {@code playSound} call retriggered that often would just stack
     * overlapping copies of the same short clip).
     */
    private void tickHum(float performance) {
        if (level == null || (level.getGameTime() & 19) != 0) {
            return;
        }
        float pitch = 0.8f + 0.2f * performance;
        // Quartered per direct feedback that the aircon_motor's sounds were
        // "way too loud" — same 0.25x applied to the bottom half's own hum
        // (both electrical variants) and its failure explosion.
        float volume = (0.3f + 0.7f * performance) * 0.25f;
        level.playSound(null, worldPosition, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, volume, pitch);
    }

    /**
     * Streams straight up out of the TOP/UP face (count=0 / literal-velocity
     * convention, same as SteamOutletBlockEntity#tickSteamParticles elsewhere
     * in this codebase) — denser and faster the higher the combined
     * voltage/setting performance is, layered with a warm orange
     * {@link #hotEmberDust} so this specific output still reads as "hot",
     * not identical to the venter's cold intake side.
     * <p>
     * <b>The base layer is a plain vanilla {@link ParticleTypes#CLOUD}, NOT
     * Create's own {@code AirFlowParticleData}</b> (what this used before) —
     * confirmed by reading Create's real {@code AirFlowParticle} source: its
     * {@code tick()} completely IGNORES whatever velocity is passed to
     * {@code sendParticles} and instead recomputes its own motion every tick
     * from {@code source.getAirCurrent()}'s real {@code direction}/
     * {@code maxDistance}/{@code pushing}, and calls {@code remove()} outright
     * the instant the particle's position isn't inside
     * {@code airCurrent.bounds.inflate(0.25f)} — a real, confirmed cause of
     * particles not being visible at all, not just moving at the wrong
     * speed (see {@code AirconVenterBlockEntity#tickExtraParticles}'s own
     * doc, which hit the identical bug — this class's own base layer was
     * only ever masked from the same symptom by {@link #hotEmberDust}, a
     * genuinely different, already-correct vanilla particle type, riding
     * along on top of it). {@code riseSpeed} now actually controls how fast
     * it rises, same as {@code emberRiseSpeed} already did for the ember
     * layer.
     * <p>
     * Originally layered the literal {@code ParticleTypes.LAVA} blob particle
     * on top — reported as reading "over the top lava smoke" rather than a
     * subtle hot-air tint, since that particle is a chunky orange droplet
     * animation, not a soft color wash. Replaced with a plain vanilla
     * {@code DustParticleOptions} tint instead — see {@link #hotEmberDust}'s
     * own doc for why this, rather than Create's real fan-over-lava color
     * morph, is what actually gets reused here. A real Cold
     * Sweat/CPG-thermal-driven "warm air" effect (varying by biome/weather)
     * is deferred to a later integration phase.
     * <p>
     * The ember tint is gated on {@link #isHotAirRecirculating} — real
     * feedback that "hot" is a matter of genuine returning hot_air, not just
     * the fan spinning (which, on its own, still shows the plain grey base
     * particle — the fan mechanically moves air whether or not anything is
     * actually flowing back into the motor). Its own rise speed scales
     * harder with fan speed than the base particle's does (2x performance's
     * own contribution, not half) per a real report that it read as barely
     * rising at all at high fan speed.
     */
    private void tickExtraParticles(ServerLevel serverLevel, float performance) {
        long ticksSinceEpoch = serverLevel.getGameTime() + worldPosition.hashCode();
        int interval = Math.max(1, Math.round(EXTRA_PARTICLE_BASE_INTERVAL / Math.max(performance, 0.05f)));
        if (ticksSinceEpoch % interval != 0) {
            return;
        }
        int count = 1 + Math.round(performance * 3);
        double riseSpeed = 0.03 + performance * 0.08;
        boolean hotAirRecirculating = isHotAirRecirculating();
        double emberRiseSpeed = riseSpeed + performance * 0.15;
        for (int i = 0; i < count; i++) {
            double x = worldPosition.getX() + 0.5 + (serverLevel.random.nextDouble() - 0.5) * 0.4;
            double y = worldPosition.getY() + 0.95;
            double z = worldPosition.getZ() + 0.5 + (serverLevel.random.nextDouble() - 0.5) * 0.4;
            serverLevel.sendParticles(ParticleTypes.CLOUD, x, y, z, 0, 0.0, riseSpeed, 0.0, 1.0);
            if (hotAirRecirculating) {
                serverLevel.sendParticles(hotEmberDust(serverLevel), x, y, z, 0, 0.0, emberRiseSpeed, 0.0, 1.0);
            }
        }
    }

    /**
     * The same warm orange Create's own Encased Fan tints its real
     * {@code AirFlowParticleData} stream with when blowing over lava — see
     * {@code AllFanProcessingTypes.BlastingType#morphAirFlow},
     * {@code Color.mixColors(0xFF4400, 0xFF8855, random)}. That whole
     * mechanism only ever activates when a genuine lava block sits in a
     * genuine Create {@code AirCurrent}'s scan path (Create's own
     * {@code AirFlowParticle} recolors ITSELF client-side by asking
     * {@code source.getAirCurrent().getTypeAt(distance)} for a matching
     * {@code FanProcessingType} every tick — confirmed by reading its real
     * source — there is no way to just hand it a color) — this vent has
     * neither a registered processing type nor real lava behind it, so
     * reusing that system isn't an option. Reproduced here instead as a
     * plain vanilla {@code DustParticleOptions} (server-spawnable, no client
     * particle registration needed) blended between the exact same two hex
     * colors Create uses, so the top face always reads as venting something
     * hot regardless of what's physically above it.
     */
    private static ParticleOptions hotEmberDust(ServerLevel serverLevel) {
        float t = serverLevel.random.nextFloat();
        float g = Mth.lerp(t, 0x44 / 255f, 0x88 / 255f);
        float b = Mth.lerp(t, 0x00 / 255f, 0x55 / 255f);
        return new DustParticleOptions(new Vector3f(1.0f, g, b), 1.0f);
    }

    // --- IAirCurrentSource — the TOP/UP face is always the vent. ---

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
        float performance = getPerformance();
        // Snapped to exactly 0 below the epsilon (see PERFORMANCE_EPSILON's
        // own doc) so Create's own native AirCurrent#tick — gated on
        // getSpeed() != 0, an exact check this class doesn't control — stops
        // spawning its own particles on the same timeline as everything else
        // here, instead of trailing for several extra seconds on a
        // barely-nonzero performance value.
        return performance <= PERFORMANCE_EPSILON ? 0f : performance * MAX_FAN_SPEED;
    }

    @Override
    public Direction getAirflowOriginSide() {
        return Direction.UP;
    }

    @Override
    @Nullable
    public Direction getAirFlowDirection() {
        return getSpeed() > 0 ? Direction.UP : null;
    }

    @Override
    public boolean isSourceRemoved() {
        return isRemoved();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.text("AC Unit Fan (top)")
                .style(ChatFormatting.WHITE)
                .forGoggles(tooltip);

        BlockState state = getBlockState();
        boolean assembled = state.getBlock() instanceof AirconMotorTopBlock
                && state.getValue(AirconMotorTopBlock.ASSEMBLED);
        CreateLang.text("Assembled: ")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.text(assembled ? "Yes" : "No")
                        .style(assembled ? ChatFormatting.GREEN : ChatFormatting.RED))
                .forGoggles(tooltip, 1);

        float performance = getPerformance();
        CreateLang.text("Performance: ")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.number(Math.round(performance * 100))
                        .text("%")
                        .style(ChatFormatting.GREEN))
                .forGoggles(tooltip, 1);

        CreateLang.text("Airflow: ")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.text(performance > 0f ? "Venting hot air" : "Idle")
                        .style(performance > 0f ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY))
                .forGoggles(tooltip, 1);

        if (!assembled) {
            CreateLang.text("Requires: ")
                    .style(ChatFormatting.GRAY)
                    .add(CreateLang.text("Wrench together with an AC Unit Motor (bottom)").style(ChatFormatting.RED))
                    .forGoggles(tooltip, 1);
        } else if (performance <= 0f) {
            CreateLang.text("Requires: ")
                    .style(ChatFormatting.GRAY)
                    .add(CreateLang.text("Power at the AC Unit Motor (bottom)").style(ChatFormatting.RED))
                    .forGoggles(tooltip, 1);
        }

        return true;
    }
}
