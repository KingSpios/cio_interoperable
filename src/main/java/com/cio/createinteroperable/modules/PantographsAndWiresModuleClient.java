package com.cio.createinteroperable.modules;

import com.cio.createinteroperable.compat.PnwHiddenWires;
import net.neoforged.neoforge.common.NeoForge;

/** Client half of {@link PantographsAndWiresModule}: hides the mirrored CEE wires PnW already draws. */
final class PantographsAndWiresModuleClient {
    private PantographsAndWiresModuleClient() {
    }

    static void register() {
        NeoForge.EVENT_BUS.register(PnwHiddenWires.class);
    }
}
