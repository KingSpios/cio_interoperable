package com.cio.createinteroperable.grid;

import com.cio.createinteroperable.CreateInteroperable;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The connector-item gate for native appliance linking. Holding an item in
 * {@link #CPG_CONNECTORS} lets you see and wire CPG (Power Grid) nodes; an item
 * in {@link #CEE_CONNECTORS} does the same for CEE (Electro Energetics). Both
 * tags ship sensible defaults (PG / CEE connector blocks + CIO's own reskins)
 * and are data-pack extensible.
 */
public final class ApplianceGridTags {

    public static final TagKey<Item> CPG_CONNECTORS =
            TagKey.create(Registries.ITEM, CreateInteroperable.rl("cpg_connectors"));
    public static final TagKey<Item> CEE_CONNECTORS =
            TagKey.create(Registries.ITEM, CreateInteroperable.rl("cee_connectors"));

    private ApplianceGridTags() {
    }

    /** The grid the player's held connector unlocks, or {@code null} if they hold none. */
    @Nullable
    public static GridAffinity heldConnectorGrid(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.is(CPG_CONNECTORS)) {
            return GridAffinity.CPG;
        }
        if (main.is(CEE_CONNECTORS)) {
            return GridAffinity.CEE;
        }
        return null;
    }

    /**
     * The effective grid of a node: its own {@link ApplianceNode#applianceGridHint()}
     * if it has one, else the hint of the nearest board reachable through its
     * links, else {@link GridAffinity#NONE}. Bounded BFS (128 nodes).
     */
    public static GridAffinity componentGrid(Level level, ApplianceNode start) {
        GridAffinity own = start.applianceGridHint();
        if (own != GridAffinity.NONE) {
            return own;
        }
        java.util.Set<net.minecraft.core.BlockPos> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<ApplianceNode> queue = new java.util.ArrayDeque<>();
        seen.add(start.appliancePos());
        queue.add(start);
        int budget = 128;
        while (!queue.isEmpty() && budget-- > 0) {
            ApplianceNode node = queue.poll();
            for (GridConnection conn : node.applianceConnections()) {
                ApplianceNode next = conn.otherNode(level, node);
                if (next == null || !seen.add(next.appliancePos())) {
                    continue;
                }
                GridAffinity hint = next.applianceGridHint();
                if (hint != GridAffinity.NONE) {
                    return hint;
                }
                queue.add(next);
            }
        }
        return GridAffinity.NONE;
    }
}
