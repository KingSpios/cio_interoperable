package com.cio.createinteroperable.iden;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Electric twin of Iden's Decor's Core Lever / Core Button Control Panel. A
 * port of Iden's {@code HorizontalConnectableBlock} + {@code ControlPanelBlock}
 * + {@code Lever/ButtonControlPanelBlock} (same {@code facing}/{@code shape}/
 * {@code powered} properties and values, so Iden's blockstate/models render it),
 * but its {@code powered} state opens and closes an appliance-grid switch
 * instead of emitting redstone. Like Iden's, a panel joins up only with panels
 * of its own exact block.
 */
public class ElectricControlPanelBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Part> SHAPE = EnumProperty.create("shape", Part.class);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    /** Iden's button panel holds for 30 ticks. */
    private static final int BUTTON_TICKS = 30;

    /** True: a momentary button panel; false: a toggle lever panel. */
    private final boolean momentary;

    public ElectricControlPanelBlock(boolean momentary, Properties properties) {
        super(properties);
        this.momentary = momentary;
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(SHAPE, Part.SINGLE)
                .setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SHAPE, POWERED);
    }

    // --- interaction -----------------------------------------------------

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (this.momentary) {
            if (state.getValue(POWERED)) {
                return InteractionResult.CONSUME;
            }
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(POWERED, true), 3);
                level.scheduleTick(pos, this, BUTTON_TICKS);
                level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS);
                level.gameEvent(player, GameEvent.BLOCK_ACTIVATE, pos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockState next = state.cycle(POWERED);
        level.setBlock(pos, next, 3);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3F, next.getValue(POWERED) ? 0.6F : 0.5F);
        level.gameEvent(player, next.getValue(POWERED) ? GameEvent.BLOCK_ACTIVATE : GameEvent.BLOCK_DEACTIVATE, pos);
        return InteractionResult.CONSUME;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (this.momentary && state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, false), 3);
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS);
            level.gameEvent(null, GameEvent.BLOCK_DEACTIVATE, pos);
        }
    }

    // --- connectable shape (port of Iden's HorizontalConnectableBlock) ---

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        return state.setValue(SHAPE, this.partFor(state, context.getLevel(), context.getClickedPos()));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        return state.setValue(SHAPE, this.partFor(state, level, pos));
    }

    private Part partFor(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.getValue(FACING);

        BlockState front = level.getBlockState(pos.relative(facing));
        if (front.is(this) && front.getValue(FACING).getAxis() != facing.getAxis()) {
            return front.getValue(FACING) == facing.getCounterClockWise() ? Part.INNER_LEFT : Part.INNER_RIGHT;
        }
        BlockState back = level.getBlockState(pos.relative(facing.getOpposite()));
        if (back.is(this) && back.getValue(FACING).getAxis() != facing.getAxis()) {
            return back.getValue(FACING) == facing.getCounterClockWise() ? Part.OUTER_LEFT : Part.OUTER_RIGHT;
        }
        boolean left = level.getBlockState(pos.relative(facing.getClockWise())).is(this);
        boolean right = level.getBlockState(pos.relative(facing.getCounterClockWise())).is(this);
        if (left && right) {
            return Part.CENTER;
        }
        if (left) {
            return Part.LEFT;
        }
        return right ? Part.RIGHT : Part.SINGLE;
    }

    // --- shape (copied from Iden's ControlPanelBlock) ------------------------

    private static final VoxelShape EAST = Shapes.or(Block.box(0, 0, 0, 16, 11, 16), Block.box(6, 11, 0, 11, 14, 16), Block.box(0, 0, 0, 6, 16, 16));
    private static final VoxelShape SOUTH = Shapes.or(Block.box(0, 0, 0, 16, 11, 16), Block.box(0, 11, 6, 16, 14, 11), Block.box(0, 0, 0, 16, 16, 6));
    private static final VoxelShape WEST = Shapes.or(Block.box(0, 0, 0, 16, 11, 16), Block.box(5, 11, 0, 10, 14, 16), Block.box(10, 0, 0, 16, 16, 16));
    private static final VoxelShape NORTH = Shapes.or(Block.box(0, 0, 0, 16, 11, 16), Block.box(0, 11, 5, 16, 14, 10), Block.box(0, 0, 10, 16, 16, 16));

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> NORTH;
        };
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElectricSwitchBlockEntity(pos, state);
    }

    /** Mirror of Iden's {@code HorizontalConnectableProperty}. */
    public enum Part implements StringRepresentable {
        LEFT, CENTER, RIGHT, SINGLE, INNER_LEFT, INNER_RIGHT, OUTER_LEFT, OUTER_RIGHT;

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
