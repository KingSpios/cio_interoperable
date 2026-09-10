package com.cio.createinteroperable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * Shared redstone-output behaviour for the three Telephone blocks
 * ({@link TelephoneBlock}, {@link CpgTelephoneBlock}, {@link CeeTelephoneBlock}).
 * They have three different parent classes and can't share a base, so the
 * common logic lives here and each block delegates its {@code isSignalSource}/
 * {@code getSignal}/{@code getDirectSignal}/{@code onRemove} overrides to these
 * helpers.
 *
 * <p>While {@link CIOProperties#CALL_ACTIVE} is set (each BlockEntity keeps it
 * in sync with its own {@code answered && receivingCall} state — see the
 * {@code sync} calls in their server ticks), the phone behaves like a wall
 * lever:</p>
 * <ul>
 *     <li><b>weak</b> power 15 to every one of the 6 adjacent blocks, and</li>
 *     <li><b>strong</b> (direct) power 15 into the single block it is mounted
 *     against — {@code pos.relative(FACING.getOpposite())}. {@code FACING}
 *     points out of the wall toward the room (the direction the dial faces,
 *     matching the blockstate's own model rotation), so its opposite is the
 *     wall. This is the same relationship {@code LeverBlock} encodes with
 *     {@code getConnectedDirection}.</li>
 * </ul>
 *
 * <p>Only the <i>receiving</i> end emits — the phone that dialled out never
 * does (its {@code receivingCall} stays false for the whole call). This is
 * the same gate that closes the model's Call Feed / Call Breaker terminals,
 * so all of the phone's call-driven outputs fire together on one end.</p>
 */
public final class TelephoneRedstone {
    private TelephoneRedstone() {}

    public static boolean isActive(BlockState state) {
        return state.hasProperty(CIOProperties.CALL_ACTIVE) && state.getValue(CIOProperties.CALL_ACTIVE);
    }

    /** Weak power: 15 to any side while on a call, like a lever. */
    public static int weakSignal(BlockState state) {
        return isActive(state) ? 15 : 0;
    }

    /**
     * Strong power, only out of the back into the mounted-against block.
     * {@code queriedSide} is the direction from the block being powered toward
     * this phone (vanilla's {@code getDirectSignal} convention), so it equals
     * {@code FACING} exactly when the querying block is the one behind the phone.
     */
    public static int directSignal(BlockState state, Direction queriedSide, DirectionProperty facing) {
        return isActive(state) && queriedSide == state.getValue(facing) ? 15 : 0;
    }

    /**
     * Reconciles {@link CIOProperties#CALL_ACTIVE} with the phone's live call
     * state and, on a change, fires the same neighbour updates a wall lever
     * fires: this block's own neighbours plus the mounted-against block's
     * neighbours, so strong power propagates through it. Safe to call every
     * server tick — a cheap no-op when nothing changed.
     */
    public static void sync(Level level, BlockPos pos, BlockState state, DirectionProperty facing, boolean active) {
        if (level == null || level.isClientSide || !state.hasProperty(CIOProperties.CALL_ACTIVE)) {
            return;
        }
        if (state.getValue(CIOProperties.CALL_ACTIVE) == active) {
            return;
        }
        Block block = state.getBlock();
        level.setBlock(pos, state.setValue(CIOProperties.CALL_ACTIVE, active), Block.UPDATE_ALL);
        level.updateNeighborsAt(pos, block);
        level.updateNeighborsAt(pos.relative(state.getValue(facing).getOpposite()), block);
    }

    /**
     * Lever-style {@code onRemove} tail: when a phone that was mid-call is
     * removed (broken, exploded, piston-less replacement), re-notify the
     * mounted-against block's neighbours so anything reading strong power
     * through it drops immediately. Caller still invokes {@code super.onRemove}.
     */
    public static void onRemoved(Level level, BlockPos pos, BlockState state, BlockState newState,
                                 boolean movedByPiston, DirectionProperty facing) {
        if (movedByPiston || state.is(newState.getBlock()) || !isActive(state)) {
            return;
        }
        Block block = state.getBlock();
        level.updateNeighborsAt(pos, block);
        level.updateNeighborsAt(pos.relative(state.getValue(facing).getOpposite()), block);
    }
}
