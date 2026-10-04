package com.cio.createinteroperable.mixin.pnpengine;

import com.cio.createinteroperable.compat.PipesNPhysicsAirconPressure;
import de.devin.pipesnphysics.engine.boundary.BoundaryColumn;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;

/**
 * Makes the Aircon Motor a pressure source in Pipes n Physics' solver.
 * <p>
 * {@code FluidPass#assembleBranch} builds one solver branch per pipe run, with an EMF term that a
 * pump node adds ({@code outSign * head}, where {@code outSign} is +1 when the run leaves through
 * its {@code a} end). This adds the same kind of term for a run that ends at a motor port: the
 * cold port pushes out, the hot port pulls in, so the AC loop is driven without pumps on the
 * line. A column's solver index equals its position in {@code participants} (columns are
 * registered first, in order), which is how the branch's endpoints map back to columns.
 */
@Mixin(targets = "de.devin.pipesnphysics.engine.FluidPass", remap = false)
public abstract class FluidPassAirconMixin {
    @Shadow @Final private Level level;
    @Shadow @Final private List<BoundaryColumn> participants;

    @ModifyArg(method = "assembleBranch",
            at = @At(value = "INVOKE",
                    target = "Lde/devin/pipesnphysics/engine/solve/NetworkSolver$BranchSpec;<init>(IIDDIDDDZD)V"),
            index = 3, require = 0)
    private double createinteroperable$motorPressure(int a, int b, double conductance, double emf,
                                                     int allowedSign, double crestHeight, double crestFloor,
                                                     double crestPos, boolean crestWet, double primeAllowance) {
        return emf + headAt(a, +1) + headAt(b, -1);
    }

    private double headAt(int solverIndex, int outSign) {
        if (solverIndex < 0 || solverIndex >= participants.size()) {
            return 0;
        }
        return outSign * PipesNPhysicsAirconPressure.signedHead(level, participants.get(solverIndex));
    }
}
