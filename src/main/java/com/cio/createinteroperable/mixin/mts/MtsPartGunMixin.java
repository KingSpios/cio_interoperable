package com.cio.createinteroperable.mixin.mts;

import com.cio.createinteroperable.mts.MtsAaSearchlights;
import minecrafttransportsimulator.entities.instances.PartGun;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * An unpowered AA Spotlight doesn't traverse or elevate: every aim path in
 * {@code PartGun} &mdash; a seated gunner, an AI controller, the drift back to
 * the default angles &mdash; goes through {@code handleMovement}, and the
 * {@code spin} auto-rotate modifier is already held at 0 by the plate. Runs on
 * both sides (the gate reads a synced IV variable), so client and server agree
 * and the beam never snaps.
 */
@Mixin(value = PartGun.class, remap = false)
public abstract class MtsPartGunMixin {

    @Inject(method = "handleMovement(DD)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void cio$lockUnpoweredSpotlight(double deltaYaw, double deltaPitch, CallbackInfo ci) {
        if (MtsAaSearchlights.isLockedOut((PartGun) (Object) this)) {
            ci.cancel();
        }
    }
}
