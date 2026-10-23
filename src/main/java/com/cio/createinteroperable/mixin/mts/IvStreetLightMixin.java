package com.cio.createinteroperable.mixin.mts;

import com.cio.createinteroperable.mts.IvPoleLights;
import minecrafttransportsimulator.blocks.tileentities.instances.TileEntityPole_StreetLight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * An unpowered street light lights nothing: IV's pole block turns its lamps'
 * {@code getLightProvided()} into the block's real light level every tick, so
 * returning 0 here darkens the world around it, not just the model.
 */
@Mixin(value = TileEntityPole_StreetLight.class, remap = false)
public abstract class IvStreetLightMixin {

    @Inject(method = "getLightProvided", at = @At("HEAD"), cancellable = true, remap = false)
    private void cio$noLightUnpowered(CallbackInfoReturnable<Float> cir) {
        if (IvPoleLights.blocksWorldLight((TileEntityPole_StreetLight) (Object) this)) {
            cir.setReturnValue(0.0F);
        }
    }
}
