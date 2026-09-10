package com.cio.createinteroperable;

import com.cio.createinteroperable.compat.ElectroEnergeticsCompat;
import com.cio.createinteroperable.compat.PowerGridCompat;
import com.cio.createinteroperable.deb.CeeDebRectifierRenderer;
import com.cio.createinteroperable.deb.DebRectifierRenderer;
import com.cio.createinteroperable.deb.RedstoneSwitchRenderer;
import com.cio.createinteroperable.grid.CrayfishCompat;
import com.cio.createinteroperable.grid.CrayfishClient;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client-only rendering registration for the Brass Heater's and Steam
 * Outlet's shafts — two separate backends each, mirroring exactly why Power
 * Grid's Variac ships both TunedBlockRenderer and TunedBlockVisual:
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
        // Interoperable Coupler — animated gauge needles (pointer_east/west).
        // Needs-both bridge feature: BE types are null when either mod is absent.
        // The renderer classes' own render(...) overrides are typed on their
        // BlockEntity, which is PG/CEE-rooted — merely loading the renderer
        // class (even just to call its static init(), which eagerly registers
        // PartialModels before Flywheel bakes its set) forces the JVM to
        // resolve that BlockEntity's superclass chain. A real crash log showed
        // this: init() was previously called unconditionally above, and threw
        // NoClassDefFoundError on a PG-absent install. So init() must live
        // behind the exact same gate as its BE type's registration.
        if (PowerGridCompat.present() && ElectroEnergeticsCompat.present()) {
            // The single Coupler is disabled (see CIOBlocks.BRIDGE_EXTRAS) —
            // only the Grid (Double) Coupler ships.
            InteroperableDoubleCouplerRenderer.init();
            event.registerBlockEntityRenderer(CIOBlockEntities.DOUBLE_COUPLER.get(), InteroperableDoubleCouplerRenderer::new);
            event.registerBlockEntityRenderer(CIOBlockEntities.TELEPHONE.get(), TelephoneRenderer::new);
        }
        // The two single-protocol Telephone variants share the same generic
        // TelephoneRenderer<T> (see its own class doc) — only the info-plate
        // text, no protocol-specific drawing at all.
        if (PowerGridCompat.present()) {
            event.registerBlockEntityRenderer(CIOBlockEntities.CPG_TELEPHONE.get(), TelephoneRenderer::new);
        }
        if (ElectroEnergeticsCompat.present()) {
            event.registerBlockEntityRenderer(CIOBlockEntities.CEE_TELEPHONE.get(), TelephoneRenderer::new);
        }
        // Draws the tier viewers / needle gauge, plus (only with Crayfish
        // installed) the yellow wrench-link box + connection lines. PG-only
        // BlockEntity family — CEE-wired tiers get their own renderer below.
        if (PowerGridCompat.present()) {
            DebRectifierRenderer.init();
            event.registerBlockEntityRenderer(CIOBlockEntities.DEB_RECTIFIER.get(), DebRectifierRenderer::new);
            event.registerBlockEntityRenderer(CIOBlockEntities.DEB_RECTIFIER_TIER1.get(), DebRectifierRenderer::new);
            event.registerBlockEntityRenderer(CIOBlockEntities.DEB_RECTIFIER_TIER3.get(), DebRectifierRenderer::new);
            event.registerBlockEntityRenderer(CIOBlockEntities.DEB_RECTIFIER_TIER4.get(), DebRectifierRenderer::new);
            RedstoneSwitchRenderer.init();
            event.registerBlockEntityRenderer(CIOBlockEntities.REDSTONE_SWITCH.get(), RedstoneSwitchRenderer::new);
        }
        if (ElectroEnergeticsCompat.present()) {
            CeeDebRectifierRenderer.init();
            event.registerBlockEntityRenderer(CIOBlockEntities.CEE_DEB_RECTIFIER.get(), CeeDebRectifierRenderer::new);
            event.registerBlockEntityRenderer(CIOBlockEntities.CEE_DEB_RECTIFIER_TIER1.get(), CeeDebRectifierRenderer::new);
            event.registerBlockEntityRenderer(CIOBlockEntities.CEE_DEB_RECTIFIER_TIER3.get(), CeeDebRectifierRenderer::new);
            event.registerBlockEntityRenderer(CIOBlockEntities.CEE_DEB_RECTIFIER_TIER4.get(), CeeDebRectifierRenderer::new);
            RedstoneSwitchRenderer.init();
            event.registerBlockEntityRenderer(CIOBlockEntities.CEE_REDSTONE_SWITCH.get(), RedstoneSwitchRenderer::new);
        }
        // With Crayfish installed, hang its own node-box + wire renderer on the
        // CIO Let's Do lamp type and on Beachparty's radio / mini-fridge types.
        // Without it, the native grid draws every node cube + wire from one
        // RenderLevelStage handler (ApplianceGridClientEvents) instead.
        if (CrayfishCompat.present()) {
            CrayfishClient.registerApplianceNodeRenderers(event);
        }
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
        });
    }
}
