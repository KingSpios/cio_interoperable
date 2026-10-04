package com.cio.createinteroperable.vista;

import com.mrcrayfish.furniture.refurbished.electricity.Connection;
import com.mrcrayfish.furniture.refurbished.electricity.IElectricityNode;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;

/**
 * Crayfish half of {@link VistaTvSupport#retireGhost}: re-homes a stale TV
 * block entity's Refurbished Furniture links onto the wall's real master
 * before the stale one is removed. Imports Crayfish types, so it is only ever
 * called behind {@code CrayfishCompat.present()}.
 */
public final class VistaCrayfishLinks {

    private VistaCrayfishLinks() {
    }

    public static void moveLinks(BlockEntity ghost, BlockEntity master) {
        if (!(ghost instanceof IElectricityNode from)) {
            return;
        }
        IElectricityNode to = master instanceof IElectricityNode node ? node : null;
        for (Connection conn : new ArrayList<>(from.getNodeConnections())) {
            IElectricityNode other = conn.getOtherNode(from);
            from.getNodeConnections().remove(conn);
            if (other == null) {
                continue;
            }
            other.removeNodeConnection(conn);
            if (to != null && other != to && !to.isConnectedToNode(other) && !to.isNodeConnectionLimitReached()) {
                to.connectToNode(other);
            }
        }
        ghost.setChanged();
    }
}
