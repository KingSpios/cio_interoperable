package com.cio.createinteroperable.deb;

import com.cio.createinteroperable.grid.ApplianceNode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Create: Interoperable's contract for the powered end of the appliance grid &mdash;
 * the Domestic Electrical Board / Power Kit, seen by whichever grid backend is
 * distributing power (Crayfish's node graph via
 * {@code com.cio.createinteroperable.mixin.crayfish.DebSourceNodeMixin}, or the
 * native {@link com.cio.createinteroperable.grid.ApplianceGrid}).
 *
 * <p>The board owns all of the electrical maths (pools, caps, thermal, the
 * grid-facing resistance) in {@link DebRectifierBlockEntity}. A backend only
 * needs: how far it reaches, whether it has blown, and which pools were live as
 * of the last electrical tick &mdash; then it reports back the appliances it
 * found so the next tick's load is right. {@link #distributeAppliancePower()}
 * bundles that whole pass for the native backend.</p>
 */
public interface ApplianceSource extends ApplianceNode {

    @Override
    default boolean isApplianceSource() {
        return true;
    }

    /** Chebyshev range (blocks) within which an appliance is served. */
    int sourceRangeBlocks();

    /** True once the board has detonated &mdash; the backend should stop energising. */
    boolean sourceExploded();

    /** Whether this pool was live (OK and above brownout) as of the last electrical tick. */
    boolean sourceRailLive(Pool pool);

    /**
     * Backend &rarr; board, once per server tick: the rated watts and the count
     * of linked appliances it found, indexed by {@link Pool#ordinal()}. Arrays
     * are {@code Pool.values().length} long and are copied, not retained.
     */
    void reportLinkedAppliances(double[] wattsByPool, int[] countByPool);

    /** True while at least one pool is energising appliances. */
    default boolean sourcePowered() {
        return appliancePowered();
    }

    /**
     * One server-tick distribution pass for the native grid: walk the connected
     * network, split recognised appliances by pool, bill the drawing ones, report
     * the tally back, and raise {@code receivingPower} on every appliance whose
     * pool was live. Mirrors {@code DebSourceNodeMixin.earlyNodeTick} (the
     * Crayfish path). Runs server-side only; the caller checks that.
     */
    default void distributeAppliancePower() {
        if (sourceExploded()) {
            return;
        }
        pruneApplianceConnections();
        int poolCount = Pool.values().length;
        List<List<ApplianceNode>> byPool = new java.util.ArrayList<>(poolCount);
        for (int i = 0; i < poolCount; i++) {
            byPool.add(new java.util.ArrayList<>());
        }
        double[] watts = new double[poolCount];
        int[] counts = new int[poolCount];

        BlockPos origin = appliancePos();
        int range = sourceRangeBlocks();
        AABB bounds = new AABB(origin).inflate(range);

        net.minecraft.world.level.Level level = applianceLevel();
        for (ApplianceNode node : searchApplianceNetwork(256, false, bounds)) {
            if (node == this) {
                continue;
            }
            // Make sure every linked node is on the reconcile loop (foreign
            // appliance BEs cannot self-register from setLevel).
            if (level != null) {
                com.cio.createinteroperable.grid.ApplianceGrid.get(level).addNode(node);
            }
            if (!withinChebyshev(origin, node.appliancePos(), range)) {
                continue;
            }
            ApplianceLoads.Spec spec = ApplianceLoads.lookup(node.applianceOwner());
            if (spec == null) {
                continue;
            }
            int i = spec.pool().ordinal();
            byPool.get(i).add(node);
            if (ApplianceLoads.isDrawingPower(node.applianceOwner())) {
                watts[i] += spec.watts();
            }
        }
        for (int i = 0; i < poolCount; i++) {
            counts[i] = byPool.get(i).size();
        }
        reportLinkedAppliances(watts, counts);

        for (Pool pool : Pool.values()) {
            if (sourceRailLive(pool)) {
                for (ApplianceNode node : byPool.get(pool.ordinal())) {
                    node.setApplianceReceivingPower(true);
                }
            }
        }
    }

    private static boolean withinChebyshev(BlockPos origin, BlockPos other, int range) {
        int dx = Math.abs(other.getX() - origin.getX());
        int dy = Math.abs(other.getY() - origin.getY());
        int dz = Math.abs(other.getZ() - origin.getZ());
        return Math.max(dx, Math.max(dy, dz)) <= range;
    }
}
