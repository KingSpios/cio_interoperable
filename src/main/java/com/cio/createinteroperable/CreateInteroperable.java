package com.cio.createinteroperable;

import com.cio.createinteroperable.compat.Mods;
import com.cio.createinteroperable.modules.CIOModules;
import com.cio.createinteroperable.telephone.TelephoneSoundRelay;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(CreateInteroperable.ID)
public class CreateInteroperable {
    public static final String ID = "createinteroperable";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateInteroperable(IEventBus modEventBus, ModContainer modContainer) {
        boolean pg = Mods.POWER_GRID.present();
        boolean cee = Mods.ELECTRO_ENERGETICS.present();
        if (!pg && !cee) {
            LOGGER.warn("Neither Power Grid nor Electro Energetics is installed — " +
                    "Create: Interoperable's electrical content needs at least one of them and is left out. " +
                    "The steam and climate blocks (Steam Outlet, Steam Hearth, Aircon) still load.");
        }

        CIOConfig.register(modContainer);
        CIOConditions.register(modEventBus);
        CIOFluids.register(modEventBus);
        CIOParticles.register(modEventBus);
        CIOSounds.register(modEventBus);
        // Content that exists on every install, then the single creative tab
        // (its core entries first, so modules' entries follow them)...
        CIOBlocks.register(modEventBus);
        CIOBlockEntities.register(modEventBus);
        CIOMenus.register(modEventBus);
        CIOItems.register(modEventBus);
        CIOCreativeTab.register(modEventBus);
        // ...then every optional-mod feature, each only if its mods are loaded.
        CIOModules.register(modEventBus);
        // World sounds (jukeboxes, note blocks, machines...) heard down a telephone line; works off every phone kind.
        NeoForge.EVENT_BUS.register(TelephoneSoundRelay.class);
    }

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(ID, path);
    }
}
