package com.cio.createinteroperable;

import com.cio.createinteroperable.compat.ElectroEnergeticsCompat;
import com.cio.createinteroperable.compat.PowerGridCompat;
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
        boolean pg = PowerGridCompat.present();
        boolean cee = ElectroEnergeticsCompat.present();
        if (!pg && !cee) {
            LOGGER.error("Neither Power Grid nor Electro Energetics is installed — " +
                    "Create: Interoperable bridges the two and requires at least one of them. " +
                    "The mod will continue loading, but none of its blocks/items are functional.");
        }

        CIOConfig.register(modContainer);
        CIOFluids.register(modEventBus);
        CIOBlocks.register(modEventBus);
        CIOBlockEntities.register(modEventBus);
        CIOItems.register(modEventBus);
        // CIODevices registers against Electro Energetics' own registry
        // (CEERegistries.SIMULATED_DEVICE_TYPE) in a static initializer — that
        // class must never be touched at all when CEE is absent.
        if (cee) {
            CIODevices.register(modEventBus);
        }
        CIOCreativeTab.register(modEventBus);
    }

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(ID, path);
    }
}
