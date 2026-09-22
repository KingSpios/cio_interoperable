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
 * A registered, Creative-menu-placeable duct segment: EAST-relative face is
 * the "motor-facing" side (accepts cold_air in, offers hot_air out — see
 * {@link AirconVenterBlockEntity}), WEST-relative face is where a
 * further-down-the-chain venter's relayed hot_air lands. "East"/"west" are
 * relative to {@link #FACING} (model authored at FACING=NORTH, matching
 * {@code aircon_venter.json}'s literal faces), remapped per-state by
 * {@link #rotate} — same clockwise "y" blockstate rotation convention as
 * {@link ShapeRotation}/{@code AirconMotorBottomBlock}.
 * <p>
 * Up to {@link AirconVenterAssembly#MAX_CHAIN} venters in a straight,
 * same-FACING row automatically link into one chain purely by placement —
 * see {@link AirconVenterAssembly}, recomputed live every tick by
 * {@link AirconVenterBlockEntity}. The wrench here does nothing but rotate;
 * an earlier version tried to double it up as an explicit "assemble" action,
 * which the user asked to remove — placement alone is now the whole story.
 */
public class AirconVenterBlock extends Block implements IBE<AirconVenterBlockEntity>, IWrenchable {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ASSEMBLED = BooleanProperty.create("assembled");

    /** Rough envelope of body_box + fin elements — see AirconMotorBottomBlock's SHAPES doc for why this isn't pixel-perfect. */
    private static final Map<Direction, VoxelShape> SHAPES = ShapeRotation.forHorizontalFacing(
            new ShapeRotation.Box(0, 0, 1, 16, 16, 15));

    public AirconVenterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(ASSEMBLED, false));
    }

    /** NORTH=0, EAST=1, SOUTH=2, WEST=3 clockwise steps — same convention {@link ShapeRotation} uses internally. */
    private static int clockwiseSteps(Direction facing) {
        return switch (facing) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }

    /** @return which real world direction the model's {@code base} face (as authored at FACING=NORTH) points to at the given FACING. */
    static Direction rotate(Direction base, Direction facing) {
        Direction d = base;
        for (int i = 0, steps = clockwiseSteps(facing); i < steps; i++) {
            d = d.getClockWise();
        }
        return d;
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

    /** Plain facing-cycle rotate — chain membership is automatic now (see {@link AirconVenterAssembly}), not wrench-triggered, so there's nothing else for a wrench to do here. */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
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
    public Class<AirconVenterBlockEntity> getBlockEntityClass() {
        return AirconVenterBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends AirconVenterBlockEntity> getBlockEntityType() {
        return CIOBlockEntities.AIRCON_VENTER.get();
    }
}
