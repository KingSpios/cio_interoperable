package com.cio.createinteroperable.letsdo;

import com.cio.createinteroperable.CIOBlockEntities;
import com.cio.createinteroperable.CreateInteroperable;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Exposes a NeoForge {@code IFluidHandler} on the DOWN face of a Let's Do kitchen
 * sink's lower half, so Create (or any mod's) pipes connect there and pumped-in
 * water flips the sink's vanilla {@code filled} blockstate.
 *
 * <p>The capability is bound to {@link CIOBlockEntities#LETSDO_SINK}, the block
 * entity {@code FarmAndCharmSinkBlockMixin} attaches to the sink — Create's pipe
 * code ignores blocks with no block entity, so a bare-block capability would
 * never be seen. The "needs water" interaction change lives in
 * {@link SinkInteractionHandler}; shared state helpers live in {@link SinkStates}.
 *
 * <p>Alpine Whispers 1.0.4+'s sink and bathtub have block entities of their own;
 * the same kind of fill-only handler goes on those, backed by the
 * {@link PipeFluidBuffer} {@link AlpineFixture} adds to them (faces per
 * {@link AlpineFixture#acceptsPipe}).
 */
@EventBusSubscriber(modid = CreateInteroperable.ID)
public final class SinkSupplyCompat {
    private SinkSupplyCompat() {
    }

    @SubscribeEvent
    static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        registerAlpineFixture(event, "sink");
        registerAlpineFixture(event, "bathtub");

        int count = SinkStates.sinkBlocks().size();
        if (count == 0) {
            return; // no Let's Do sink mod installed
        }
        CreateInteroperable.LOGGER.info("Create: Interoperable: enabling pipe water supply on {} Let's Do sink block(s)", count);
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                CIOBlockEntities.LETSDO_SINK.get(),
                (be, side) -> side == Direction.DOWN ? be.fluidHandler() : null);
    }

    @SuppressWarnings("unchecked")
    private static void registerAlpineFixture(RegisterCapabilitiesEvent event, String path) {
        BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(ResourceLocation.fromNamespaceAndPath("alpinewhispers", path))
                .ifPresent(type -> {
                    CreateInteroperable.LOGGER.info("Create: Interoperable: enabling pipe water supply on alpinewhispers:{}", path);
                    event.registerBlockEntity(
                            Capabilities.FluidHandler.BLOCK,
                            (BlockEntityType<BlockEntity>) type,
                            (be, side) -> be instanceof AlpineFixture fixture
                                    && AlpineFixture.acceptsPipe(be.getBlockState(), side) ? fixture.cio$supply() : null);
                });
    }
}
