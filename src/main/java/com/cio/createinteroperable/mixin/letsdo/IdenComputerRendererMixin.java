package com.cio.createinteroperable.mixin.letsdo;

import com.cio.createinteroperable.CIOConfig;
import com.cio.createinteroperable.grid.ApplianceNode;
import com.cio.createinteroperable.grid.CrayfishClient;
import com.cio.createinteroperable.grid.CrayfishCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Iden's Decor's {@code ComputerBlockRenderer} is the only thing that shows a
 * floppy disk's text; this makes it draw nothing while the computer is
 * unpowered ({@link IdenComputerNodeMixin}). It also stands in for Crayfish's
 * own {@code ElectricBlockEntityRenderer}, which Iden's renderer displaces:
 * the wrench-node marker and connection lines are drawn first, so they show
 * precisely when the screen is dark (same shape as {@code VistaTvRendererMixin}).
 * Without Crayfish the native grid draws nodes itself.
 *
 * <p>Client, name-targeted, non-required &mdash; a no-op without Iden's Decor.</p>
 */
@Mixin(targets = "net.identidade.iden_decor.blockentity.renderer.ComputerBlockRenderer", remap = false)
public abstract class IdenComputerRendererMixin {

    @Inject(
            method = "render(Lnet/identidade/iden_decor/blockentity/ComputerBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void cio$gateOnPower(@Coerce BlockEntity blockEntity, float partialTick, PoseStack poseStack,
                                 MultiBufferSource buffer, int light, int overlay, CallbackInfo ci) {
        if (CrayfishCompat.present()) {
            CrayfishClient.drawNodeOverlayIfNode(blockEntity);
        }
        if (CIOConfig.IDEN_COMPUTER_REQUIRE_POWER.get()
                && (Object) blockEntity instanceof ApplianceNode node && !node.appliancePowered()) {
            ci.cancel();
        }
    }
}
