package com.cio.createinteroperable;

import com.cio.createinteroperable.compat.ElectroEnergeticsCompat;
import com.cio.createinteroperable.compat.PowerGridCompat;
import com.cio.createinteroperable.deb.CeeDebRectifierBlock;
import com.cio.createinteroperable.deb.CeePowerKitTier1Block;
import com.cio.createinteroperable.deb.CeePowerKitTier3Block;
import com.cio.createinteroperable.deb.CeePowerKitTier4Block;
import com.cio.createinteroperable.deb.CeeRedstoneSwitchBlock;
import com.cio.createinteroperable.deb.DebRectifierBlock;
import com.cio.createinteroperable.deb.RedstoneSwitchBlock;
import com.cio.createinteroperable.deb.PowerKitTier1Block;
import com.cio.createinteroperable.deb.PowerKitTier3Block;
import com.cio.createinteroperable.deb.PowerKitTier4Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CIOBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreateInteroperable.ID);

    // Power Grid and Electro Energetics are each optional (see
    // com.cio.createinteroperable.compat) — either one alone, or both, is
    // supported. Cached once here since this class gates a lot of entries on
    // them; both checks are cheap (ModList.isLoaded) but repeating the calls
    // dozens of times below would just be noise.
    private static final boolean PG = PowerGridCompat.present();
    private static final boolean CEE = ElectroEnergeticsCompat.present();
    private static final boolean BOTH = PG && CEE;

    /**
     * Design decision (2026-09-05): only the Grid (Double) Coupler, the three
     * Telephone variants, and the DEB/Power Kit family ship as real bridge
     * content. The multiblock Transformer, its 1x1 interim predecessor
     * (core/small), the single Coupler, and the custom PG connector reskins
     * are kept in source (in case of a future revival) but never register —
     * every field below gated on this flag is disabled, not deleted.
     */
    private static final boolean BRIDGE_EXTRAS = false;

    // --- Placeable markers (survival-obtainable, items exist for these) ---
    // The whole assembled-multiblock family (keystones/filler/assembled
    // columns) only makes sense as a complete PG<->CEE bridge — a lone
    // keystone with no counterpart to assemble against is meaningless — so
    // it's gated as one unit rather than picked apart per class.

    public static final DeferredBlock<InteroperablePgKeystoneBlock> PG_KEYSTONE = BRIDGE_EXTRAS && BOTH ? BLOCKS.register("pg_keystone",
            () -> new InteroperablePgKeystoneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops())) : null;

    public static final DeferredBlock<InteroperableCeeKeystoneBlock> CEE_KEYSTONE = BRIDGE_EXTRAS && BOTH ? BLOCKS.register("cee_keystone",
            () -> new InteroperableCeeKeystoneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops())) : null;

    public static final DeferredBlock<InteroperableFillerBlock> FILLER = BRIDGE_EXTRAS && BOTH ? BLOCKS.register("filler",
            () -> new InteroperableFillerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops())) : null;

    // --- Assembled result (never placed directly, no items registered) ---

    public static final DeferredBlock<InteroperablePgAssembledBlock> PG_ASSEMBLED = BRIDGE_EXTRAS && BOTH ? BLOCKS.register("pg_assembled",
            () -> new InteroperablePgAssembledBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .noLootTable())) : null;

    public static final DeferredBlock<InteroperableCeeAssembledBlock> CEE_ASSEMBLED = BRIDGE_EXTRAS && BOTH ? BLOCKS.register("cee_assembled",
            () -> new InteroperableCeeAssembledBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .noLootTable())) : null;

    public static final DeferredBlock<InteroperableCoreAssembledBlock> CORE_ASSEMBLED = BRIDGE_EXTRAS && BOTH ? BLOCKS.register("core_assembled",
            () -> new InteroperableCoreAssembledBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .noLootTable())) : null;

    // --- Interim 1x1 bridge (clone of Power Grid's Small Transformer
    // wrench-formation mechanic/assets, see SKILL.md) ---

    public static final DeferredBlock<InteroperableCoreBlock> CORE = BRIDGE_EXTRAS ? BLOCKS.register("core",
            () -> new InteroperableCoreBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops())) : null;

    // Never placed directly — produced by InteroperableCoreBlock#onWrenched, no item registered.
    // Needs both mods (bridge block, see cio-context) — InteroperableCoreBlock's
    // own wrench-conversion guards on BOTH too, so this never gets invoked
    // through a missing-mod code path either.
    public static final DeferredBlock<InteroperableSmallBlock> SMALL = BRIDGE_EXTRAS && BOTH ? BLOCKS.register("small",
            () -> new InteroperableSmallBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .noLootTable())) : null;

    // --- Interoperable Coupler (standalone one-way CPG<->CEE bridge with a
    // 3-position flow control + gauge-needle reversal dead time) ---

    public static final DeferredBlock<InteroperableCouplerBlock> COUPLER = BRIDGE_EXTRAS && BOTH ? BLOCKS.register("coupler",
            () -> new InteroperableCouplerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion())) : null;

    // Double Coupler — the real current-carrying bridge: a two-terminal winding
    // per side (PG +/- pair + CEE node pair), so downstream load reflects onto
    // the source grid. (The single COUPLER above is being repurposed as a
    // to-ground signal relay, twinned across two blocks later.)
    public static final DeferredBlock<InteroperableDoubleCouplerBlock> DOUBLE_COUPLER = BOTH ? BLOCKS.register("double_coupler",
            () -> new InteroperableDoubleCouplerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion())) : null;

    // --- Steam heating loop (Create Boiler -> Steam Outlet -> pipes -> Brass Heater -> Cold Sweat) ---

    public static final DeferredBlock<SteamOutletBlock> STEAM_OUTLET = BLOCKS.register("steam_outlet",
            () -> new SteamOutletBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<BrassHeaterBlock> BRASS_HEATER = BLOCKS.register("brass_heater",
            () -> new BrassHeaterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops()));

    // --- Telephone — three variants, same model. Interoperable needs both
    // mods; CPG/CEE Telephone each need only their own. ---

    public static final DeferredBlock<TelephoneBlock> TELEPHONE = BOTH ? BLOCKS.register("telephone",
            () -> new TelephoneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops())) : null;

    public static final DeferredBlock<CpgTelephoneBlock> CPG_TELEPHONE = PG ? BLOCKS.register("cpg_telephone",
            () -> new CpgTelephoneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops())) : null;

    public static final DeferredBlock<CeeTelephoneBlock> CEE_TELEPHONE = CEE ? BLOCKS.register("cee_telephone",
            () -> new CeeTelephoneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops())) : null;

    // --- Aesthetic PG connector reskins (see CIOConnectorBlock/CIOConnectorGlassBlock docs) ---
    // Not interoperable — plain PG-only, equivalent in specs to PG's own
    // wire_connector/heavy_wire_connector, just different models/textures.

    public static final DeferredBlock<CIOConnectorBlock> CIO_CONNECTOR = BRIDGE_EXTRAS && PG ? BLOCKS.register("cio_connector",
            () -> new CIOConnectorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops())) : null;

    public static final DeferredBlock<CIOConnectorGlassBlock> CIO_CONNECTOR_GLASS = BRIDGE_EXTRAS && PG ? BLOCKS.register("cio_connector_glass",
            () -> new CIOConnectorGlassBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops())) : null;

    // --- Domestic Electrical Board (Power Grid-fed replacement for
    // Crayfish Refurbished Furniture's Electricity Generator) — PG-only. ---

    public static final DeferredBlock<DebRectifierBlock> DEB_RECTIFIER = PG ? BLOCKS.register("deb_rectifier",
            () -> new DebRectifierBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion())) : null;

    // Tier 1 ("Improvised") — 12 V only + Power Feed, physical needle gauge.
    public static final DeferredBlock<PowerKitTier1Block> DEB_RECTIFIER_TIER1 = PG ? BLOCKS.register("deb_rectifier_tier1",
            () -> new PowerKitTier1Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion())) : null;

    // Tier 3 ("Commercial") — 240 V / 120 V mode-switch, HV + 120 V + 12 V feeds, thermal.
    public static final DeferredBlock<PowerKitTier3Block> DEB_RECTIFIER_TIER3 = PG ? BLOCKS.register("deb_rectifier_tier3",
            () -> new PowerKitTier3Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.5f, 8.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion())) : null;

    // Tier 4 ("Industrial") — 120 V / 240 V / 1 kV three-tap substation, HV + 120 V + 12 V feeds, thermal.
    public static final DeferredBlock<PowerKitTier4Block> DEB_RECTIFIER_TIER4 = PG ? BLOCKS.register("deb_rectifier_tier4",
            () -> new PowerKitTier4Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 10.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion())) : null;

    // Redstone Switch — a redstone-gated two-pole inline break. Throughput caps
    // and thermal model calibrated against the tier-2 Power Kit. PG-only.
    public static final DeferredBlock<RedstoneSwitchBlock> REDSTONE_SWITCH = PG ? BLOCKS.register("redstone_switch",
            () -> new RedstoneSwitchBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion())) : null;

    // --- CEE-wired Power Kits — same models as the CPG kits above, but their
    // own BlockEntity family (Cee*BlockEntity) runs entirely on Electro
    // Energetics' solver, with no Power Grid class anywhere in its hierarchy —
    // CEE-only. ---

    public static final DeferredBlock<CeeDebRectifierBlock> CEE_DEB_RECTIFIER = CEE ? BLOCKS.register("cee_deb_rectifier",
            () -> new CeeDebRectifierBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion())) : null;

    public static final DeferredBlock<CeePowerKitTier1Block> CEE_DEB_RECTIFIER_TIER1 = CEE ? BLOCKS.register("cee_deb_rectifier_tier1",
            () -> new CeePowerKitTier1Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion())) : null;

    public static final DeferredBlock<CeePowerKitTier3Block> CEE_DEB_RECTIFIER_TIER3 = CEE ? BLOCKS.register("cee_deb_rectifier_tier3",
            () -> new CeePowerKitTier3Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.5f, 8.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion())) : null;

    public static final DeferredBlock<CeePowerKitTier4Block> CEE_DEB_RECTIFIER_TIER4 = CEE ? BLOCKS.register("cee_deb_rectifier_tier4",
            () -> new CeePowerKitTier4Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 10.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion())) : null;

    public static final DeferredBlock<CeeRedstoneSwitchBlock> CEE_REDSTONE_SWITCH = CEE ? BLOCKS.register("cee_redstone_switch",
            () -> new CeeRedstoneSwitchBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion())) : null;

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
