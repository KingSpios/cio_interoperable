package com.cio.createinteroperable.modules;

import com.cio.createinteroperable.grid.CrayfishClient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client half of {@link RefurbishedFurnitureModule}. */
final class RefurbishedFurnitureModuleClient {
    private RefurbishedFurnitureModuleClient() {
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener(EntityRenderersEvent.RegisterRenderers.class, CrayfishClient::registerApplianceNodeRenderers);
    }
}
