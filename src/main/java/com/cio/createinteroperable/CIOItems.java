package com.cio.createinteroperable;

import com.cio.createinteroperable.compat.ElectroEnergeticsCompat;
import com.cio.createinteroperable.compat.PowerGridCompat;
import net.minecraft.world.item.BlockItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CIOItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreateInteroperable.ID);

    // Mirrors CIOBlocks' PG/CEE/BOTH gates exactly — an item entry is only
    // ever registered when its backing block was.
    private static final boolean PG = PowerGridCompat.present();
    private static final boolean CEE = ElectroEnergeticsCompat.present();
    private static final boolean BOTH = PG && CEE;

    // Mirrors CIOBlocks.BRIDGE_EXTRAS exactly — see that field's doc.
    private static final boolean BRIDGE_EXTRAS = false;

    // Only the 3 placeable markers get items — the 3 assembled blocks are
    // never placed directly by a player (produced only by wrench assembly),
    // same as Power Grid's own TransformerMediumBlock has no item.
    public static final DeferredItem<BlockItem> PG_KEYSTONE = BRIDGE_EXTRAS && BOTH ? ITEMS.registerSimpleBlockItem(
            "pg_keystone", CIOBlocks.PG_KEYSTONE) : null;

    public static final DeferredItem<BlockItem> CEE_KEYSTONE = BRIDGE_EXTRAS && BOTH ? ITEMS.registerSimpleBlockItem(
            "cee_keystone", CIOBlocks.CEE_KEYSTONE) : null;

    public static final DeferredItem<BlockItem> FILLER = BRIDGE_EXTRAS && BOTH ? ITEMS.registerSimpleBlockItem(
            "filler", CIOBlocks.FILLER) : null;

    // --- Interim 1x1 bridge (see CIOBlocks) ---

    public static final DeferredItem<BlockItem> CORE = BRIDGE_EXTRAS ? ITEMS.registerSimpleBlockItem(
            "core", CIOBlocks.CORE) : null;

    // --- Interoperable Coupler ---

    public static final DeferredItem<BlockItem> COUPLER = BRIDGE_EXTRAS && BOTH ? ITEMS.registerSimpleBlockItem(
            "coupler", CIOBlocks.COUPLER) : null;

    public static final DeferredItem<BlockItem> DOUBLE_COUPLER = BOTH ? ITEMS.registerSimpleBlockItem(
            "double_coupler", CIOBlocks.DOUBLE_COUPLER) : null;

    // --- Steam heating loop (unrelated to PG/CEE, always registered) ---

    public static final DeferredItem<BlockItem> STEAM_OUTLET = ITEMS.registerSimpleBlockItem(
            "steam_outlet", CIOBlocks.STEAM_OUTLET);

    public static final DeferredItem<BlockItem> BRASS_HEATER = ITEMS.registerSimpleBlockItem(
            "brass_heater", CIOBlocks.BRASS_HEATER);

    // --- Telephone — Interoperable (both), CPG-only, CEE-only ---

    public static final DeferredItem<BlockItem> TELEPHONE = BOTH ? ITEMS.registerSimpleBlockItem(
            "telephone", CIOBlocks.TELEPHONE) : null;

    public static final DeferredItem<BlockItem> CPG_TELEPHONE = PG ? ITEMS.registerSimpleBlockItem(
            "cpg_telephone", CIOBlocks.CPG_TELEPHONE) : null;

    public static final DeferredItem<BlockItem> CEE_TELEPHONE = CEE ? ITEMS.registerSimpleBlockItem(
            "cee_telephone", CIOBlocks.CEE_TELEPHONE) : null;

    // --- Aesthetic PG connector reskins (PG-only) ---

    public static final DeferredItem<BlockItem> CIO_CONNECTOR = BRIDGE_EXTRAS && PG ? ITEMS.registerSimpleBlockItem(
            "cio_connector", CIOBlocks.CIO_CONNECTOR) : null;

    public static final DeferredItem<BlockItem> CIO_CONNECTOR_GLASS = BRIDGE_EXTRAS && PG ? ITEMS.registerSimpleBlockItem(
            "cio_connector_glass", CIOBlocks.CIO_CONNECTOR_GLASS) : null;

    // --- Domestic Electrical Board (PG-only) ---

    public static final DeferredItem<BlockItem> DEB_RECTIFIER = PG ? ITEMS.registerSimpleBlockItem(
            "deb_rectifier", CIOBlocks.DEB_RECTIFIER) : null;

    public static final DeferredItem<BlockItem> DEB_RECTIFIER_TIER1 = PG ? ITEMS.registerSimpleBlockItem(
            "deb_rectifier_tier1", CIOBlocks.DEB_RECTIFIER_TIER1) : null;

    public static final DeferredItem<BlockItem> DEB_RECTIFIER_TIER3 = PG ? ITEMS.registerSimpleBlockItem(
            "deb_rectifier_tier3", CIOBlocks.DEB_RECTIFIER_TIER3) : null;

    public static final DeferredItem<BlockItem> DEB_RECTIFIER_TIER4 = PG ? ITEMS.registerSimpleBlockItem(
            "deb_rectifier_tier4", CIOBlocks.DEB_RECTIFIER_TIER4) : null;

    public static final DeferredItem<BlockItem> REDSTONE_SWITCH = PG ? ITEMS.registerSimpleBlockItem(
            "redstone_switch", CIOBlocks.REDSTONE_SWITCH) : null;

    // CEE-wired Power Kits (CEE-only).
    public static final DeferredItem<BlockItem> CEE_DEB_RECTIFIER = CEE ? ITEMS.registerSimpleBlockItem(
            "cee_deb_rectifier", CIOBlocks.CEE_DEB_RECTIFIER) : null;

    public static final DeferredItem<BlockItem> CEE_DEB_RECTIFIER_TIER1 = CEE ? ITEMS.registerSimpleBlockItem(
            "cee_deb_rectifier_tier1", CIOBlocks.CEE_DEB_RECTIFIER_TIER1) : null;

    public static final DeferredItem<BlockItem> CEE_DEB_RECTIFIER_TIER3 = CEE ? ITEMS.registerSimpleBlockItem(
            "cee_deb_rectifier_tier3", CIOBlocks.CEE_DEB_RECTIFIER_TIER3) : null;

    public static final DeferredItem<BlockItem> CEE_DEB_RECTIFIER_TIER4 = CEE ? ITEMS.registerSimpleBlockItem(
            "cee_deb_rectifier_tier4", CIOBlocks.CEE_DEB_RECTIFIER_TIER4) : null;

    public static final DeferredItem<BlockItem> CEE_REDSTONE_SWITCH = CEE ? ITEMS.registerSimpleBlockItem(
            "cee_redstone_switch", CIOBlocks.CEE_REDSTONE_SWITCH) : null;

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
