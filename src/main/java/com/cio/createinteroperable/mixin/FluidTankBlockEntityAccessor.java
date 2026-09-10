package com.cio.createinteroperable.mixin;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * FluidTankBlockEntity#width/#height are `protected` — same package as
 * BoilerData (com.simibubi.create.content.fluids.tank), so BoilerData's own
 * evaluate() reads them directly, but not accessible from our own Mixin
 * package. A plain @Accessor mixin is the standard way to read a
 * protected/private field on a class we don't otherwise touch, without
 * widening Create's own real access modifiers.
 * <p>
 * Used by BoilerDataMixin to replicate evaluate()'s exact footprint-scanning
 * loop (same yOffset/xOffset/zOffset bounds) when counting attached Steam
 * Outlets, so the count stays correct for every boiler size Create supports.
 */
@Mixin(FluidTankBlockEntity.class)
public interface FluidTankBlockEntityAccessor {
    @Accessor("width")
    int createinteroperable$getWidth();

    @Accessor("height")
    int createinteroperable$getHeight();
}
