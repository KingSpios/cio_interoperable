package com.cio.createinteroperable.modules;

import com.cio.createinteroperable.CIOCreativeTab;
import com.cio.createinteroperable.CIODevices;
import com.cio.createinteroperable.thermal.cee.CeeAirconMotorBottom240Block;
import com.cio.createinteroperable.thermal.cee.CeeAirconMotorBottomBlock;
import com.cio.createinteroperable.thermal.cee.CeeAirconMotorBottomBlockEntity;
import com.cio.createinteroperable.cee.CeeCompactTransformerBlock;
import com.cio.createinteroperable.cee.CeeCompactTransformerBlockEntity;
import com.cio.createinteroperable.cee.CeeMiniConnectorBlock;
import com.cio.createinteroperable.cee.CeeStreetlightBlock;
import com.cio.createinteroperable.cee.CeeStreetlightBlockEntity;
import com.cio.createinteroperable.cee.CeeStreetlightLightBlock;
import com.cio.createinteroperable.telephone.cee.CeeTelephoneBlock;
import com.cio.createinteroperable.telephone.cee.CeeTelephoneBlockEntity;
import com.cio.createinteroperable.CreateInteroperable;
import com.cio.createinteroperable.compat.Mods;
import com.cio.createinteroperable.powerkit.cee.CeeDebRectifierBlock;
import com.cio.createinteroperable.powerkit.cee.CeePowerKitTier1Block;
import com.cio.createinteroperable.powerkit.cee.CeePowerKitTier1BlockEntity;
import com.cio.createinteroperable.powerkit.cee.CeePowerKitTier2BlockEntity;
import com.cio.createinteroperable.powerkit.cee.CeePowerKitTier3Block;
import com.cio.createinteroperable.powerkit.cee.CeePowerKitTier3BlockEntity;
import com.cio.createinteroperable.powerkit.cee.CeePowerKitTier4Block;
import com.cio.createinteroperable.powerkit.cee.CeePowerKitTier4BlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Electro Energetics content: the CEE Aircon motor bottoms, the CEE
 * Telephone, the Compact Transformer, the Streetlight, the Mini Double
 * Connector and the CEE Power Kits (the experimental CEE Redstone Switch is
 * in {@link RedstoneSwitchModule}), plus CIO's
 * SimulatedDevice types ({@link CIODevices}). Touched only after
 * {@link CIOModules} has checked {@link #REQUIRES}.
 */
public final class ElectroEnergeticsModule {
    public static final String REQUIRES = Mods.Id.ELECTRO_ENERGETICS;

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreateInteroperable.ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateInteroperable.ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreateInteroperable.ID);

    private ElectroEnergeticsModule() {
    }

    // --- Blocks ---

    public static final DeferredBlock<CeeTelephoneBlock> CEE_TELEPHONE = BLOCKS.register("cee_telephone",
            () -> new CeeTelephoneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    ));

    /** CEE-only Compact Transformer — no CPG equivalent. */
    public static final DeferredBlock<CeeCompactTransformerBlock> CEE_COMPACT_TRANSFORMER = BLOCKS.register("cee_mini_transformer",
            () -> new CeeCompactTransformerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(1.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    ));

    /** CEE-only Streetlight — no CPG equivalent. */
    public static final DeferredBlock<CeeStreetlightBlock> CEE_STREETLIGHT = BLOCKS.register("cee_streetlight",
            () -> new CeeStreetlightBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.5f, 6.0f)
                    .sound(SoundType.LANTERN)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    ));

    /** CEE-only Mini Double Connector — two separate pins, no CPG equivalent. */
    public static final DeferredBlock<CeeMiniConnectorBlock> CEE_MINI_CONNECTOR = BLOCKS.register("cee_mini_connector",
            () -> new CeeMiniConnectorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(1.0f, 6.0f)
                    .sound(SoundType.COPPER)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    ));

    /** The Streetlight's projected light (invisible, air-like, no item). */
    public static final DeferredBlock<CeeStreetlightLightBlock> CEE_STREETLIGHT_LIGHT = BLOCKS.register("cee_streetlight_light",
            () -> new CeeStreetlightLightBlock(BlockBehaviour.Properties.of()));

    public static final DeferredBlock<CeeAirconMotorBottomBlock> CEE_AIRCON_MOTOR_BOTTOM = BLOCKS.register("cee_aircon_motor_bottom",
            () -> new CeeAirconMotorBottomBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    ));

    public static final DeferredBlock<CeeAirconMotorBottom240Block> CEE_AIRCON_MOTOR_BOTTOM_240 = BLOCKS.register("cee_aircon_motor_bottom_240",
            () -> new CeeAirconMotorBottom240Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    ));

    public static final DeferredBlock<CeeDebRectifierBlock> CEE_DEB_RECTIFIER = BLOCKS.register("cee_deb_rectifier",
            () -> new CeeDebRectifierBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .noOcclusion()));

    public static final DeferredBlock<CeePowerKitTier1Block> CEE_DEB_RECTIFIER_TIER1 = BLOCKS.register("cee_deb_rectifier_tier1",
            () -> new CeePowerKitTier1Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .noOcclusion()));

    public static final DeferredBlock<CeePowerKitTier3Block> CEE_DEB_RECTIFIER_TIER3 = BLOCKS.register("cee_deb_rectifier_tier3",
            () -> new CeePowerKitTier3Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.5f, 8.0f)
                    .noOcclusion()));

    public static final DeferredBlock<CeePowerKitTier4Block> CEE_DEB_RECTIFIER_TIER4 = BLOCKS.register("cee_deb_rectifier_tier4",
            () -> new CeePowerKitTier4Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 10.0f)
                    .noOcclusion()));

    // --- Block entities ---

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CeeTelephoneBlockEntity>> CEE_TELEPHONE_BE =
            BLOCK_ENTITIES.register("cee_telephone", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new CeeTelephoneBlockEntity(ElectroEnergeticsModule.CEE_TELEPHONE_BE.get(), pos, state),
                    ElectroEnergeticsModule.CEE_TELEPHONE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CeeCompactTransformerBlockEntity>> CEE_COMPACT_TRANSFORMER_BE =
            BLOCK_ENTITIES.register("cee_mini_transformer", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new CeeCompactTransformerBlockEntity(ElectroEnergeticsModule.CEE_COMPACT_TRANSFORMER_BE.get(), pos, state),
                    ElectroEnergeticsModule.CEE_COMPACT_TRANSFORMER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CeeStreetlightBlockEntity>> CEE_STREETLIGHT_BE =
            BLOCK_ENTITIES.register("cee_streetlight", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new CeeStreetlightBlockEntity(ElectroEnergeticsModule.CEE_STREETLIGHT_BE.get(), pos, state),
                    ElectroEnergeticsModule.CEE_STREETLIGHT.get()).build(null));

    // CEE-wired Power Kit tiers — own BlockEntity family, CEE-only.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CeePowerKitTier2BlockEntity>> CEE_DEB_RECTIFIER_BE =
            BLOCK_ENTITIES.register("cee_deb_rectifier", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new CeePowerKitTier2BlockEntity(ElectroEnergeticsModule.CEE_DEB_RECTIFIER_BE.get(), pos, state),
                    ElectroEnergeticsModule.CEE_DEB_RECTIFIER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CeePowerKitTier1BlockEntity>> CEE_DEB_RECTIFIER_TIER1_BE =
            BLOCK_ENTITIES.register("cee_deb_rectifier_tier1", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new CeePowerKitTier1BlockEntity(ElectroEnergeticsModule.CEE_DEB_RECTIFIER_TIER1_BE.get(), pos, state),
                    ElectroEnergeticsModule.CEE_DEB_RECTIFIER_TIER1.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CeePowerKitTier3BlockEntity>> CEE_DEB_RECTIFIER_TIER3_BE =
            BLOCK_ENTITIES.register("cee_deb_rectifier_tier3", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new CeePowerKitTier3BlockEntity(ElectroEnergeticsModule.CEE_DEB_RECTIFIER_TIER3_BE.get(), pos, state),
                    ElectroEnergeticsModule.CEE_DEB_RECTIFIER_TIER3.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CeePowerKitTier4BlockEntity>> CEE_DEB_RECTIFIER_TIER4_BE =
            BLOCK_ENTITIES.register("cee_deb_rectifier_tier4", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new CeePowerKitTier4BlockEntity(ElectroEnergeticsModule.CEE_DEB_RECTIFIER_TIER4_BE.get(), pos, state),
                    ElectroEnergeticsModule.CEE_DEB_RECTIFIER_TIER4.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CeeAirconMotorBottomBlockEntity>> CEE_AIRCON_MOTOR_BOTTOM_BE =
            BLOCK_ENTITIES.register("cee_aircon_motor_bottom", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new CeeAirconMotorBottomBlockEntity(ElectroEnergeticsModule.CEE_AIRCON_MOTOR_BOTTOM_BE.get(), pos, state),
                    ElectroEnergeticsModule.CEE_AIRCON_MOTOR_BOTTOM.get(), ElectroEnergeticsModule.CEE_AIRCON_MOTOR_BOTTOM_240.get()).build(null));

    // --- Items ---

    public static final DeferredItem<BlockItem> CEE_TELEPHONE_ITEM = ITEMS.registerSimpleBlockItem(
            "cee_telephone", ElectroEnergeticsModule.CEE_TELEPHONE);

    public static final DeferredItem<BlockItem> CEE_COMPACT_TRANSFORMER_ITEM = ITEMS.registerSimpleBlockItem(
            "cee_mini_transformer", ElectroEnergeticsModule.CEE_COMPACT_TRANSFORMER);

    public static final DeferredItem<BlockItem> CEE_STREETLIGHT_ITEM = ITEMS.registerSimpleBlockItem(
            "cee_streetlight", ElectroEnergeticsModule.CEE_STREETLIGHT);

    public static final DeferredItem<BlockItem> CEE_MINI_CONNECTOR_ITEM = ITEMS.registerSimpleBlockItem(
            "cee_mini_connector", ElectroEnergeticsModule.CEE_MINI_CONNECTOR);

    // CEE-wired Power Kits (CEE-only).
    public static final DeferredItem<BlockItem> CEE_DEB_RECTIFIER_ITEM = ITEMS.registerSimpleBlockItem(
            "cee_deb_rectifier", ElectroEnergeticsModule.CEE_DEB_RECTIFIER);

    public static final DeferredItem<BlockItem> CEE_DEB_RECTIFIER_TIER1_ITEM = ITEMS.registerSimpleBlockItem(
            "cee_deb_rectifier_tier1", ElectroEnergeticsModule.CEE_DEB_RECTIFIER_TIER1);

    public static final DeferredItem<BlockItem> CEE_DEB_RECTIFIER_TIER3_ITEM = ITEMS.registerSimpleBlockItem(
            "cee_deb_rectifier_tier3", ElectroEnergeticsModule.CEE_DEB_RECTIFIER_TIER3);

    public static final DeferredItem<BlockItem> CEE_DEB_RECTIFIER_TIER4_ITEM = ITEMS.registerSimpleBlockItem(
            "cee_deb_rectifier_tier4", ElectroEnergeticsModule.CEE_DEB_RECTIFIER_TIER4);

    public static final DeferredItem<BlockItem> CEE_AIRCON_MOTOR_BOTTOM_ITEM = ITEMS.registerSimpleBlockItem(
            "cee_aircon_motor_bottom", ElectroEnergeticsModule.CEE_AIRCON_MOTOR_BOTTOM);

    public static final DeferredItem<BlockItem> CEE_AIRCON_MOTOR_BOTTOM_240_ITEM = ITEMS.registerSimpleBlockItem(
            "cee_aircon_motor_bottom_240", ElectroEnergeticsModule.CEE_AIRCON_MOTOR_BOTTOM_240);

    static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        ITEMS.register(modEventBus);
        // Against CEE's own SIMULATED_DEVICE_TYPE registry.
        CIODevices.register(modEventBus);
        modEventBus.addListener(RegisterCapabilitiesEvent.class, ElectroEnergeticsModule::registerCapabilities);

        CIOCreativeTab.add(CIOCreativeTab.Section.CLIMATE, CEE_AIRCON_MOTOR_BOTTOM_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.CLIMATE, CEE_AIRCON_MOTOR_BOTTOM_240_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, CEE_DEB_RECTIFIER_TIER1_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, CEE_DEB_RECTIFIER_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, CEE_DEB_RECTIFIER_TIER3_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, CEE_DEB_RECTIFIER_TIER4_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, CEE_TELEPHONE_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, CEE_COMPACT_TRANSFORMER_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, CEE_STREETLIGHT_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, CEE_MINI_CONNECTOR_ITEM);
        CIOCreativeTab.offerIcon(1, CEE_DEB_RECTIFIER_ITEM);

        if (FMLEnvironment.dist.isClient()) {
            ElectroEnergeticsModuleClient.register(modEventBus);
        }
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CEE_AIRCON_MOTOR_BOTTOM_BE.get(),
                (be, side) -> be.getFluidHandler(side));
    }
}
