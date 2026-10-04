package com.cio.createinteroperable.mixin.pnw;

import com.cio.createinteroperable.compat.PnwHiddenWires;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps CEE from drawing (or offering for interaction) the copy of a PnW wire
 * that the bridge mirrors into its circuit. PnW already renders that wire; only
 * a CEE cable that reaches a native CEE connector should show up. The decision
 * itself, including wires that arrive before their chunks, is in
 * {@link PnwHiddenWires}.
 */
@Mixin(targets = "com.george_vi.electroenergetics.client.WireRenderer", remap = false)
abstract class PnwWireRendererMixin {
    @Inject(method = "addConnection", at = @At("HEAD"), cancellable = true, remap = false)
    private static void cio$hideMirroredWire(InWorldNodeConnection connection, WireData data, CallbackInfo ci) {
        if (PnwHiddenWires.intercept(connection, data)) {
            ci.cancel();
        }
    }

    @Inject(method = "removeConnections", at = @At("HEAD"), remap = false)
    private static void cio$forgetParkedWire(InWorldNodeConnection connection, CallbackInfo ci) {
        PnwHiddenWires.forget(connection);
    }

    @Inject(method = "clearAllWireConnections", at = @At("HEAD"), remap = false)
    private static void cio$clearParkedWires(CallbackInfo ci) {
        PnwHiddenWires.clear();
    }
}
