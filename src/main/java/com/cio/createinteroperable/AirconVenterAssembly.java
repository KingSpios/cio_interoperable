package com.cio.createinteroperable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Pure, stateless "how big is my row, and where do I sit in it" query — an
 * Aircon Venter chain used to require a discrete wrench click (this class's
 * own {@code tryAssemble}, since removed) to actually link up; the user
 * asked for that to go away entirely and have chains "just work" by
 * placement alone, so this is now called fresh every tick by
 * {@link AirconVenterBlockEntity#tick()} instead of once on a wrench event.
 * <p>
 * Every venter in a contiguous same-{@code FACING} row independently walks
 * to both of the row's true boundaries and arrives at the exact same
 * {@code length}, so there is nothing to store, cache across positions, or
 * sync — no "who is the anchor" concept needed at all, unlike the old
 * wrench-triggered version (which only ever expanded outward from whichever
 * block was actually clicked).
 */
public final class AirconVenterAssembly {
    /** "Up to 5" per the design conversation — how many, counted from the row's WEST end, actually relay to a neighbor. */
    public static final int MAX_CHAIN = 5;
    /**
     * Safety cap on how far the boundary walk looks in each direction — far
     * more than any legitimate chain, this only bounds the worst-case cost
     * of an absurd, accidental wall of venters (each lookup is a cheap
     * blockstate read, so even this cap is not expensive).
     */
    private static final int MAX_SCAN_PER_SIDE = 32;

    private AirconVenterAssembly() {
    }

    /** @return this row's info as measured from {@code pos} — identical for every member of the row. */
    public static ChainInfo computeChainInfo(Level level, BlockPos pos, Direction facing) {
        Direction westDir = AirconVenterBlock.rotate(Direction.WEST, facing);
        Direction eastDir = westDir.getOpposite();
        int westCount = walkBoundary(level, pos, westDir, facing);
        int eastCount = walkBoundary(level, pos, eastDir, facing);
        return new ChainInfo(westCount + 1 + eastCount, westCount);
    }

    private static int walkBoundary(Level level, BlockPos from, Direction dir, Direction facing) {
        int count = 0;
        BlockPos cursor = from.relative(dir);
        while (count < MAX_SCAN_PER_SIDE) {
            BlockState state = level.getBlockState(cursor);
            if (!(state.getBlock() instanceof AirconVenterBlock) || state.getValue(AirconVenterBlock.FACING) != facing) {
                break;
            }
            count++;
            cursor = cursor.relative(dir);
        }
        return count;
    }

    /**
     * @param length total contiguous same-FACING venters in this row
     * (including self), capped at {@code MAX_SCAN_PER_SIDE * 2 + 1}.
     * @param indexFromWest this venter's own 0-based position counting from
     * the row's west end — deterministic and stable for every member
     * without needing a stored "anchor", since every member walks to the
     * same true boundary.
     */
    public record ChainInfo(int length, int indexFromWest) {
        /** @return whether this venter actively relays to its chain neighbors — solo (length 1) venters work fine on their own but have nothing to relay to; anything at/after the {@link #MAX_CHAIN}th position in an over-long row sits idle for relay purposes (see {@link #isOverLimit}). */
        public boolean isActive() {
            return length >= 2 && indexFromWest < MAX_CHAIN;
        }

        /** @return whether this row has more members than {@link #MAX_CHAIN} can actually relay through — surfaced as a goggle warning, see {@code AirconVenterBlockEntity#addToGoggleTooltip}. */
        public boolean isOverLimit() {
            return length > MAX_CHAIN;
        }
    }
}
