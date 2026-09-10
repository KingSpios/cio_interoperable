package com.cio.createinteroperable.mixin.crn;

import com.mrcrayfish.furniture.refurbished.electricity.IElectricityNode;
import com.mrcrayfish.furniture.refurbished.item.WrenchItem;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets the Crayfish wrench link a node that lives behind a <b>full block shape</b>
 * &mdash; Create Train Navigator's Advanced Display being the case that motivated
 * this.
 *
 * <p>{@code WrenchItem#useOn} returns {@link InteractionResult#SUCCESS}
 * unconditionally, which swallows the interaction before vanilla can fall through
 * to {@code WrenchItem#use()} where the actual node raycast + link lives. For a
 * Crayfish appliance that's a small model the wrench ray simply misses the block
 * (so {@code useOn} is never reached and {@code use()} runs), but a display is an
 * opaque full cube: every click lands on the block face, {@code useOn} eats it,
 * and no link can be made from the front.</p>
 *
 * <p>When the clicked block carries an {@link IElectricityNode}, return
 * {@code PASS} instead so vanilla proceeds to {@code use()} &mdash; whose own
 * {@code performNodeRaycast} re-checks that the crosshair is actually on the node
 * box and no-ops otherwise, so this only ever <em>enables</em> a legitimate link,
 * never forces one. Crayfish-gated, non-required.</p>
 */
@Mixin(WrenchItem.class)
public abstract class CrnWrenchNodeClickMixin {

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void cio$passNodeBlocksToUse(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        BlockEntity be = context.getLevel().getBlockEntity(context.getClickedPos());
        if (be instanceof IElectricityNode) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }
}
