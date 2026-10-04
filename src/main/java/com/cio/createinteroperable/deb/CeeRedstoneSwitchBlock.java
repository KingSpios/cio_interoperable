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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;

/**
 * The Electro Energetics-wired twin of {@link RedstoneSwitchBlock}: same model,
 * same {@code POWERED}-driven contact and throughput/thermal simulation, but it
 * exposes CEE connection nodes instead of Power Grid terminals and runs on
 * Electro Energetics' solver through {@link RedstoneSwitchCeeDevice}. No Power
 * Grid type anywhere in this block's or its BlockEntity's hierarchy, so it is
 * safe to register on a CEE-only install.
 */
public class CeeRedstoneSwitchBlock extends SimpleElectricalDeviceBlock<RedstoneSwitchCeeDevice>
        implements IBE<CeeRedstoneSwitchBlockEntity> {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    /** NORTH-authored CEE node centres (block units). Ids match the PG terminal indices. */
    private static final Map<Integer, Vec3> NODES = nodeMap(new double[][] {
            {9.5, 1.5, 0.5}, {6.5, 1.5, 0.5},    // 0/1 input +/-
            {9.5, 15.5, 1.5}, {6.5, 15.5, 1.5},  // 2/3 output +/-
    });

    private final VoxelShaper shaper;

    public CeeRedstoneSwitchBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
        this.shaper = VoxelShaper.forDirectional(PowerKitGeometry.REDSTONE_SWITCH_SHAPE, Direction.NORTH)
                .withVerticalShapes(PowerKitGeometry.REDSTONE_SWITCH_SHAPE);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean signal = context.getLevel().hasNeighborSignal(context.getClickedPos());
        return defaultBlockState()
                .setValue(FACING, context.getClickedFace().getOpposite())
                .setValue(POWERED, signal);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shaper.get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shaper.get(state.getValue(FACING));
    }

    /** Fixed by its mount &mdash; a plain wrench must not spin it. Sneak-wrench (pickup + node teardown) stays with the base class. */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof CeeRedstoneSwitchBlockEntity be) {
            be.manualToggle();
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public SimulatedDeviceType<RedstoneSwitchCeeDevice> getDevice() {
        return CIODevices.REDSTONE_SWITCH.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        int angle = PowerKitGeometry.angleFor(state);
        Map<Integer, Vec3> out = new HashMap<>();
        NODES.forEach((id, v) -> out.put(id, PowerKitGeometry.rotateY(v, angle)));
        return out;
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        Vec3 v = NODES.get(id);
        return v == null ? null : PowerKitGeometry.rotateY(v, PowerKitGeometry.angleFor(state));
    }

    @Override
    public Class<CeeRedstoneSwitchBlockEntity> getBlockEntityClass() {
        return CeeRedstoneSwitchBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CeeRedstoneSwitchBlockEntity> getBlockEntityType() {
        return CIOBlockEntities.CEE_REDSTONE_SWITCH.get();
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
