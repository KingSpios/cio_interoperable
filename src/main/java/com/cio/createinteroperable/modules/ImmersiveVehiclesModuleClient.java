package com.cio.createinteroperable.modules;

import com.cio.createinteroperable.mts.MtsAaClient;
import net.neoforged.neoforge.common.NeoForge;

/** Client half of {@link ImmersiveVehiclesModule}: the AA Spotlight's look-at "Missing power" label. */
final class ImmersiveVehiclesModuleClient {
    private ImmersiveVehiclesModuleClient() {
    }

    static void register() {
        NeoForge.EVENT_BUS.register(MtsAaClient.class);
    }
}
