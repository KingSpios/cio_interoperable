package com.cio.createinteroperable.mixin.crn;

import com.cio.createinteroperable.CIOConfig;
import com.cio.createinteroperable.grid.ApplianceGrid;
import com.cio.createinteroperable.grid.ApplianceNode;
import com.cio.createinteroperable.grid.CrayfishCompat;
import com.cio.createinteroperable.grid.GridConnection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/**
 * Native (no-Crayfish) power node for Create Train Navigator's Advanced Display
 * &mdash; the {@link ApplianceNode} half, mirroring {@link
 * com.cio.createinteroperable.mixin.letsdo.GramophoneNodeMixin}. A freestanding
 * display only renders its texts while a live rail from a Domestic Electrical
 * Board reaches it; the actual "blank the screen" happens client-side in {@link
 * CrnDisplayRenderMixin}, which reads {@link #appliancePowered()} off the
 * display's controller block entity.
 *
 * <p>Every display block entity carries this (Create Train Navigator uses one
 * block-entity type for every display shape and every board size). Power is
 * aggregated across a board: wiring <em>any</em> one block of a multi-block
 * display powers the whole panel &mdash; each block's
 * {@link #reconcileAppliancePower()} floods its own connected display cluster
 * ({@link #cioNode$clusterHasDirectFeed()}) and lights itself if any member has
 * a live DEB feed. There is no controller / corner logic: every block reaches
 * the same verdict from the same cluster and self-heals every tick, so which
 * nub was wired, where the controller sits, and rebuilding a wired board all
 * stop mattering.</p>
 *
 * <p>Always applied when Create Train Navigator is present. With Refurbished
 * Furniture <em>also</em> present, {@link CrnDisplayCrayfishMixin} bolts
 * Crayfish's {@code IModuleNode} on top and forwards its power state here, so
 * this stays the single source of truth for both backends; only the connection
 * persistence + native grid registration below are gated to the no-Crayfish
 * path.</p>
 */
@Mixin(targets = "de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity")
public abstract class CrnDisplayNodeMixin implements ApplianceNode {

    @Unique private final Set<GridConnection> cioNode$conns = new HashSet<>();
    @Unique private boolean cioNode$powered;
    @Unique private boolean cioNode$receiving;
    @Unique private boolean cioNode$registered;
    /** Ticks since this block was last fed straight from the DEB; small = "directly powered right now". Sticky so the controller's cross-block read is immune to per-tick clear order. */
    @Unique private int cioNode$fedTicks = 99;

    @Unique
    private BlockEntity cioNode$be() {
        return (BlockEntity) (Object) this;
    }

    /** Foreign BEs can't self-register from {@code setLevel}; hook every path that has a live level instead. */
    @Unique
    private void cioNode$ensureRegistered() {
        if (this.cioNode$registered || CrayfishCompat.present()
                || !CIOConfig.CRN_DISPLAYS_REQUIRE_POWER.get()) {
            return;
        }
        Level level = cioNode$be().getLevel();
        if (level != null) {
            ApplianceGrid.get(level).addNode(this);
            this.cioNode$registered = true;
        }
    }

    // --- ApplianceNode ---------------------------------------------------

    @Override
    public BlockEntity applianceOwner() {
        return cioNode$be();
    }

    @Override
    public Set<GridConnection> applianceConnections() {
        return this.cioNode$conns;
    }

    @Override
    public int applianceConnectionLimit() {
        return 6;
    }

    /**
     * The display models are opaque, full-height panels &mdash; a node cube left
     * at cell centre is buried inside the mesh (invisible, depth-occluded) and
     * sits behind a full block shape that makes the wrench raycast land on the
     * block face instead of the node. Push the cube half a block out along the
     * screen normal ({@code FACING}) so it floats just proud of the screen: it
     * renders in open air, and its back half still overlaps the owning cell so
     * {@code BlockGetter.traverseBlocks} (both {@code WrenchItem.performNodeRaycast}
     * and the native {@code ApplianceRaycast}) still resolves it while the cursor
     * is in this block's cell. Shared by the Crayfish adapter's
     * {@code imn$getNodeInteractBox}.
     */
    @Override
    public AABB applianceNodeBox() {
        BlockState state = cioNode$be().getBlockState();
        if (!state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return ApplianceNode.APPLIANCE_NODE_BOX;
        }
        Vec3i n = state.getValue(HorizontalDirectionalBlock.FACING).getNormal();
        return ApplianceNode.APPLIANCE_NODE_BOX.move(n.getX() * 0.5, n.getY() * 0.5, n.getZ() * 0.5);
    }

    @Override
    public boolean appliancePowered() {
        return this.cioNode$powered;
    }

    @Override
    public void setAppliancePowered(boolean powered) {
        if (this.cioNode$powered == powered) {
            return;
        }
        this.cioNode$powered = powered;
        BlockEntity be = cioNode$be();
        be.setChanged();
        Level level = be.getLevel();
        if (level != null && !level.isClientSide) {
            // Create Train Navigator's BE already ships getUpdatePacket / onDataPacket,
            // so a plain block update is enough to carry the flag to the client renderer.
            level.sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 2);
        }
    }

    @Override
    public boolean applianceReceivingPower() {
        return this.cioNode$receiving;
    }

    @Override
    public void setApplianceReceivingPower(boolean receiving) {
        this.cioNode$receiving = receiving;
    }

    /** Sticky "a live DEB rail is reaching this exact block" &mdash; OR'd across the whole board so any one wired nub lights all of it. */
    @Override
    public boolean applianceDirectlyFed() {
        return this.cioNode$fedTicks <= 3;
    }

    /**
     * A Create Train Navigator display is a connected cluster of blocks (one
     * block type, one facing, face-adjacent), and only the blocks the player
     * actually wired get {@code receiving} from the DEB. Every block tracks its
     * own direct feed ({@link #cioNode$fedTicks}); then <b>every</b> block
     * &mdash; controller or not &mdash; independently sets its own
     * {@code powered} from "does any block in my cluster have a direct feed
     * right now" ({@link #cioNode$clusterHasDirectFeed()}). No controller /
     * corner assumptions, no cross-block writes: each node self-heals from the
     * cluster every tick, so wiring one nub powers the whole board and the "no
     * power" overlay clears on the unwired blocks too, whichever nub was chosen
     * and wherever the controller happens to sit. Runs under both backends
     * (Crayfish's {@code ElectricityTicker} and the native {@code ApplianceGrid}
     * both call this on every registered node).
     */
    @Override
    public void reconcileAppliancePower() {
        if (applianceReceivingPower()) {
            this.cioNode$fedTicks = 0;
        } else if (this.cioNode$fedTicks < 99) {
            this.cioNode$fedTicks++;
        }

        boolean powered = cioNode$clusterHasDirectFeed();
        if (this.cioNode$powered != powered) {
            setAppliancePowered(powered);
        }
    }

    /**
     * Breadth-first flood over this block's connected display cluster &mdash;
     * face-adjacent blocks of the same block and same {@code FACING}, bounded by
     * Create Train Navigator's 16&times;16 board maximum (256 cells) &mdash;
     * returning true as soon as any member reports {@link #applianceDirectlyFed()}.
     * Adjacency is the four in-plane directions (screen up/down and the two
     * in-plane horizontals); the display normal is skipped so a board can't
     * "power through" a wall to another board stuck to its back.
     */
    @Unique
    private boolean cioNode$clusterHasDirectFeed() {
        BlockEntity self = cioNode$be();
        Level level = self.getLevel();
        BlockState state = self.getBlockState();
        if (level == null || !state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return applianceDirectlyFed();
        }
        Block block = state.getBlock();
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        Direction horizontal = facing.getClockWise();
        Direction[] dirs = {
                Direction.UP, Direction.DOWN, horizontal, horizontal.getOpposite()
        };
        Set<BlockPos> seen = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        BlockPos start = self.getBlockPos();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty() && seen.size() <= 256) {
            BlockPos pos = queue.poll();
            if (level.getBlockEntity(pos) instanceof ApplianceNode node && node.applianceDirectlyFed()) {
                return true;
            }
            for (Direction d : dirs) {
                BlockPos next = pos.relative(d);
                if (seen.contains(next)) {
                    continue;
                }
                BlockState ns = level.getBlockState(next);
                if (!ns.is(block) || !ns.hasProperty(HorizontalDirectionalBlock.FACING)
                        || ns.getValue(HorizontalDirectionalBlock.FACING) != facing) {
                    continue;
                }
                seen.add(next);
                queue.add(next);
            }
        }
        return false;
    }

    // --- native registration ------------------------------------------

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void cioNode$registerOnTick(CallbackInfo ci) {
        cioNode$ensureRegistered();
    }

    // --- persistence + client sync ----------------------------------

    @Inject(method = "write(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;Z)V",
            at = @At("TAIL"))
    private void cioNode$write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        if (!CrayfishCompat.present()) {
            // Connection graph is Crayfish's when it is installed; ours otherwise.
            writeApplianceNbt(tag);
        }
        // Always: the client renderer's text gate + the "no power" overlay both
        // read appliancePowered() client-side, and this rides Create Train
        // Navigator's own getUpdatePacket / onDataPacket sync (clientPacket=true
        // here) with no extra packet. Crayfish's own writeNodeNbt only carries
        // connections, never the powered flag.
        tag.putBoolean("CioPowered", this.cioNode$powered);
    }

    @Inject(method = "read(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;Z)V",
            at = @At("TAIL"))
    private void cioNode$read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        if (!CrayfishCompat.present()) {
            readApplianceNbt(tag);
        }
        if (tag.contains("CioPowered")) {
            this.cioNode$powered = tag.getBoolean("CioPowered");
        }
        cioNode$ensureRegistered();
    }
}
