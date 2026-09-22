package com.cio.createinteroperable.compat;

import com.momosoftworks.coldsweat.api.temperature.modifier.SimpleTempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.api.util.placement.Matcher;
import com.momosoftworks.coldsweat.api.util.placement.Placement;
import com.momosoftworks.coldsweat.core.init.ModEffects;
import com.momosoftworks.coldsweat.core.init.ModParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
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
 * Fixed by calling {@code Temperature.addModifier} directly ourselves,
 * instead of depending on an event firing as a side effect of a cosmetic
 * status icon.
 * <p>
 * <b>Second fix (2026-09-21), corrected from a real reported symptom — "the
 * airconditioner was simply not affecting the player"</b>: this used
 * {@code WarmthTempModifier(int strength)}, which — confirmed by reading its
 * real source, {@code ThermalSourceTempModifier#calculate} — only applies its
 * {@code warming} value on the branch where the player's CURRENT world-trait
 * reading is already BELOW the midpoint of {@code MIN_TEMP}/{@code MAX_TEMP};
 * on the branch where they're already above it, {@code cooling} (hardcoded to
 * 0 on this class) is used instead, i.e. a literal no-op. That's the correct,
 * intentional design for Cold Sweat's own real Hearth (an "insulation
 * strength" that nudges you toward comfortable from whichever side you're
 * on), but wrong for what CIO actually wants here: a real radiator/venter
 * genuinely raises or lowers the coordinate's temperature by a known, exact
 * amount — unconditionally, not "only if you're currently on the correct
 * side of some unrelated config midpoint" — the same unconditional additive
 * semantics {@code AirconClimateAmbientTempMixin} already uses for every
 * position-based ambient read. Switched to {@code SimpleTempModifier}
 * (confirmed real source: {@code calculate} returns {@code temp -> temp +
 * value} for {@code Operation.ADD}, no branching at all) with the SAME real
 * °C-derived delta the ambient mixin sums in, converted to MC by the caller —
 * so the player's own felt body temperature and Cold Sweat's own
 * world-temperature HUD gauge ({@code Overlays}, reading
 * {@code cap.getTrait(Temperature.Trait.WORLD)} — a persistent per-player
 * capability value, a completely different mechanism from the position-query
 * methods the rest of this project's Cold Sweat work touches) now track the
 * exact same number a Thermometer at that position would show, instead of an
 * abstract 1/2/3 "strength" run through a curve that could silently do
 * nothing depending on ambient conditions the player has no visibility into.
 * <p>
 * <b>Known remaining gap</b>: if two distinct CIO sources (e.g. a radiator
 * AND a venter, or two radiators) both currently want to affect the same
 * entity, each call here wipes ANY existing {@code SimpleTempModifier}
 * before adding its own (needed so a single source's own 20-tick
 * reapplication doesn't stack duplicates of itself) — which means whichever
 * source's reapplication runs last in a given 20-tick window currently wins,
 * not a genuine sum of both. Fixing that properly needs one centralized,
 * per-player application (summing every nearby source's delta before ever
 * touching {@code Temperature.addModifier}, mirroring
 * {@code AirconClimateAmbientTempMixin}'s own multi-source scan) instead of
 * N independent per-source calls — not implemented yet; flagging honestly
 * rather than silently leaving it to look "fixed" for every scenario.
 * <p>
 * <b>Third fix (2026-09-21) — same root cause and fix shape as
 * {@link ColdSweatChillEffect}'s own identical third fix</b>: the caller
 * ({@link RadiatorValveNorthBlockEntity#tickHearthEffect}) used to gate this
 * ENTIRE method behind {@code RoomClimateComparison#warmthLivable}. Since
 * the {@code SimpleTempModifier} below is the ONLY thing in this codebase
 * that ever touches a player's own felt/HUD temperature (confirmed by
 * reading real Cold Sweat source — the player's {@code WORLD} trait is built
 * from its own position-ticked modifier chain, which never reads
 * {@code WorldHelper}'s position-query methods {@code AirconClimateAmbientTempMixin}
 * patches), that gate meant the player's own number never moved at all on a
 * weak tier in a room that hadn't reached "comfortable" yet — not just that
 * it didn't also grant WARMTH. Split into an unconditional real change plus
 * an optionally-shown icon, same shape as the chill side.
 */
public final class ColdSweatWarmthEffect {
    private ColdSweatWarmthEffect() {
    }

    /**
     * @param deltaMc the REAL additive warming delta, in Cold Sweat's own
     * "MC" temperature unit — pass the exact same value that feeds
     * {@code AirconClimateAmbientTempMixin}'s position-based ambient sum
     * (e.g. {@code ColdSweatWorldTemp.celsiusToMc(WARM_ADD_C)}), not an
     * abstract tier number — see this class's own doc for why that
     * distinction is load-bearing now.
     * @param showStatusIcon whether to also grant the visible {@code WARMTH}
     * status icon this cycle — the real temperature change below always
     * applies regardless of this flag (see class doc's third fix).
     */
    public static void apply(LivingEntity entity, double deltaMc, int durationTicks, boolean showStatusIcon) {
        if (showStatusIcon) {
            // Visible status icon — matches the real Hearth's own player-facing
            // feedback. Purely cosmetic scaling of the numeral shown; the real
            // temperature math below no longer depends on this value at all.
            int iconAmplifier = Mth.clamp((int) Math.round(Math.abs(deltaMc) * 2.0) - 1, 0, 2);
            entity.addEffect(new MobEffectInstance(ModEffects.WARMTH, durationTicks, iconAmplifier, false, false, true));
        }

        // The actual warming — a plain, unconditional additive shift (see
        // class doc for why SimpleTempModifier, not WarmthTempModifier, and
        // why this call itself must never be gated).
        // Replace any previous instance rather than stacking, so this
        // source's own periodic reapplication doesn't accumulate duplicates
        // of itself (see class doc's "known remaining gap" for the
        // multi-source caveat this doesn't yet solve).
        Temperature.removeModifiers(entity, Temperature.Trait.WORLD, SimpleTempModifier.class);
        Temperature.addModifier(entity,
                new SimpleTempModifier(deltaMc, SimpleTempModifier.Operation.ADD).expires(durationTicks),
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
