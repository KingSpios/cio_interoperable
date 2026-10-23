package com.cio.createinteroperable.mixin.pnw;

import com.cio.createinteroperable.compat.PnwCeeWireBridge;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.UUID;

/**
 * Mirrors PnW's authoritative graph lifecycle into the optional CEE backend.
 *
 * <p>PnW is not on the compile classpath, so its {@code WireEdge} parameter is
 * taken as {@code @Coerce Object}: without {@code @Coerce} Mixin rejects the
 * handler's descriptor and (the config being optional) silently skips the
 * whole mixin.</p>
 */
@Mixin(targets = "de.mrjulsen.wires.graph.WireGraph", remap = false)
abstract class PnwWireGraphMixin {
    @Inject(method = "updateEdge", at = @At("TAIL"), remap = false)
    private void cio$mirrorUpdatedEdge(@Coerce Object edge, boolean notifyClients, CallbackInfo ci) {
        PnwCeeWireBridge.upsert(this, edge);
    }

    @Inject(method = "removeEdge", at = @At("HEAD"), remap = false)
    private void cio$removeMirroredEdge(UUID id, Vector3d position, Optional<Player> player, CallbackInfo ci) {
        PnwCeeWireBridge.remove(this, id);
    }
}
