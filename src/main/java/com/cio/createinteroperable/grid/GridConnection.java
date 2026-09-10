package com.cio.createinteroperable.grid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Objects;

/**
 * An undirected link between two {@link ApplianceNode}s, identified purely by the
 * two block positions. A near-port of MrCrayfish's {@code Connection}: order
 * doesn't matter ({@link #equals}/{@link #hashCode} normalise to a min/max pair),
 * and the on-disk form matches Crayfish's so a world could migrate either way.
 */
public final class GridConnection {

    private final BlockPos a;
    private final BlockPos b;
    private final BlockPos min;
    private final BlockPos max;

    private GridConnection(BlockPos a, BlockPos b) {
        this.a = a.immutable();
        this.b = b.immutable();
        boolean aFirst = a.compareTo(b) <= 0;
        this.min = (aFirst ? this.a : this.b);
        this.max = (aFirst ? this.b : this.a);
    }

    public static GridConnection of(BlockPos a, BlockPos b) {
        return new GridConnection(a, b);
    }

    public BlockPos getPosA() {
        return this.a;
    }

    public BlockPos getPosB() {
        return this.b;
    }

    /** The endpoint that isn't {@code node}'s position, or {@code null} if {@code node} is on neither end. */
    public BlockPos otherPos(ApplianceNode node) {
        BlockPos p = node.appliancePos();
        if (this.a.equals(p)) {
            return this.b;
        }
        if (this.b.equals(p)) {
            return this.a;
        }
        return null;
    }

    /** The node at the end opposite {@code node}, resolved from the level, or {@code null}. */
    public ApplianceNode otherNode(Level level, ApplianceNode node) {
        BlockPos other = otherPos(node);
        if (other == null) {
            return null;
        }
        return level.getBlockEntity(other) instanceof ApplianceNode n ? n : null;
    }

    /** True when both endpoints currently resolve to a live appliance node. */
    public boolean isConnected(Level level) {
        return level.getBlockEntity(this.a) instanceof ApplianceNode
                && level.getBlockEntity(this.b) instanceof ApplianceNode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GridConnection that)) {
            return false;
        }
        return this.min.equals(that.min) && this.max.equals(that.max);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.min, this.max);
    }

    @Override
    public String toString() {
        return "GridConnection[" + this.min.toShortString() + " <-> " + this.max.toShortString() + "]";
    }
}
