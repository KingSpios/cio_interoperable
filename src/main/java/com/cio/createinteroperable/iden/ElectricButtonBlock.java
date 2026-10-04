package com.cio.createinteroperable.iden;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Electric twin of an Iden's Decor button (Heavy Button, Gate Button): a
 * momentary appliance-grid switch, closed for the button's press time (20
 * ticks, as Iden's) and emitting no redstone. Same blockstate properties and
 * the same shapes as Iden's own button classes, so Iden's blockstate/models
 * render it unchanged.
 */
public class ElectricButtonBlock extends ButtonBlock implements EntityBlock {

    /** Which of Iden's two button bodies this is (their hitboxes differ). */
    public enum Style { HEAVY, GATE }

    private final Style style;

    public ElectricButtonBlock(Style style, Properties properties) {
        super(BlockSetType.GOLD, 20, properties);
        this.style = style;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean pressed = state.getValue(POWERED);
        AttachFace face = state.getValue(FACE);
        Direction dir = state.getValue(FACING);
        return this.style == Style.HEAVY ? heavyShape(pressed, face, dir) : gateShape(pressed, face, dir);
    }

    // --- no redstone ---------------------------------------------------

    @Override
    protected boolean isSignalSource(BlockState state) {
        return false;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 0;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // Skip ButtonBlock's neighbour update; still drop the block entity.
        if (state.hasBlockEntity() && !state.is(newState.getBlock())) {
            level.removeBlockEntity(pos);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElectricSwitchBlockEntity(pos, state);
    }

    // --- Iden's shapes (copied from HeavyButtonBlock / GateButtonBlock) ---

    private static final VoxelShape HEAVY_FLOOR_Z = Block.box(5, 0, 3, 11, 4, 13);
    private static final VoxelShape HEAVY_FLOOR_X = Block.box(3, 0, 5, 13, 4, 11);
    private static final VoxelShape HEAVY_CEILING_Z = Block.box(5, 12, 3, 11, 16, 13);
    private static final VoxelShape HEAVY_CEILING_X = Block.box(3, 12, 5, 13, 16, 11);
    private static final VoxelShape HEAVY_NORTH = Block.box(5, 3, 12, 11, 13, 16);
    private static final VoxelShape HEAVY_SOUTH = Block.box(5, 3, 0, 11, 13, 4);
    private static final VoxelShape HEAVY_WEST = Block.box(12, 3, 5, 16, 13, 11);
    private static final VoxelShape HEAVY_EAST = Block.box(0, 3, 5, 4, 13, 11);
    private static final VoxelShape HEAVY_P_FLOOR_Z = Block.box(5, 0, 3, 11, 3, 13);
    private static final VoxelShape HEAVY_P_FLOOR_X = Block.box(3, 0, 5, 13, 3, 11);
    private static final VoxelShape HEAVY_P_CEILING_Z = Block.box(5, 13, 3, 11, 16, 13);
    private static final VoxelShape HEAVY_P_CEILING_X = Block.box(3, 13, 5, 13, 16, 11);
    private static final VoxelShape HEAVY_P_NORTH = Block.box(5, 3, 13, 11, 13, 16);
    private static final VoxelShape HEAVY_P_SOUTH = Block.box(5, 3, 0, 11, 13, 3);
    private static final VoxelShape HEAVY_P_WEST = Block.box(13, 3, 5, 16, 13, 11);
    private static final VoxelShape HEAVY_P_EAST = Block.box(0, 3, 5, 3, 13, 11);

    private static VoxelShape heavyShape(boolean pressed, AttachFace face, Direction dir) {
        boolean x = dir.getAxis() == Direction.Axis.X;
        return switch (face) {
            case FLOOR -> x ? (pressed ? HEAVY_P_FLOOR_X : HEAVY_FLOOR_X) : (pressed ? HEAVY_P_FLOOR_Z : HEAVY_FLOOR_Z);
            case CEILING -> x ? (pressed ? HEAVY_P_CEILING_X : HEAVY_CEILING_X) : (pressed ? HEAVY_P_CEILING_Z : HEAVY_CEILING_Z);
            case WALL -> switch (dir) {
                case NORTH -> pressed ? HEAVY_P_NORTH : HEAVY_NORTH;
                case SOUTH -> pressed ? HEAVY_P_SOUTH : HEAVY_SOUTH;
                case WEST -> pressed ? HEAVY_P_WEST : HEAVY_WEST;
                case EAST -> pressed ? HEAVY_P_EAST : HEAVY_EAST;
                default -> HEAVY_FLOOR_X;
            };
        };
    }

    private static final VoxelShape GATE_FLOOR = Block.box(4, 0, 4, 12, 8, 12);
    private static final VoxelShape GATE_CEILING = Block.box(4, 8, 4, 12, 16, 12);
    private static final VoxelShape GATE_NORTH = Block.box(4, 4, 8, 12, 12, 16);
    private static final VoxelShape GATE_SOUTH = Block.box(4, 4, 0, 12, 12, 8);
    private static final VoxelShape GATE_WEST = Block.box(8, 4, 4, 16, 12, 12);
    private static final VoxelShape GATE_EAST = Block.box(0, 4, 4, 8, 12, 12);
    private static final VoxelShape GATE_P_FLOOR = Block.box(4, 0, 4, 12, 7, 12);
    private static final VoxelShape GATE_P_CEILING = Block.box(4, 9, 4, 12, 16, 12);
    private static final VoxelShape GATE_P_NORTH = Block.box(4, 4, 9, 12, 12, 16);
    private static final VoxelShape GATE_P_SOUTH = Block.box(4, 4, 0, 12, 12, 7);
    private static final VoxelShape GATE_P_WEST = Block.box(9, 4, 4, 16, 12, 12);
    private static final VoxelShape GATE_P_EAST = Block.box(0, 4, 4, 7, 12, 12);

    private static VoxelShape gateShape(boolean pressed, AttachFace face, Direction dir) {
        return switch (face) {
            case FLOOR -> pressed ? GATE_P_FLOOR : GATE_FLOOR;
            case CEILING -> pressed ? GATE_P_CEILING : GATE_CEILING;
            case WALL -> switch (dir) {
                case NORTH -> pressed ? GATE_P_NORTH : GATE_NORTH;
                case SOUTH -> pressed ? GATE_P_SOUTH : GATE_SOUTH;
                case WEST -> pressed ? GATE_P_WEST : GATE_WEST;
                case EAST -> pressed ? GATE_P_EAST : GATE_EAST;
                default -> pressed ? GATE_P_FLOOR : GATE_FLOOR;
            };
        };
    }
}
