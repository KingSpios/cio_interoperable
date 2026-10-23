package com.cio.createinteroperable.mixin.mts;

import mcinterface1211.WrapperWorld;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Immersive Vehicles' world wrapper keeps its vanilla {@link Level} protected; CIO needs it to place the plate's node. */
@Mixin(value = WrapperWorld.class, remap = false)
public interface WrapperWorldAccessor {

    @Accessor(value = "world", remap = false)
    Level cio$getLevel();
}
