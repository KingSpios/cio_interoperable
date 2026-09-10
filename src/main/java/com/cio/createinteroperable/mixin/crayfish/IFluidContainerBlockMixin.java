package com.cio.createinteroperable.mixin.crayfish;

import com.cio.createinteroperable.CIOConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Removes Refurbished Furniture's "water from nowhere" at the source.
 *
 * <p>{@code tryAndFillWithFluid} is a {@code default} method on RF's
 * {@code IFluidContainerBlock} interface, called from the {@code interact} of
 * RF's kitchen sink / bathroom sink / bath / toilet whenever their
 * {@code dispenseWater} option is on (the default) — it fills the fixture's own
 * tank with a free bucket of water. Any held item that isn't a bucket or bottle
 * falls through RF's earlier checks and reaches it, so right-clicking with a
 * block was still a free tap.
 *
 * <p>Cancelling the method here (returning PASS) whenever
 * {@link CIOConfig#RF_REQUIRE_SUPPLY} is set closes that for every caller and
 * every held item — and supersedes RF's {@code dispenseWater} option, which is
 * never consulted while the gate is on. The bare-hand "pull from an adjacent
 * supply" behaviour lives in {@link RefurbishedFixtureTapMixin}; real
 * bucket/bottle emptying runs earlier in {@code interact} and never reaches
 * this method.
 *
 * <p>Crayfish hard dependency: gated by {@link CrayfishMixinPlugin}; a no-op
 * when Refurbished Furniture is absent.
 */
@Mixin(targets = "com.mrcrayfish.furniture.refurbished.blockentity.fluid.IFluidContainerBlock", remap = false)
public interface IFluidContainerBlockMixin {

    @Inject(method = "tryAndFillWithFluid", at = @At("HEAD"), cancellable = true, remap = false)
    private void createinteroperable$noFreeWater(Level level, BlockPos pos, Fluid fluid, Vec3 splashPos,
                                                 CallbackInfoReturnable<InteractionResult> cir) {
        if (CIOConfig.RF_REQUIRE_SUPPLY.get()) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }
}
