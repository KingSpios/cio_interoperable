package com.cio.createinteroperable.deb;

import com.cio.createinteroperable.CIOBlockEntities;
import com.cio.createinteroperable.CIODevices;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
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
        implements IBE<CeeDebRectifierBlockEntity> {

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
            {9.5, 15.5, 1.5}, {11.5, 15.5, 1.5},
    });

    private final VoxelShaper shaper;

    public CeeDebRectifierBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
        this.shaper = VoxelShaper.forDirectional(outlineNorth(), Direction.NORTH)
                .withVerticalShapes(outlineNorth());
    }

    /** NORTH-authored outline; tier subclasses override with their own model's shape. */
    protected VoxelShape outlineNorth() {
        return PowerKitGeometry.TIER2_SHAPE;
    }

    /** NORTH-authored CEE node centres by id; tier subclasses override. */
    protected Map<Integer, Vec3> nodesNorth() {
        return TIER2_NODES;
    }

    // --- blockstate / placement / shape ------------------------------

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Back plate against whatever surface was clicked, same as PG's
        // DirectionalElectricBlock.
        return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shaper.get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shaper.get(state.getValue(FACING));
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

    // --- CEE device wiring -----------------------------------------

    @Override
    public SimulatedDeviceType<DebCeeDevice> getDevice() {
        return CIODevices.POWER_KIT.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        int angle = PowerKitGeometry.angleFor(state);
        Map<Integer, Vec3> out = new HashMap<>();
        nodesNorth().forEach((id, v) -> out.put(id, PowerKitGeometry.rotateY(v, angle)));
        return out;
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        Vec3 v = nodesNorth().get(id);
        return v == null ? null : PowerKitGeometry.rotateY(v, PowerKitGeometry.angleFor(state));
    }

    // --- Create IBE ---------------------------------------------

    @Override
    public Class<CeeDebRectifierBlockEntity> getBlockEntityClass() {
        return CeeDebRectifierBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CeeDebRectifierBlockEntity> getBlockEntityType() {
        return CIOBlockEntities.CEE_DEB_RECTIFIER.get();
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
