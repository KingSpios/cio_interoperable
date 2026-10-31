package com.cio.createinteroperable.modules;

import com.cio.createinteroperable.CIOCreativeTab;
import com.cio.createinteroperable.CIOItems;
import com.cio.createinteroperable.CIODevices;
import com.cio.createinteroperable.thermal.cee.CeeAirconMotorBottom240Block;
import com.cio.createinteroperable.thermal.cee.CeeAirconMotorBottomBlock;
import com.cio.createinteroperable.thermal.cee.CeeAirconMotorBottomBlockEntity;
import com.cio.createinteroperable.cee.CeeCompactTransformerBlock;
import com.cio.createinteroperable.cee.CeeCompactTransformerBlockEntity;
import com.cio.createinteroperable.cee.CeeMiniConnectorBlock;
import com.cio.createinteroperable.inverter.cee.CeeStyleInverterBlock;
import com.cio.createinteroperable.inverter.cee.InverterBlock;
import com.cio.createinteroperable.inverter.cee.InverterBlockEntity;
import com.cio.createinteroperable.inverter.cee.InverterSettingsPacket;
import com.cio.createinteroperable.embedded.cee.CeeEmbeddedConnectorBlock;
import com.cio.createinteroperable.embedded.cee.CeeEmbeddedConnectorBlockEntity;
import com.cio.createinteroperable.streetlight.StreetlightLightBlock;
import com.cio.createinteroperable.streetlight.cee.CeeStreetlightBlock;
import com.cio.createinteroperable.streetlight.cee.CeeStreetlightBlockEntity;
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
import com.cio.createinteroperable.pole.PoleInsulators;
import com.cio.createinteroperable.pole.UtilityPoleBlockEntity;
import com.cio.createinteroperable.pole.cee.CeeUtilityPoleArmBlock;
import com.cio.createinteroperable.pole.cee.CeeUtilityPoleBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Electro Energetics content: the CEE Aircon motor bottoms, the CEE
 * Telephone, the Compact Transformer, the CIO and CEE Inverters, the Streetlight, the Mini Double
 * Connector, the Embedded Connector and the CEE Power Kits (the experimental CEE Redstone Switch is
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

    /** CEE-only DC to AC Inverter: DC in, three phases + neutral out, 15 kW, a computer's data pin. */
    public static final DeferredBlock<InverterBlock> CEE_INVERTER = BLOCKS.register("cee_inverter",
            () -> new InverterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(1.5f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    ));

    /** CEE-only CEE Inverter: the CIO Inverter's device in the synchroscope's case, 20 kW. */
    public static final DeferredBlock<CeeStyleInverterBlock> CEE_STYLE_INVERTER = BLOCKS.register("cee_style_inverter",
            () -> new CeeStyleInverterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(1.5f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    ));

    /** CEE Streetlight — its CPG twin is PowerGridModule.CPG_STREETLIGHT. */
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

    /** CEE Embedded Connector &mdash; two pins in a hollow, wall-copying block with a lid; its CPG twin is in PowerGridModule. */
    public static final DeferredBlock<CeeEmbeddedConnectorBlock> CEE_EMBEDDED_CONNECTOR = BLOCKS.register("cee_embedded_connector",
            () -> new CeeEmbeddedConnectorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(1.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    ));

    /** Utility Pole &mdash; a wooden fence post whose pins carry CEE wires; one item builds the whole column. */
    public static final DeferredBlock<CeeUtilityPoleBlock> UTILITY_POLE = BLOCKS.register("utility_pole",
            () -> new CeeUtilityPoleBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .forceSolidOn()
                    .instrument(NoteBlockInstrument.BASS)
                    .strength(2.0f, 3.0f)
                    .sound(SoundType.WOOD)
                    ));

    /** A Utility Pole's extended arm (no item: a fence on a pole mount places it, and it drops what it was made of). */
    public static final DeferredBlock<CeeUtilityPoleArmBlock> UTILITY_POLE_ARM = BLOCKS.register("utility_pole_arm",
            () -> new CeeUtilityPoleArmBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .instrument(NoteBlockInstrument.BASS)
                    .strength(2.0f, 3.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)
                    .noLootTable()
                    ));

    /** The Streetlight's projected light (invisible, air-like, no item). */
    public static final DeferredBlock<StreetlightLightBlock> CEE_STREETLIGHT_LIGHT = BLOCKS.register("cee_streetlight_light",
            () -> new StreetlightLightBlock(BlockBehaviour.Properties.of()));

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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InverterBlockEntity>> CEE_INVERTER_BE =
            BLOCK_ENTITIES.register("cee_inverter", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new InverterBlockEntity(ElectroEnergeticsModule.CEE_INVERTER_BE.get(), pos, state),
                    ElectroEnergeticsModule.CEE_INVERTER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InverterBlockEntity>> CEE_STYLE_INVERTER_BE =
            BLOCK_ENTITIES.register("cee_style_inverter", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new InverterBlockEntity(ElectroEnergeticsModule.CEE_STYLE_INVERTER_BE.get(), pos, state),
                    ElectroEnergeticsModule.CEE_STYLE_INVERTER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CeeStreetlightBlockEntity>> CEE_STREETLIGHT_BE =
            BLOCK_ENTITIES.register("cee_streetlight", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new CeeStreetlightBlockEntity(ElectroEnergeticsModule.CEE_STREETLIGHT_BE.get(), pos, state),
                    ElectroEnergeticsModule.CEE_STREETLIGHT.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CeeEmbeddedConnectorBlockEntity>> CEE_EMBEDDED_CONNECTOR_BE =
            BLOCK_ENTITIES.register("cee_embedded_connector", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new CeeEmbeddedConnectorBlockEntity(ElectroEnergeticsModule.CEE_EMBEDDED_CONNECTOR_BE.get(), pos, state),
                    ElectroEnergeticsModule.CEE_EMBEDDED_CONNECTOR.get()).build(null));

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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<UtilityPoleBlockEntity>> UTILITY_POLE_BE =
            BLOCK_ENTITIES.register("utility_pole", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new UtilityPoleBlockEntity(ElectroEnergeticsModule.UTILITY_POLE_BE.get(), pos, state),
                    ElectroEnergeticsModule.UTILITY_POLE.get()).build(null));

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

    public static final DeferredItem<BlockItem> CEE_INVERTER_ITEM = ITEMS.registerSimpleBlockItem(
            "cee_inverter", ElectroEnergeticsModule.CEE_INVERTER);

    public static final DeferredItem<BlockItem> CEE_STYLE_INVERTER_ITEM = ITEMS.registerSimpleBlockItem(
            "cee_style_inverter", ElectroEnergeticsModule.CEE_STYLE_INVERTER);

    public static final DeferredItem<BlockItem> CEE_MINI_CONNECTOR_ITEM = ITEMS.registerSimpleBlockItem(
            "cee_mini_connector", ElectroEnergeticsModule.CEE_MINI_CONNECTOR);

    public static final DeferredItem<BlockItem> CEE_EMBEDDED_CONNECTOR_ITEM = ITEMS.registerSimpleBlockItem(
            "cee_embedded_connector", ElectroEnergeticsModule.CEE_EMBEDDED_CONNECTOR);

    public static final DeferredItem<BlockItem> UTILITY_POLE_ITEM = ITEMS.registerSimpleBlockItem(
            "utility_pole", ElectroEnergeticsModule.UTILITY_POLE);

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
        modEventBus.addListener(RegisterPayloadHandlersEvent.class, ElectroEnergeticsModule::registerPayloads);
        // CEE's own Connector sits flush on a Utility Pole MID (see PoleInsulators).
        modEventBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(PoleInsulators::install));

        CIOCreativeTab.add(CIOCreativeTab.Section.CLIMATE, CEE_AIRCON_MOTOR_BOTTOM_ITEM, CIOCreativeTab.CLIMATE_BLOCKS);
        CIOCreativeTab.add(CIOCreativeTab.Section.CLIMATE, CEE_AIRCON_MOTOR_BOTTOM_240_ITEM, CIOCreativeTab.CLIMATE_BLOCKS);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, CEE_DEB_RECTIFIER_TIER1_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, CEE_DEB_RECTIFIER_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, CEE_DEB_RECTIFIER_TIER3_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, CEE_DEB_RECTIFIER_TIER4_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, CEE_TELEPHONE_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, CEE_COMPACT_TRANSFORMER_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, CEE_INVERTER_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, CEE_STYLE_INVERTER_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, CEE_STREETLIGHT_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, CEE_MINI_CONNECTOR_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, CEE_EMBEDDED_CONNECTOR_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, UTILITY_POLE_ITEM);
        CIOCreativeTab.offerIcon(1, CIOItems.TAB_ICON);

        if (FMLEnvironment.dist.isClient()) {
            ElectroEnergeticsModuleClient.register(modEventBus);
        }
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(CreateInteroperable.ID);
        registrar.playToServer(InverterSettingsPacket.TYPE, InverterSettingsPacket.STREAM_CODEC, InverterSettingsPacket::handle);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CEE_AIRCON_MOTOR_BOTTOM_BE.get(),
                (be, side) -> be.getFluidHandler(side));
    }
}
