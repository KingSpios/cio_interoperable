package com.cio.createinteroperable;

import net.minecraft.world.item.BlockItem;
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

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
