package com.cio.createinteroperable.mixin.pnpengine;

import com.cio.createinteroperable.compat.PipesNPhysicsAirconPressure;
import com.cio.createinteroperable.compat.PnpColumnAccess;
import de.devin.pipesnphysics.engine.boundary.BoundaryColumn;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets the Aircon Motor's and venters' ports give at any fill level (see
 * {@link PipesNPhysicsAirconPressure#givesFromAnyLevel}), which PnP otherwise only grants to
 * basins and declared multi-port machines resolved through a null face.
 */
@Mixin(value = BoundaryColumn.class, remap = false)
public abstract class BoundaryColumnMixin implements PnpColumnAccess {
    @Shadow private boolean givesFromAnyLevel;

    @Override
    public void cio$giveFromAnyLevel() {
        givesFromAnyLevel = true;
    }

    @Inject(method = "resolveGenericHandler", at = @At("RETURN"), require = 0)
    private static void createinteroperable$aircon(Level level, BlockPos pos, Direction face, IFluidHandler cap,
                                                   CallbackInfoReturnable<BoundaryColumn> cir) {
        BoundaryColumn column = cir.getReturnValue();
        if (column != null && PipesNPhysicsAirconPressure.givesFromAnyLevel(level, pos, face)) {
            ((PnpColumnAccess) (Object) column).cio$giveFromAnyLevel();
        }
    }
}
