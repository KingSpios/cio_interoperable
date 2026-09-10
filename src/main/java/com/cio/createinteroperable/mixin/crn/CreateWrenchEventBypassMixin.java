package com.cio.createinteroperable.mixin.crn;

import com.mrcrayfish.furniture.refurbished.electricity.IElectricityNode;
import com.mrcrayfish.furniture.refurbished.item.WrenchItem;
import com.simibubi.create.content.equipment.wrench.WrenchEventHandler;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops Create's {@link WrenchEventHandler} from hijacking the <b>MrCrayfish
 * wrench</b> when it is used on an electricity node.
 *
 * <p>{@code WrenchEventHandler} is a {@code RightClickBlock} listener that fires
 * {@code IWrenchable#onWrenched} for <em>any</em> item in the {@code c:tools/wrench}
 * tag &mdash; the Crayfish wrench included &mdash; and then unconditionally
 * cancels the event. On a Create Train Navigator display that both opens the
 * display settings screen <em>and</em> kills the interaction before Crayfish's
 * {@code WrenchItem#use()} node-link logic can run.</p>
 *
 * <p>Scoped as tight as possible: only the Crayfish wrench, and only when the
 * clicked block actually carries an {@link IElectricityNode}. Create's own
 * wrench (a separate {@code WrenchItem#useOn} path), every other wrench, and
 * every non-node block are untouched. Crayfish-gated, non-required.</p>
 */
@Mixin(WrenchEventHandler.class)
public abstract class CreateWrenchEventBypassMixin {

    @Inject(method = "useOwnWrenchLogicForCreateBlocks", at = @At("HEAD"), cancellable = true, remap = false)
    private static void cio$letCrayfishWrenchLinkNodes(PlayerInteractEvent.RightClickBlock event, CallbackInfo ci) {
        if (event.getItemStack().getItem() instanceof WrenchItem
                && event.getLevel().getBlockEntity(event.getPos()) instanceof IElectricityNode) {
            ci.cancel();
        }
    }
}
