package com.cio.createinteroperable;

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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * The top half of the Aircon Motor multiblock — purely visual/particle
 * (blade spin + hot air output). All electrical state lives on
 * {@link AirconMotorBottomBlock} one block below; see {@link AirconMotorAssembly}
 * for the wrench pairing and {@link AirconMotorTopBlockEntity#getEfficiency}
 * for how this half reads the bottom's live state (a fixed relative lookup,
 * no stored cross-link needed since the two are always exactly 1 apart).
 */
public class AirconMotorTopBlock extends Block implements IBE<AirconMotorTopBlockEntity>, IWrenchable {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Shared with {@link AirconMotorBottomBlock} and the CEE-wired bottom variant — see {@link CIOProperties#AIRCON_ASSEMBLED}'s own doc. */
    public static final BooleanProperty ASSEMBLED = CIOProperties.AIRCON_ASSEMBLED;

    /** Rough envelope of body_box + fin + blade elements — see AirconMotorBottomBlock's SHAPES doc for why this isn't pixel-perfect. */
    private static final Map<Direction, VoxelShape> SHAPES = ShapeRotation.forHorizontalFacing(
            new ShapeRotation.Box(0, 0, 1, 16, 16, 15));

    public AirconMotorTopBlock(Properties properties) {
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

    /** See {@link AirconMotorBottomBlock#onWrenched} — identical assemble-first-else-rotate logic, mirrored direction. */
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
    public Class<AirconMotorTopBlockEntity> getBlockEntityClass() {
        return AirconMotorTopBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends AirconMotorTopBlockEntity> getBlockEntityType() {
        return CIOBlockEntities.AIRCON_MOTOR_TOP.get();
    }
}
