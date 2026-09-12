package com.cio.createinteroperable.mixin.letsdo;

import com.cio.createinteroperable.CIOConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;

/**
 * Caps Bibliocraft's Fancy Lantern at a vanilla Soul Lantern's light level
 * (10) at most, whichever gold/iron/clear/colored/soul flavor it is.
 *
 * <p>Reading Bibliocraft's own registration ({@code BCBlocks.java}): the
 * "regular" gold/iron Fancy Lanterns are registered at a full 15 (matching
 * vanilla's Lantern), while the "soul" flavors already register at 10
 * (matching Soul Lantern). Clamping every Fancy Lantern state to
 * {@code <= 10} is therefore a real nerf for the 15 ones and a no-op for the
 * already-10 ones &mdash; no need to tell the two families apart.
 *
 * <p>The cached light value every block reads (including the light engine's
 * hot path) lives on {@code BlockBehaviour.BlockStateBase#getLightEmission()},
 * not on {@code Block} itself, so that's what this targets &mdash; same shape as
 * {@code LampEmissiveMixin}'s {@code emissiveRendering} cap. Since this runs on
 * every block in the game, the gate is a cached {@code Set<Block>} lookup
 * (built once from a registry scan), not a per-call class-name string
 * comparison.
 *
 * <p>Name-targeted (by class name, not a real import), non-required &mdash; a
 * no-op without Bibliocraft.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BibliocraftLanternLightMixin {

    /** A vanilla Soul Lantern's light level — the cap. */
    private static final int SOUL_LANTERN_LIGHT = 10;

    private static final String FANCY_LANTERN_CLASS =
            "com.github.minecraftschurlimods.bibliocraft.content.fancylight.FancyLanternBlock";

    @Unique
    private static Set<Block> cio$fancyLanterns;

    @Unique
    private static boolean cio$isFancyLantern(Block block) {
        Set<Block> cached = cio$fancyLanterns;
        if (cached == null) {
            cached = new HashSet<>();
            for (Block candidate : BuiltInRegistries.BLOCK) {
                if (candidate.getClass().getName().equals(FANCY_LANTERN_CLASS)) {
                    cached.add(candidate);
                }
            }
            cio$fancyLanterns = cached;
        }
        return cached.contains(block);
    }

    @Inject(method = "getLightEmission()I", at = @At("RETURN"), cancellable = true, require = 0)
    private void cio$capFancyLanternLight(CallbackInfoReturnable<Integer> cir) {
        // Block/BlockState construction (and thus this getter) can run during
        // registry setup, before NeoForge has loaded configs — e.g. Amendments'
        // WallLanternBlock constructor queries light emission at construction
        // time. Reading CIOConfig this early throws
        // "Cannot get config value before config is loaded.", so bail until it is.
        if (!CIOConfig.SPEC.isLoaded()) {
            return;
        }
        if (cir.getReturnValue() > SOUL_LANTERN_LIGHT
                && CIOConfig.BIBLIOCRAFT_LANTERN_LIGHT_CAP.get()
                && cio$isFancyLantern(((BlockState) (Object) this).getBlock())) {
            cir.setReturnValue(SOUL_LANTERN_LIGHT);
        }
    }
}
