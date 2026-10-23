package com.cio.createinteroperable.modules;

import com.cio.createinteroperable.CeeStreetlightRenderer;
import com.cio.createinteroperable.TelephoneRenderer;
import com.cio.createinteroperable.deb.CeeDebRectifierRenderer;
import com.cio.createinteroperable.deb.RedstoneSwitchRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client half of {@link ElectroEnergeticsModule}: renderers and render layers for its blocks. */
final class ElectroEnergeticsModuleClient {
    private ElectroEnergeticsModuleClient() {
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener(EntityRenderersEvent.RegisterRenderers.class, ElectroEnergeticsModuleClient::registerRenderers);
        modEventBus.addListener(FMLClientSetupEvent.class, ElectroEnergeticsModuleClient::clientSetup);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ElectroEnergeticsModule.CEE_TELEPHONE_BE.get(), TelephoneRenderer::new);
        // Additive glow (rays, glass, bulb); init() registers its PartialModels before Flywheel bakes.
        CeeStreetlightRenderer.init();
        event.registerBlockEntityRenderer(ElectroEnergeticsModule.CEE_STREETLIGHT_BE.get(), CeeStreetlightRenderer::new);
        CeeDebRectifierRenderer.init();
        event.registerBlockEntityRenderer(ElectroEnergeticsModule.CEE_DEB_RECTIFIER_BE.get(), CeeDebRectifierRenderer::new);
        event.registerBlockEntityRenderer(ElectroEnergeticsModule.CEE_DEB_RECTIFIER_TIER1_BE.get(), CeeDebRectifierRenderer::new);
        event.registerBlockEntityRenderer(ElectroEnergeticsModule.CEE_DEB_RECTIFIER_TIER3_BE.get(), CeeDebRectifierRenderer::new);
        event.registerBlockEntityRenderer(ElectroEnergeticsModule.CEE_DEB_RECTIFIER_TIER4_BE.get(), CeeDebRectifierRenderer::new);
        RedstoneSwitchRenderer.init();
        event.registerBlockEntityRenderer(ElectroEnergeticsModule.CEE_REDSTONE_SWITCH_BE.get(), RedstoneSwitchRenderer::new);
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        // Reuses the PG bottom's model file; same cutoutMipped fix (see CIOClient#clientSetup).
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(ElectroEnergeticsModule.CEE_AIRCON_MOTOR_BOTTOM.get(), RenderType.cutoutMipped());
            ItemBlockRenderTypes.setRenderLayer(ElectroEnergeticsModule.CEE_AIRCON_MOTOR_BOTTOM_240.get(), RenderType.cutoutMipped());
        });
    }
}
