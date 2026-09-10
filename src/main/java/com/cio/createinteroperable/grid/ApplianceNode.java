package com.cio.createinteroperable.grid;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Create: Interoperable's own contract for "a furniture block that can be
 * powered from the appliance grid" &mdash; the CIO-side replacement for
 * implementing MrCrayfish's {@code IElectricityNode} directly.
 *
 * <p>The block entity holds the {@code powered} / {@code receiving} flags and a
 * {@link GridConnection} set, and does whatever the appliance needs when its
 * power changes. When Refurbished Furniture is installed,
 * {@code com.cio.createinteroperable.mixin.crayfish}'s adapters bolt Crayfish's
 * {@code IModuleNode} back onto the same class and forward every call here, so
 * Crayfish's ticker, wrench linking, wire renderer and "no power" overlay keep
 * working exactly as before. When it is absent, the native
 * {@link ApplianceGrid} + {@link ApplianceLinkManager} drive these instead, and
 * the default methods below (a near-port of {@code IElectricityNode}'s) provide
 * the connection graph.</p>
 *
 * <p>Method names are CIO-namespaced so a class can carry both this and
 * Crayfish's interface at once without signature clashes.</p>
 */
public interface ApplianceNode {

    /** NBT keys &mdash; identical to Crayfish's so a world can migrate either way. */
    String NBT_CONNECTIONS = "Connections";
    String NBT_NODE_POS = "NodePos";

    /**
     * Node hit box, block-local. Identical to Crayfish's
     * {@code IModuleNode.DEFAULT_NODE_BOX} (a centred 4&nbsp;px cube) so the node
     * marker and link raycast line up whichever backend is active.
     */
    AABB APPLIANCE_NODE_BOX = new AABB(0.375D, 0.375D, 0.375D, 0.625D, 0.625D, 0.625D);

    /** Fallback link range when a network has no source to bound it (blocks, Chebyshev). */
    int DEFAULT_MAX_LINK_LENGTH = 24;

    /** The block entity backing this node. */
    BlockEntity applianceOwner();

    /** Live, mutable connection set. Persisted via {@link #writeApplianceNbt}/{@link #readApplianceNbt}. */
    Set<GridConnection> applianceConnections();

    default BlockPos appliancePos() {
        return applianceOwner().getBlockPos();
    }

    default Level applianceLevel() {
        return applianceOwner().getLevel();
    }

    /** True for the Domestic Electrical Board; two sources never link to each other. */
    default boolean isApplianceSource() {
        return false;
    }

    /** Block-local node box; override to move the cube off cell-centre (the DEB does). */
    default AABB applianceNodeBox() {
        return APPLIANCE_NODE_BOX;
    }

    /**
     * This node's own grid, if it is inherently tied to one (a CPG vs. CEE
     * Domestic Electrical Board). A plain appliance returns {@link GridAffinity#NONE};
     * the client resolves its effective grid by walking to the nearest board.
     */
    default GridAffinity applianceGridHint() {
        return GridAffinity.NONE;
    }

    /** World-space node box. */
    default AABB positionedApplianceNodeBox() {
        return applianceNodeBox().move(appliancePos());
    }

    default boolean applianceValid() {
        return !applianceOwner().isRemoved();
    }

    // --- power state -------------------------------------------------

    /** True while a live rail is reaching this appliance. */
    boolean appliancePowered();

    /**
     * Set by the grid backend. Implementations do the appliance-visible work
     * here (repaint a lamp, stop a gramophone, &hellip;) and should early-out
     * when the value is unchanged.
     */
    void setAppliancePowered(boolean powered);

    /** Scratch flag the backend raises for this appliance during a distribution pass. */
    boolean applianceReceivingPower();

    void setApplianceReceivingPower(boolean receiving);

    /**
     * Whether a live rail is reaching <em>this exact</em> appliance directly
     * from the board &mdash; as opposed to power it borrows from a sibling in a
     * multi-block appliance. The default is just {@link #applianceReceivingPower()};
     * a multi-block appliance overrides it with a de-bounced/sticky read so its
     * controller can poll every part without a per-tick clear-order race, and
     * without mistaking the controller's own pushed-down state for a real feed.
     */
    default boolean applianceDirectlyFed() {
        return applianceReceivingPower();
    }

    /**
     * Fold {@link #applianceReceivingPower()} into {@link #appliancePowered()}.
     * Mirrors the core of Crayfish's {@code IModuleNode#updateNodePoweredState}
     * (minus its debug "everything is powered" cheat, which the Crayfish adapter
     * keeps by delegating to the real default). Override to add
     * appliance-specific per-tick reconciliation.
     */
    default void reconcileAppliancePower() {
        boolean receiving = applianceReceivingPower();
        if (receiving != appliancePowered()) {
            setAppliancePowered(receiving);
        }
    }

    // --- connection graph (near-port of IElectricityNode) -----------

    default int applianceConnectionLimit() {
        return 16;
    }

    default boolean applianceConnectionLimitReached() {
        return applianceConnections().size() >= applianceConnectionLimit();
    }

    default boolean isConnectedToAppliance(ApplianceNode other) {
        return applianceConnections().contains(GridConnection.of(appliancePos(), other.appliancePos()));
    }

    /** Add a reciprocal link to {@code other}. Returns true if it was newly formed. */
    default boolean connectApplianceTo(ApplianceNode other) {
        GridConnection conn = GridConnection.of(appliancePos(), other.appliancePos());
        if (!applianceConnections().add(conn)) {
            return false;
        }
        other.applianceConnections().add(conn);
        applianceOwner().setChanged();
        other.applianceOwner().setChanged();
        syncApplianceNode();
        other.syncApplianceNode();
        return true;
    }

    /** Drop one link on both ends. */
    default void disconnectAppliance(GridConnection conn) {
        if (!applianceConnections().remove(conn)) {
            return;
        }
        applianceOwner().setChanged();
        syncApplianceNode();
        Level level = applianceLevel();
        if (level != null && conn.otherNode(level, this) instanceof ApplianceNode other) {
            other.applianceConnections().remove(conn);
            other.applianceOwner().setChanged();
            other.syncApplianceNode();
        }
    }

    /** Break every link touching this node &mdash; call from {@code setRemoved}. */
    default void removeAllApplianceConnections() {
        Level level = applianceLevel();
        if (level != null) {
            for (GridConnection conn : new ArrayList<>(applianceConnections())) {
                if (conn.otherNode(level, this) instanceof ApplianceNode other) {
                    other.applianceConnections().remove(conn);
                    other.applianceOwner().setChanged();
                    other.syncApplianceNode();
                }
            }
        }
        applianceConnections().clear();
        applianceOwner().setChanged();
    }

    /**
     * Drop a link only when the far end's block position is <b>loaded</b> and is
     * confirmed <b>no longer an appliance node</b> (the block was broken or
     * replaced). An unloaded far end means "don't know yet" &mdash; the link is
     * kept, so leaving the area and returning never severs a connection. This is
     * the only teardown path; nothing runs on {@code setRemoved}/chunk-unload.
     */
    default void pruneApplianceConnections() {
        Level level = applianceLevel();
        if (level == null) {
            return;
        }
        applianceConnections().removeIf(conn -> {
            BlockPos other = conn.otherPos(this);
            if (other == null) {
                return true;
            }
            if (!level.isLoaded(other)) {
                return false; // don't know yet -> keep
            }
            if (level.getBlockEntity(other) instanceof ApplianceNode n) {
                // keep only if the far end still holds the reciprocal link
                return !n.applianceConnections().contains(conn);
            }
            return true; // loaded, and not a node any more -> gone for good
        });
    }

    /**
     * Push this node's connection set to tracking clients. Default sends a full
     * block-entity update; a node that already syncs can override to no-op.
     */
    default void syncApplianceNode() {
        BlockEntity be = applianceOwner();
        Level level = be.getLevel();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 2);
        }
    }

    // --- persistence (Crayfish-compatible format) -------------------

    default void writeApplianceNbt(CompoundTag tag) {
        BlockPos self = appliancePos();
        long[] others = applianceConnections().stream()
                .map(conn -> conn.getPosA().equals(self) ? conn.getPosB() : conn.getPosA())
                .mapToLong(BlockPos::asLong)
                .toArray();
        tag.putLongArray(NBT_CONNECTIONS, others);
        tag.putLong(NBT_NODE_POS, self.asLong());
    }

    default void readApplianceNbt(CompoundTag tag) {
        Set<GridConnection> set = applianceConnections();
        set.clear();
        if (!tag.contains(NBT_CONNECTIONS, net.minecraft.nbt.Tag.TAG_LONG_ARRAY)) {
            return;
        }
        BlockPos self = appliancePos();
        BlockPos shift = BlockPos.ZERO;
        if (tag.contains(NBT_NODE_POS, net.minecraft.nbt.Tag.TAG_LONG)) {
            BlockPos stored = BlockPos.of(tag.getLong(NBT_NODE_POS));
            if (!self.equals(stored)) {
                shift = self.subtract(stored);
            }
        }
        for (long packed : tag.getLongArray(NBT_CONNECTIONS)) {
            set.add(GridConnection.of(self, BlockPos.of(packed).offset(shift)));
        }
    }

    /** Persist connections onto a wrench-picked item (drop the transient power flags). */
    default void saveApplianceNbtToItem(ItemStack stack, net.minecraft.core.HolderLookup.Provider provider) {
        BlockEntity be = applianceOwner();
        CompoundTag tag = be.saveWithoutMetadata(provider);
        tag.remove(NBT_CONNECTIONS);
        tag.remove(NBT_NODE_POS);
        net.minecraft.world.item.BlockItem.setBlockEntityData(stack, be.getType(), tag);
    }

    // --- traversal -------------------------------------------------

    /**
     * Breadth-first walk of the connected network from this node, bounded by
     * {@code maxNodes} and (optionally) a world-space {@code bounds} box.
     * {@code stopEarly} caps the result the moment {@code maxNodes} is reached.
     */
    default List<ApplianceNode> searchApplianceNetwork(int maxNodes, boolean stopEarly, AABB bounds) {
        Level level = applianceLevel();
        List<ApplianceNode> result = new ArrayList<>();
        if (level == null) {
            return result;
        }
        Set<BlockPos> seen = new HashSet<>();
        seen.add(appliancePos());
        result.add(this);
        Queue<ApplianceNode> queue = new ArrayDeque<>();
        queue.add(this);
        while (!queue.isEmpty() && result.size() < maxNodes) {
            ApplianceNode current = queue.poll();
            for (GridConnection conn : current.applianceConnections()) {
                ApplianceNode next = conn.otherNode(level, current);
                if (next == null || !seen.add(next.appliancePos())) {
                    continue;
                }
                if (bounds != null && !bounds.contains(next.appliancePos().getCenter())) {
                    continue;
                }
                result.add(next);
                if (stopEarly && result.size() >= maxNodes) {
                    return result;
                }
                queue.add(next);
            }
        }
        return result;
    }
}
