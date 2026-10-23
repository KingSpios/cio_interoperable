package com.cio.createinteroperable.modules;

import com.cio.createinteroperable.InteroperableDoubleCouplerRenderer;
import com.cio.createinteroperable.TelephoneRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client half of {@link BridgeModule}. */
final class BridgeModuleClient {
    private BridgeModuleClient() {
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener(EntityRenderersEvent.RegisterRenderers.class, BridgeModuleClient::registerRenderers);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // Animated gauge needles. init() eagerly registers PartialModels before
        // Flywheel bakes its set; the renderer is typed on a PG/CEE-rooted
        // BlockEntity, so even init() may only run with both mods present (a
        // real NoClassDefFoundError on a PG-absent install, back when it ran
        // unconditionally). The single Coupler is disabled (BRIDGE_EXTRAS).
        InteroperableDoubleCouplerRenderer.init();
        event.registerBlockEntityRenderer(BridgeModule.DOUBLE_COUPLER_BE.get(), InteroperableDoubleCouplerRenderer::new);
        event.registerBlockEntityRenderer(BridgeModule.TELEPHONE_BE.get(), TelephoneRenderer::new);
    }
}
