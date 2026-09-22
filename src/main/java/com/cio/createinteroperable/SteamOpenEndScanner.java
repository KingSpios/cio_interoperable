package com.cio.createinteroperable;

import com.cio.createinteroperable.compat.PipesNPhysicsCompat;
import com.cio.createinteroperable.compat.PipesNPhysicsPipeContent;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Walks a real Create pipe network outward from a connected Steam Outlet to
 * find its genuinely open/dangling ends and vent {@link CIOOpenPipeEffects}
 * there directly — the replacement for hooking Create's
 * {@code OpenPipeEffectHandler} (see {@link CIOOpenPipeEffects}'s own doc for
 * why that hook had to be abandoned: it silently capped every real transfer
 * through an open end to 1 mB, which is not a limitation of the cosmetic
 * signal, it was hard-capping the actual mB/tick venting rate).
 * <p>
 * Deliberately a direct BFS from the SOURCE (the Outlet already knows its own
 * real valve fraction exactly) rather than reacting to a downstream event —
 * a strictly better signal than the old frequency-derived proxy, and reused
 * network topology helpers Create itself already provides
 * ({@link FluidPropagator#getPipe}/{@code getPipeConnections}/{@code isOpenEnd}),
 * the same ones {@code FluidPropagator.propagateChangedPipe} and
 * {@code PumpBlockEntity#distributePressureTo} use for their own pipe-run
 * walks.
 * <p>
 * Bounded on both cell count and open-end count so one call from one Outlet's
 * {@code tick()} (see {@link SteamOutletBlockEntity}) can never become an
 * unbounded search on a huge or looping network — run on a periodic stagger,
 * not every tick (see {@code SteamOutletBlockEntity#OPEN_END_SCAN_INTERVAL_TICKS}).
 * <p>
 * Each discovered open end is additionally checked against
 * {@link com.cio.createinteroperable.compat.PipesNPhysicsPipeContent} when
 * Pipes n Physics is present — its own real per-pipe-cell content is ties
 * the visual to that SPECIFIC pipe block genuinely holding steam right now,
 * not just "the feeding Outlet's tank has something and a path exists."
 */
final class SteamOpenEndScanner {
    private static final int MAX_VISITED_CELLS = 256;
    private static final int MAX_OPEN_ENDS_PER_SCAN = 24;

    private SteamOpenEndScanner() {
    }

    /**
     * @param startPipePos a position already confirmed connected (see
     *                     SteamOutletBlockEntity#isConnectedAbove) — typically
     *                     the pipe cell directly above the Outlet. A no-op if
     *                     that position isn't actually a real Create pipe cell
     *                     (e.g. the Outlet feeds straight into a plain tank/
     *                     pump with no pipe network to walk at all).
     * @param valveFraction the REAL, exact 0..1 valve position of the Outlet
     *                      driving this scan — used directly, no proxy needed.
     */
    static void scanAndVent(ServerLevel level, BlockPos startPipePos, double valveFraction) {
        if (FluidPropagator.getPipe(level, startPipePos) == null) {
            return;
        }

        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> frontier = new ArrayDeque<>();
        frontier.add(startPipePos);
        int ventedCount = 0;

        while (!frontier.isEmpty() && visited.size() < MAX_VISITED_CELLS && ventedCount < MAX_OPEN_ENDS_PER_SCAN) {
            BlockPos pos = frontier.poll();
            if (!visited.add(pos)) {
                continue;
            }
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, pos);
            if (pipe == null) {
                continue;
            }

            for (Direction direction : FluidPropagator.getPipeConnections(state, pipe)) {
                if (FluidPropagator.isOpenEnd(level, pos, direction)) {
                    // When Pipes n Physics is present, its own real per-cell
                    // content is a strictly better answer than anything we
                    // can infer from topology + the Outlet's own buffer alone
                    // (see PipesNPhysicsPipeContent's own doc: this specific
                    // pipe block, confirmed genuinely empty right now, is the
                    // one case worth suppressing on). Absent PnP — or if the
                    // guarded read can't confirm emptiness — this falls back
                    // to venting, exactly as before.
                    boolean confirmedEmpty = PipesNPhysicsCompat.present()
                            && PipesNPhysicsPipeContent.isDefinitelyEmpty(pipe);
                    if (!confirmedEmpty) {
                        CIOOpenPipeEffects.ventAt(level, pos, direction, valveFraction);
                        ventedCount++;
                        if (ventedCount >= MAX_OPEN_ENDS_PER_SCAN) {
                            break;
                        }
                    }
                    continue;
                }
                BlockPos neighbor = pos.relative(direction);
                if (!visited.contains(neighbor)) {
                    frontier.add(neighbor);
                }
            }
        }
    }
}
