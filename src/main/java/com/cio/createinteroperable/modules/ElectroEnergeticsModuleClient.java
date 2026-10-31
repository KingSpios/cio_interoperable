package com.cio.createinteroperable.modules;

import com.cio.createinteroperable.streetlight.StreetlightRenderer;
import com.cio.createinteroperable.pole.client.StreetSignRenderer;
import com.cio.createinteroperable.telephone.TelephoneRenderer;
import com.cio.createinteroperable.powerkit.cee.CeeDebRectifierRenderer;
import com.cio.createinteroperable.embedded.client.EmbeddedConnectorModel;
import com.cio.createinteroperable.CreateInteroperable;
import com.cio.createinteroperable.inverter.cee.CeeStyleInverterRenderer;
import com.cio.createinteroperable.inverter.cee.InverterClient;
import com.cio.createinteroperable.inverter.cee.InverterRenderer;
import com.cio.createinteroperable.powerkit.client.PowerKitPaintModel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** Client half of {@link ElectroEnergeticsModule}: renderers and render layers for its blocks. */
final class ElectroEnergeticsModuleClient {
    private ElectroEnergeticsModuleClient() {
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener(EntityRenderersEvent.RegisterRenderers.class, ElectroEnergeticsModuleClient::registerRenderers);
        modEventBus.addListener(FMLClientSetupEvent.class, ElectroEnergeticsModuleClient::clientSetup);
        modEventBus.addListener(ModelEvent.ModifyBakingResult.class, ElectroEnergeticsModuleClient::modifyBakingResult);
        modEventBus.addListener(RegisterColorHandlersEvent.Block.class, ElectroEnergeticsModuleClient::registerBlockColors);
        // The inverter's frequency knob (hold use + scroll) listens on the game bus.
        InverterClient.register();
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ElectroEnergeticsModule.CEE_TELEPHONE_BE.get(), TelephoneRenderer::new);
        // Street sign text and paint (the plate itself is part of the pole model).
        event.registerBlockEntityRenderer(ElectroEnergeticsModule.UTILITY_POLE_BE.get(), StreetSignRenderer::new);
        // Additive glow (rays, glass, bulb); init() registers its PartialModels before Flywheel bakes.
        StreetlightRenderer.init();
        event.registerBlockEntityRenderer(ElectroEnergeticsModule.CEE_STREETLIGHT_BE.get(), StreetlightRenderer::new);
        // The inverter's visor, frequency knob and push button; init() registers its partials before Flywheel bakes.
        InverterRenderer.init();
        event.registerBlockEntityRenderer(ElectroEnergeticsModule.CEE_INVERTER_BE.get(), InverterRenderer::new);
        CeeStyleInverterRenderer.init();
        event.registerBlockEntityRenderer(ElectroEnergeticsModule.CEE_STYLE_INVERTER_BE.get(), CeeStyleInverterRenderer::new);
        CeeDebRectifierRenderer.init();
        event.registerBlockEntityRenderer(ElectroEnergeticsModule.CEE_DEB_RECTIFIER_BE.get(), CeeDebRectifierRenderer::new);
        event.registerBlockEntityRenderer(ElectroEnergeticsModule.CEE_DEB_RECTIFIER_TIER1_BE.get(), CeeDebRectifierRenderer::new);
        event.registerBlockEntityRenderer(ElectroEnergeticsModule.CEE_DEB_RECTIFIER_TIER3_BE.get(), CeeDebRectifierRenderer::new);
        event.registerBlockEntityRenderer(ElectroEnergeticsModule.CEE_DEB_RECTIFIER_TIER4_BE.get(), CeeDebRectifierRenderer::new);
    }

    /** The Embedded Connector draws its walls from the block its casing copies; the Domestic / Commercial kits take their paint. */
    private static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        EmbeddedConnectorModel.wrap(event.getModels(), ElectroEnergeticsModule.CEE_EMBEDDED_CONNECTOR.get());
        PowerKitPaintModel.wrap(event.getModels(), event.getTextureGetter(), ElectroEnergeticsModule.CEE_DEB_RECTIFIER.get(),
                CreateInteroperable.rl("block/deb_rectifier_tier1"), "domestic");
        PowerKitPaintModel.wrap(event.getModels(), event.getTextureGetter(), ElectroEnergeticsModule.CEE_DEB_RECTIFIER_TIER3.get(),
                CreateInteroperable.rl("block/deb_rectifier_tier3_white"), "commercial");
    }

    private static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(EmbeddedConnectorModel::color, ElectroEnergeticsModule.CEE_EMBEDDED_CONNECTOR.get());
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        // Reuses the PG bottom's model file; same cutoutMipped fix (see CIOClient#clientSetup).
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(ElectroEnergeticsModule.CEE_AIRCON_MOTOR_BOTTOM.get(), RenderType.cutoutMipped());
            ItemBlockRenderTypes.setRenderLayer(ElectroEnergeticsModule.CEE_AIRCON_MOTOR_BOTTOM_240.get(), RenderType.cutoutMipped());
        });
    }
}
