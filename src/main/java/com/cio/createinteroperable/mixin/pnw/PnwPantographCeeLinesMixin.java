package com.cio.createinteroperable.mixin.pnw;

import com.george_vi.electroenergetics.client.WireRenderer;
import com.george_vi.electroenergetics.content.railway_electrification.catenary.CatenaryConnection;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import net.createmod.catnip.data.Pair;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets PnW's pantograph animation ride Electro Energetics' contact lines too.
 *
 * <p>PnW raises its pantograph head by intersecting a horizontal head segment,
 * swept upward, with PnW's catenary wire segments. CEE's contact lines are
 * just as straight: its catenary spans run between the bottom centres of two
 * catenary holders, and its taut (zero-sag) wires, the bus bars included, run
 * node to node. This tests those segments with PnW's own intersection routine
 * and keeps whichever wire is lower, so the head follows the actual CEE line.</p>
 */
@Mixin(targets = "de.mrjulsen.paw.blockentity.PantographBlockEntity", remap = false)
abstract class PnwPantographCeeLinesMixin {
    /** Broad-phase margin: head sweep height plus half the head width, with room to spare. */
    @Unique
    private static final double CIO$REACH = 5;

    @Shadow
    protected static Vector3d checkWireIntersection(Vector3d c, Vector3d d, Vector3d a, Vector3d b, Vector3d direction) {
        throw new AssertionError();
    }

    @Inject(method = "calculateWireContact", at = @At("RETURN"), cancellable = true, remap = false)
    private void cio$includeCeeLines(Vector3d worldPosition, Vector3d upVec, Vector3d rightVec, CallbackInfoReturnable<Double> cir) {
        Level level = ((net.minecraft.world.level.block.entity.BlockEntity) (Object) this).getLevel();
        if (level == null) {
            return;
        }
        Vector3d a = new Vector3d(worldPosition).sub(rightVec);
        Vector3d b = new Vector3d(worldPosition).add(rightVec);
        double best = cir.getReturnValueD() < 0 ? Double.MAX_VALUE : cir.getReturnValueD();
        boolean hit = false;

        for (CatenaryConnection catenary : WireRenderer.CATENARY) {
            double h = cio$height(catenary.pos1().getBottomCenter(), catenary.pos2().getBottomCenter(), worldPosition, upVec, a, b);
            if (h >= 0 && h < best) {
                best = h;
                hit = true;
            }
        }
        for (Pair<InWorldNodeConnection, WireData> wire : WireRenderer.WIRE_CONNECTIONS) {
            if (wire.getSecond().wireType().getSag() != 0) {
                continue;
            }
            Vec3 p1 = wire.getFirst().node1().getPosition(level);
            Vec3 p2 = wire.getFirst().node2().getPosition(level);
            if (p1 == null || p2 == null) {
                continue;
            }
            double h = cio$height(p1, p2, worldPosition, upVec, a, b);
            if (h >= 0 && h < best) {
                best = h;
                hit = true;
            }
        }
        if (hit) {
            cir.setReturnValue(best);
        }
    }

    /** Head height at which it meets the segment p1-p2, measured PnW's way; -1 if it never does. */
    @Unique
    private static double cio$height(Vec3 p1, Vec3 p2, Vector3d origin, Vector3d upVec, Vector3d a, Vector3d b) {
        // Cheap reject: the segment's box, grown by the sweep, must contain the head origin.
        if (origin.x < Math.min(p1.x, p2.x) - CIO$REACH || origin.x > Math.max(p1.x, p2.x) + CIO$REACH
                || origin.z < Math.min(p1.z, p2.z) - CIO$REACH || origin.z > Math.max(p1.z, p2.z) + CIO$REACH
                || origin.y < Math.min(p1.y, p2.y) - CIO$REACH || origin.y > Math.max(p1.y, p2.y) + 1) {
            return -1;
        }
        Vector3d d = checkWireIntersection(new Vector3d(p1.x, p1.y, p1.z), new Vector3d(p2.x, p2.y, p2.z), a, b, upVec);
        if (d == null) {
            return -1;
        }
        double rise = d.y - origin.y;
        Vector3d scaledUp = new Vector3d(upVec).normalize().mul(rise);
        double sideways = new Vector3d(scaledUp.x(), 0, scaledUp.z()).length();
        return Math.sqrt(sideways * sideways + rise * rise);
    }
}
