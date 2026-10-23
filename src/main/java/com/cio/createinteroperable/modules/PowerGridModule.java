package com.cio.createinteroperable.modules;

import com.cio.createinteroperable.AirconMotorBottom240Block;
import com.cio.createinteroperable.AirconMotorBottomBlock;
import com.cio.createinteroperable.AirconMotorBottomBlockEntity;
import com.cio.createinteroperable.CIOConnectorBlock;
import com.cio.createinteroperable.CIOConnectorGlassBlock;
import com.cio.createinteroperable.CIOCreativeTab;
import com.cio.createinteroperable.CIODoubleConnectorBlock;
import com.cio.createinteroperable.CIODoubleConnectorBlockEntity;
import com.cio.createinteroperable.CpgTelephoneBlock;
import com.cio.createinteroperable.CpgTelephoneBlockEntity;
import com.cio.createinteroperable.CreateInteroperable;
import com.cio.createinteroperable.compat.Mods;
import com.cio.createinteroperable.deb.DebRectifierBlock;
import com.cio.createinteroperable.deb.PowerKitTier1Block;
import com.cio.createinteroperable.deb.PowerKitTier1BlockEntity;
import com.cio.createinteroperable.deb.PowerKitTier2BlockEntity;
import com.cio.createinteroperable.deb.PowerKitTier3Block;
import com.cio.createinteroperable.deb.PowerKitTier3BlockEntity;
import com.cio.createinteroperable.deb.PowerKitTier4Block;
import com.cio.createinteroperable.deb.PowerKitTier4BlockEntity;
import com.cio.createinteroperable.deb.RedstoneSwitchBlock;
import com.cio.createinteroperable.deb.RedstoneSwitchBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
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
import org.patryk3211.powergrid.electricity.wireconnector.ConnectorBlockEntity;

/**
 * Power Grid content: everything CIO registers only when Power Grid is
 * installed — the PG Aircon motor bottoms, the CPG Telephone, the CPG Double
 * Connector, the Domestic Electrical Board / CPG Power Kits and the CPG
 * Redstone Switch. Touched only after {@link CIOModules} has checked
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

    // Redstone Switch — a redstone-gated two-pole inline break. Throughput caps
    // and thermal model calibrated against the tier-2 Power Kit. PG-only.
    public static final DeferredBlock<RedstoneSwitchBlock> REDSTONE_SWITCH = BLOCKS.register("redstone_switch",
            () -> new RedstoneSwitchBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RedstoneSwitchBlockEntity>> REDSTONE_SWITCH_BE =
            BLOCK_ENTITIES.register("redstone_switch", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new RedstoneSwitchBlockEntity(PowerGridModule.REDSTONE_SWITCH_BE.get(), pos, state),
                    PowerGridModule.REDSTONE_SWITCH.get()).build(null));

    // Aircon motor bottom (both voltages share one type) — see CIOBlocks.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AirconMotorBottomBlockEntity>> AIRCON_MOTOR_BOTTOM_BE =
            BLOCK_ENTITIES.register("aircon_motor_bottom", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new AirconMotorBottomBlockEntity(PowerGridModule.AIRCON_MOTOR_BOTTOM_BE.get(), pos, state),
                    PowerGridModule.AIRCON_MOTOR_BOTTOM.get(), PowerGridModule.AIRCON_MOTOR_BOTTOM_240.get()).build(null));

    // --- Items ---

    public static final DeferredItem<BlockItem> CPG_TELEPHONE_ITEM = ITEMS.registerSimpleBlockItem(
            "cpg_telephone", PowerGridModule.CPG_TELEPHONE);

    public static final DeferredItem<BlockItem> CIO_CONNECTOR_ITEM = BRIDGE_EXTRAS ? ITEMS.registerSimpleBlockItem(
            "cio_connector", PowerGridModule.CIO_CONNECTOR) : null;

    public static final DeferredItem<BlockItem> CIO_CONNECTOR_GLASS_ITEM = BRIDGE_EXTRAS ? ITEMS.registerSimpleBlockItem(
            "cio_connector_glass", PowerGridModule.CIO_CONNECTOR_GLASS) : null;

    public static final DeferredItem<BlockItem> CIO_DOUBLE_CONNECTOR_ITEM = ITEMS.registerSimpleBlockItem(
            "cpg_double_connector", PowerGridModule.CIO_DOUBLE_CONNECTOR);

    public static final DeferredItem<BlockItem> DEB_RECTIFIER_ITEM = ITEMS.registerSimpleBlockItem(
            "deb_rectifier", PowerGridModule.DEB_RECTIFIER);

    public static final DeferredItem<BlockItem> DEB_RECTIFIER_TIER1_ITEM = ITEMS.registerSimpleBlockItem(
            "deb_rectifier_tier1", PowerGridModule.DEB_RECTIFIER_TIER1);

    public static final DeferredItem<BlockItem> DEB_RECTIFIER_TIER3_ITEM = ITEMS.registerSimpleBlockItem(
            "deb_rectifier_tier3", PowerGridModule.DEB_RECTIFIER_TIER3);

    public static final DeferredItem<BlockItem> DEB_RECTIFIER_TIER4_ITEM = ITEMS.registerSimpleBlockItem(
            "deb_rectifier_tier4", PowerGridModule.DEB_RECTIFIER_TIER4);

    public static final DeferredItem<BlockItem> REDSTONE_SWITCH_ITEM = ITEMS.registerSimpleBlockItem(
            "redstone_switch", PowerGridModule.REDSTONE_SWITCH);

    public static final DeferredItem<BlockItem> AIRCON_MOTOR_BOTTOM_ITEM = ITEMS.registerSimpleBlockItem(
            "aircon_motor_bottom", PowerGridModule.AIRCON_MOTOR_BOTTOM);

    public static final DeferredItem<BlockItem> AIRCON_MOTOR_BOTTOM_240_ITEM = ITEMS.registerSimpleBlockItem(
            "aircon_motor_bottom_240", PowerGridModule.AIRCON_MOTOR_BOTTOM_240);

    static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(RegisterCapabilitiesEvent.class, PowerGridModule::registerCapabilities);

        CIOCreativeTab.add(CIOCreativeTab.Section.CLIMATE, AIRCON_MOTOR_BOTTOM_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.CLIMATE, AIRCON_MOTOR_BOTTOM_240_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, DEB_RECTIFIER_TIER1_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, DEB_RECTIFIER_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, DEB_RECTIFIER_TIER3_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, DEB_RECTIFIER_TIER4_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, CPG_TELEPHONE_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.DEVICES, CIO_DOUBLE_CONNECTOR_ITEM);
        CIOCreativeTab.offerIcon(2, DEB_RECTIFIER_ITEM);

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
