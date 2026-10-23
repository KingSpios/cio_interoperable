package com.cio.createinteroperable.modules;

import com.cio.createinteroperable.compat.Mods;
import com.cio.createinteroperable.compat.PnwCeePantographTypes;
import com.cio.createinteroperable.compat.PnwCeeWireBridge;
import com.cio.createinteroperable.compat.PnwCeeWireTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Pantographs & Wires <-> Electro Energetics conductor bridge (experimental
 * branch only). The bridge avoids PnW link-time types; its mixins are
 * separately guarded (PnwMixinPlugin). Touched only after {@link CIOModules}
 * has checked {@link #REQUIRES}.
 */
public final class PantographsAndWiresModule {
    public static final String REQUIRES = Mods.Id.ELECTRO_ENERGETICS + "," + Mods.Id.PANTOGRAPHS_AND_WIRES;

    private PantographsAndWiresModule() {
    }

    static void register(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.register(PnwCeeWireBridge.class);
        PnwCeeWireTypes.register(modEventBus);
        PnwCeePantographTypes.register(modEventBus);
        if (FMLEnvironment.dist.isClient()) {
            PantographsAndWiresModuleClient.register();
        }
    }
}
