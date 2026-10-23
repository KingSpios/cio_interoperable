package com.cio.createinteroperable.mixin.pnw;

import com.cio.createinteroperable.compat.PnwNodePicker;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.detached_nodes.DetachedNodeHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A cantilever's contact-wire node hangs in the air, away from any block the
 * player can click, so the spool must address it the way CEE addresses its own
 * free-floating nodes: by sending the node itself when the player right-clicks.
 * Only the wire-spool branch of the tick is redirected (ordinal 1); the
 * wrench branch (ordinal 0) deletes detached nodes and is left alone.
 */
@Mixin(targets = "com.george_vi.electroenergetics.content.wire_spool.WireApplyingBehaviour", remap = false)
abstract class PnwWireApplyingMixin {
    @Redirect(method = "tick", remap = false, at = @At(value = "INVOKE", ordinal = 1, remap = false,
            target = "Lcom/george_vi/electroenergetics/simulation/infrastructure/detached_nodes/DetachedNodeHelper;isDetached(Lcom/george_vi/electroenergetics/foundation/nodes/InWorldNode;)Z"))
    private static boolean cio$targetCantileverTips(InWorldNode node) {
        return DetachedNodeHelper.isDetached(node) || PnwNodePicker.isCantileverNode(node);
    }
}
