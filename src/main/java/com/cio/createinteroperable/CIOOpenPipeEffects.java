package com.cio.createinteroperable;

import com.cio.createinteroperable.compat.ColdSweatCompat;
import com.cio.createinteroperable.compat.ColdSweatWarmthEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * The white campfire-style smoke a genuinely open/dangling pipe end vents,
 * scaled by the real valve fraction of whichever Steam Outlet is actually
 * feeding it — found and triggered by {@link SteamOpenEndScanner}'s own
 * pipe-network walk, NOT by Create's {@code OpenPipeEffectHandler} hook.
 * <p>
 * A previous revision of this class DID register through that hook (Create's
 * public, non-mixin extension point for exactly this — water extinguishing
 * fire, milk clearing potions, etc.). It had to be abandoned, and this is
 * important enough to spell out so nobody re-adds it later: reading Create's
 * real {@code OpenEndedPipe.OpenEndFluidHandler#fill} directly shows that for
 * ANY fluid with {@code OpenPipeEffectHandler.REGISTRY.get(fluid) != null}
 * AND no placeable block form (steam has neither a block NOR should have
 * one — see {@link CIOFluids}'s own doc), the resource handed to
 * {@code super.fill(...)} is unconditionally replaced with
 * {@code copyWithAmount(1)} BEFORE the real mass-transfer accounting runs:
 * <pre>
 * if (effectHandler != null &amp;&amp; !hasBlockState)
 *     resource = FluidHelper.copyStackWithAmount(resource, 1);
 * int fill = super.fill(resource, action);
 * </pre>
 * This is not merely "no real volume signal for the effect callback" (the
 * conclusion an earlier pass drew) — the truncated {@code resource} IS what
 * gets filled and IS what the return value (how much the caller/network
 * believes was actually accepted, and therefore how much it drains from the
 * real source) reports. In other words: simply being registered in this
 * registry, for a blockless fluid, hard-caps EVERY real transfer through
 * EVERY open pipe end to exactly 1 mB per {@code fill()} call — regardless of
 * pipe pressure, Pipes n Physics' own conductance/viscosity solve, or
 * anything else upstream. This is Create's own deliberate design for the
 * registry's intended use (a discrete one-shot trigger, not a volume-scaled
 * mechanic) — confirmed as the literal, sole, sourced cause of a real
 * reported bug ("pipes hold 250mb, deplete at exactly 1mb/tick, forever, no
 * matter what else changes") once traced end to end.
 * <p>
 * Un-registering restores real, uncapped (well, capped at the pipe's own
 * 1000mb buffer per call, not 1) transfer — {@code fill()} without a
 * registered handler still calls {@code provideFluidToSpace} +
 * {@code setFluid(EMPTY)} for a blockless fluid immediately after accepting
 * it, so steam is still faithfully "accepted, then vented to atmosphere,
 * gone" with zero extra code, exactly the flavor originally intended — real
 * volume just moves now. The cost is losing the automatic per-call trigger
 * for this particle effect, replaced by {@link SteamOpenEndScanner}'s own
 * direct pipe-network walk from each Steam Outlet instead — which is a
 * strictly better signal anyway: the real originating valve fraction,
 * not a frequency-derived guess.
 */
final class CIOOpenPipeEffects {
    private CIOOpenPipeEffects() {
    }

    /** Matches SteamOutletBlockEntity's own leak-warmth numbers — see its class doc for why. */
    private static final int WARMTH_AMPLIFIER = 1;
    private static final int WARMTH_DURATION_TICKS = 20;
    private static final double WARMTH_RADIUS = 2.0;

    /** Rise speed in blocks/tick — see SteamOutletBlockEntity's own pair for why this alone gives the "taller at higher pressure" effect. */
    private static final double LOW_RISE_SPEED = 0.015;
    private static final double HIGH_RISE_SPEED = 0.06;
    /** Particles spawned per puff. */
    private static final int LOW_PARTICLE_COUNT = 1;
    private static final int HIGH_PARTICLE_COUNT = 6;
    /** Floor so a near-closed valve still reads as a faint, unmistakably-low-pressure puff rather than nothing — same philosophy as SteamOutletBlockEntity's own MIN_LEAK_FRACTION. */
    private static final double MIN_FRACTION = 0.15;

    private static double lerp(double low, double high, double fraction) {
        return low + (high - low) * fraction;
    }

    /**
     * Spawns one puff at the open mouth {@code pipePos.relative(openFace)} and applies Cold Sweat
     * warmth there, scaled by {@code valveFraction} (0..1, the REAL originating Outlet's valve
     * position — see {@link SteamOpenEndScanner}). Called once per discovered open end each scan
     * pass, so the scan's own cadence supplies the "how often" — no separate per-position interval
     * needed here.
     */
    static void ventAt(ServerLevel serverLevel, BlockPos pipePos, Direction openFace, double valveFraction) {
        double fraction = Mth.clamp(Math.max(MIN_FRACTION, valveFraction), 0.0, 1.0);
        BlockPos mouth = pipePos.relative(openFace);

        boolean underwater = serverLevel.getFluidState(mouth).is(FluidTags.WATER);
        double riseSpeed = lerp(LOW_RISE_SPEED, HIGH_RISE_SPEED, fraction);
        int count = (int) Math.round(lerp(LOW_PARTICLE_COUNT, HIGH_PARTICLE_COUNT, fraction));

        for (int i = 0; i < count; i++) {
            double x = mouth.getX() + 0.5 + (serverLevel.random.nextDouble() - 0.5) * 0.4;
            double y = mouth.getY() + 0.5 + (serverLevel.random.nextDouble() - 0.5) * 0.4;
            double z = mouth.getZ() + 0.5 + (serverLevel.random.nextDouble() - 0.5) * 0.4;
            serverLevel.sendParticles(underwater ? ParticleTypes.BUBBLE : CIOParticles.RADIATOR_SMOKE.get(),
                    x, y, z, 0, 0.0, riseSpeed, 0.0, 1.0);
        }
        if (ColdSweatCompat.present()) {
            AABB warmArea = new AABB(mouth).inflate(WARMTH_RADIUS);
            for (LivingEntity entity : serverLevel.getEntitiesOfClass(LivingEntity.class, warmArea)) {
                ColdSweatWarmthEffect.apply(entity, WARMTH_AMPLIFIER, WARMTH_DURATION_TICKS, true);
            }
        }
    }
}
