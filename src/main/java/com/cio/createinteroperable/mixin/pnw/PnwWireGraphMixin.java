package com.cio.createinteroperable.mixin.pnw;

import com.cio.createinteroperable.compat.PnwCeeWireBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/** Mirrors PnW's authoritative graph lifecycle into the optional CEE backend. */
@Mixin(targets = "de.mrjulsen.wires.graph.WireGraph", remap = false)
abstract class PnwWireGraphMixin {
    @Inject(method = "updateEdge", at = @At("TAIL"), remap = false)
    private void cio$mirrorUpdatedEdge(Object edge, boolean notifyClients, CallbackInfo ci) {
        PnwCeeWireBridge.upsert(this, edge);
    }

    @Inject(method = "removeEdge", at = @At("HEAD"), remap = false)
    private void cio$removeMirroredEdge(UUID id, Object position, Object player, CallbackInfo ci) {
        PnwCeeWireBridge.remove(this, id);
    }
}
