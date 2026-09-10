package com.cio.createinteroperable;

import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Sits ONLY directly on top of a Create Fluid Tank (never on its side) —
 * this is a deliberate, hardcoded requirement, not a placement preference:
 * {@link #canSurvive} only ever checks {@code pos.below()}. Instead of
 * drawing rotational power, it reads the boiler's real activeHeat/waterSupply
 * (via BoilerData, made reachable with no real Steam Engine required by
 * BoilerDataMixin — see that class) and produces "steam" fluid into a small
 * internal tank, exposed to Create's pipe network on its TOP face (the
 * bottom is permanently occupied by the tank, so output moved up).
 * <p>
 * FACING no longer indicates which side the tank is on (there is only ever
 * one valid direction — straight down — so that's not a placement choice
 * anymore). It is purely which horizontal side the shaft socket faces, same
 * role FACING plays on BrassHeaterBlock, and {@link #getStateForPlacement}
 * uses the exact same "prefer aligning with an adjacent shaft" heuristic
 * BrassHeaterBlock does, since the tank side of the placement decision is
 * gone.
 * <p>
 * The shaft (see #hasShaftTowards, on the FACING-opposite face) drives a
 * real fluid valve (see {@link #OPEN}, and SteamOutletBlockEntity#pointer):
 * spin it one direction and it opens, the other direction and it closes,
 * exactly like Create's own FluidValveBlock. This only gates whether the
 * produced steam is actually released to the pipe network above — it is NOT
 * what makes an attached Steam Engine's SU output drop. That dilution is
 * purely structural (see BoilerDataMixin): merely sitting on top of the tank
 * counts this block into BoilerData#attachedEngines regardless of valve
 * position, same as a real Steam Engine bolted to the same boiler. Closing
 * the valve stops steam from leaving, but the boiler still "knows" this
 * outlet is tapping it.
 */
public class SteamOutletBlock extends KineticBlock implements IBE<SteamOutletBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /**
     * A real fluid valve, not a throttle — mirrors Create's own
     * FluidValveBlock#ENABLED exactly: spinning the shaft one direction
     * (positive speed) chases this toward true, the other direction chases
     * it toward false (see SteamOutletBlockEntity#pointer). Gates production
     * as a hard on/off, not a proportional gradient.
     */
    public static final BooleanProperty OPEN = BooleanProperty.create("open");

    private static final VoxelShape SHAPE = Shapes.block();

    public SteamOutletBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
    }

    public Direction getFacing(BlockState state) {
        return state.getValue(FACING);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, OPEN);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING).getOpposite();
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirrorIn) {
        return state.rotate(mirrorIn.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Tank direction is no longer a placement choice (always straight
        // down — see #canSurvive), so FACING is decided purely by shaft
        // alignment, same heuristic BrassHeaterBlock uses: prefer lining up
        // with an already-present neighbor that has a shaft facing us.
        for (Direction side : Iterate.horizontalDirections) {
            BlockState neighbor = context.getLevel().getBlockState(context.getClickedPos().relative(side));
            if (neighbor.getBlock() instanceof IRotate rotate
                    && rotate.hasShaftTowards(context.getLevel(), context.getClickedPos().relative(side), neighbor, side.getOpposite())) {
                return defaultBlockState().setValue(FACING, side.getOpposite());
            }
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).getBlock() instanceof FluidTankBlock;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                   LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !canSurvive(state, level, pos)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!oldState.is(state.getBlock())) {
            FluidTankBlock.updateBoilerState(state, level, pos.below());
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.hasBlockEntity() && (!state.is(newState.getBlock()) || !newState.hasBlockEntity())) {
            level.removeBlockEntity(pos);
        }
        if (!state.is(newState.getBlock())) {
            FluidTankBlock.updateBoilerState(state, level, pos.below());
        }
    }

    @Override
    public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos,
                                CollisionContext context) {
        return SHAPE;
    }

    @Override
    public Class<SteamOutletBlockEntity> getBlockEntityClass() {
        return SteamOutletBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SteamOutletBlockEntity> getBlockEntityType() {
        return CIOBlockEntities.STEAM_OUTLET.get();
    }
}
