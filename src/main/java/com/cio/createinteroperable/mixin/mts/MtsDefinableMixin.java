package com.cio.createinteroperable.mixin.mts;

import com.cio.createinteroperable.mts.IvPoleLights;
import com.cio.createinteroperable.mts.MtsAaSearchlights;
import minecrafttransportsimulator.entities.components.AEntityD_Definable;
import minecrafttransportsimulator.jsondefs.JSONAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The AA Spotlight and pole-light gates that live on Immersive Vehicles' shared
 * definable-entity base class:
 * <ul>
 *   <li>{@code performAction} (server only &mdash; every clicked button, lever
 *       and custom keybind lands here): refuse switching the beam or
 *       auto-rotate on without power.</li>
 *   <li>{@code updateLightBrightness} (client, just before the model draws):
 *       black out every light of an unpowered Spotlight, standby LED included,
 *       and of a street light / traffic signal on an unpowered pole
 *       ({@link IvPoleLights}).</li>
 * </ul>
 * Both are no-ops for every other entity.
 */
@Mixin(value = AEntityD_Definable.class, remap = false)
public abstract class MtsDefinableMixin {

    @Inject(method = "performAction", at = @At("HEAD"), cancellable = true, remap = false)
    private void cio$refuseUnpoweredSpotlight(JSONAction action, boolean conditionsTrue, CallbackInfo ci) {
        if (MtsAaSearchlights.blocksAction((AEntityD_Definable<?>) (Object) this, action, conditionsTrue)) {
            ci.cancel();
        }
    }

    @Inject(method = "updateLightBrightness", at = @At("TAIL"), remap = false)
    private void cio$darkenUnpoweredSpotlight(float partialTicks, CallbackInfo ci) {
        AEntityD_Definable<?> self = (AEntityD_Definable<?>) (Object) this;
        MtsAaSearchlights.dimLights(self);
        IvPoleLights.dimLights(self);
    }
}
