package com.cio.createinteroperable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrench-assembly for the Multi Radiator: a straight horizontal run of
 * exactly one {@link RadiatorValveNorthBlock}, 0-{@link #MAX_MIDDLE} plain
 * copper blocks, and exactly one {@link RadiatorValveSouthBlock}, with the
 * two end caps facing directly away from each other (so the run reads as
 * "valve_north_(middle)_south_valve", per the design conversation this was
 * scaffolded from). Deliberately much simpler than {@code InteroperableAssembly}
 * (the real PG<->CEE multiblock): that one has to try 4 orientations because
 * its two ends are otherwise indistinguishable blobs; here each end block
 * already carries its own FACING, so there's exactly one axis/direction to
 * check, not four to guess.
 * <p>
 * TODO(design): the raw "joining material" is assumed to be vanilla
 * {@code minecraft:copper_block} (see {@link #RAW_MIDDLE_MATERIAL}), taken
 * literally from "assembled by joining copper blocks in a horizontal line."
 * If a dedicated CIO filler block was meant instead, only this constant needs
 * to change.
 */
public class RadiatorAssembly {
    /** Raw, not-yet-converted material a player stacks between the two end caps. */
    public static final net.minecraft.world.level.block.Block RAW_MIDDLE_MATERIAL = Blocks.COPPER_BLOCK;

    /** Matches "extended with up to 3 middle blocks." */
    public static final int MAX_MIDDLE = 3;

    private RadiatorAssembly() {
    }

    /**
     * Attempts to assemble a Multi Radiator using {@code pos} (a just-wrenched
     * North or South valve block) as one known end. Converts any raw copper
     * blocks found in between into {@link RadiatorMiddleBlock}, flips
     * {@code ASSEMBLED} on both end caps, and cross-links their
     * BlockEntities. Returns false (no world changes) if {@code pos} isn't a
     * radiator end cap, or no valid run is found in its inward direction —
     * callers should fall back to a normal wrench-rotate in that case, same
     * as a lone unpaired core does elsewhere in this project.
     */
    public static boolean tryAssemble(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        boolean startIsNorth = state.getBlock() instanceof RadiatorValveNorthBlock;
        boolean startIsSouth = state.getBlock() instanceof RadiatorValveSouthBlock;
        if (!startIsNorth && !startIsSouth) {
            return false;
        }

        // Both end blocks point OUTWARD (away from the run) via their own
        // FACING — see RadiatorValveNorthBlock/RadiatorValveSouthBlock class
        // docs — so "into the run" is always FACING's opposite, for either end.
        Direction inward = startIsNorth
                ? state.getValue(RadiatorValveNorthBlock.FACING).getOpposite()
                : state.getValue(RadiatorValveSouthBlock.FACING).getOpposite();

        List<BlockPos> middlePositions = new ArrayList<>();
        BlockPos cursor = pos.relative(inward);
        while (middlePositions.size() < MAX_MIDDLE && level.getBlockState(cursor).is(RAW_MIDDLE_MATERIAL)) {
            middlePositions.add(cursor);
            cursor = cursor.relative(inward);
        }

        BlockState partnerState = level.getBlockState(cursor);
        boolean partnerValid = startIsNorth
                ? partnerState.getBlock() instanceof RadiatorValveSouthBlock
                        && partnerState.getValue(RadiatorValveSouthBlock.FACING) == inward
                : partnerState.getBlock() instanceof RadiatorValveNorthBlock
                        && partnerState.getValue(RadiatorValveNorthBlock.FACING) == inward;
        if (!partnerValid) {
            return false;
        }

        BlockPos northPos = startIsNorth ? pos : cursor;
        BlockPos southPos = startIsNorth ? cursor : pos;
        Direction.Axis axis = inward.getAxis();

        for (BlockPos middlePos : middlePositions) {
            level.setBlockAndUpdate(middlePos, CIOBlocks.RADIATOR_MIDDLE.get().defaultBlockState()
                    .setValue(RadiatorMiddleBlock.AXIS, axis));
        }
        level.setBlockAndUpdate(northPos, level.getBlockState(northPos).setValue(RadiatorValveNorthBlock.ASSEMBLED, true));
        level.setBlockAndUpdate(southPos, level.getBlockState(southPos).setValue(RadiatorValveSouthBlock.ASSEMBLED, true));

        if (level.getBlockEntity(northPos) instanceof RadiatorValveNorthBlockEntity northBe) {
            northBe.setAssembledPartner(southPos, middlePositions);
        }
        if (level.getBlockEntity(southPos) instanceof RadiatorValveSouthBlockEntity southBe) {
            southBe.setAssembledPartner(northPos, middlePositions.size());
        }
        return true;
    }
}
