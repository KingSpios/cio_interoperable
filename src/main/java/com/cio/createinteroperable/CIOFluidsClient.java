package com.cio.createinteroperable;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Client-only registration of steam's render extensions (still/flowing
 * texture + tint), split from CIOFluids so this class — which references
 * client-only types (IClientFluidTypeExtensions' default methods touch
 * net.minecraft.client.Minecraft) — is never loaded on a dedicated server.
 * {@code value = Dist.CLIENT} makes FML's annotation scan skip this class
 * entirely server-side, same idiom used throughout NeoForge's own mods.
 * <p>
 * Texture paths point at assets that don't exist yet — a grey-white steam
 * still/flow texture is expected to land alongside the Steam Outlet/Brass
 * Heater's own custom models (see conversation: "Custom models will be
 * created"). Until then this renders as missing-texture in a transparent
 * pipe, same known-gap pattern already established elsewhere in this mod for
 * the real multiblock.
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
    }
}
