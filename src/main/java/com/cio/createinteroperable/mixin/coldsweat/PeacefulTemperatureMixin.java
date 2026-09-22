package com.cio.createinteroperable.mixin.coldsweat;

import com.cio.createinteroperable.CreateInteroperable;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cold Sweat's own {@code EntityTempManager.isPeacefulMode} (real source):
 * <pre>{@code
 * return entity.level().getDifficulty() == Difficulty.PEACEFUL && ConfigSettings.USE_PEACEFUL_MODE.get();
 * }</pre>
 * which feeds {@code isImmuneToTemperature} — on a Peaceful-difficulty world
 * (Cold Sweat's own {@code use_peaceful}/{@code nullify_in_peaceful} config
 * defaults to {@code true}), the ENTIRE temperature simulation is skipped for
 * every entity, same as vanilla nullifying starvation damage on Peaceful.
 * <p>
 * Per the user's ask ("temperature should still affect the player even in
 * Peaceful mode, at Normal difficulty rates"): forced to always {@code false}
 * here, so Minecraft's own world difficulty (Peaceful/Easy/Normal/Hard) never
 * gates Cold Sweat's simulation at all. Cold Sweat's own SEPARATE, unrelated
 * "difficulty" concept — {@code ConfigSettings.DIFFICULTY}, an internal
 * SUPER_EASY/EASY/NORMAL/HARD/CUSTOM rate multiplier, defaults to
 * {@code NORMAL} and is untouched by this mixin — so this alone already
 * delivers "still affects the player, at Normal-difficulty rates" with
 * nothing else to change.
 * <p>
 * <b>Targeted by string, not {@code EntityTempManager.class}</b> — this
 * config is optional/{@code required: false} (Cold Sweat is a soft
 * dependency), and a direct class-literal {@code @Mixin} value forces the JVM
 * to resolve that type just to read this mixin's own annotation, bypassing
 * the config's {@code required: false} soft-skip and throwing
 * {@code NoClassDefFoundError} on a Cold-Sweat-absent install — the exact
 * same failure mode confirmed (and fixed) on {@code ThermalBehaviourMixin},
 * see that class's own doc.
 */
@Mixin(targets = "com.momosoftworks.coldsweat.common.capability.handler.EntityTempManager")
public abstract class PeacefulTemperatureMixin {
    @Inject(
            method = "isPeacefulMode(Lnet/minecraft/world/entity/LivingEntity;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void createinteroperable$neverPeaceful(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!createinteroperable$logged) {
            createinteroperable$logged = true;
            CreateInteroperable.LOGGER.info(
                    "[Create: Interoperable] Cold Sweat EntityTempManager#isPeacefulMode mixin active (temperature simulation no longer nullified on Peaceful worlds)");
        }
        cir.setReturnValue(false);
    }

    @Unique
    private static boolean createinteroperable$logged = false;
}
