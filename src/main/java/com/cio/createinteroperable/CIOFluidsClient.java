package com.cio.createinteroperable;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Client-only registration of steam/hot_air/cold_air's render extensions
 * (still/flowing texture + tint), split from CIOFluids so this class — which
 * references client-only types (IClientFluidTypeExtensions' default methods
 * touch net.minecraft.client.Minecraft) — is never loaded on a dedicated
 * server. {@code value = Dist.CLIENT} makes FML's annotation scan skip this
 * class entirely server-side, same idiom used throughout NeoForge's own mods.
 * <p>
 * {@code hot_air}/{@code cold_air}'s still/flow textures (2026-09-20) are
 * generated copies of {@code steam_still}/{@code steam_flow} — the same
 * opaque 16x16 grayscale "wisp" noise pattern (translucency comes entirely
 * from each fluid's own {@code getTintColor()} alpha below, not the PNG's own
 * alpha channel, which is fully opaque in all three), recolored per-pixel: a
 * two-tone faint cyan (brighter source pixels)/dark grey (darker source
 * pixels) blend for cold_air, a single faint orange blend for hot_air, both
 * at a 35% blend factor so the original noise pattern stays clearly visible
 * underneath rather than reading as a flat wash — real per-pixel color
 * variation on top of, not instead of, the runtime tint below. Placeholder
 * art, not final — swap the four PNGs directly to replace it, no Java
 * changes needed.
 */
// RegisterClientExtensionsEvent implements IModBusEvent, so the modern
// EventBusSubscriber auto-detects the mod bus — no explicit bus = ... needed
// (that attribute is deprecated in this NeoForge version).
@EventBusSubscriber(modid = CreateInteroperable.ID, value = Dist.CLIENT)
public class CIOFluidsClient {
    private static final ResourceLocation STILL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateInteroperable.ID, "block/fluid/steam_still");
    private static final ResourceLocation FLOWING_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateInteroperable.ID, "block/fluid/steam_flow");

    private static final ResourceLocation HOT_AIR_STILL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateInteroperable.ID, "block/fluid/hot_air_still");
    private static final ResourceLocation HOT_AIR_FLOWING_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateInteroperable.ID, "block/fluid/hot_air_flow");

    private static final ResourceLocation COLD_AIR_STILL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateInteroperable.ID, "block/fluid/cold_air_still");
    private static final ResourceLocation COLD_AIR_FLOWING_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateInteroperable.ID, "block/fluid/cold_air_flow");

    @SubscribeEvent
    static void registerFluidExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return STILL_TEXTURE;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return FLOWING_TEXTURE;
            }

            @Override
            public int getTintColor() {
                // Pale grey-white, slightly translucent — placeholder tint
                // until the real texture supplies its own shading.
                return 0xC0EEEEEE;
            }
        }, CIOFluids.STEAM_TYPE.get());

        // Warm-tinted white for hot air, cool-tinted white for cold air, on
        // top of the recolored textures themselves (see this class's own
        // doc) — the two layer together, tint on top of per-pixel variation.
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return HOT_AIR_STILL_TEXTURE;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return HOT_AIR_FLOWING_TEXTURE;
            }

            @Override
            public int getTintColor() {
                return 0xC0FFCC99;
            }
        }, CIOFluids.HOT_AIR_TYPE.get());

        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return COLD_AIR_STILL_TEXTURE;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return COLD_AIR_FLOWING_TEXTURE;
            }

            @Override
            public int getTintColor() {
                return 0xC0CCEEFF;
            }
        }, CIOFluids.COLD_AIR_TYPE.get());
    }
}
