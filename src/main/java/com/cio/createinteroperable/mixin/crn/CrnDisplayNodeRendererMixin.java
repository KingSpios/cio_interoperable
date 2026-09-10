package com.cio.createinteroperable.mixin.crn;

import com.cio.createinteroperable.grid.CrayfishClient;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Create Train Navigator registers its own block-entity renderer for the
 * Advanced Display, so Crayfish's {@code ElectricBlockEntityRenderer} &mdash;
 * which draws the wrench-node marker, the hover box and the connection lines
 * &mdash; is never attached. Once the display is an {@code IElectricityNode}
 * ({@link CrnDisplayCrayfishMixin}) this routes it through the exact static
 * helper the Domestic Electrical Board's own renderer calls, so it gets the
 * identical node visuals. Drawing is deferred and only happens while a wrench is
 * held, so this is nearly free otherwise.
 *
 * <p>Injected at {@code render} HEAD (before Create Train Navigator's own
 * controller-only early-return) so the marker shows on every block of a board,
 * not just its controller. Client, name-targeted, Crayfish-gated &mdash; a no-op
 * without either mod.</p>
 */
@Mixin(targets = "de.mrjulsen.crn.client.ber.AdvancedDisplayRenderInstance", remap = false)
public abstract class CrnDisplayNodeRendererMixin {

    @Unique private BlockEntity cioNodeRender$be;

    @Inject(
            method = "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lde/mrjulsen/crn/block/blockentity/AdvancedDisplayBlockEntity;)V",
            at = @At("HEAD"), remap = false, require = 0)
    private void cioNodeRender$capture(Level level, BlockPos pos, BlockState state,
                                       @Coerce BlockEntity blockEntity, CallbackInfo ci) {
        this.cioNodeRender$be = blockEntity;
    }

    @Inject(method = "render(Lde/mrjulsen/mcdragonlib/client/ber/BERGraphics;F)V",
            at = @At("HEAD"), remap = false, require = 0)
    private void cioNodeRender$drawNode(CallbackInfo ci) {
        BlockEntity be = this.cioNodeRender$be;
        if (be != null && !be.isRemoved()) {
            CrayfishClient.drawDebNodeOverlay(be);
        }
    }
}
