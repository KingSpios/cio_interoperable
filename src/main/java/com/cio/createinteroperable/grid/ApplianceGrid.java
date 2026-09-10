package com.cio.createinteroperable.grid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-{@link Level} registry + reconcile loop for the native appliance grid.
 * Every CIO appliance node (lamp, gramophone, radio, mini-fridge) and every
 * Domestic Electrical Board registers here from its own {@code setLevel}; the
 * client also populates it, for rendering the node cubes.
 *
 * <p>{@link #tick()} (driven from {@code Level.tickBlockEntities} HEAD by the
 * {@code ApplianceGridHolder} mixin) folds each <em>passive</em> node's
 * {@code receiving} flag into {@code powered} and clears it &mdash; sources drive
 * their own distribution pass from {@code electricalTick}. Dormant on the client
 * and whenever Refurbished Furniture is installed.</p>
 */
public final class ApplianceGrid {

    /** Implemented by {@code Level} via {@code ApplianceGridHolder}. */
    public interface Access {
        ApplianceGrid cio$applianceGrid();
    }

    private final Level level;
    private final Map<BlockPos, WeakReference<ApplianceNode>> nodes = new ConcurrentHashMap<>();

    public ApplianceGrid(Level level) {
        this.level = level;
    }

    public static ApplianceGrid get(Level level) {
        return ((Access) level).cio$applianceGrid();
    }

    public void addNode(ApplianceNode node) {
        this.nodes.put(node.appliancePos().immutable(), new WeakReference<>(node));
    }

    /** Passive nodes fold {@code receiving} into {@code powered} once per tick, then clear it. */
    public void tick() {
        if (this.level.isClientSide || CrayfishCompat.present()) {
            return;
        }
        Iterator<BlockPos> it = this.nodes.keySet().iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            ApplianceNode node = resolve(pos);
            if (node == null || !node.applianceValid()) {
                it.remove();
                continue;
            }
            if (!node.isApplianceSource() && this.level.shouldTickBlocksAt(pos)) {
                node.reconcileAppliancePower();
                node.setApplianceReceivingPower(false);
                if ((this.level.getGameTime() & 15L) == 0L) {
                    node.pruneApplianceConnections();
                }
            }
        }
    }

    /** Live nodes, for client-side cube/wire rendering. Prunes dead weak refs. */
    public List<ApplianceNode> liveNodes() {
        List<ApplianceNode> out = new ArrayList<>();
        Iterator<BlockPos> it = this.nodes.keySet().iterator();
        while (it.hasNext()) {
            ApplianceNode node = resolve(it.next());
            if (node == null) {
                it.remove();
            } else {
                out.add(node);
            }
        }
        return out;
    }

    private ApplianceNode resolve(BlockPos pos) {
        WeakReference<ApplianceNode> ref = this.nodes.get(pos);
        ApplianceNode node = ref != null ? ref.get() : null;
        if (node == null && this.level.isLoaded(pos)
                && this.level.getBlockEntity(pos) instanceof ApplianceNode n) {
            node = n;
            this.nodes.put(pos, new WeakReference<>(node));
        }
        return node != null && !node.applianceOwner().isRemoved() ? node : null;
    }
}
