package com.cio.createinteroperable.mixin.mts;

import mcinterface1211.WrapperEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Immersive Vehicles' entity/player wrapper keeps its vanilla {@link Entity} protected; CIO needs it for an action-bar hint. */
@Mixin(value = WrapperEntity.class, remap = false)
public interface WrapperEntityAccessor {

    @Accessor(value = "entity", remap = false)
    Entity cio$getEntity();
}
