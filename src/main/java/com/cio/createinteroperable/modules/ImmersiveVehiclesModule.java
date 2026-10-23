package com.cio.createinteroperable.modules;

import com.cio.createinteroperable.CreateInteroperable;
import com.cio.createinteroperable.compat.Mods;
import com.cio.createinteroperable.mts.MtsAaPowerNodeBlock;
import com.cio.createinteroperable.mts.MtsAaPowerNodeBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Immersive Vehicles content (PURELY EXPERIMENTAL, experimental branch only):
 * the invisible power terminal CIO drops under a ground-placed AA Base Plate's
 * corner (see com.cio.createinteroperable.mts). Everything else server-side
 * rides on the mixin.mts mixins. Touched only after {@link CIOModules} has
 * checked {@link #REQUIRES}.
 */
public final class ImmersiveVehiclesModule {
    public static final String REQUIRES = Mods.Id.IMMERSIVE_VEHICLES;

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreateInteroperable.ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateInteroperable.ID);

    private ImmersiveVehiclesModule() {
    }

    // No item, no loot, no collision/outline; placed and removed only by code.
    // --- Blocks ---

    public static final DeferredBlock<MtsAaPowerNodeBlock> MTS_AA_POWER_NODE = BLOCKS.register("mts_aa_power_node", () -> new MtsAaPowerNodeBlock(BlockBehaviour.Properties.of()
                    .noCollission()
                    .noOcclusion()
                    .noLootTable()
                    .replaceable()
                    .strength(-1.0f, 3_600_000.0f)
                    .pushReaction(PushReaction.DESTROY)));

    // --- Block entities ---

    // Immersive Vehicles AA Base Plate power terminal (see com.cio.createinteroperable.mts).
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MtsAaPowerNodeBlockEntity>> MTS_AA_POWER_NODE_BE =
            BLOCK_ENTITIES.register("mts_aa_power_node", () -> BlockEntityType.Builder.of(
                    MtsAaPowerNodeBlockEntity::new, ImmersiveVehiclesModule.MTS_AA_POWER_NODE.get()).build(null));

    static void register(IEventBus modEventBus) {
        CreateInteroperable.LOGGER.warn("Immersive Vehicles detected: CIO's Immersive Vehicles integration is purely " +
                "experimental (untested in real play); disable it with the mtsAaSpotlight / " +
                "mtsPoleLights config toggles if it misbehaves.");
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        if (FMLEnvironment.dist.isClient()) {
            ImmersiveVehiclesModuleClient.register();
        }
    }
}
