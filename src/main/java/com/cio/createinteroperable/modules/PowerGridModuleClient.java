package com.cio.createinteroperable.modules;

import com.cio.createinteroperable.pole.client.StreetSignRenderer;
import com.cio.createinteroperable.telephone.TelephoneRenderer;
import com.cio.createinteroperable.powerkit.cpg.DebRectifierRenderer;
import com.cio.createinteroperable.streetlight.StreetlightRenderer;
import com.cio.createinteroperable.cpg.spectral.SpectralBulbRenderer;
import com.cio.createinteroperable.cpg.spectral.client.SpectralFixtureModel;
import com.cio.createinteroperable.CreateInteroperable;
import com.cio.createinteroperable.powerkit.client.PowerKitPaintModel;
import com.cio.createinteroperable.embedded.client.EmbeddedConnectorModel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** Client half of {@link PowerGridModule}: renderers and render layers for its blocks. */
final class PowerGridModuleClient {
    private PowerGridModuleClient() {
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener(EntityRenderersEvent.RegisterRenderers.class, PowerGridModuleClient::registerRenderers);
        modEventBus.addListener(FMLClientSetupEvent.class, PowerGridModuleClient::clientSetup);
        modEventBus.addListener(ModelEvent.ModifyBakingResult.class, PowerGridModuleClient::modifyBakingResult);
        modEventBus.addListener(RegisterColorHandlersEvent.Block.class, PowerGridModuleClient::registerBlockColors);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // Shares the generic TelephoneRenderer<T> with the other two phones
        // (info-plate text only, nothing protocol-specific).
        event.registerBlockEntityRenderer(PowerGridModule.CPG_TELEPHONE_BE.get(), TelephoneRenderer::new);
        // Street sign text and paint (the plate itself is part of the pole model).
        event.registerBlockEntityRenderer(PowerGridModule.CPG_UTILITY_POLE_BE.get(), StreetSignRenderer::new);
        // Tier viewers / needle gauge, plus (only with Crayfish installed) the
        // yellow wrench-link box + connection lines. init() registers the
        // PartialModels before Flywheel bakes its set.
        DebRectifierRenderer.init();
        event.registerBlockEntityRenderer(PowerGridModule.DEB_RECTIFIER_BE.get(), DebRectifierRenderer::new);
        event.registerBlockEntityRenderer(PowerGridModule.DEB_RECTIFIER_TIER1_BE.get(), DebRectifierRenderer::new);
        event.registerBlockEntityRenderer(PowerGridModule.DEB_RECTIFIER_TIER3_BE.get(), DebRectifierRenderer::new);
        event.registerBlockEntityRenderer(PowerGridModule.DEB_RECTIFIER_TIER4_BE.get(), DebRectifierRenderer::new);
        // The Streetlight's glow, shared with the CEE one (init() registers its PartialModels before Flywheel bakes).
        StreetlightRenderer.init();
        event.registerBlockEntityRenderer(PowerGridModule.CPG_STREETLIGHT_BE.get(), StreetlightRenderer::new);
        // The UV / IR Bulbs' fixture models and Factory Light rays (PartialModels, so before Flywheel bakes).
        SpectralBulbRenderer.init();
    }

    /** The Embedded Connector draws its walls from the block its casing copies; PG's Factory Light and Ceiling Tile Lamp get the UV / IR Bulbs' coloured lens. */
    private static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        EmbeddedConnectorModel.wrap(event.getModels(), PowerGridModule.CPG_EMBEDDED_CONNECTOR.get());
        SpectralFixtureModel.wrap(event.getModels(), event.getTextureGetter());
        // the Domestic / Commercial kits take their paint (body box: the tier 1 sheet / the white tier 3 one)
        PowerKitPaintModel.wrap(event.getModels(), event.getTextureGetter(), PowerGridModule.DEB_RECTIFIER.get(),
                CreateInteroperable.rl("block/deb_rectifier_tier1"), "domestic");
        PowerKitPaintModel.wrap(event.getModels(), event.getTextureGetter(), PowerGridModule.DEB_RECTIFIER_TIER3.get(),
                CreateInteroperable.rl("block/deb_rectifier_tier3_white"), "commercial");
    }

    private static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(EmbeddedConnectorModel::color, PowerGridModule.CPG_EMBEDDED_CONNECTOR.get());
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
