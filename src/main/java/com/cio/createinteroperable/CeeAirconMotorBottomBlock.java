package com.cio.createinteroperable;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;

/**
 * The Electro Energetics-wired twin of {@link AirconMotorBottomBlock}: same
 * model, same dynamic-resistance load, same Off/Low/Mid/Max slider, same
 * fluid mechanics (ported near-verbatim onto
 * {@link CeeAirconMotorBottomBlockEntity} â see that class's own doc for why
 * this codebase duplicates that logic rather than sharing it, matching the
 * existing {@code CeeDebRectifierBlockEntity}/{@code DebRectifierBlockEntity}
 * precedent), but it exposes CEE connection nodes instead of Power Grid
 * terminals and runs on Electro Energetics' solver through
 * {@link AirconCeeDevice}. No Power Grid type anywhere in this block's or
 * its BlockEntity's hierarchy, so it is safe to register on a CEE-only
 * install. Implements the zero-dependency {@link AirconMotorBottomMarker} so
 * the always-loaded {@link AirconMotorAssembly} can pair this with
 * {@link AirconMotorTopBlock} (the SAME top half the Power Grid variant
 * uses â see that class's own doc) without ever referencing this class
 * directly.
 */
public class CeeAirconMotorBottomBlock extends SimpleElectricalDeviceBlock<AirconCeeDevice>
        implements IBE<CeeAirconMotorBottomBlockEntity>, AirconMotorBottomMarker {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Shared with {@link AirconMotorBottomBlock}/{@link AirconMotorTopBlock} â see {@link CIOProperties#AIRCON_ASSEMBLED}'s own doc. */
    public static final BooleanProperty ASSEMBLED = CIOProperties.AIRCON_ASSEMBLED;

    /** Matches {@link AirconCeeDevice}'s own node ids â 0 positive, 1 negative. */
    public static final int POSITIVE_NODE = 0;
    public static final int NEGATIVE_NODE = 1;

    /**
     * NORTH-authored CEE node centres (block units) â copied from the exact
     * centres of {@link AirconMotorBottomBlock}'s own PG
     * {@code POSITIVE_TERMINAL_BASE}/{@code NEGATIVE_TERMINAL_BASE}
     * ({@code aircon_motor_bottom.json}'s {@code power_pin_1_positive}
     * x9-11/y0.1-2.1/z0-2 and {@code power_pin_2_negative} x5-7/y0.1-2.1/z0-2),
     * so the CEE connection nodes sit at the exact same visible pin
     * geometry the PG terminals occupy.
     */
    private static final Map<Integer, Vec3> NODES = nodeMap(new double[][] {
            {10.0, 1.1, 1.0},  // 0 positive
            {6.0, 1.1, 1.0},   // 1 negative
    });

    /** Same rough envelope as {@link AirconMotorBottomBlock}'s own SHAPES â not pixel-perfect, just enough to avoid the full-cube neighbor-culling bug (see that class's own doc). Duplicated rather than shared since that field is private to the PG class. */
    private static final Map<Direction, VoxelShape> SHAPES = ShapeRotation.forHorizontalFacing(
            new ShapeRotation.Box(0, 0, 1, 16, 16, 15),
            new ShapeRotation.Box(4, 0, 0, 12, 3, 2));

    /** Design voltage of this motor variant — 120 V standard; {@link CeeAirconMotorBottom240Block} overrides. Read by the BlockEntity for every voltage-scaled number. */
    public float ratedVolts() {
        return 120f;
    }

    public CeeAirconMotorBottomBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(ASSEMBLED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, ASSEMBLED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirrorIn) {
        return state.rotate(mirrorIn.getRotation(state.getValue(FACING)));
    }

    /** Same "assemble-first-else-rotate" shape as {@link AirconMotorBottomBlock#onWrenched} â no PG-specific wire-connection refresh needed here (CEE node positions don't move with ASSEMBLED). */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (AirconMotorAssembly.tryAssemble(level, pos)) {
            IWrenchable.playRotateSound(level, pos);
            return InteractionResult.SUCCESS;
        }
        if (state.getValue(ASSEMBLED)) {
            return InteractionResult.PASS;
        }
        BlockState rotated = state.setValue(FACING, state.getValue(FACING).getClockWise());
        if (!level.isClientSide) {
            level.setBlockAndUpdate(pos, rotated);
        }
        IWrenchable.playRotateSound(level, pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public SimulatedDeviceType<AirconCeeDevice> getDevice() {
        return CIODevices.AIRCON_MOTOR.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        int angle = angleDegrees(state.getValue(FACING));
        Map<Integer, Vec3> out = new HashMap<>();
        NODES.forEach((id, v) -> out.put(id, rotateY(v, angle)));
        return out;
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        Vec3 v = NODES.get(id);
        return v == null ? null : rotateY(v, angleDegrees(state.getValue(FACING)));
    }

    /**
     * NORTH=0, EAST=90, SOUTH=180, WEST=270 â same convention
     * {@code AirconMotorBottomBlockEntity}'s own (private) copy uses; that
     * package-private {@code deb.PowerKitGeometry} helper the other CEE
     * blocks in this codebase share isn't accessible from this (root)
     * package, so this is a small local duplicate rather than a cross-
     * package visibility change to an existing, already-used utility.
     */
    private static int angleDegrees(Direction facing) {
        return switch (facing) {
            case NORTH -> 0;
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }

    /** Rotates a block-local (0..1) offset about the block's own vertical center by a multiple of 90Â° â same pattern as {@code AirconMotorBottomBlockEntity#rotateY}. */
    private static Vec3 rotateY(Vec3 base, int angle) {
        double dx = base.x - 0.5, dz = base.z - 0.5;
        return switch (angle) {
            case 90 -> new Vec3(0.5 - dz, base.y, 0.5 + dx);
            case 180 -> new Vec3(0.5 - dx, base.y, 0.5 - dz);
            case 270 -> new Vec3(0.5 + dz, base.y, 0.5 - dx);
            default -> base;
        };
    }

    @Override
    public Class<CeeAirconMotorBottomBlockEntity> getBlockEntityClass() {
        return CeeAirconMotorBottomBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CeeAirconMotorBottomBlockEntity> getBlockEntityType() {
        return CIOBlockEntities.CEE_AIRCON_MOTOR_BOTTOM.get();
    }

    private static Map<Integer, Vec3> nodeMap(double[][] centres16) {
        Map<Integer, Vec3> m = new HashMap<>();
        for (int i = 0; i < centres16.length; i++) {
            double[] c = centres16[i];
            m.put(i, new Vec3(c[0] / 16.0, c[1] / 16.0, c[2] / 16.0));
        }
        return Map.copyOf(m);
    }
}
