package com.cio.createinteroperable.modules;

import com.cio.createinteroperable.TelephoneRenderer;
import com.cio.createinteroperable.deb.DebRectifierRenderer;
import com.cio.createinteroperable.deb.RedstoneSwitchRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client half of {@link PowerGridModule}: renderers and render layers for its blocks. */
final class PowerGridModuleClient {
    private PowerGridModuleClient() {
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener(EntityRenderersEvent.RegisterRenderers.class, PowerGridModuleClient::registerRenderers);
        modEventBus.addListener(FMLClientSetupEvent.class, PowerGridModuleClient::clientSetup);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // Shares the generic TelephoneRenderer<T> with the other two phones
        // (info-plate text only, nothing protocol-specific).
        event.registerBlockEntityRenderer(PowerGridModule.CPG_TELEPHONE_BE.get(), TelephoneRenderer::new);
        // Tier viewers / needle gauge, plus (only with Crayfish installed) the
        // yellow wrench-link box + connection lines. init() registers the
        // PartialModels before Flywheel bakes its set.
        DebRectifierRenderer.init();
        event.registerBlockEntityRenderer(PowerGridModule.DEB_RECTIFIER_BE.get(), DebRectifierRenderer::new);
        event.registerBlockEntityRenderer(PowerGridModule.DEB_RECTIFIER_TIER1_BE.get(), DebRectifierRenderer::new);
        event.registerBlockEntityRenderer(PowerGridModule.DEB_RECTIFIER_TIER3_BE.get(), DebRectifierRenderer::new);
        event.registerBlockEntityRenderer(PowerGridModule.DEB_RECTIFIER_TIER4_BE.get(), DebRectifierRenderer::new);
        RedstoneSwitchRenderer.init();
        event.registerBlockEntityRenderer(PowerGridModule.REDSTONE_SWITCH_BE.get(), RedstoneSwitchRenderer::new);
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        // Same model as the CEE bottom; see CIOClient#clientSetup for why the
        // aircon blocks need cutoutMipped rather than solid.
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(PowerGridModule.AIRCON_MOTOR_BOTTOM.get(), RenderType.cutoutMipped());
            ItemBlockRenderTypes.setRenderLayer(PowerGridModule.AIRCON_MOTOR_BOTTOM_240.get(), RenderType.cutoutMipped());
        });
    }
}
