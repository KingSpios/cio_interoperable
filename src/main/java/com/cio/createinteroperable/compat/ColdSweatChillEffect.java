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
 * The cooling mirror of {@link ColdSweatWarmthEffect}.
 * <p>
 * <b>First historical note, corrected 2026-09-20</b>: this class originally
 * claimed Cold Sweat has no separate cooling {@code TempModifier} and worked
 * around that by negating a {@code WarmthTempModifier}'s strength — that
 * claim was wrong ({@code FrigidnessTempModifier} genuinely exists as its
 * own class), and the workaround was also silently broken (see git history
 * for the full trace). Fixed at the time by switching to the real
 * {@code FrigidnessTempModifier(int strength)}.
 * <p>
 * <b>Second fix (2026-09-21), corrected from a real reported symptom — "the
 * airconditioner was simply not affecting the player"</b>: even the real
 * {@code FrigidnessTempModifier} turned out wrong for what CIO actually
 * wants here. Confirmed by reading {@code ThermalSourceTempModifier#calculate}:
 * its {@code cooling} value only applies on the branch where the player's
 * CURRENT world-trait reading is already ABOVE the midpoint of
 * {@code MIN_TEMP}/{@code MAX_TEMP} — correct, intentional design for Cold
 * Sweat's own real Hearth (nudge toward comfortable from whichever side
 * you're on), but wrong for a venter that genuinely lowers a coordinate's
 * temperature by a known, exact amount regardless of whether the player
 * happens to already be on the "warm" side of some unrelated config
 * midpoint. In a normal-temperature room, that branch may simply never
 * fire, and the effect does nothing even though the status icon shows
 * happily the whole time. Switched to {@code SimpleTempModifier}
 * (real source confirmed: {@code Operation.ADD} calculates {@code temp ->
 * temp + value}, unconditionally, no branching) with the SAME real
 * °C-derived delta ({@link AirconVenterBlockEntity#getCoolingOffsetMc})
 * that already feeds {@code AirconClimateAmbientTempMixin}'s position-based
 * ambient sum — same reasoning, same fix shape as
 * {@link ColdSweatWarmthEffect}'s own identical change; see that class's own
 * doc for the full explanation of why this now also makes the player's felt
 * temperature track Cold Sweat's own world-temperature HUD gauge
 * consistently, and for the known multi-source-clobbering gap this doesn't
 * yet solve.
 * <p>
 * Status icon now uses the real, distinct {@code ModEffects.FRIGIDNESS}
 * (confirmed to genuinely exist, alongside {@code WARMTH}/{@code GRACE}/
 * {@code ICE_RESISTANCE} — the original doc's claim that Cold Sweat has no
 * separate cold-side effect was wrong on this count too) instead of reusing
 * {@code WARMTH} at amplifier 0.
 * <p>
 * <b>Third fix (2026-09-21) — the real cooling was still gated behind
 * {@code RoomClimateComparison#chillLivable}, one layer further out (see
 * {@link AirconVenterBlockEntity#tickHearthEffect}), which reproduced the
 * exact same "not affecting the player" symptom this class's own second fix
 * above already believed it had solved.</b> Read closely: the player's own
 * felt/displayed temperature (Cold Sweat's {@code Overlays} HUD gauge, backed
 * by {@code cap.getTrait(Temperature.Trait.WORLD)} — confirmed by reading
 * real Cold Sweat source, {@code EntityTempManager}) is built from a chain of
 * {@code TempModifier}s (biome/elevation/block/etc.) ticked directly from the
 * player's own position — it does NOT read {@code WorldHelper}'s
 * position-query methods {@code AirconClimateAmbientTempMixin} patches at
 * all. So the {@code SimpleTempModifier} this class adds is the ONLY thing in
 * this codebase that ever touches that number — meaning gating the call to
 * this method behind {@code chillLivable} didn't gate "whether the player
 * also gets a bonus on top of an already-correct reading," it gated whether
 * the reading itself ever changed. At Low/Mid, in a room warmer than
 * roughly {@code STATUS_CHILL_CEILING_C + dropC}, {@code chillLivable} fails
 * and the player's own UI number never moved at all. Fixed by splitting this
 * method's two actions: the real {@code SimpleTempModifier} now applies
 * unconditionally (the caller no longer gates the call on livability — see
 * that class's own doc), while {@code showStatusIcon} gates only the cosmetic
 * {@code FRIGIDNESS} icon, matching the design intent: the source of truth
 * always moves by the setting's own flat degree count; whether that's ALSO
 * "comfortable enough to grant a status" is a separate, later decision.
 */
public final class ColdSweatChillEffect {
    private ColdSweatChillEffect() {
    }

    /**
     * @param deltaMc the REAL additive cooling delta, in Cold Sweat's own
     * "MC" temperature unit — already negative (a cooling drop), e.g.
     * {@link AirconVenterBlockEntity}'s own {@code coolingOffsetMc} field —
     * pass it straight through, not an abstract tier number.
     * @param showStatusIcon whether to also grant the visible {@code
     * FRIGIDNESS} status icon this cycle — the real temperature change below
     * always applies regardless of this flag (see class doc's third fix).
     */
    public static void apply(LivingEntity entity, double deltaMc, int durationTicks, boolean showStatusIcon) {
        if (showStatusIcon) {
            // Visible status icon — matches the real Hearth's own player-facing feedback.
            int iconAmplifier = Mth.clamp((int) Math.round(Math.abs(deltaMc) * 2.0) - 1, 0, 2);
            entity.addEffect(new MobEffectInstance(ModEffects.FRIGIDNESS, durationTicks, iconAmplifier, false, false, true));
        }

        // The actual cooling — a plain, unconditional additive shift (see
        // class doc for why SimpleTempModifier, not FrigidnessTempModifier,
        // and why this call itself must never be gated).
        Temperature.removeModifiers(entity, Temperature.Trait.WORLD, SimpleTempModifier.class);
        Temperature.addModifier(entity,
                new SimpleTempModifier(deltaMc, SimpleTempModifier.Operation.ADD).expires(durationTicks),
                Temperature.Trait.WORLD, Placement.LAST.noDuplicates(Matcher.SAME_CLASS));
    }

    /**
     * Cold Sweat's own real {@code cold_air} ambient particle (the cold-fuel
     * counterpart to {@code warm_air} — see {@link ColdSweatWarmthEffect#spawnAirParticle}
     * for the exact same call shape this mirrors) — confirmed as a real,
     * separately registered particle type in {@code ModParticleTypes}, not
     * reused/tinted from the warm one.
     */
    public static void spawnAirParticle(ServerLevel serverLevel, double x, double y, double z, double xMotion, double zMotion) {
        serverLevel.sendParticles(ModParticleTypes.COLD_AIR.get(), x, y, z, 0, xMotion, 0.0, zMotion, 1.0);
    }
}
