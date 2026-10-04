package com.cio.createinteroperable.mixin.mts;

import com.cio.createinteroperable.mts.MtsAaSearchlights;
import minecrafttransportsimulator.entities.instances.APart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * End of every Immersive Vehicles part's tick (every subclass calls
 * {@code super.update()}): lets a ground-placed AA Base Plate keep its power
 * node and publish its power state (see {@link MtsAaSearchlights#tickPart}).
 */
@Mixin(value = APart.class, remap = false)
public abstract class MtsPartTickMixin {

    @Inject(method = "update", at = @At("TAIL"), remap = false)
    private void cio$aaBaseTick(CallbackInfo ci) {
        MtsAaSearchlights.tickPart((APart) (Object) this);
    }
}
