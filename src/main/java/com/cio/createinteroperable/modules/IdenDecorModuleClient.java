package com.cio.createinteroperable.modules;

import com.cio.createinteroperable.iden.IdenPhone;
import com.cio.createinteroperable.iden.IdenTelephoneRenderer;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import java.util.function.Supplier;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client half of {@link IdenDecorModule}: the swapped-in telephone's tap nubs + node overlay. */
final class IdenDecorModuleClient {
    private IdenDecorModuleClient() {
    }

    static <T extends SmartBlockEntity & IdenPhone> void registerTelephone(IEventBus modEventBus,
                                                                          Supplier<BlockEntityType<T>> type) {
        modEventBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            IdenTelephoneRenderer.init();
            event.registerBlockEntityRenderer(type.get(), IdenTelephoneRenderer::new);
        });
    }
}
