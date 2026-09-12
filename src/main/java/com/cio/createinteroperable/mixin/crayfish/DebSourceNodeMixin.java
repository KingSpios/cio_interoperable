package com.cio.createinteroperable.mixin.crayfish;

import com.cio.createinteroperable.deb.ApplianceLoads;
import com.cio.createinteroperable.deb.DebNodeBox;
import com.cio.createinteroperable.deb.DebRectifierBlockEntity;
import com.cio.createinteroperable.deb.Pool;
import com.mrcrayfish.furniture.refurbished.electricity.Connection;
import com.mrcrayfish.furniture.refurbished.electricity.IElectricityNode;
import com.mrcrayfish.furniture.refurbished.electricity.ISourceNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Re-attaches MrCrayfish's {@code ISourceNode} to Create: Interoperable's own
 * {@link DebRectifierBlockEntity} (the Power Grid-rooted Power Kit family —
 * tiers 1/2/3/4) when Refurbished Furniture is installed. Crayfish then
 * auto-registers the board into its {@code ElectricityTicker} and its wrench,
 * link handler, node-indicator overlay and wire renderer all key off the
 * interface &mdash; exactly as before the source model was moved onto
 * {@link com.cio.createinteroperable.deb.ApplianceSource}.
 *
 * <p>The CEE-rooted twin ({@code CeeDebRectifierBlockEntity} and its own tier
 * subclasses) is <b>not</b> targeted by this mixin and never becomes an
 * {@code IElectricityNode} &mdash; appliances link to it over CEE's own
 * node/wire system instead, which draws its own connection visuals.
 *
 * <p>The per-pool distribution logic that used to live in
 * {@code DebRectifierBlockEntity.earlyNodeTick} is reproduced verbatim in
 * {@link #earlyNodeTick} here; it reads the board's live-pool / range / blown
 * state through {@code ApplianceSource} and reports its link tally back the same
 * way. Soft {@link Implements}, and the whole config is skipped by
 * {@link CrayfishMixinPlugin} when Crayfish is absent.</p>
 */
@Mixin(DebRectifierBlockEntity.class)
@Implements(@Interface(iface = ISourceNode.class, prefix = "isn$"))
public abstract class DebSourceNodeMixin {

    @Unique
    private final Set<Connection> cio$connections = new HashSet<>();
    @Unique
    private boolean cio$overloaded;

    @Unique
    private DebRectifierBlockEntity cio$self() {
        return (DebRectifierBlockEntity) (Object) this;
    }

    @Unique
    private ISourceNode cio$node() {
        return (ISourceNode) (Object) this;
    }

    // --- IElectricityNode / ISourceNode identity + state -----------------

    public BlockPos isn$getNodePosition() {
        return this.cio$self().getBlockPos();
    }

    public Level isn$getNodeLevel() {
        return this.cio$self().getLevel();
    }

    public BlockEntity isn$getNodeOwner() {
        return this.cio$self();
    }

    public Set<Connection> isn$getNodeConnections() {
        return this.cio$connections;
    }

    public boolean isn$isNodePowered() {
        return this.cio$self().sourcePowered();
    }

    public void isn$setNodePowered(boolean powered) {
        // The board recomputes this every electricalTick from its pools; accept a
        // transient set from Crayfish in between, same as the old direct impl.
        this.cio$self().setAppliancePowered(powered);
    }

    public void isn$setNodeOverloaded(boolean value) {
        this.cio$overloaded = value;
    }

    public boolean isn$isNodeOverloaded() {
        return this.cio$overloaded;
    }

    public int isn$getMaxPowerableNodes() {
        return 256;
    }

    public int isn$getNodeMaximumConnections() {
        return 256;
    }

    /** Wrench-link zone on the model's {@code body_box}, per facing. */
    public AABB isn$getNodeInteractBox() {
        return DebNodeBox.forState(this.cio$self().getBlockState());
    }

    /**
     * Replaces {@code ISourceNode}'s default (which unconditionally energises
     * every reachable node) with per-pool gating: sum the linked appliances by
     * pool, then energise only the pools that were OK and above brownout as of
     * the board's last electrical tick.
     */
    public void isn$earlyNodeTick(Level level) {
        DebRectifierBlockEntity be = this.cio$self();
        if (level.isClientSide) {
            // Keep Crayfish's client-side "node is in a powered network" render working.
            this.cio$node().searchNodeNetwork(true).nodes()
                    .forEach(node -> node.getPowerSources().add(be.getBlockPos()));
            return;
        }
        if (be.sourceExploded()) {
            return;
        }

        int poolCount = Pool.values().length;

        // Crayfish's own searchNodeNetwork() deliberately excludes (and never
        // traverses past) any other ISourceNode -- confirmed by decompiling
        // ISourceNode's real lambdas, which filter both the include- and the
        // traverse-predicate on !isSourceNode(). That means the loop below,
        // which only ever sees searchNodeNetwork()'s *filtered* results, can
        // never observe another board -- it's designed out of that list on
        // purpose, so one source's distribution pass never reaches into
        // another source's own territory. Detecting a shared network
        // therefore needs a raw walk of the actual connection graph first,
        // ignoring that filtering.
        boolean conflict = cio$networkHasOtherSource();
        be.setNetworkConflict(conflict);
        if (conflict) {
            be.reportLinkedAppliances(new double[poolCount], new int[poolCount]);
            return;
        }

        List<List<IElectricityNode>> nodesByPool = new ArrayList<>(poolCount);
        for (int i = 0; i < poolCount; i++) {
            nodesByPool.add(new ArrayList<>());
        }
        double[] watts = new double[poolCount];
        int[] counts = new int[poolCount];

        int range = be.sourceRangeBlocks();
        BlockPos origin = be.getBlockPos();
        for (IElectricityNode node : this.cio$node().searchNodeNetwork(false).nodes()) {
            BlockEntity owner = node.getNodeOwner();
            if (owner == be) {
                continue;
            }
            if (!cio$withinRange(origin, node.getNodePosition(), range)) {
                continue;
            }
            ApplianceLoads.Spec spec = ApplianceLoads.lookup(owner);
            if (spec == null) {
                continue;
            }
            int i = spec.pool().ordinal();
            nodesByPool.get(i).add(node);
            // Keep switched-off appliances energised (so they work the moment
            // they're turned on) but don't bill their load until they are.
            if (ApplianceLoads.isDrawingPower(owner)) {
                watts[i] += spec.watts();
            }
        }
        for (int i = 0; i < poolCount; i++) {
            counts[i] = nodesByPool.get(i).size();
        }
        be.reportLinkedAppliances(watts, counts);

        for (Pool pool : Pool.values()) {
            if (be.sourceRailLive(pool)) {
                for (IElectricityNode node : nodesByPool.get(pool.ordinal())) {
                    node.setNodeReceivingPower(true);
                }
            }
        }
    }

    /**
     * Raw BFS over the actual {@code Connection} graph (bypassing
     * {@code searchNodeNetwork()}'s source-excluding filter entirely) to
     * answer one question: is another {@code ApplianceSource} board reachable
     * anywhere on this physical network at all? Bounded the same way the
     * native backend's own network walk is ({@code ApplianceNode#searchApplianceNetwork}).
     */
    @Unique
    private boolean cio$networkHasOtherSource() {
        IElectricityNode self = this.cio$node();
        Set<BlockPos> seen = new HashSet<>();
        seen.add(self.getNodePosition());
        java.util.Deque<IElectricityNode> queue = new java.util.ArrayDeque<>();
        queue.add(self);
        int maxNodes = 256;
        while (!queue.isEmpty() && seen.size() < maxNodes) {
            IElectricityNode current = queue.poll();
            for (Connection conn : current.getNodeConnections()) {
                IElectricityNode next = conn.getOtherNode(current);
                if (next == null || !seen.add(next.getNodePosition())) {
                    continue;
                }
                if (next.getNodeOwner() instanceof com.cio.createinteroperable.grid.ApplianceNode an && an.isApplianceSource()) {
                    return true;
                }
                queue.add(next);
            }
        }
        return false;
    }

    @Unique
    private static boolean cio$withinRange(BlockPos origin, BlockPos other, int range) {
        int dx = Math.abs(other.getX() - origin.getX());
        int dy = Math.abs(other.getY() - origin.getY());
        int dz = Math.abs(other.getZ() - origin.getZ());
        return Math.max(dx, Math.max(dy, dz)) <= range;
    }

    // --- NBT (were inline calls in the old BlockEntity read/write) -------

    @Inject(method = "read", at = @At("TAIL"))
    private void cio$readNode(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        this.cio$node().readNodeNbt(tag);
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void cio$writeNode(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        this.cio$node().writeNodeNbt(tag);
    }
}
