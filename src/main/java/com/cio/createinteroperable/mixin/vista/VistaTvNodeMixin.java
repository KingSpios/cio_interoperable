package com.cio.createinteroperable.mixin.vista;

import com.cio.createinteroperable.CIOConfig;
import com.cio.createinteroperable.CreateInteroperable;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Native (no-Crayfish) power node for Vista's TV &mdash; the
 * {@link ApplianceNode} half, mirroring
 * {@link com.cio.createinteroperable.mixin.crn.CrnDisplayNodeMixin}.
 *
 * <p>Unlike a Create Train Navigator board, only the TV's bottom-left /
 * {@code SINGLE} tile ever gets a real {@code TVBlockEntity} &mdash; a grown
 * width &times; height connected wall has exactly one node for the whole wall,
 * which already tracks the wall's size ({@code getConnectedCount()}). So
 * instead of every tile reaching its own verdict from a shared cluster (CRN's
 * approach), this one node reads redstone across the whole wall, reaches the
 * verdict, and writes it onto every physical tile's own {@code powered}
 * blockstate itself ({@code cioNode$propagate}), since those other tiles have
 * no node of their own to self-heal from.</p>
 *
 * <p>The verdict ({@link #reconcileAppliancePower()}) inverts Vista's own
 * redstone semantics on request: with no live DEB rail the TV is always off,
 * full stop; once a rail reaches it, it defaults ON with <em>no</em> redstone
 * needed, and a redstone signal on any tile of the wall becomes a
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

    /** Vista 5.5.x's public {@code setHasEnergy(boolean)}, if present (see {@link #cioNode$syncVistaEnergy}). */
    @Unique private static volatile java.lang.reflect.Method cioNode$energySetter;
    @Unique private static volatile boolean cioNode$energySetterMissing;

    @Unique private final Set<GridConnection> cioNode$conns = new HashSet<>();
    @Unique private boolean cioNode$powered;
    @Unique private boolean cioNode$receiving;
    /** Last (fed, redstone) pair written to the debug log; -1 = none yet. */
    @Unique private int cioNode$loggedInputs = -1;

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
            cioNode$syncVistaEnergy(powered);
        }
    }

    /**
     * Vista 5.5.x keeps its own {@code hasEnergy} flag, which its screen
     * requires when Vista's own {@code use_furniture_electricity} (or
     * {@code consume_energy}) option is on. CIO's Crayfish adapter overrides
     * Vista's {@code setNodePowered}, which was that flag's only writer, so
     * mirror CIO's verdict into it; otherwise those options would leave the
     * screen dark forever. Reflective, like {@link #cioNode$wallSize()}, and a
     * no-op on Vista builds without it.
     */
    @Unique
    private void cioNode$syncVistaEnergy(boolean powered) {
        if (cioNode$energySetterMissing) {
            return;
        }
        try {
            java.lang.reflect.Method setter = cioNode$energySetter;
            if (setter == null) {
                setter = cioNode$be().getClass().getMethod("setHasEnergy", boolean.class);
                cioNode$energySetter = setter;
            }
            setter.invoke(this, powered);
        } catch (NoSuchMethodException e) {
            cioNode$energySetterMissing = true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Never let Vista's own bookkeeping break CIO's verdict.
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
     * Fold the DEB feed and the redstone kill switch into one verdict: no feed
     * = always off; a feed with no redstone on the wall = on; a feed with
     * redstone reaching <em>any</em> tile of the connected wall = forced off
     * (the kill switch). The wall is one multiblock, like a Create Train
     * Navigator board, so a lever on any of its screens counts, not only on
     * the bottom-left tile that carries the block entity. Runs every tick
     * under both backends, so a redstone change is picked up with no
     * {@code neighborChanged} hook. Every second the verdict is also
     * re-written onto the wall's tiles, since Vista rewrites their
     * {@code powered} state itself whenever the wall grows or shrinks.
     */
    @Override
    public void reconcileAppliancePower() {
        boolean receiving = applianceReceivingPower();
        BlockEntity be = cioNode$be();
        Level level = be.getLevel();
        boolean redstone = level != null && !level.isClientSide && cioNode$wallHasSignal(level, be.getBlockPos());
        boolean kill = receiving && redstone;
        boolean powered = receiving && !kill;
        if (level != null && !level.isClientSide) {
            int inputs = (receiving ? 1 : 0) | (redstone ? 2 : 0);
            if (inputs != this.cioNode$loggedInputs) {
                this.cioNode$loggedInputs = inputs;
                CreateInteroperable.LOGGER.debug("Vista TV {}: fed by a Power Kit={}, redstone on wall={}, wall {} tiles -> screen {}",
                        be.getBlockPos(), receiving, redstone, cioNode$wallPositions(level, be.getBlockPos()).size(),
                        powered ? "ON" : "OFF");
            }
        }
        if (this.cioNode$powered != powered) {
            setAppliancePowered(powered);
        } else if (level != null && !level.isClientSide && level.getGameTime() % 20L == 0L) {
            cioNode$propagate(level, be.getBlockPos(), powered);
        }
    }

    /** Whether redstone reaches any tile of this TV's connected wall. */
    @Unique
    private boolean cioNode$wallHasSignal(Level level, BlockPos master) {
        for (BlockPos pos : cioNode$wallPositions(level, master)) {
            if (level.hasNeighborSignal(pos)) {
                return true;
            }
        }
        return false;
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

    /** Screen tiles in this TV's connected wall. */
    @Unique
    private double cioNode$wallTiles() {
        int[] size = cioNode$wallSize();
        return (double) size[0] * size[1];
    }

    /** {width, height} of this TV's connected wall, from either shape of {@code getConnectedCount()}; {1, 1} if unreadable. */
    @Unique
    private int[] cioNode$wallSize() {
        try {
            java.lang.reflect.Method getter = cioNode$countGetter;
            if (getter == null) {
                getter = cioNode$be().getClass().getMethod("getConnectedCount");
                cioNode$countGetter = getter;
            }
            Object count = getter.invoke(this);
            if (count instanceof Number side) {
                return new int[] {side.intValue(), side.intValue()}; // older Vista: side of an N x N wall
            }
            if (count instanceof Record) {
                int width = ((Number) count.getClass().getMethod("x").invoke(count)).intValue();
                int height = ((Number) count.getClass().getMethod("y").invoke(count)).intValue();
                return new int[] {width, height}; // Vista 5.5.x: Vec2i(width, height)
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Unknown future shape: treat it as a single screen rather than lose the mixin.
        }
        return new int[] {1, 1};
    }

    // --- wall-wide propagation (only this tile has a node/BE) -----------

    /** Wall sides beyond this are treated as corrupt data, not a real TV wall. */
    @Unique private static final int CIO_MAX_WALL_SIDE = 64;

    /**
     * Every physical tile of this TV's connected wall. The block entity sits
     * on the wall's bottom-left tile (Vista's {@code findMasterBlockEntity}
     * walks down, then toward {@code FACING}'s clockwise side, to reach it),
     * so the wall spans {@code getConnectedCount()}'s width toward the
     * counter-clockwise side and its height upward. Uses Vista's own wall
     * rectangle rather than a flood fill, so an unrelated wall that merely
     * touches this one is never included. Positions that aren't this TV block
     * at the same facing are skipped.
     */
    @Unique
    private List<BlockPos> cioNode$wallPositions(Level level, BlockPos master) {
        List<BlockPos> out = new ArrayList<>();
        BlockState masterState = level.getBlockState(master);
        if (!masterState.hasProperty(HorizontalDirectionalBlock.FACING)) {
            out.add(master);
            return out;
        }
        Block block = masterState.getBlock();
        Direction facing = masterState.getValue(HorizontalDirectionalBlock.FACING);
        Direction right = facing.getCounterClockWise();
        int[] size = cioNode$wallSize();
        int width = Math.max(1, Math.min(size[0], CIO_MAX_WALL_SIDE));
        int height = Math.max(1, Math.min(size[1], CIO_MAX_WALL_SIDE));
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                BlockPos pos = master.relative(right, x).above(y);
                BlockState state = level.getBlockState(pos);
                if (state.is(block) && state.hasProperty(HorizontalDirectionalBlock.FACING)
                        && state.getValue(HorizontalDirectionalBlock.FACING) == facing) {
                    out.add(pos);
                }
            }
        }
        return out;
    }

    /** Write the verdict onto every tile's {@code powered} blockstate (only tiles that differ are touched). */
    @Unique
    private void cioNode$propagate(Level level, BlockPos master, boolean on) {
        Property<?> powerProp = level.getBlockState(master).getBlock().getStateDefinition().getProperty("powered");
        if (powerProp == null) {
            return;
        }
        String value = on ? "direct" : "off";
        for (BlockPos pos : cioNode$wallPositions(level, master)) {
            BlockState state = level.getBlockState(pos);
            BlockState updated = VistaTvSupport.withEnumByName(state, powerProp, value);
            if (updated != state) {
                level.setBlock(pos, updated, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }

    // --- native registration ---------------------------------------------

    /**
     * Registers the node, or retires a stale block entity: one left on a tile
     * that is no longer its wall's bottom-left corner ({@code hasBlockEntity()}
     * is false for every other tile, via Moonlight's {@code
     * IOptionalEntityBlock}). See {@link VistaTvSupport#retireGhost}.
     */
    @Inject(method = "onTick", at = @At("HEAD"), cancellable = true, require = 0)
    private static void cioNode$registerOnTick(Level level, BlockPos pos, BlockState state,
                                                @Coerce BlockEntity tv, CallbackInfo ci) {
        if (!(tv instanceof ApplianceNode node)) {
            return;
        }
        if (CIOConfig.VISTA_TVS_REQUIRE_POWER.get() && !state.hasBlockEntity()) {
            VistaTvSupport.retireGhost(level, pos, state, tv);
            ci.cancel();
            return;
        }
        cioNode$ensureRegistered(level, node);
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
