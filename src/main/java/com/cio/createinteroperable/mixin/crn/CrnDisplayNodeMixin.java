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
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
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
 * display powers the whole panel &mdash; {@link #reconcileAppliancePower()} on
 * the controller walks its own {@code width}&times;{@code height} footprint and
 * lights up if any member's node is powered. This is deliberately independent of
 * which block is currently the controller, so extending or rebuilding a wired
 * board never dark-screens it.</p>
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

    /** Create Train Navigator's own "this block owns the multiblock" flag. */
    @Shadow private boolean isController;

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

    /** Sticky "a live DEB rail is reaching this exact block" &mdash; what the controller polls across the panel. */
    @Override
    public boolean applianceDirectlyFed() {
        return this.cioNode$fedTicks <= 3;
    }

    /**
     * A Create Train Navigator display is a rectangle of blocks that share one
     * {@code isController} block, and only blocks the player actually wired get
     * {@code receiving} from the DEB. So: every block tracks its own direct feed
     * ({@link #cioNode$fedTicks}); the <b>controller</b> then decides the whole
     * panel's power from any block being fed and writes that verdict onto every
     * member, so the "no power" overlay clears on the unwired blocks too. The
     * controller is the sole writer of a member's {@code powered} flag (no
     * feedback loop &mdash; it only ever reads members' <em>direct</em> feed,
     * never their mirrored state); an orphaned directly-fed block still lights
     * itself if no controller is loaded to do it.
     */
    @Override
    public void reconcileAppliancePower() {
        if (applianceReceivingPower()) {
            this.cioNode$fedTicks = 0;
        } else if (this.cioNode$fedTicks < 99) {
            this.cioNode$fedTicks++;
        }

        if (!this.isController) {
            if (applianceDirectlyFed() && !this.cioNode$powered) {
                setAppliancePowered(true);
            }
            return;
        }

        boolean[] panelPowered = { false };
        cioNode$forEachPanelNode(node -> panelPowered[0] |= node.applianceDirectlyFed());
        boolean powered = panelPowered[0];
        cioNode$forEachPanelNode(node -> {
            if (node.appliancePowered() != powered) {
                node.setAppliancePowered(powered);
            }
        });
    }

    /**
     * Visit every appliance node in the controller's panel &mdash; walk its
     * {@code counter-clockwise x down} footprint (the shape
     * {@code IMultiblockBlockEntity#applyToAll} uses), same block + same facing,
     * bounded by Create Train Navigator's 16&times;16 maximum, stopping at the
     * first gap in each direction. Includes the controller itself.
     */
    @Unique
    private void cioNode$forEachPanelNode(java.util.function.Consumer<ApplianceNode> action) {
        BlockEntity self = cioNode$be();
        Level level = self.getLevel();
        BlockState state = self.getBlockState();
        if (level == null || !state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            action.accept((ApplianceNode) (Object) this);
            return;
        }
        Block block = state.getBlock();
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        Direction left = facing.getCounterClockWise();
        BlockPos origin = self.getBlockPos();
        for (int x = 0; x < 16; x++) {
            BlockPos column = origin.relative(left, x);
            BlockState cs = level.getBlockState(column);
            if (x > 0 && (!cs.is(block) || cs.getValue(HorizontalDirectionalBlock.FACING) != facing)) {
                break;
            }
            for (int y = 0; y < 16; y++) {
                BlockPos pos = column.below(y);
                if (!(x == 0 && y == 0)) {
                    BlockState ps = level.getBlockState(pos);
                    if (!ps.is(block) || ps.getValue(HorizontalDirectionalBlock.FACING) != facing) {
                        break;
                    }
                }
                if (level.getBlockEntity(pos) instanceof ApplianceNode node) {
                    action.accept(node);
                }
            }
        }
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
