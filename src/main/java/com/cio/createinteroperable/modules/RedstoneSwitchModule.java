package com.cio.createinteroperable.modules;

import com.cio.createinteroperable.CreateInteroperable;
import com.cio.createinteroperable.compat.Mods;
import com.cio.createinteroperable.experimental.cee.CeeRedstoneSwitchBlock;
import com.cio.createinteroperable.experimental.cee.CeeRedstoneSwitchBlockEntity;
import com.cio.createinteroperable.experimental.cee.RedstoneSwitchCeeDevice;
import com.cio.createinteroperable.experimental.cpg.RedstoneSwitchBlock;
import com.cio.createinteroperable.experimental.cpg.RedstoneSwitchBlockEntity;
import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The CPG / CEE Redstone Switch — EXPERIMENTAL, experimental branch only (see
 * the {@code experimental} package): a redstone-gated two-pole inline break
 * that cuts or enables a Power Kit's appliance network, throughput and
 * thermal model calibrated against the tier-2 Power Kit. Deliberately
 * <b>not</b> on the creative tab and without a recipe (/give only), so it adds
 * no {@code CIOCreativeTab} entries. Each half is its own module; this outer
 * class is only their holder. Registry ids are the ones the switch always had.
 */
public final class RedstoneSwitchModule {

    private RedstoneSwitchModule() {
    }

    /** The Power Grid Redstone Switch. */
    public static final class Cpg {
        public static final String REQUIRES = Mods.Id.POWER_GRID;

        private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreateInteroperable.ID);
        private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
                DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateInteroperable.ID);
        private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreateInteroperable.ID);

        public static final DeferredBlock<RedstoneSwitchBlock> BLOCK = BLOCKS.register("redstone_switch",
                () -> new RedstoneSwitchBlock(BlockBehaviour.Properties.of()
                        .mapColor(MapColor.METAL)
                        .strength(2.0f, 6.0f)
                        .noOcclusion()));

        public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RedstoneSwitchBlockEntity>> BLOCK_ENTITY =
                BLOCK_ENTITIES.register("redstone_switch", () -> BlockEntityType.Builder.of(
                        (pos, state) -> new RedstoneSwitchBlockEntity(Cpg.BLOCK_ENTITY.get(), pos, state),
                        Cpg.BLOCK.get()).build(null));

        public static final DeferredItem<BlockItem> ITEM = ITEMS.registerSimpleBlockItem("redstone_switch", Cpg.BLOCK);

        private Cpg() {
        }

        static void register(IEventBus modEventBus) {
            BLOCKS.register(modEventBus);
            BLOCK_ENTITIES.register(modEventBus);
            ITEMS.register(modEventBus);
            if (FMLEnvironment.dist.isClient()) {
                RedstoneSwitchModuleClient.register(modEventBus, BLOCK_ENTITY);
            }
        }
    }

    /** The Electro Energetics Redstone Switch, with its CEE device type (two independent pole resistors). */
    public static final class Cee {
        public static final String REQUIRES = Mods.Id.ELECTRO_ENERGETICS;

        private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreateInteroperable.ID);
        private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
                DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateInteroperable.ID);
        private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreateInteroperable.ID);
        private static final DeferredRegister<SimulatedDeviceType<?>> DEVICES =
                DeferredRegister.create(CEERegistries.SIMULATED_DEVICE_TYPE, CreateInteroperable.ID);

        public static final DeferredBlock<CeeRedstoneSwitchBlock> BLOCK = BLOCKS.register("cee_redstone_switch",
                () -> new CeeRedstoneSwitchBlock(BlockBehaviour.Properties.of()
                        .mapColor(MapColor.METAL)
                        .strength(2.0f, 6.0f)
                        .noOcclusion()));

        public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CeeRedstoneSwitchBlockEntity>> BLOCK_ENTITY =
                BLOCK_ENTITIES.register("cee_redstone_switch", () -> BlockEntityType.Builder.of(
                        (pos, state) -> new CeeRedstoneSwitchBlockEntity(Cee.BLOCK_ENTITY.get(), pos, state),
                        Cee.BLOCK.get()).build(null));

        public static final DeferredItem<BlockItem> ITEM = ITEMS.registerSimpleBlockItem("cee_redstone_switch", Cee.BLOCK);

        public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<RedstoneSwitchCeeDevice>> DEVICE =
                DEVICES.register("redstone_switch", () -> new SimulatedDeviceType<>(CreateInteroperable.rl("redstone_switch"),
                        ((type, level, pos, sd) -> new RedstoneSwitchCeeDevice(level, pos, sd, type))));

        private Cee() {
        }

        static void register(IEventBus modEventBus) {
            BLOCKS.register(modEventBus);
            BLOCK_ENTITIES.register(modEventBus);
            ITEMS.register(modEventBus);
            DEVICES.register(modEventBus);
            if (FMLEnvironment.dist.isClient()) {
                RedstoneSwitchModuleClient.register(modEventBus, BLOCK_ENTITY);
            }
        }
    }
}
