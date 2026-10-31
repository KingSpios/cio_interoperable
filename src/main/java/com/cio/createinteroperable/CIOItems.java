package com.cio.createinteroperable;

import com.cio.createinteroperable.copycat.CopycatPipeCasingItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Items that exist on every install. Items for optional-mod content live in
 * their module (see {@link com.cio.createinteroperable.modules.CIOModules}).
 */
public class CIOItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreateInteroperable.ID);

    // --- Steam heating loop (unrelated to PG/CEE, always registered) ---

    public static final DeferredItem<BlockItem> STEAM_OUTLET = ITEMS.registerSimpleBlockItem(
            "steam_outlet", CIOBlocks.STEAM_OUTLET);

    public static final DeferredItem<BlockItem> BRASS_HEATER = ITEMS.registerSimpleBlockItem(
            "brass_heater", CIOBlocks.BRASS_HEATER);

    // Multi Radiator — only the two end caps are player-placed; the middle
    // segment is only ever produced by wrenching a valid run (see
    // RadiatorAssembly), same "no item" precedent as the real multiblock's
    // own assembled-only positions.
    public static final DeferredItem<BlockItem> RADIATOR_VALVE_NORTH = ITEMS.registerSimpleBlockItem(
            "multi_radiator_north", CIOBlocks.RADIATOR_VALVE_NORTH);

    public static final DeferredItem<BlockItem> RADIATOR_VALVE_SOUTH = ITEMS.registerSimpleBlockItem(
            "multi_radiator_south", CIOBlocks.RADIATOR_VALVE_SOUTH);

    // --- Aircon top + venter (the motor bottoms are in the PG / CEE modules) ---

    public static final DeferredItem<BlockItem> AIRCON_MOTOR_TOP = ITEMS.registerSimpleBlockItem(
            "aircon_motor_top", CIOBlocks.AIRCON_MOTOR_TOP);

    public static final DeferredItem<BlockItem> AIRCON_VENTER = ITEMS.registerSimpleBlockItem(
            "aircon_venter", CIOBlocks.AIRCON_VENTER);

    // --- Copycat Pipe Casing: used on a Fluid Pipe, like Copper Casing ---

    public static final DeferredItem<CopycatPipeCasingItem> COPYCAT_PIPE_CASING = ITEMS.registerItem(
            "copycat_pipe_casing", CopycatPipeCasingItem::new);

    // --- Creative tab icon ---

    /**
     * Never listed anywhere (hidden from recipe viewers by the
     * {@code c:hidden_from_recipe_viewers} tag): only the CIO tab's own icon. Its
     * item model is the Tier 2 Power Kit drawn larger than the real kit's
     * inventory icon. Offered by the Power Grid / Electro Energetics modules.
     */
    public static final DeferredItem<Item> TAB_ICON = ITEMS.registerSimpleItem("tab_icon");

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
