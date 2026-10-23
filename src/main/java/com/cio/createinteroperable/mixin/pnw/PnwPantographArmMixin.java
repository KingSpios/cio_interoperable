package com.cio.createinteroperable.mixin.pnw;

import com.cio.createinteroperable.compat.PnwHiddenWires;
import com.george_vi.electroenergetics.client.WireRenderer;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import net.createmod.catnip.data.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * CEE animates a pantograph's arm up to the wire above it by searching the
 * client's list of drawn wires. PnW contact-wire mirrors are hidden from that
 * list, so the arm is also shown them here. Electrical contact itself is
 * decided server-side and needs no help.
 */
@Mixin(targets = "com.george_vi.electroenergetics.content.railway_electrification.pantograph.PantographMovementBehaviour", remap = false)
abstract class PnwPantographArmMixin {
    @Redirect(method = "tick", remap = false, at = @At(value = "FIELD", opcode = org.objectweb.asm.Opcodes.GETSTATIC, remap = false,
            target = "Lcom/george_vi/electroenergetics/client/WireRenderer;WIRE_CONNECTIONS:Ljava/util/List;"))
    private List<Pair<InWorldNodeConnection, WireData>> cio$includeContactWires() {
        return PnwHiddenWires.withContactWires(WireRenderer.WIRE_CONNECTIONS);
    }
}
