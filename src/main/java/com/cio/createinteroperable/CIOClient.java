package com.cio.createinteroperable;

import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
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
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CIOBlockEntities.BRASS_HEATER.get(), BrassHeaterRenderer::new);
        event.registerBlockEntityRenderer(CIOBlockEntities.STEAM_OUTLET.get(), SteamOutletRenderer::new);
        // Aircon — the top (fan) registers unconditionally (it's
        // electrical-backend-agnostic, sitting on either the PG or CEE bottom
        // variant, or neither), so its renderer must too. The venter has no
        // renderer of its own yet.
        AirconMotorTopRenderer.init();
        event.registerBlockEntityRenderer(CIOBlockEntities.AIRCON_MOTOR_TOP.get(), AirconMotorTopRenderer::new);
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
