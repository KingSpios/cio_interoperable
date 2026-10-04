package com.cio.createinteroperable.iden;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Electro Energetics side of an Iden's Decor telephone's tap: the only class
 * in its integration that names a CEE type, and only ever called behind
 * {@code ElectroEnergeticsCompat.present()}, so it never loads without CEE.
 * The walk is the same one CIO's own telephones do ({@code ceeTapReaches}),
 * from tap node 2 to tap node 2, so an Iden phone and a CIO phone on one tap
 * line reach each other.
 */
public final class IdenPhoneCeeTaps {

    /** Bounds the walk against a pathological or cyclic wire layout. */
    private static final int MAX_TAP_HOPS = 256;

    private IdenPhoneCeeTaps() {
    }

    public static boolean reaches(Level level, BlockPos from, BlockPos to) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        InWorldNode start = new InWorldNode(IdenPhoneBlocks.CEE_TAP_NODE, from);
        InWorldNode goal = new InWorldNode(IdenPhoneBlocks.CEE_TAP_NODE, to);
        if (start.equals(goal)) {
            return true;
        }
        InfrastructureSavedData sd = InfrastructureSavedData.load(serverLevel);
        Set<InWorldNode> visited = new HashSet<>();
        Deque<InWorldNode> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);
        int hops = 0;
        while (!queue.isEmpty() && hops++ < MAX_TAP_HOPS) {
            InWorldNode current = queue.poll();
            for (InWorldNodeConnection connection : sd.getConnections(current)) {
                InWorldNode next = connection.node1().equals(current) ? connection.node2() : connection.node1();
                if (next.equals(goal)) {
                    return true;
                }
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        return false;
    }

    /** True while at least one CEE wire is on this phone's tap node. */
    public static boolean wired(Level level, BlockPos pos) {
        return level instanceof ServerLevel serverLevel
                && !InfrastructureSavedData.load(serverLevel)
                        .getConnections(new InWorldNode(IdenPhoneBlocks.CEE_TAP_NODE, pos)).isEmpty();
    }
}
