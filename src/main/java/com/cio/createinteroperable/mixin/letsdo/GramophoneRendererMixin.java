package com.cio.createinteroperable.mixin.letsdo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mrcrayfish.furniture.refurbished.client.renderer.blockentity.ElectricBlockEntityRenderer;
import com.mrcrayfish.furniture.refurbished.electricity.IElectricityNode;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Let's Do Furniture registers its own {@code GramophoneRenderer} for the
 * gramophone BE type, so Crayfish's {@code ElectricBlockEntityRenderer} &mdash;
 * which draws the wrench-node marker, the hover highlight box and (from this
 * end) the connection lines &mdash; is never attached. Once the gramophone is an
 * {@code IElectricityNode} ({@link GramophoneBlockEntityMixin}) this routes it
 * through the exact static helper CIO's own Power Kit renderer calls, so it gets
 * the identical node visuals. Drawing is deferred and only happens while a
 * wrench is held, so this is nearly free otherwise.
 *
 * <p>Client, name-targeted, non-required &mdash; a no-op without Let's Do Furniture.
 */
@Mixin(targets = "com.berksire.furniture.client.render.block.GramophoneRenderer", remap = false)
public abstract class GramophoneRendererMixin {

    @Inject(
            method = "render(Lcom/berksire/furniture/core/block/entity/GramophoneBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At("HEAD"), require = 0, remap = false)
    private void cio$drawElectricNode(BlockEntity blockEntity, float partialTicks, PoseStack poseStack,
                                      MultiBufferSource bufferSource, int light, int overlay, CallbackInfo ci) {
        if (blockEntity instanceof IElectricityNode node) {
            ElectricBlockEntityRenderer.drawNodeAndConnections(node);
        }
    }
}
