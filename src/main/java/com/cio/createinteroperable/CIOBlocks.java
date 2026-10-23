package com.cio.createinteroperable;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Blocks that exist on every install, whatever else is loaded. Everything
 * that needs Power Grid, Electro Energetics or another optional mod is
 * registered by its module instead (see
 * {@link com.cio.createinteroperable.modules.CIOModules}).
 */
public class CIOBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreateInteroperable.ID);

    // --- Steam heating loop (Create Boiler -> Steam Outlet -> pipes -> Brass Heater -> Cold Sweat) ---

    public static final DeferredBlock<SteamOutletBlock> STEAM_OUTLET = BLOCKS.register("steam_outlet",
            () -> new SteamOutletBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    ));

    public static final DeferredBlock<BrassHeaterBlock> BRASS_HEATER = BLOCKS.register("brass_heater",
            () -> new BrassHeaterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    ));

    // --- Multi Radiator — extendable steam->water multiblock (scaffold, see
    // RadiatorAssembly). North/South are player-placed end caps; middle is
    // only ever produced by wrenching a valid run (see RadiatorMiddleBlock's
    // own TODO on why it has no loot table yet). ---

    public static final DeferredBlock<RadiatorValveNorthBlock> RADIATOR_VALVE_NORTH = BLOCKS.register("multi_radiator_north",
            () -> new RadiatorValveNorthBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .emissiveRendering((state, level, pos) -> isGlowingTier(state.getValue(RadiatorValveNorthBlock.HEAT_LEVEL)))));

    public static final DeferredBlock<RadiatorValveSouthBlock> RADIATOR_VALVE_SOUTH = BLOCKS.register("multi_radiator_south",
            () -> new RadiatorValveSouthBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .emissiveRendering((state, level, pos) -> isGlowingTier(state.getValue(RadiatorValveSouthBlock.HEAT_LEVEL)))));

    public static final DeferredBlock<RadiatorMiddleBlock> RADIATOR_MIDDLE = BLOCKS.register("multi_radiator_middle",
            () -> new RadiatorMiddleBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .noLootTable()
                    .emissiveRendering((state, level, pos) -> isGlowingTier(state.getValue(RadiatorMiddleBlock.HEAT_LEVEL)))));

    /**
     * {@code Properties#emissiveRendering} is a whole-block flag (see
     * {@code BlockBehaviour.BlockStateBase#emissiveRendering} — confirmed via
     * javap, it forces every quad full-bright, not just a chosen texture
     * slot), so this can't isolate just the {@code minipipe} elements' "0"
     * texture from the block's brass/basin geometry. Accepted anyway: the
     * pipe run dominates each segment's surface area, so the visible effect
     * still reads as "the pipes glow" at WARM/HOT/BLAZING. No light is
     * actually emitted (see {@code getLightEmission} — untouched) — this only
     * changes how existing light renders on the model.
     */
    private static boolean isGlowingTier(BrassHeaterBlock.HeatLevel tier) {
        return tier == BrassHeaterBlock.HeatLevel.WARM
                || tier == BrassHeaterBlock.HeatLevel.HOT
                || tier == BrassHeaterBlock.HeatLevel.BLAZING;
    }

    // --- Aircon — gas-mechanics phase. The motor bottom needs real electrical
    // terminals, so it comes in a Power Grid variant (PowerGridModule) and an
    // Electro Energetics variant (ElectroEnergeticsModule) — see
    // AirconMotorBottomInfo's own doc. The top (fan) and the venter are both
    // electrical-backend-agnostic (see AirconMotorTopBlockEntity/
    // AirconVenterBlockEntity's own docs) and register unconditionally, so
    // either bottom variant — or neither, on an install with neither mod —
    // can pair with the exact same top half. ---

    public static final DeferredBlock<AirconMotorTopBlock> AIRCON_MOTOR_TOP = BLOCKS.register("aircon_motor_top",
            () -> new AirconMotorTopBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    ));

    public static final DeferredBlock<AirconVenterBlock> AIRCON_VENTER = BLOCKS.register("aircon_venter",
            () -> new AirconVenterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    ));

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
