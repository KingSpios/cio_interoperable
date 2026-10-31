package com.cio.createinteroperable.modules;

import com.cio.createinteroperable.compat.Mods;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * MrCrayfish's Refurbished Furniture: CIO's furniture-power layer runs on
 * Crayfish's node graph when it's installed (mostly via the
 * createinteroperable.*crayfish* mixin configs). The only bootstrap-time piece
 * is client side: Crayfish's own node box + wire renderer on CIO's appliance
 * nodes. Without Crayfish the native grid draws them instead
 * (ApplianceGridClientEvents). Touched only after {@link CIOModules} has
 * checked {@link #REQUIRES}.
 */
public final class RefurbishedFurnitureModule {
    public static final String REQUIRES = Mods.Id.REFURBISHED_FURNITURE;

    private RefurbishedFurnitureModule() {
    }

    static void register(IEventBus modEventBus) {
        // Its fridge and freezer in the universal appliance wrapper.
        com.cio.createinteroperable.appliance.Appliances.register(com.cio.createinteroperable.appliance.CrayfishAppliances.create());
        // Sitting on Refurbished Furniture's toilet cleanses like Urban Decor's (see CrayfishToilets).
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                com.cio.createinteroperable.appliance.CrayfishToilets::onPlayerTick);
        if (FMLEnvironment.dist.isClient()) {
            RefurbishedFurnitureModuleClient.register(modEventBus);
        }
    }
}
