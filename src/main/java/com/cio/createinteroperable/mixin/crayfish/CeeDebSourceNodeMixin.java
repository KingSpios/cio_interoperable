package com.cio.createinteroperable.mixin.crayfish;

import com.cio.createinteroperable.deb.ApplianceLoads;
import com.cio.createinteroperable.deb.CeeDebRectifierBlockEntity;
import com.cio.createinteroperable.deb.DebNodeBox;
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
 * CEE-rooted twin of {@link DebSourceNodeMixin} &mdash; re-attaches MrCrayfish's
 * {@code ISourceNode} to {@link CeeDebRectifierBlockEntity} (the Electro
 * Energetics-wired Power Kit family) when Refurbished Furniture is installed.
 *
 * <p>Before the Power Kit BlockEntity hierarchy was split into independent PG-
 * and CEE-rooted classes, both sides shared one {@code DebRectifierBlockEntity}
 * class, so {@link DebSourceNodeMixin} alone covered both. After the split,
 * only the PG-rooted class kept that mixin — this class restores the same
 * Crayfish integration for the CEE-rooted twin, which was otherwise silently
 * losing its ability to power linked appliances whenever Crayfish is present
 * (the board would skip its own native distribution pass, deferring to
 * Crayfish's {@code ElectricityTicker}, which was never told about it).</p>
 *
 * <p>The body is a deliberate duplicate of {@code DebSourceNodeMixin}'s, not a
 * shared base &mdash; the two target entirely different BlockEntity classes on
 * purpose (see {@code CeeDebRectifierBlockEntity}'s own class doc), and this
 * project's established pattern is full duplication across the PG/CEE split
 * rather than an abstraction that would force one side to reference the
 * other's class. Every method here reads through {@link com.cio.createinteroperable.deb.ApplianceSource},
 * the protocol-neutral interface both BlockEntity families already implement.</p>
 */
@Mixin(CeeDebRectifierBlockEntity.class)
@Implements(@Interface(iface = ISourceNode.class, prefix = "isn$"))
public abstract class CeeDebSourceNodeMixin {

    @Unique
    private final Set<Connection> cio$connections = new HashSet<>();
    @Unique
    private boolean cio$overloaded;

    @Unique
    private CeeDebRectifierBlockEntity cio$self() {
        return (CeeDebRectifierBlockEntity) (Object) this;
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
        CeeDebRectifierBlockEntity be = this.cio$self();
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
        List<List<IElectricityNode>> nodesByPool = new ArrayList<>(poolCount);
        for (int i = 0; i < poolCount; i++) {
            nodesByPool.add(new ArrayList<>());
        }
        double[] watts = new double[poolCount];
        int[] counts = new int[poolCount];

        int range = be.sourceRangeBlocks();
        BlockPos origin = be.getBlockPos();
        for (IElectricityNode node : this.cio$node().searchNodeNetwork(false).nodes()) {
            if (!cio$withinRange(origin, node.getNodePosition(), range)) {
                continue;
            }
            ApplianceLoads.Spec spec = ApplianceLoads.lookup(node.getNodeOwner());
            if (spec == null) {
                continue;
            }
            int i = spec.pool().ordinal();
            nodesByPool.get(i).add(node);
            // Keep switched-off appliances energised (so they work the moment
            // they're turned on) but don't bill their load until they are.
            if (ApplianceLoads.isDrawingPower(node.getNodeOwner())) {
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
