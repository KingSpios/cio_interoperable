package com.cio.createinteroperable;

import com.cio.createinteroperable.compat.ColdSweatCompat;
import com.cio.createinteroperable.compat.ColdSweatIntegration;
import com.cio.createinteroperable.compat.ElectroEnergeticsCompat;
import com.cio.createinteroperable.compat.IdenDecorCompat;
import com.cio.createinteroperable.iden.ElectricSwitches;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import com.cio.createinteroperable.compat.PipesNPhysicsCompat;
import com.cio.createinteroperable.compat.PipesNPhysicsIntegration;
import com.cio.createinteroperable.compat.PowerGridCompat;
import com.cio.createinteroperable.compat.PantographsAndWiresCompat;
import com.cio.createinteroperable.compat.PnwCeeWireBridge;
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
        CIOParticles.register(modEventBus);
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

        // Iden's Decor: rename its redstone buttons/switches "Redstone ..." so
        // they read apart from CIO's Electric twins (see ElectricSwitches).
        if (IdenDecorCompat.present()) {
            modEventBus.addListener((FMLCommonSetupEvent event) -> ElectricSwitches.renameOriginals());
        }

        // See ColdSweatIntegration's own doc for why this is a runtime
        // present()-gated call rather than @EventBusSubscriber: that
        // annotation would let NeoForge's classpath scanner load the class
        // (and touch Cold-Sweat-only types in its method signatures)
        // unconditionally, which throws on a Cold-Sweat-absent install.
        if (ColdSweatCompat.present()) {
            ColdSweatIntegration.register();
        }
        // Same present()-gated pattern: PipesNPhysicsIntegration's own method
        // signatures touch Pipes-n-Physics-only types (FluidHandlerApi/Role),
        // so this class must never load at all when that mod is absent.
        if (PipesNPhysicsCompat.present()) {
            PipesNPhysicsIntegration.register(modEventBus);
        }
        // Voice relay for answered Telephone calls. VoiceCallTracker holds no Simple Voice Chat
        // types; the plugin class itself is found and instantiated by Simple Voice Chat.
        if (com.cio.createinteroperable.compat.SimpleVoiceChatCompat.present()) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(com.cio.createinteroperable.voice.VoiceCallTracker.class);
        }
        if (cee && PantographsAndWiresCompat.present()) {
            // The bridge itself avoids PnW link-time types; its mixins are
            // separately guarded, so this remains a complete no-op otherwise.
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(PnwCeeWireBridge.class);
        }
    }

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(ID, path);
    }
}
