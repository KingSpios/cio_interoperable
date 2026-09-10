package com.cio.createinteroperable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Wrench-assembly logic for the Interoperable Transformer, adapted from
 * Power Grid's own TransformerCoreBlock#locate2x2 (regular, non-Nether
 * path). Same idea — scan around the wrenched block for a complete,
 * correctly-typed rectangle, and if found, replace every position in it
 * with the real assembled blockstate.
 *
 * Two differences from PG's original, both because our two ends are NOT
 * interchangeable the way Medium Transformer's 4 identical corners are:
 *  - 4 orientations to try (N/S/E/W), not 2 axis probes — a direction, not
 *    just an axis, has to be pinned down.
 *  - Marker blocks are typed (PG keystone / CEE keystone / filler) rather
 *    than one uniform "core" block, so the scan checks each of the 6
 *    positions against the specific type it must be, not just "any core".
 *
 * Layout, bottom row (top row is the same 6 positions one block above):
 *   [PG keystone] [filler] [CEE keystone]      PG_FACING points this way -->
 */
public class InteroperableAssembly {

    /**
     * @return true if a complete, correctly-typed 2x3 rectangle was found
     * around wrenchedPos (in any of the 4 orientations) and assembled.
     */
    public static boolean tryAssemble(Level level, BlockPos wrenchedPos) {
        for (Direction pgDir : new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST }) {
            if (tryAssembleAlong(level, wrenchedPos, pgDir))
                return true;
        }
        return false;
    }

    private static boolean tryAssembleAlong(Level level, BlockPos wrenchedPos, Direction pgDir) {
        Direction ceeDir = pgDir.getOpposite();
        // The wrenched block could be any of the 6 positions in the
        // structure; try every possibility for where "middle bottom" is,
        // derived from each one, rather than assuming which was clicked.
        BlockPos[] middleBottomCandidates = new BlockPos[] {
                wrenchedPos.relative(ceeDir),         // wrenched = PG bottom
                wrenchedPos.relative(ceeDir).below(), // wrenched = PG top
                wrenchedPos,                          // wrenched = middle bottom
                wrenchedPos.below(),                  // wrenched = middle top
                wrenchedPos.relative(pgDir),          // wrenched = CEE bottom
                wrenchedPos.relative(pgDir).below(),  // wrenched = CEE top
        };
        for (BlockPos middleBottom : middleBottomCandidates) {
            if (checkAndAssemble(level, middleBottom, pgDir))
                return true;
        }
        return false;
    }

    private static boolean checkAndAssemble(Level level, BlockPos middleBottom, Direction pgDir) {
        Direction ceeDir = pgDir.getOpposite();
        BlockPos pgBottom = middleBottom.relative(pgDir);
        BlockPos ceeBottom = middleBottom.relative(ceeDir);
        BlockPos middleTop = middleBottom.above();
        BlockPos pgTop = pgBottom.above();
        BlockPos ceeTop = ceeBottom.above();

        if (!isMarker(level, pgBottom, CIOBlocks.PG_KEYSTONE.get())) return false;
        if (!isMarker(level, pgTop, CIOBlocks.PG_KEYSTONE.get())) return false;
        if (!isMarker(level, ceeBottom, CIOBlocks.CEE_KEYSTONE.get())) return false;
        if (!isMarker(level, ceeTop, CIOBlocks.CEE_KEYSTONE.get())) return false;
        if (!isMarker(level, middleBottom, CIOBlocks.FILLER.get())) return false;
        if (!isMarker(level, middleTop, CIOBlocks.FILLER.get())) return false;

        if (level.isClientSide)
            return true;

        level.setBlockAndUpdate(pgBottom, CIOBlocks.PG_ASSEMBLED.get().defaultBlockState()
                .setValue(CIOProperties.PG_FACING, pgDir).setValue(CIOProperties.TOP, false));
        level.setBlockAndUpdate(pgTop, CIOBlocks.PG_ASSEMBLED.get().defaultBlockState()
                .setValue(CIOProperties.PG_FACING, pgDir).setValue(CIOProperties.TOP, true));

        level.setBlockAndUpdate(ceeBottom, CIOBlocks.CEE_ASSEMBLED.get().defaultBlockState()
                .setValue(CIOProperties.PG_FACING, pgDir).setValue(CIOProperties.TOP, false));
        level.setBlockAndUpdate(ceeTop, CIOBlocks.CEE_ASSEMBLED.get().defaultBlockState()
                .setValue(CIOProperties.PG_FACING, pgDir).setValue(CIOProperties.TOP, true));

        level.setBlockAndUpdate(middleBottom, CIOBlocks.CORE_ASSEMBLED.get().defaultBlockState()
                .setValue(CIOProperties.PG_FACING, pgDir).setValue(CIOProperties.TOP, false));
        level.setBlockAndUpdate(middleTop, CIOBlocks.CORE_ASSEMBLED.get().defaultBlockState()
                .setValue(CIOProperties.PG_FACING, pgDir).setValue(CIOProperties.TOP, true));

        return true;
    }

    private static boolean isMarker(Level level, BlockPos pos, Block expected) {
        return level.getBlockState(pos).is(expected);
    }
}
