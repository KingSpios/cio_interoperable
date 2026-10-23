package com.cio.createinteroperable;

import com.cio.createinteroperable.compat.Mods;
import com.cio.createinteroperable.modules.CIOModules;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(CreateInteroperable.ID)
public class CreateInteroperable {
    public static final String ID = "createinteroperable";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateInteroperable(IEventBus modEventBus, ModContainer modContainer) {
        boolean pg = Mods.POWER_GRID.present();
        boolean cee = Mods.ELECTRO_ENERGETICS.present();
        if (!pg && !cee) {
            LOGGER.error("Neither Power Grid nor Electro Energetics is installed — " +
                    "Create: Interoperable bridges the two and requires at least one of them. " +
                    "The mod will continue loading, but none of its blocks/items are functional.");
        }

        CIOConfig.register(modContainer);
        CIOFluids.register(modEventBus);
        CIOParticles.register(modEventBus);
        // Content that exists on every install, then the single creative tab
        // (its core entries first, so modules' entries follow them)...
        CIOBlocks.register(modEventBus);
        CIOBlockEntities.register(modEventBus);
        CIOItems.register(modEventBus);
        CIOCreativeTab.register(modEventBus);
        // ...then every optional-mod feature, each only if its mods are loaded.
        CIOModules.register(modEventBus);
    }

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(ID, path);
    }
}
