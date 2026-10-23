package com.cio.createinteroperable.modules;

import com.cio.createinteroperable.CIOCreativeTab;
import com.cio.createinteroperable.CreateInteroperable;
import com.cio.createinteroperable.InteroperableCeeAssembledBlock;
import com.cio.createinteroperable.InteroperableCeeKeystoneBlock;
import com.cio.createinteroperable.InteroperableCoreAssembledBlock;
import com.cio.createinteroperable.InteroperableCoreBlock;
import com.cio.createinteroperable.InteroperableCouplerBlock;
import com.cio.createinteroperable.InteroperableCouplerBlockEntity;
import com.cio.createinteroperable.InteroperableDoubleCouplerBlock;
import com.cio.createinteroperable.InteroperableDoubleCouplerBlockEntity;
import com.cio.createinteroperable.InteroperableFillerBlock;
import com.cio.createinteroperable.InteroperablePgAssembledBlock;
import com.cio.createinteroperable.InteroperablePgAssembledBlockEntity;
import com.cio.createinteroperable.InteroperablePgKeystoneBlock;
import com.cio.createinteroperable.InteroperableSmallBlock;
import com.cio.createinteroperable.InteroperableSmallBlockEntity;
import com.cio.createinteroperable.TelephoneBlock;
import com.cio.createinteroperable.TelephoneBlockEntity;
import com.cio.createinteroperable.compat.Mods;
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
 * PG&lt;-&gt;CEE bridge content, needing both grid mods: the Grid (Double)
 * Coupler and the Interoperable Telephone, plus the disabled bridge extras.
 * Touched only after {@link CIOModules} has checked {@link #REQUIRES}.
 */
public final class BridgeModule {
    public static final String REQUIRES = Mods.Id.POWER_GRID + "," + Mods.Id.ELECTRO_ENERGETICS;

    /**
     * Design decision (2026-09-05): only the Grid (Double) Coupler, the three
     * Telephone variants, and the DEB/Power Kit family ship as real bridge
     * content. The multiblock Transformer, its 1x1 interim predecessor
     * (core/small), the single Coupler, and the custom PG connector reskins
     * (in PowerGridModule) are kept in source (in case of a future revival)
     * but never register — every field gated on this flag is disabled, not
     * deleted.
     */
    public static final boolean BRIDGE_EXTRAS = false;

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreateInteroperable.ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateInteroperable.ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreateInteroperable.ID);

    private BridgeModule() {
    }

    // The whole assembled-multiblock family (keystones/filler/assembled
    // columns) only makes sense as a complete PG<->CEE bridge — a lone
    // keystone with no counterpart to assemble against is meaningless — so
    // it's gated as one unit rather than picked apart per class.

    // --- Blocks ---

    public static final DeferredBlock<InteroperablePgKeystoneBlock> PG_KEYSTONE = BRIDGE_EXTRAS ? BLOCKS.register("pg_keystone",
            () -> new InteroperablePgKeystoneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    )) : null;

    public static final DeferredBlock<InteroperableCeeKeystoneBlock> CEE_KEYSTONE = BRIDGE_EXTRAS ? BLOCKS.register("cee_keystone",
            () -> new InteroperableCeeKeystoneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    )) : null;

    public static final DeferredBlock<InteroperableFillerBlock> FILLER = BRIDGE_EXTRAS ? BLOCKS.register("filler",
            () -> new InteroperableFillerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    )) : null;

    public static final DeferredBlock<InteroperablePgAssembledBlock> PG_ASSEMBLED = BRIDGE_EXTRAS ? BLOCKS.register("pg_assembled",
            () -> new InteroperablePgAssembledBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .noLootTable())) : null;

    public static final DeferredBlock<InteroperableCeeAssembledBlock> CEE_ASSEMBLED = BRIDGE_EXTRAS ? BLOCKS.register("cee_assembled",
            () -> new InteroperableCeeAssembledBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .noLootTable())) : null;

    public static final DeferredBlock<InteroperableCoreAssembledBlock> CORE_ASSEMBLED = BRIDGE_EXTRAS ? BLOCKS.register("core_assembled",
            () -> new InteroperableCoreAssembledBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .noLootTable())) : null;

    public static final DeferredBlock<InteroperableCoreBlock> CORE = BRIDGE_EXTRAS ? BLOCKS.register("core",
            () -> new InteroperableCoreBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    )) : null;

    // Never placed directly — produced by InteroperableCoreBlock#onWrenched, no item registered.
    // Needs both mods (bridge block, see cio-context) — InteroperableCoreBlock's
    // own wrench-conversion guards on BOTH too, so this never gets invoked
    // through a missing-mod code path either.
    public static final DeferredBlock<InteroperableSmallBlock> SMALL = BRIDGE_EXTRAS ? BLOCKS.register("small",
            () -> new InteroperableSmallBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .noLootTable())) : null;

    public static final DeferredBlock<InteroperableCouplerBlock> COUPLER = BRIDGE_EXTRAS ? BLOCKS.register("coupler",
            () -> new InteroperableCouplerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .noOcclusion())) : null;

    // Double Coupler — the real current-carrying bridge: a two-terminal winding
    // per side (PG +/- pair + CEE node pair), so downstream load reflects onto
    // the source grid. (The single COUPLER above is being repurposed as a
    // to-ground signal relay, twinned across two blocks later.)
    public static final DeferredBlock<InteroperableDoubleCouplerBlock> DOUBLE_COUPLER = BLOCKS.register("double_coupler",
            () -> new InteroperableDoubleCouplerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .noOcclusion()));

    public static final DeferredBlock<TelephoneBlock> TELEPHONE = BLOCKS.register("telephone",
            () -> new TelephoneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    ));

    // --- Block entities ---

    // Only the PG column needs a real vanilla BlockEntity — the CEE column
    // uses CEE's own SimulatedDevice/DevicesSavedData system instead (no
    // BlockEntity at all), and the core column has no state to hold.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InteroperablePgAssembledBlockEntity>> PG_ASSEMBLED_BE =
            BRIDGE_EXTRAS ? BLOCK_ENTITIES.register("pg_assembled", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new InteroperablePgAssembledBlockEntity(BridgeModule.PG_ASSEMBLED_BE.get(), pos, state),
                    BridgeModule.PG_ASSEMBLED.get()).build(null)) : null;

    // Interim 1x1 bridge block's own BlockEntity — same reasoning as
    // PG_ASSEMBLED above (only the PG-electrical side needs a vanilla
    // BlockEntity; the CEE side is handled by SimulatedDevice/DevicesSavedData).
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InteroperableSmallBlockEntity>> SMALL_BE =
            BRIDGE_EXTRAS ? BLOCK_ENTITIES.register("small", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new InteroperableSmallBlockEntity(BridgeModule.SMALL_BE.get(), pos, state),
                    BridgeModule.SMALL.get()).build(null)) : null;

    // Interoperable Coupler — same reasoning as SMALL above (PG-electrical side
    // needs a vanilla BlockEntity; the CEE side is a SimulatedDevice). Reuses
    // InteroperableDevice / CIODevices.INTEROPERABLE unchanged.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InteroperableCouplerBlockEntity>> COUPLER_BE =
            BRIDGE_EXTRAS ? BLOCK_ENTITIES.register("coupler", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new InteroperableCouplerBlockEntity(BridgeModule.COUPLER_BE.get(), pos, state),
                    BridgeModule.COUPLER.get()).build(null)) : null;

    // Double Coupler — real current-carrying bridge (2-terminal winding per side).
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InteroperableDoubleCouplerBlockEntity>> DOUBLE_COUPLER_BE =
            BLOCK_ENTITIES.register("double_coupler", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new InteroperableDoubleCouplerBlockEntity(BridgeModule.DOUBLE_COUPLER_BE.get(), pos, state),
                    BridgeModule.DOUBLE_COUPLER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TelephoneBlockEntity>> TELEPHONE_BE =
            BLOCK_ENTITIES.register("telephone", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new TelephoneBlockEntity(BridgeModule.TELEPHONE_BE.get(), pos, state),
                    BridgeModule.TELEPHONE.get()).build(null));

    // --- Items ---

    // Only the 3 placeable markers get items — the 3 assembled blocks are
    // never placed directly by a player (produced only by wrench assembly),
    // same as Power Grid's own TransformerMediumBlock has no item.
    public static final DeferredItem<BlockItem> PG_KEYSTONE_ITEM = BRIDGE_EXTRAS ? ITEMS.registerSimpleBlockItem(
            "pg_keystone", BridgeModule.PG_KEYSTONE) : null;

    public static final DeferredItem<BlockItem> CEE_KEYSTONE_ITEM = BRIDGE_EXTRAS ? ITEMS.registerSimpleBlockItem(
            "cee_keystone", BridgeModule.CEE_KEYSTONE) : null;

    public static final DeferredItem<BlockItem> FILLER_ITEM = BRIDGE_EXTRAS ? ITEMS.registerSimpleBlockItem(
            "filler", BridgeModule.FILLER) : null;

    public static final DeferredItem<BlockItem> CORE_ITEM = BRIDGE_EXTRAS ? ITEMS.registerSimpleBlockItem(
            "core", BridgeModule.CORE) : null;

    public static final DeferredItem<BlockItem> COUPLER_ITEM = BRIDGE_EXTRAS ? ITEMS.registerSimpleBlockItem(
            "coupler", BridgeModule.COUPLER) : null;

    public static final DeferredItem<BlockItem> DOUBLE_COUPLER_ITEM = ITEMS.registerSimpleBlockItem(
            "double_coupler", BridgeModule.DOUBLE_COUPLER);

    public static final DeferredItem<BlockItem> TELEPHONE_ITEM = ITEMS.registerSimpleBlockItem(
            "telephone", BridgeModule.TELEPHONE);

    static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        ITEMS.register(modEventBus);

        CIOCreativeTab.add(CIOCreativeTab.Section.BRIDGE, DOUBLE_COUPLER_ITEM);
        CIOCreativeTab.add(CIOCreativeTab.Section.BRIDGE, TELEPHONE_ITEM);

        if (FMLEnvironment.dist.isClient()) {
            BridgeModuleClient.register(modEventBus);
        }
    }
}
