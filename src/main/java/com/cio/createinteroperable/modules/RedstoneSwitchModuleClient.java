package com.cio.createinteroperable.modules;

import com.cio.createinteroperable.experimental.RedstoneSwitchDisplay;
import com.cio.createinteroperable.experimental.RedstoneSwitchRenderer;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import java.util.function.Supplier;

/** Client half of {@link RedstoneSwitchModule}: both switches share one renderer. */
final class RedstoneSwitchModuleClient {
    private RedstoneSwitchModuleClient() {
    }

    static <T extends SmartBlockEntity & RedstoneSwitchDisplay> void register(IEventBus modEventBus, Supplier<BlockEntityType<T>> type) {
        modEventBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            RedstoneSwitchRenderer.init();
            event.registerBlockEntityRenderer(type.get(), RedstoneSwitchRenderer::new);
        });
    }
}
