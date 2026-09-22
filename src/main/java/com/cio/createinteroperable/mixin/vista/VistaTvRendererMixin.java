package com.cio.createinteroperable.mixin.vista;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mrcrayfish.furniture.refurbished.client.renderer.blockentity.ElectricBlockEntityRenderer;
import com.mrcrayfish.furniture.refurbished.electricity.IElectricityNode;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vista registers its own {@code TvBlockEntityRenderer} for the TV, so
 * Crayfish's {@code ElectricBlockEntityRenderer} &mdash; which draws the
 * wrench-node marker, the hover highlight box and (from this end) the
 * connection lines &mdash; is never attached. Once the TV is an {@code
 * IElectricityNode} ({@link VistaTvBlockEntityMixin}) this routes it through
 * the exact static helper CIO's own Power Kit renderer calls, so it gets the
 * identical node visuals. Same shape as {@code GramophoneRendererMixin}.
 *
 * <p>Injected at HEAD, <em>before</em> Vista's own {@code
 * if (!blockEntity.isScreenOn(partialTick)) return;} early-out &mdash; the
 * marker (and Crayfish's look-at "Missing power" label, which depends on this
 * node actually being reachable/visible while wrenching) needs to show
 * precisely when the screen is off, which is the case a player actually needs
 * it for.</p>
 *
 * <p>Client, name-targeted, Crayfish-gated &mdash; a no-op without either mod.
 */
@Mixin(targets = "net.mehvahdjukaar.vista.client.renderer.TvBlockEntityRenderer", remap = false)
public abstract class VistaTvRendererMixin {

    @Inject(
            method = "render(Lnet/mehvahdjukaar/vista/common/tv/TVBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At("HEAD"), require = 0, remap = false)
    private void cio$drawElectricNode(@Coerce BlockEntity blockEntity, float partialTick, PoseStack poseStack,
                                      MultiBufferSource buffer, int light, int overlay, CallbackInfo ci) {
        if (blockEntity instanceof IElectricityNode node) {
            ElectricBlockEntityRenderer.drawNodeAndConnections(node);
        }
    }
}
