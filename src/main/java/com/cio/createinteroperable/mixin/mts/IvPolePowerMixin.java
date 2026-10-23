package com.cio.createinteroperable.mixin.mts;

import com.cio.createinteroperable.mts.IvPowerState;
import minecrafttransportsimulator.blocks.tileentities.instances.TileEntityPole;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Carries the pole block's "lamps may light" flag for its components (see {@link IvPowerState}). */
@Mixin(value = TileEntityPole.class, remap = false)
public abstract class IvPolePowerMixin implements IvPowerState {

    @Unique
    private boolean cio$ivPowered;

    @Override
    public boolean cio$ivPowered() {
        return this.cio$ivPowered;
    }

    @Override
    public void cio$setIvPowered(boolean powered) {
        this.cio$ivPowered = powered;
    }
}
