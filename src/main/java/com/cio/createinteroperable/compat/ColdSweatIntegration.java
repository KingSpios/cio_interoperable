package com.cio.createinteroperable.compat;

import com.cio.createinteroperable.BrassHeaterBlock;
import com.cio.createinteroperable.CIOBlocks;
import com.cio.createinteroperable.RadiatorMiddleBlock;
import com.cio.createinteroperable.RadiatorValveNorthBlock;
import com.cio.createinteroperable.RadiatorValveSouthBlock;
import com.momosoftworks.coldsweat.api.event.core.registry.BlockTempRegisterEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Registers this mod's heat sources with Cold Sweat the way Cold Sweat's own
 * real Create integration does it (see its bundled {@code CreateFluidTankTemp}) —
 * a plain {@code BlockTemp} per {@link BlockTempRegisterEvent}, NOT the
 * {@code block_temp} datapack JSON this project tried first (see
 * {@link ColdSweatCompat}'s doc for why that never worked at all).
 * <p>
 * Deliberately NOT annotated {@code @EventBusSubscriber} — that would let
 * NeoForge's classpath scanner discover and load this class unconditionally
 * at mod-loading time, which touches {@link BlockTempRegisterEvent} (a
 * Cold-Sweat-only class) in this class's own method signature and would
 * throw on a Cold-Sweat-absent install. Instead {@link #register()} is only
 * ever called from {@code CreateInteroperable}'s constructor, itself gated on
 * {@link ColdSweatCompat#present()} — same pattern already proven in this
 * project for {@code InteroperableDoubleCouplerRenderer.init()} (PG+CEE-gated).
 * <p>
 * The Multi Radiator gets BOTH mechanisms now, deliberately layered rather
 * than either/or: this plain distance-based {@code BlockTemp} radius (short —
 * the ORIGINAL 6/9/12, not Brass Heater's own bumped 11/14/17, since this is
 * meant as "close enough to touch the pipes" warmth, not a room-filling
 * effect) for anyone standing right next to it regardless of whether they're
 * in an enclosed room, PLUS the separate Hearth-style "trapped in a sealed
 * room" mechanic (flood-fill + Cold Sweat's own WARMTH mob effect, see
 * RadiatorValveNorthBlockEntity#tickHearthEffect and
 * {@link ColdSweatWarmthEffect}) for the "heats the whole house" case a plain
 * radius can't do (it doesn't stop at walls). Brass Heater only ever had the
 * first kind.
 */
public final class ColdSweatIntegration {
    private ColdSweatIntegration() {
    }

    /** Call only when {@link ColdSweatCompat#present()} — see class doc. */
    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(ColdSweatIntegration.class);
    }

    private static BrassHeaterBlock.HeatLevel radiatorTier(BlockState state) {
        if (state.getBlock() instanceof RadiatorValveNorthBlock) {
            return state.getValue(RadiatorValveNorthBlock.HEAT_LEVEL);
        }
        if (state.getBlock() instanceof RadiatorValveSouthBlock) {
            return state.getValue(RadiatorValveSouthBlock.HEAT_LEVEL);
        }
        if (state.getBlock() instanceof RadiatorMiddleBlock) {
            return state.getValue(RadiatorMiddleBlock.HEAT_LEVEL);
        }
        return BrassHeaterBlock.HeatLevel.COLD;
    }

    @SubscribeEvent
    static void onBlockTempRegister(BlockTempRegisterEvent event) {
        // Brass Heater — ranges bumped +5 blocks from the original 6/9/12
        // (diagonal falloff made the old radii feel much shorter than they
        // looked on paper).
        event.register(new ColdSweatHeatTemp(BrassHeaterBlock.HeatLevel.WARM,
                state -> state.getValue(BrassHeaterBlock.HEAT_LEVEL), 15, 11,
                CIOBlocks.BRASS_HEATER.get()));
        event.register(new ColdSweatHeatTemp(BrassHeaterBlock.HeatLevel.HOT,
                state -> state.getValue(BrassHeaterBlock.HEAT_LEVEL), 20, 14,
                CIOBlocks.BRASS_HEATER.get()));
        event.register(new ColdSweatHeatTemp(BrassHeaterBlock.HeatLevel.BLAZING,
                state -> state.getValue(BrassHeaterBlock.HEAT_LEVEL), 26, 17,
                CIOBlocks.BRASS_HEATER.get()));

        // Multi Radiator — the original, un-bumped 6/9/12: short-range "close
        // to the pipes" warmth, layered underneath the Hearth-style room
        // effect rather than replacing it.
        event.register(new ColdSweatHeatTemp(BrassHeaterBlock.HeatLevel.WARM,
                ColdSweatIntegration::radiatorTier, 15, 6,
                CIOBlocks.RADIATOR_VALVE_NORTH.get(), CIOBlocks.RADIATOR_VALVE_SOUTH.get(), CIOBlocks.RADIATOR_MIDDLE.get()));
        event.register(new ColdSweatHeatTemp(BrassHeaterBlock.HeatLevel.HOT,
                ColdSweatIntegration::radiatorTier, 20, 9,
                CIOBlocks.RADIATOR_VALVE_NORTH.get(), CIOBlocks.RADIATOR_VALVE_SOUTH.get(), CIOBlocks.RADIATOR_MIDDLE.get()));
        event.register(new ColdSweatHeatTemp(BrassHeaterBlock.HeatLevel.BLAZING,
                ColdSweatIntegration::radiatorTier, 26, 12,
                CIOBlocks.RADIATOR_VALVE_NORTH.get(), CIOBlocks.RADIATOR_VALVE_SOUTH.get(), CIOBlocks.RADIATOR_MIDDLE.get()));
    }
}
