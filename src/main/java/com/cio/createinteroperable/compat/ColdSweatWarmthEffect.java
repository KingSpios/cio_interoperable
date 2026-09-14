package com.cio.createinteroperable.compat;

import com.momosoftworks.coldsweat.api.temperature.modifier.WarmthTempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.api.util.placement.Matcher;
import com.momosoftworks.coldsweat.api.util.placement.Placement;
import com.momosoftworks.coldsweat.core.init.ModEffects;
import com.momosoftworks.coldsweat.core.init.ModParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * The ONLY reference in this codebase to Cold Sweat's real {@code
 * ModEffects.WARMTH} mob effect and its temperature-modifier machinery —
 * isolated the same way as {@link ColdSweatWorldTemp}/{@link ColdSweatHeatTemp}
 * so always-loaded classes (like RadiatorValveNorthBlockEntity) never touch a
 * Cold-Sweat-only type directly. Callers MUST check
 * {@link ColdSweatCompat#present()} before calling in; this class is only
 * ever actually loaded once that's true.
 * <p>
 * Originally this only did the first half below (apply the mob effect) on the
 * assumption that Cold Sweat's own temperature pipeline read its
 * presence/amplifier directly — it does not. Reading Cold Sweat's real
 * source found the actual chain: the real Hearth's {@code insulateEntity}
 * applies the exact same {@code ModEffects.WARMTH} mob effect, and a
 * SEPARATE listener ({@code EntityTempManager#onInsulationAdded}, subscribed
 * to {@code MobEffectEvent.Added}) is what converts that into a real
 * {@code WarmthTempModifier} added via {@code Temperature.addModifier}. That
 * listener only reliably fires on a genuinely NEW effect application — not
 * confirmed to refire on refreshing an already-active one — and this class is
 * called every ~1 second to keep the effect topped up, which is exactly the
 * "refresh, not fresh-add" case. So the actual temperature change could
 * quietly never land (or lapse) even while the status icon looked fine.
 * <p>
 * Fixed by calling {@code Temperature.addModifier} directly ourselves, the
 * same way that listener does internally, instead of depending on an event
 * firing as a side effect of a cosmetic status icon.
 */
public final class ColdSweatWarmthEffect {
    private ColdSweatWarmthEffect() {
    }

    public static void apply(LivingEntity entity, int amplifier, int durationTicks) {
        // Visible status icon — matches the real Hearth's own player-facing feedback.
        entity.addEffect(new MobEffectInstance(ModEffects.WARMTH, durationTicks, amplifier, false, false, true));

        // The actual warming — see class doc for why this can't be left to
        // happen implicitly via the mob effect alone. Mirrors
        // EntityTempManager#onInsulationAdded's own real logic exactly:
        // strength = amplifier + 1, replace any existing WarmthTempModifier
        // rather than stacking, expire it alongside the status effect.
        int strength = amplifier + 1;
        Temperature.removeModifiers(entity, Temperature.Trait.WORLD, WarmthTempModifier.class);
        Temperature.addModifier(entity, new WarmthTempModifier(strength).expires(durationTicks),
                Temperature.Trait.WORLD, Placement.LAST.noDuplicates(Matcher.SAME_CLASS));
    }

    /**
     * The same ambient {@code warm_air} particle Cold Sweat's own Hearth
     * drifts around inside its trapped room (see its
     * {@code HearthBlockEntity#spawnAirParticle}) — a slow, gentle rise with
     * a little horizontal drift, spawned at a random point within the given
     * cell. Server-broadcast via {@code sendParticles} with count=0 (an exact
     * velocity, not a random spread — same convention used elsewhere in this
     * project's own particle code) rather than the real Hearth's client-only
     * {@code addParticle}, since we don't have a client-side room to render
     * this from.
     */
    public static void spawnAirParticle(ServerLevel serverLevel, double x, double y, double z, double xMotion, double zMotion) {
        serverLevel.sendParticles(ModParticleTypes.WARM_AIR.get(), x, y, z, 0, xMotion, 0.0, zMotion, 1.0);
    }
}
