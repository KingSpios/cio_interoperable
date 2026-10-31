package com.cio.createinteroperable.powerkit.cee;

import com.cio.createinteroperable.powerkit.PowerKitPaint;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import com.cio.createinteroperable.CIODevices;
import com.cio.createinteroperable.cee.CeeNodeExtents;
import com.cio.createinteroperable.modules.ElectroEnergeticsModule;
import com.cio.createinteroperable.powerkit.PowerKitGeometry;
import com.cio.createinteroperable.powerkit.PowerKitLabels;
import com.cio.createinteroperable.powerkit.PowerKitSignalNub;
import com.cio.createinteroperable.powerkit.PowerKitSwitch;
import org.jetbrains.annotations.Nullable;
import com.cio.createinteroperable.powerkit.cpg.DebRectifierBlock;
import com.cio.createinteroperable.powerkit.cpg.DebRectifierBlockEntity;
import com.george_vi.electroenergetics.CEESoundEvents;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;

/**
 * "CEE Domestic Power Kit" (tier 2) &mdash; the Electro Energetics-wired twin of
 * {@link DebRectifierBlock}. Same model, same {@link DebRectifierBlockEntity},
 * same Crayfish appliance link and thermal / pool / goggle behaviour; the only
 * difference is the grid port. Instead of Power Grid terminals it exposes CEE
 * connection nodes at the <em>same spots</em> the PG nubs sit, and the shared
 * {@code DebRectifierBlockEntity} runs its intake + feeds on Electro
 * Energetics' solver via {@link DebCeeDevice}, using its own CEE-only
 * {@link CeeDebRectifierBlockEntity} BlockEntity family — no Power Grid type
 * anywhere in this block's or its BlockEntity's hierarchy, so this block is
 * safe to register even when Power Grid is absent.
 *
 * <p>Wall / floor / ceiling mountable through a plain 6-way {@code FACING}
 * (vanilla {@link BlockStateProperties#FACING}, the very same property
 * {@code DirectionalElectricBlock} uses, so the shared BlockEntity's
 * facing-aware code keeps working). CEE node lifecycle (registration on place,
 * wire teardown on break, sneak-wrench pickup) comes from
 * {@link SimpleElectricalDeviceBlock}.</p>
 */
public class CeeDebRectifierBlock extends SimpleElectricalDeviceBlock<DebCeeDevice>
        implements IBE<CeeDebRectifierBlockEntity>, CeeNodeExtents {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    /**
     * NORTH-authored CEE node centres, block units (0..1). Ids match the PG
     * terminal indices in {@link DebRectifierBlockEntity#buildCircuit}:
     * {@code 0/1} = 120&nbsp;V intake, {@code 2/3} = 120&nbsp;V Power Feed,
     * {@code 4/5} = 12&nbsp;V Power Feed. Coordinates are the centres of
     * {@link DebRectifierBlock}'s own {@code TERMINALS} boxes.
     */
    private static final Map<Integer, Vec3> TIER2_NODES = nodeMap(new double[][] {
            {9.5, 1.5, 0.5}, {6.5, 1.5, 0.5},
            {6.5, 15.5, 1.5}, {4.5, 15.5, 1.5},
            {11.5, 15.5, 1.5}, {9.5, 15.5, 1.5},
    });

    private final PowerKitGeometry.OrientedShape shaper;

    public CeeDebRectifierBlock(Properties properties) {
        super(properties);
        BlockState initial = defaultBlockState().setValue(FACING, Direction.NORTH);
        registerDefaultState(masterSwitch().present() ? initial.setValue(PowerKitSwitch.ENABLED, true) : initial);
        this.shaper = new PowerKitGeometry.OrientedShape(outlineNorth());
    }

    /** NORTH-authored outline; tier subclasses override with their own model's shape. */
    protected VoxelShape outlineNorth() {
        return PowerKitGeometry.TIER2_SHAPE;
    }

    /** NORTH-authored CEE node centres by id; tier subclasses override. */
    protected Map<Integer, Vec3> nodesNorth() {
        return TIER2_NODES;
    }

    /**
     * The master switch this tier's model carries: tier 2's slide switch.
     * Tier subclasses override. Must return a constant &mdash; it is read from
     * {@link #createBlockStateDefinition}, i.e. during the super constructor.
     */
    public PowerKitSwitch.Kind masterSwitch() {
        return PowerKitSwitch.Kind.SLIDE_SWITCH;
    }

    /**
     * Whether a dye repaints this tier's body box ({@link PowerKitPaint}): the
     * Domestic and Commercial kits. Tier subclasses override. Must return a
     * constant &mdash; it is read from {@link #createBlockStateDefinition},
     * i.e. during the super constructor.
     */
    public boolean dyeable() {
        return true;
    }

    /**
     * This tier's signal nub box ({@link PowerKitSignalNub}), NORTH-authored px, or
     * null for none. Tier subclasses override. The nub (CEE node
     * {@link PowerKitSignalNub#NODE_ID}) exists only with the ComputerCraft CEE
     * Addon installed; nothing in {@link DebCeeDevice} touches it.
     */
    @Nullable
    public double[] signalNubBox() {
        return PowerKitSignalNub.TIER2_BOX;
    }

    /** The signal nub's NORTH-authored centre while it exists, else null. */
    @Nullable
    private Vec3 signalNodeNorth() {
        return PowerKitSignalNub.present() ? PowerKitSignalNub.centre(signalNubBox()) : null;
    }

    private static final String[] TIER2_OUTLETS = {"120V", "12V"};

    /** Outlet pairs from node id 2 on, in id order (see {@link PowerKitLabels#ceeNode}); tier subclasses override. */
    protected String[] outletsNorth() {
        return TIER2_OUTLETS;
    }

    // --- blockstate / placement / shape ------------------------------

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, PowerKitGeometry.TOP);
        if (masterSwitch().present()) {
            builder.add(PowerKitSwitch.ENABLED);
        }
        if (dyeable()) {
            builder.add(PowerKitPaint.COLOR);
        }
    }

    /** A dye repaints a dyeable kit; anything else goes on as before. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        ItemInteractionResult dyed = PowerKitPaint.tryDye(stack, state, level, pos, player);
        return dyed != null ? dyed : super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Back plate against whatever surface was clicked, same as the CPG kits.
        return PowerKitGeometry.placed(defaultBlockState(), context);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return PowerKitGeometry.rotate(state, rotation);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shaper.get(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shaper.get(state);
    }

    /**
     * The kit's orientation is fixed by the surface it was placed against &mdash;
     * a plain wrench must not spin it off its mount. Sneak-wrench (pick up +
     * CEE wire teardown) stays with {@link SimpleElectricalDeviceBlock}.
     */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }

    /** A right-click on the master switch flips it; anywhere else falls through. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        InteractionResult result = PowerKitSwitch.tryToggle(masterSwitch(), state, level, pos, hit,
                CEESoundEvents.VOLTAGE_REGULATOR.get());
        return result != InteractionResult.PASS ? result : super.useWithoutItem(state, level, pos, player, hit);
    }

    // --- CEE device wiring -----------------------------------------

    @Override
    public SimulatedDeviceType<DebCeeDevice> getDevice() {
        return CIODevices.POWER_KIT.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        // Turned with the model on every mount, floor and ceiling included.
        Map<Integer, Vec3> out = new HashMap<>();
        nodesNorth().forEach((id, v) -> out.put(id, PowerKitGeometry.orient(state, v)));
        Vec3 signal = signalNodeNorth();
        if (signal != null) {
            out.put(PowerKitSignalNub.NODE_ID, PowerKitGeometry.orient(state, signal));
        }
        return out;
    }

    /** A 1 px nub, the size of every node on tiers 1&ndash;3. */
    protected static final Vec3 NUB_1PX = new Vec3(1 / 16.0, 1 / 16.0, 1 / 16.0);

    /** NORTH-authored size (block units) of the model nub behind node {@code id}; tier subclasses override. */
    protected Vec3 nubSizeNorth(int id) {
        return NUB_1PX;
    }

    /** {@link #nubSizeNorth}, with the 1 px signal nub ahead of the tier's own sizes. */
    private Vec3 nubSize(int id) {
        return id == PowerKitSignalNub.NODE_ID ? NUB_1PX : nubSizeNorth(id);
    }

    /**
     * The nub's size for the wire-spool node outline. CEE draws that outline as a
     * cube of {@link #getNodeSize}; CIO's {@code WireSpoolNodeBoxMixin} swaps in the
     * exact box from {@link #getNodeExtents}, so a flat nub (tier 4's 2&times;1&times;2
     * intake / HV) isn't a cube. The cube fallback is the nub's largest side.
     */
    @Override
    public float getNodeSize(Level level, BlockPos pos, BlockState state, int id) {
        Vec3 size = nubSize(id);
        return (float) Math.max(size.x, Math.max(size.y, size.z));
    }

    /** {@link #nubSizeNorth} turned with the facing, the same way the node positions are. */
    @Override
    public Vec3 getNodeExtents(Level level, BlockPos pos, BlockState state, int id) {
        return PowerKitGeometry.orientSize(state, nubSize(id));
    }

    /** Hover label for each CEE node; the intake one follows the kit's selected tap. */
    @Override
    public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int id) {
        if (id == PowerKitSignalNub.NODE_ID) {
            return PowerKitLabels.signal();
        }
        String intake = null;
        if (level != null && level.getBlockEntity(pos) instanceof CeeDebRectifierBlockEntity be) {
            intake = PowerKitLabels.volts(be.primaryVolts());
        }
        return PowerKitLabels.ceeNode(id, intake, outletsNorth());
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        if (id == PowerKitSignalNub.NODE_ID) {
            Vec3 signal = signalNodeNorth();
            return signal == null ? null : PowerKitGeometry.orient(state, signal);
        }
        Vec3 v = nodesNorth().get(id);
        return v == null ? null : PowerKitGeometry.orient(state, v);
    }

    // --- Create IBE ---------------------------------------------

    @Override
    public Class<CeeDebRectifierBlockEntity> getBlockEntityClass() {
        return CeeDebRectifierBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CeeDebRectifierBlockEntity> getBlockEntityType() {
        return ElectroEnergeticsModule.CEE_DEB_RECTIFIER_BE.get();
    }

    // --- helpers ----------------------------------------------

    static Map<Integer, Vec3> nodeMap(double[][] centres16) {
        Map<Integer, Vec3> m = new HashMap<>();
        for (int i = 0; i < centres16.length; i++) {
            double[] c = centres16[i];
            m.put(i, new Vec3(c[0] / 16.0, c[1] / 16.0, c[2] / 16.0));
        }
        return Map.copyOf(m);
    }
}
