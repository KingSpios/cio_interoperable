package com.cio.createinteroperable.mixin.vista;

import com.cio.createinteroperable.CIOConfig;
import com.cio.createinteroperable.deb.MeteredAppliance;
import com.cio.createinteroperable.deb.ScalableAppliance;
import com.cio.createinteroperable.grid.ApplianceGrid;
import com.cio.createinteroperable.grid.ApplianceNode;
import com.cio.createinteroperable.grid.CrayfishCompat;
import com.cio.createinteroperable.grid.GridConnection;
import com.cio.createinteroperable.vista.VistaTvSupport;
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
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Native (no-Crayfish) power node for Vista's TV &mdash; the
 * {@link ApplianceNode} half, mirroring
 * {@link com.cio.createinteroperable.mixin.crn.CrnDisplayNodeMixin}.
 *
 * <p>Unlike a Create Train Navigator board, only the TV's bottom-left /
 * {@code SINGLE} tile ever gets a real {@code TVBlockEntity} &mdash; a grown
 * N&times;N connected wall has exactly one node for the whole wall, which
 * already tracks its own side length ({@code connectedTvsAmount}). So instead
 * of every tile reaching its own verdict from a shared cluster (CRN's
 * approach), this one node reaches the verdict and then floods it onto every
 * physical tile's own {@code powered} blockstate itself ({@link
 * #cio$propagate}), since those other tiles have no node of their own to
 * self-heal from.</p>
 *
 * <p>The verdict ({@link #reconcileAppliancePower()}) inverts Vista's own
 * redstone semantics on request: with no live DEB rail the TV is always off,
 * full stop; once a rail reaches it, it defaults ON with <em>no</em> redstone
 * needed, and a redstone signal at this exact (node-bearing) tile becomes a
 * manual kill switch instead &mdash; forcing the screen off without cutting
 * power. Billed only while actually on (see {@link #cio$isConsuming()}), and
 * scaled by wall size (see {@link #cio$loadScale()}).</p>
 *
 * <p>Always applied when Vista is present. With Refurbished Furniture
 * <em>also</em> present, {@code VistaTvBlockEntityMixin} bolts Crayfish's
 * {@code IModuleNode} on top and forwards its power state here, so this stays
 * the single source of truth for both backends; only the connection
 * persistence + native grid registration below are gated to the no-Crayfish
 * path.</p>
 */
@Mixin(targets = "net.mehvahdjukaar.vista.common.tv.TVBlockEntity")
public abstract class VistaTvNodeMixin implements ApplianceNode, MeteredAppliance, ScalableAppliance {

    /**
     * Vista's public {@code getConnectedCount()}, read reflectively rather than
     * {@code @Shadow}ed: its return type has already changed once (an
     * {@code int} wall side in older builds, a Moonlight {@code Vec2i}
     * width &times; height from 5.5.x), and a {@code @Shadow} whose signature
     * no longer matches silently drops this entire mixin, leaving TVs that
     * need no power at all.
     */
    @Unique private static volatile java.lang.reflect.Method cioNode$countGetter;

    @Unique private final Set<GridConnection> cioNode$conns = new HashSet<>();
    @Unique private boolean cioNode$powered;
    @Unique private boolean cioNode$receiving;

    @Unique
    private BlockEntity cioNode$be() {
        return (BlockEntity) (Object) this;
    }

    /** Foreign BEs can't self-register from {@code setLevel}; the ticker below hooks it instead. */
    @Unique
    private static void cioNode$ensureRegistered(Level level, ApplianceNode node) {
        if (level.isClientSide || CrayfishCompat.present() || !CIOConfig.VISTA_TVS_REQUIRE_POWER.get()) {
            return;
        }
        ApplianceGrid.get(level).addNode(node);
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
     * The TV screen is an opaque, near-full-cube panel &mdash; a node cube
     * left at cell centre (the default) is buried inside it, unreachable by
     * any raycast (wrench-link <em>and</em> the look-at "Missing power" label
     * both use a plain AABB hit test, same as vanilla block-picking). Push
     * the cube half a block out along the screen normal ({@code FACING}) so
     * it floats just proud of the screen instead, exactly like {@code
     * CrnDisplayNodeMixin}'s identical fix for CRN's opaque display panel.
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
            cioNode$propagate(level, be.getBlockPos(), powered);
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

    /**
     * Fold the DEB feed and the local redstone kill switch into one verdict:
     * no feed = always off; a feed with no redstone here = on; a feed with
     * redstone here = forced off (the kill switch). Runs every tick under
     * both backends, so a redstone change is picked up with no separate
     * {@code neighborChanged} hook &mdash; a transient one-tick mismatch from a
     * stray vanilla redstone write self-heals on the very next tick, same as
     * CRN's board-wide verdict.
     */
    @Override
    public void reconcileAppliancePower() {
        boolean receiving = applianceReceivingPower();
        BlockEntity be = cioNode$be();
        Level level = be.getLevel();
        boolean kill = receiving && level != null && level.hasNeighborSignal(be.getBlockPos());
        boolean powered = receiving && !kill;
        if (this.cioNode$powered != powered) {
            setAppliancePowered(powered);
        }
    }

    // --- metered load: only while the screen is actually on -------------

    @Override
    public boolean cio$isConsuming() {
        return this.cioNode$powered;
    }

    // --- scaled load: one unit per screen tile of the connected wall ------

    @Override
    public double cio$loadScale() {
        return Math.max(1, cioNode$wallTiles());
    }

    /** Screen tiles in this TV's connected wall, from either shape of {@code getConnectedCount()}. */
    @Unique
    private double cioNode$wallTiles() {
        try {
            java.lang.reflect.Method getter = cioNode$countGetter;
            if (getter == null) {
                getter = cioNode$be().getClass().getMethod("getConnectedCount");
                cioNode$countGetter = getter;
            }
            Object count = getter.invoke(this);
            if (count instanceof Number side) {
                return side.doubleValue() * side.doubleValue(); // older Vista: side of an N x N wall
            }
            if (count instanceof Record) {
                int width = ((Number) count.getClass().getMethod("x").invoke(count)).intValue();
                int height = ((Number) count.getClass().getMethod("y").invoke(count)).intValue();
                return (double) width * height; // Vista 5.5.x: Vec2i(width, height)
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Unknown future shape: bill a single screen rather than lose the mixin.
        }
        return 1;
    }

    // --- wall-wide propagation (only this tile has a node/BE) -----------

    /**
     * Breadth-first flood over every physical tile of this TV's connected
     * wall (same block, same {@code FACING}, face-adjacent through up/down and
     * the two in-plane horizontals), setting each one's {@code powered}
     * blockstate directly &mdash; bounded generously above Vista's own maximum
     * board area so a legitimately large wall is never cut short.
     */
    @Unique
    private static void cioNode$propagate(Level level, BlockPos start, boolean on) {
        BlockState startState = level.getBlockState(start);
        if (!startState.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return;
        }
        Block block = startState.getBlock();
        Direction facing = startState.getValue(HorizontalDirectionalBlock.FACING);
        Property<?> powerProp = block.getStateDefinition().getProperty("powered");
        if (powerProp == null) {
            return;
        }
        Direction horizontal = facing.getClockWise();
        Direction[] dirs = {Direction.UP, Direction.DOWN, horizontal, horizontal.getOpposite()};
        String value = on ? "direct" : "off";

        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty() && seen.size() <= 1024) {
            BlockPos pos = queue.poll();
            BlockState state = level.getBlockState(pos);
            BlockState updated = VistaTvSupport.withEnumByName(state, powerProp, value);
            if (updated != state) {
                level.setBlock(pos, updated, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
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
    }

    // --- native registration ---------------------------------------------

    @Inject(method = "onTick", at = @At("HEAD"), require = 0)
    private static void cioNode$registerOnTick(Level level, BlockPos pos, BlockState state,
                                                @Coerce BlockEntity tv, CallbackInfo ci) {
        if (tv instanceof ApplianceNode node) {
            cioNode$ensureRegistered(level, node);
        }
    }

    // --- teardown ----------------------------------------------------

    @Inject(method = "setRemoved", at = @At("TAIL"), require = 0)
    private void cioNode$onRemoved(CallbackInfo ci) {
        removeAllApplianceConnections();
    }

    // --- persistence / native connection graph (gated to the no-Crayfish path) ---

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void cioNode$save(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        if (!CrayfishCompat.present()) {
            writeApplianceNbt(tag);
            tag.putBoolean("CioPowered", this.cioNode$powered);
        }
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void cioNode$load(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        if (!CrayfishCompat.present()) {
            readApplianceNbt(tag);
            this.cioNode$powered = tag.getBoolean("CioPowered");
        }
    }
}
