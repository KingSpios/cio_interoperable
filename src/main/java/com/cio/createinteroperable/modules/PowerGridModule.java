package com.cio.createinteroperable.modules;

import com.cio.createinteroperable.thermal.cpg.AirconMotorBottom240Block;
import com.cio.createinteroperable.thermal.cpg.AirconMotorBottomBlock;
import com.cio.createinteroperable.thermal.cpg.AirconMotorBottomBlockEntity;
import com.cio.createinteroperable.cpg.CIOConnectorBlock;
import com.cio.createinteroperable.cpg.CIOConnectorGlassBlock;
import com.cio.createinteroperable.CIOCreativeTab;
import com.cio.createinteroperable.CIOItems;
import com.cio.createinteroperable.cpg.CIODoubleConnectorBlock;
import com.cio.createinteroperable.cpg.CIODoubleConnectorBlockEntity;
import com.cio.createinteroperable.embedded.cpg.CpgEmbeddedConnectorBlock;
import com.cio.createinteroperable.embedded.cpg.CpgEmbeddedConnectorBlockEntity;
import com.cio.createinteroperable.telephone.cpg.CpgTelephoneBlock;
import com.cio.createinteroperable.telephone.cpg.CpgTelephoneBlockEntity;
import com.cio.createinteroperable.CreateInteroperable;
import com.cio.createinteroperable.compat.Mods;
import com.cio.createinteroperable.powerkit.cpg.DebRectifierBlock;
import com.cio.createinteroperable.powerkit.cpg.PowerKitTier1Block;
import com.cio.createinteroperable.powerkit.cpg.PowerKitTier1BlockEntity;
import com.cio.createinteroperable.powerkit.cpg.PowerKitTier2BlockEntity;
import com.cio.createinteroperable.powerkit.cpg.PowerKitTier3Block;
import com.cio.createinteroperable.powerkit.cpg.PowerKitTier3BlockEntity;
import com.cio.createinteroperable.powerkit.cpg.PowerKitTier4Block;
import com.cio.createinteroperable.powerkit.cpg.PowerKitTier4BlockEntity;
import com.cio.createinteroperable.pole.cpg.CpgUtilityPoleArmBlock;
import com.cio.createinteroperable.pole.cpg.CpgUtilityPoleArmBlockEntity;
import com.cio.createinteroperable.pole.cpg.CpgUtilityPoleBlock;
import com.cio.createinteroperable.pole.cpg.CpgUtilityPoleBlockEntity;
import com.cio.createinteroperable.streetlight.StreetlightLightBlock;
import com.cio.createinteroperable.streetlight.cpg.CpgStreetlightBlock;
import com.cio.createinteroperable.streetlight.cpg.CpgStreetlightBlockEntity;
import com.cio.createinteroperable.cpg.spectral.SpectralBeamLightBlock;
import com.cio.createinteroperable.cpg.spectral.SpectralBulb;
import com.cio.createinteroperable.cpg.spectral.SpectralBulbWard;
import com.cio.createinteroperable.cpg.spectral.SpectralKind;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.patryk3211.powergrid.electricity.wireconnector.ConnectorBlockEntity;

/**
 * Power Grid content: everything CIO registers only when Power Grid is
 * installed — the PG Aircon motor bottoms, the CPG Telephone, the CPG Double
 * Connector, the CPG Utility Pole, the CPG Streetlight, the UV / IR Bulbs and the Domestic Electrical Board / CPG Power Kits (the
 * experimental CPG Redstone Switch is in {@link RedstoneSwitchModule}). Touched only after {@link CIOModules} has checked
 * {@link #REQUIRES}.
 */
public final class PowerGridModule {
    public static final String REQUIRES = Mods.Id.POWER_GRID;

    /** The disabled PG connector reskins share the bridge extras' switch. */
    private static final boolean BRIDGE_EXTRAS = BridgeModule.BRIDGE_EXTRAS;

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreateInteroperable.ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateInteroperable.ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreateInteroperable.ID);

    private PowerGridModule() {
    }

    // --- Blocks ---

    public static final DeferredBlock<CpgTelephoneBlock> CPG_TELEPHONE = BLOCKS.register("cpg_telephone",
            () -> new CpgTelephoneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    ));

    // Aesthetic PG connector reskins (see CIOConnectorBlock/CIOConnectorGlassBlock
    // docs): plain PG-only, equivalent in specs to PG's own wire_connector /
    // heavy_wire_connector, just different models/textures. Disabled with the
    // bridge extras.
    public static final DeferredBlock<CIOConnectorBlock> CIO_CONNECTOR = BRIDGE_EXTRAS ? BLOCKS.register("cio_connector",
            () -> new CIOConnectorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    )) : null;

    public static final DeferredBlock<CIOConnectorGlassBlock> CIO_CONNECTOR_GLASS = BRIDGE_EXTRAS ? BLOCKS.register("cio_connector_glass",
            () -> new CIOConnectorGlassBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    )) : null;

    // Double Connector — two independent, unconnected terminal points in one
    // block (see CIODoubleConnectorBlock docs). Real, active content, unlike
    // the reskins above.
    public static final DeferredBlock<CIODoubleConnectorBlock> CIO_DOUBLE_CONNECTOR = BLOCKS.register("cpg_double_connector",
            () -> new CIODoubleConnectorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    ));

    /** CPG Embedded Connector &mdash; two terminals in a hollow, wall-copying block with a lid; its CEE twin is in ElectroEnergeticsModule. */
    public static final DeferredBlock<CpgEmbeddedConnectorBlock> CPG_EMBEDDED_CONNECTOR = BLOCKS.register("cpg_embedded_connector",
            () -> new CpgEmbeddedConnectorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(1.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    ));

    /** CPG Utility Pole &mdash; the CEE pole's twin (see ElectroEnergeticsModule), its pins carrying Power Grid wires. */
    public static final DeferredBlock<CpgUtilityPoleBlock> CPG_UTILITY_POLE = BLOCKS.register("cpg_utility_pole",
            () -> new CpgUtilityPoleBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .forceSolidOn()
                    .instrument(NoteBlockInstrument.BASS)
                    .strength(2.0f, 3.0f)
                    .sound(SoundType.WOOD)
                    ));

    /** A CPG Utility Pole's extended arm (no item: a fence on a pole mount places it, and it drops what it was made of). */
    public static final DeferredBlock<CpgUtilityPoleArmBlock> CPG_UTILITY_POLE_ARM = BLOCKS.register("cpg_utility_pole_arm",
            () -> new CpgUtilityPoleArmBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .instrument(NoteBlockInstrument.BASS)
                    .strength(2.0f, 3.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)
                    .noLootTable()
                    ));

    /** CPG Streetlight &mdash; the CEE Streetlight's twin (see ElectroEnergeticsModule), wired to Power Grid. */
    public static final DeferredBlock<CpgStreetlightBlock> CPG_STREETLIGHT = BLOCKS.register("cpg_streetlight",
            () -> new CpgStreetlightBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.5f, 6.0f)
                    .sound(SoundType.LANTERN)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    ));

    /** The CPG Streetlight's projected light (invisible, air-like, no item). */
    public static final DeferredBlock<StreetlightLightBlock> CPG_STREETLIGHT_LIGHT = BLOCKS.register("cpg_streetlight_light",
            () -> new StreetlightLightBlock(BlockBehaviour.Properties.of()));

    /** The faint light a Factory Light holding a lit UV / IR Bulb projects down its beam (invisible, air-like, no item). */
    public static final DeferredBlock<SpectralBeamLightBlock> SPECTRAL_BEAM_LIGHT = BLOCKS.register("spectral_beam_light",
            () -> new SpectralBeamLightBlock(BlockBehaviour.Properties.of()));

    public static final DeferredBlock<AirconMotorBottomBlock> AIRCON_MOTOR_BOTTOM = BLOCKS.register("aircon_motor_bottom",
            () -> new AirconMotorBottomBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    ));

    public static final DeferredBlock<AirconMotorBottom240Block> AIRCON_MOTOR_BOTTOM_240 = BLOCKS.register("aircon_motor_bottom_240",
            () -> new AirconMotorBottom240Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    ));

    // --- Domestic Electrical Board (Power Grid-fed replacement for Crayfish
    // Refurbished Furniture's Electricity Generator) and its Power Kit tiers. ---

    public static final DeferredBlock<DebRectifierBlock> DEB_RECTIFIER = BLOCKS.register("deb_rectifier",
            () -> new DebRectifierBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .noOcclusion()));

    // Tier 1 ("Improvised") — 12 V only + Power Feed, physical needle gauge.
    public static final DeferredBlock<PowerKitTier1Block> DEB_RECTIFIER_TIER1 = BLOCKS.register("deb_rectifier_tier1",
            () -> new PowerKitTier1Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .noOcclusion()));

    // Tier 3 ("Commercial") — 240 V / 120 V mode-switch, HV + 120 V + 12 V feeds, thermal.
    public static final DeferredBlock<PowerKitTier3Block> DEB_RECTIFIER_TIER3 = BLOCKS.register("deb_rectifier_tier3",
            () -> new PowerKitTier3Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.5f, 8.0f)
                    .noOcclusion()));

    // Tier 4 ("Industrial") — 120 V / 240 V / 1 kV three-tap substation, HV + 120 V + 12 V feeds, thermal.
    public static final DeferredBlock<PowerKitTier4Block> DEB_RECTIFIER_TIER4 = BLOCKS.register("deb_rectifier_tier4",
            () -> new PowerKitTier4Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 10.0f)
                    .noOcclusion()));

    // --- Block entities ---

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CpgTelephoneBlockEntity>> CPG_TELEPHONE_BE =
            BLOCK_ENTITIES.register("cpg_telephone", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new CpgTelephoneBlockEntity(PowerGridModule.CPG_TELEPHONE_BE.get(), pos, state),
                    PowerGridModule.CPG_TELEPHONE.get()).build(null));

    // --- Aesthetic PG connector reskins (see CIOConnectorBlock/CIOConnectorGlassBlock) ---
    // Reuses PG's own real ConnectorBlockEntity class verbatim (no CIO-side
    // circuit logic needed — a connector is just a 1-terminal pass-through).
    // Same dual-validBlocks pattern PG's own WIRE_CONNECTOR type uses for its
    // light/heavy pair. PG-only.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConnectorBlockEntity>> CONNECTOR_BE =
            BRIDGE_EXTRAS ? BLOCK_ENTITIES.register("connector", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new ConnectorBlockEntity(PowerGridModule.CONNECTOR_BE.get(), pos, state),
                    PowerGridModule.CIO_CONNECTOR.get(), PowerGridModule.CIO_CONNECTOR_GLASS.get()).build(null)) : null;

    // Double Connector — real, active content (unlike the disabled reskins
    // above): two independent, unconnected terminal points in one block. Own
    // BlockEntity (CIODoubleConnectorBlockEntity, setTerminalCount(2)) since
    // ConnectorBlockEntity itself hardcodes 1.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CIODoubleConnectorBlockEntity>> DOUBLE_CONNECTOR_BE =
            BLOCK_ENTITIES.register("cpg_double_connector", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new CIODoubleConnectorBlockEntity(PowerGridModule.DOUBLE_CONNECTOR_BE.get(), pos, state),
                    PowerGridModule.CIO_DOUBLE_CONNECTOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CpgEmbeddedConnectorBlockEntity>> CPG_EMBEDDED_CONNECTOR_BE =
            BLOCK_ENTITIES.register("cpg_embedded_connector", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new CpgEmbeddedConnectorBlockEntity(PowerGridModule.CPG_EMBEDDED_CONNECTOR_BE.get(), pos, state),
                    PowerGridModule.CPG_EMBEDDED_CONNECTOR.get()).build(null));

    // Domestic Electrical Board (PG-only tiers) — its own BlockEntity carries
    // the PG 4-terminal circuit and the Crayfish ISourceNode bookkeeping. The
    // CEE-wired tiers (ElectroEnergeticsModule) are a fully separate BlockEntity
    // family (CeeDebRectifierBlockEntity) with no Power Grid type in its hierarchy.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PowerKitTier2BlockEntity>> DEB_RECTIFIER_BE =
            BLOCK_ENTITIES.register("deb_rectifier", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new PowerKitTier2BlockEntity(PowerGridModule.DEB_RECTIFIER_BE.get(), pos, state),
                    PowerGridModule.DEB_RECTIFIER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PowerKitTier1BlockEntity>> DEB_RECTIFIER_TIER1_BE =
            BLOCK_ENTITIES.register("deb_rectifier_tier1", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new PowerKitTier1BlockEntity(PowerGridModule.DEB_RECTIFIER_TIER1_BE.get(), pos, state),
                    PowerGridModule.DEB_RECTIFIER_TIER1.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PowerKitTier3BlockEntity>> DEB_RECTIFIER_TIER3_BE =
            BLOCK_ENTITIES.register("deb_rectifier_tier3", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new PowerKitTier3BlockEntity(PowerGridModule.DEB_RECTIFIER_TIER3_BE.get(), pos, state),
                    PowerGridModule.DEB_RECTIFIER_TIER3.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PowerKitTier4BlockEntity>> DEB_RECTIFIER_TIER4_BE =
            BLOCK_ENTITIES.register("deb_rectifier_tier4", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new PowerKitTier4BlockEntity(PowerGridModule.DEB_RECTIFIER_TIER4_BE.get(), pos, state),
                    PowerGridModule.DEB_RECTIFIER_TIER4.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CpgUtilityPoleBlockEntity>> CPG_UTILITY_POLE_BE =
            BLOCK_ENTITIES.register("cpg_utility_pole", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new CpgUtilityPoleBlockEntity(PowerGridModule.CPG_UTILITY_POLE_BE.get(), pos, state),
                    PowerGridModule.CPG_UTILITY_POLE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CpgUtilityPoleArmBlockEntity>> CPG_UTILITY_POLE_ARM_BE =
            BLOCK_ENTITIES.register("cpg_utility_pole_arm", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new CpgUtilityPoleArmBlockEntity(PowerGridModule.CPG_UTILITY_POLE_ARM_BE.get(), pos, state),
                    PowerGridModule.CPG_UTILITY_POLE_ARM.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CpgStreetlightBlockEntity>> CPG_STREETLIGHT_BE =
            BLOCK_ENTITIES.register("cpg_streetlight", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new CpgStreetlightBlockEntity(PowerGridModule.CPG_STREETLIGHT_BE.get(), pos, state),
                    PowerGridModule.CPG_STREETLIGHT.get()).build(null));

    // Aircon motor bottom (both voltages share one type) — see CIOBlocks.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AirconMotorBottomBlockEntity>> AIRCON_MOTOR_BOTTOM_BE =
            BLOCK_ENTITIES.register("aircon_motor_bottom", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new AirconMotorBottomBlockEntity(PowerGridModule.AIRCON_MOTOR_BOTTOM_BE.get(), pos, state),
                    PowerGridModule.AIRCON_MOTOR_BOTTOM.get(), PowerGridModule.AIRCON_MOTOR_BOTTOM_240.get()).build(null));

    // --- Items ---

    public static final DeferredItem<BlockItem> CPG_TELEPHONE_ITEM = ITEMS.registerSimpleBlockItem(
            "cpg_telephone", PowerGridModule.CPG_TELEPHONE);

    public static final DeferredItem<BlockItem> CPG_UTILITY_POLE_ITEM = ITEMS.registerSimpleBlockItem(
            "cpg_utility_pole", PowerGridModule.CPG_UTILITY_POLE);

    public static final DeferredItem<BlockItem> CPG_STREETLIGHT_ITEM = ITEMS.registerSimpleBlockItem(
            "cpg_streetlight", PowerGridModule.CPG_STREETLIGHT);

    public static final DeferredItem<BlockItem> CIO_CONNECTOR_ITEM = BRIDGE_EXTRAS ? ITEMS.registerSimpleBlockItem(
            "cio_connector", PowerGridModule.CIO_CONNECTOR) : null;

    public static final DeferredItem<BlockItem> CIO_CONNECTOR_GLASS_ITEM = BRIDGE_EXTRAS ? ITEMS.registerSimpleBlockItem(
            "cio_connector_glass", PowerGridModule.CIO_CONNECTOR_GLASS) : null;

    public static final DeferredItem<BlockItem> CIO_DOUBLE_CONNECTOR_ITEM = ITEMS.registerSimpleBlockItem(
            "cpg_double_connector", PowerGridModule.CIO_DOUBLE_CONNECTOR);

    public static final DeferredItem<BlockItem> CPG_EMBEDDED_CONNECTOR_ITEM = ITEMS.registerSimpleBlockItem(
            "cpg_embedded_connector", PowerGridModule.CPG_EMBEDDED_CONNECTOR);

    public static final DeferredItem<BlockItem> DEB_RECTIFIER_ITEM = ITEMS.registerSimpleBlockItem(
            "deb_rectifier", PowerGridModule.DEB_RECTIFIER);

    public static final DeferredItem<BlockItem> DEB_RECTIFIER_TIER1_ITEM = ITEMS.registerSimpleBlockItem(
            "deb_rectifier_tier1", PowerGridModule.DEB_RECTIFIER_TIER1);

    public static final DeferredItem<BlockItem> DEB_RECTIFIER_TIER3_ITEM = ITEMS.registerSimpleBlockItem(
            "deb_rectifier_tier3", PowerGridModule.DEB_RECTIFIER_TIER3);

    public static final DeferredItem<BlockItem> DEB_RECTIFIER_TIER4_ITEM = ITEMS.registerSimpleBlockItem(
            "deb_rectifier_tier4", PowerGridModule.DEB_RECTIFIER_TIER4);

    public static final DeferredItem<BlockItem> AIRCON_MOTOR_BOTTOM_ITEM = ITEMS.registerSimpleBlockItem(
            "aircon_motor_bottom", PowerGridModule.AIRCON_MOTOR_BOTTOM);

    public static final DeferredItem<BlockItem> AIRCON_MOTOR_BOTTOM_240_ITEM = ITEMS.registerSimpleBlockItem(
            "aircon_motor_bottom_240", PowerGridModule.AIRCON_MOTOR_BOTTOM_240);

    /** UV Bulb &mdash; a PG light bulb that wards off hostile mobs instead of lighting the room, and scorches the undead. */
    public static final DeferredItem<SpectralBulb> UV_LIGHT_BULB = ITEMS.register("uv_light_bulb",
            () -> new SpectralBulb(SpectralKind.UV, new Item.Properties()));

    /** IR Bulb &mdash; the UV Bulb's twin: same ward, but makes every mob around it glow instead of hurting them. */
    public static final DeferredItem<SpectralBulb> IR_LIGHT_BULB = ITEMS.register("ir_light_bulb",
            () -> new SpectralBulb(SpectralKind.IR, new Item.Properties()));

    static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(RegisterCapabilitiesEvent.class, PowerGridModule::registerCapabilities);

        CIOCreativeTab.add(CIOCreativeTab.Section.CLIMATE, AIRCON_MOTOR_BOTTOM_ITEM, CIOCreativeTab.CLIMATE_BLOCKS);
        CIOCreativeTab.add(CIOCreativeTab.Section.CLIMATE, AIRCON_MOTOR_BOTTOM_240_ITEM, CIOCreativeTab.CLIMATE_BLOCKS);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, DEB_RECTIFIER_TIER1_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, DEB_RECTIFIER_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, DEB_RECTIFIER_TIER3_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, DEB_RECTIFIER_TIER4_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, CPG_TELEPHONE_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, CIO_DOUBLE_CONNECTOR_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, CPG_EMBEDDED_CONNECTOR_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, CPG_UTILITY_POLE_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, CPG_STREETLIGHT_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, UV_LIGHT_BULB);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, IR_LIGHT_BULB);
        NeoForge.EVENT_BUS.addListener(SpectralBulbWard::onPositionCheck);
        CIOCreativeTab.offerIcon(2, CIOItems.TAB_ICON);

        if (FMLEnvironment.dist.isClient()) {
            PowerGridModuleClient.register(modEventBus);
        }
    }

    /**
     * The PG Aircon motor bottom's fluid tanks. This lambda's parameter is a
     * PG-rooted BlockEntity, which is why it must never sit in an
     * {@code @EventBusSubscriber} class (NeoForge reflects over those
     * unconditionally) — see CIOCapabilities.
     */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, AIRCON_MOTOR_BOTTOM_BE.get(),
                (be, side) -> be.getFluidHandler(side));
    }
}
