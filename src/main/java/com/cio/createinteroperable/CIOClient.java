package com.cio.createinteroperable;

import com.cio.createinteroperable.compat.Mods;
import com.cio.createinteroperable.letsdo.BeachpartyRadioRenderer;
import com.cio.createinteroperable.pole.client.FenceMountRenderer;
import com.cio.createinteroperable.copycat.client.CopycatEncasedPipeModel;
import com.cio.createinteroperable.pole.client.UtilityPoleModelLoader;
import com.cio.createinteroperable.thermal.AirconMotorTopRenderer;
import com.cio.createinteroperable.thermal.BrassHeaterRenderer;
import com.cio.createinteroperable.thermal.BrassHeaterVisual;
import com.cio.createinteroperable.thermal.RadiatorSmokeParticle;
import com.cio.createinteroperable.thermal.SteamOutletRenderer;
import com.cio.createinteroperable.thermal.SteamOutletVisual;
import com.cio.createinteroperable.urbandecor.UrbanBathtubRenderer;
import com.cio.createinteroperable.urbandecor.UrbanColdRenderer;
import com.cio.createinteroperable.urbandecor.UrbanColdScreen;
import com.cio.createinteroperable.urbandecor.UrbanCookerScreen;
import com.cio.createinteroperable.urbandecor.UrbanLanternRenderer;
import com.cio.createinteroperable.urbandecor.UrbanMachineLampRenderer;
import com.cio.createinteroperable.urbandecor.UrbanSinkRenderer;
import com.cio.createinteroperable.urbandecor.UrbanSpeakerRenderer;
import com.cio.createinteroperable.urbandecor.UrbanToiletRenderer;
import com.cio.createinteroperable.urbandecor.UrbanWasherScreen;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * Client-only rendering registration for the content every install has (see
 * {@link CIOBlocks}). Each module registers its own blocks' renderers from
 * its *ModuleClient class (see {@link com.cio.createinteroperable.modules.CIOModules}).
 *
 * <p>The Brass Heater's and Steam Outlet's shafts get two separate backends
 * each, mirroring exactly why Power Grid's Variac ships both
 * TunedBlockRenderer and TunedBlockVisual:</p>
 * <ul>
 *     <li>The classic BlockEntityRenderer, registered via the
 *     vanilla/NeoForge EntityRenderersEvent — only actually invoked when
 *     Flywheel visualization is unavailable (confirmed via
 *     KineticBlockEntityRenderer#renderSafe's own early-return check).</li>
 *     <li>The Flywheel visual, registered through SimpleBlockEntityVisualizer
 *     — the path used whenever Flywheel IS active, which is the common case
 *     for a real Create install.</li>
 * </ul>
 * value = Dist.CLIENT keeps this whole class (which touches client-only
 * rendering types) from ever being loaded on a dedicated server.
 */
@EventBusSubscriber(modid = CreateInteroperable.ID, value = Dist.CLIENT)
public class CIOClient {
    @SubscribeEvent
    static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(CIOMenus.URBAN_WASHER.get(), UrbanWasherScreen::new);
        event.register(CIOMenus.URBAN_COOKER.get(), UrbanCookerScreen::new);
        event.register(CIOMenus.URBAN_COLD.get(), UrbanColdScreen::new);
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CIOBlockEntities.BRASS_HEATER.get(), BrassHeaterRenderer::new);
        event.registerBlockEntityRenderer(CIOBlockEntities.STEAM_OUTLET.get(), SteamOutletRenderer::new);
        // Aircon — the top (fan) registers unconditionally (it's
        // electrical-backend-agnostic, sitting on either the PG or CEE bottom
        // variant, or neither), so its renderer must too. The venter has no
        // renderer of its own yet.
        AirconMotorTopRenderer.init();
        // The fence-post brackets' PartialModels, before Flywheel bakes (phones, Power Kits, interconnect).
        FenceMountRenderer.init();
        event.registerBlockEntityRenderer(CIOBlockEntities.AIRCON_MOTOR_TOP.get(), AirconMotorTopRenderer::new);
        // Urban Decor's assembled bathtub (the type never matches a block without Urban Decor).
        event.registerBlockEntityRenderer(CIOBlockEntities.URBAN_BATHTUB.get(), UrbanBathtubRenderer::new);
        event.registerBlockEntityRenderer(CIOBlockEntities.URBAN_TOILET.get(), UrbanToiletRenderer::new);
        // The water filling a sink's basin (taps and showers share the type and draw nothing).
        event.registerBlockEntityRenderer(CIOBlockEntities.URBAN_FIXTURE.get(), UrbanSinkRenderer::new);
        // The glow of a lit stainless steel lantern (and, with Refurbished Furniture, its node marker).
        event.registerBlockEntityRenderer(CIOBlockEntities.URBAN_LANTERN.get(), UrbanLanternRenderer::new);
        // The washer / dryer / dishwasher indicator lamps; with Refurbished Furniture, CrayfishClient draws them with its node.
        if (!Mods.REFURBISHED_FURNITURE.present()) {
            event.registerBlockEntityRenderer(CIOBlockEntities.URBAN_WASHER.get(), UrbanMachineLampRenderer::new);
            event.registerBlockEntityRenderer(CIOBlockEntities.URBAN_DRYER.get(), UrbanMachineLampRenderer::new);
            event.registerBlockEntityRenderer(CIOBlockEntities.URBAN_DISHWASHER.get(), UrbanMachineLampRenderer::new);
        }
        // Beachparty's radio (no renderer of its own); with Refurbished Furniture, CrayfishClient draws it with its node.
        BlockEntityType<?> radio = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath("beachparty", "radio"));
        if (radio != null && !Mods.REFURBISHED_FURNITURE.present()) {
            registerRadio(event, radio);
        }
        // The light inside Urban Decor's open, powered fridge and freezer; with Refurbished Furniture, CrayfishClient draws it with its node.
        BlockEntityType<?> storage = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath("urban_decor", "storage_decor"));
        if (storage != null && !Mods.REFURBISHED_FURNITURE.present()) {
            registerUrbanCold(event, storage);
        }
        // Urban Decor's speaker pin (no renderer of its own); with Refurbished Furniture, CrayfishClient draws it with its node.
        BlockEntityType<?> speaker = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath("urban_decor", "speaker"));
        if (speaker != null && !Mods.REFURBISHED_FURNITURE.present() && UrbanSpeakerRenderer.wanted()) {
            registerUrbanSpeaker(event, speaker);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerUrbanCold(EntityRenderersEvent.RegisterRenderers event, BlockEntityType<?> type) {
        event.registerBlockEntityRenderer((BlockEntityType) type, (BlockEntityRendererProvider) UrbanColdRenderer::new);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerUrbanSpeaker(EntityRenderersEvent.RegisterRenderers event, BlockEntityType<?> type) {
        event.registerBlockEntityRenderer((BlockEntityType) type, (BlockEntityRendererProvider) UrbanSpeakerRenderer::new);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerRadio(EntityRenderersEvent.RegisterRenderers event, BlockEntityType<?> type) {
        event.registerBlockEntityRenderer((BlockEntityType) type, (BlockEntityRendererProvider) BeachpartyRadioRenderer::new);
    }

    /**
     * The Utility Pole's model loader. Registered on every install (it needs no
     * grid mod) so its block model parses wherever the grid module that owns the
     * block is absent, and so a second grid's pole can share it.
     */
    @SubscribeEvent
    static void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(UtilityPoleModelLoader.ID, UtilityPoleModelLoader.INSTANCE);
    }

    /** The Copycat Encased Pipe draws the block its casing copies, round its pipe openings. */
    @SubscribeEvent
    static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        CopycatEncasedPipeModel.wrap(event.getModels());
    }

    @SubscribeEvent
    static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(CopycatEncasedPipeModel::color, CIOBlocks.COPYCAT_ENCASED_PIPE.get());
    }

    @SubscribeEvent
    static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(CIOParticles.RADIATOR_SMOKE.get(), RadiatorSmokeParticle.Provider::new);
    }

    @SubscribeEvent
    static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            SimpleBlockEntityVisualizer.builder(CIOBlockEntities.BRASS_HEATER.get())
                    .factory(BrassHeaterVisual::new)
                    .apply();
            SimpleBlockEntityVisualizer.builder(CIOBlockEntities.STEAM_OUTLET.get())
                    .factory(SteamOutletVisual::new)
                    .apply();

            // The Aircon blocks' own static baked models default to
            // RenderType.solid() like every other block in this mod. Solid
            // ignores a texture's alpha channel entirely (every texel is drawn
            // fully opaque), which is exactly what produced two real,
            // separately reported "featureless grey plane instead of the
            // actual detail" symptoms on aircon_motor_top's fin/blade geometry
            // — both worked around at first by deleting the offending elements
            // from the model, not by fixing the actual cause. So the aircon
            // blocks use cutoutMipped (binary alpha-tested transparency,
            // mipmapped — Create's own norm for anything with real cutout
            // detail, e.g. its fan blades), letting any current or future
            // geometry that relies on a texture's transparency render
            // correctly. The top and venter exist on every install, so this is
            // unconditional; the PG and CEE motor bottoms get the identical fix
            // from their own module's client class.
            ItemBlockRenderTypes.setRenderLayer(CIOBlocks.AIRCON_MOTOR_TOP.get(), RenderType.cutoutMipped());
            ItemBlockRenderTypes.setRenderLayer(CIOBlocks.AIRCON_VENTER.get(), RenderType.cutoutMipped());
        });
    }
}
