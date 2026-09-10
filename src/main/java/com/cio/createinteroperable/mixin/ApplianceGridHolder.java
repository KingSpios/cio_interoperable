package com.cio.createinteroperable.mixin;

import com.cio.createinteroperable.grid.ApplianceGrid;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Holds one {@link ApplianceGrid} per {@link Level} and drives its tick from
 * {@code Level.tickBlockEntities} HEAD &mdash; the same hook MrCrayfish uses for
 * its own electricity ticker. The grid itself no-ops on the client and whenever
 * Refurbished Furniture is installed, so this mixin is always safe to apply
 * (it references only CIO classes).
 */
@Mixin(Level.class)
public abstract class ApplianceGridHolder implements ApplianceGrid.Access {

    @Unique
    private ApplianceGrid cio$applianceGrid;

    @Override
    public ApplianceGrid cio$applianceGrid() {
        if (this.cio$applianceGrid == null) {
            this.cio$applianceGrid = new ApplianceGrid((Level) (Object) this);
        }
        return this.cio$applianceGrid;
    }

    @Inject(method = "tickBlockEntities", at = @At("HEAD"))
    private void cio$tickApplianceGrid(CallbackInfo ci) {
        cio$applianceGrid().tick();
    }
}
