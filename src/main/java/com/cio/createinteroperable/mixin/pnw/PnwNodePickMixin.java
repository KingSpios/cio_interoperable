package com.cio.createinteroperable.mixin.pnw;

import com.cio.createinteroperable.compat.PnwNodePicker;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets CEE's node hover/selection find PnW cantilever contact-wire tips. */
@Mixin(value = InWorldNode.class, remap = false)
abstract class PnwNodePickMixin {
    @Inject(method = "getHitNode", at = @At("RETURN"), cancellable = true, remap = false)
    private static void cio$pickCantileverTips(BlockHitResult hit, ClientLevel level, CallbackInfoReturnable<InWorldNode> cir) {
        InWorldNode tip = PnwNodePicker.pick(level, cir.getReturnValue());
        if (tip != null) {
            cir.setReturnValue(tip);
        }
    }
}
