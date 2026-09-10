package com.cio.createinteroperable;

import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A dead-end (single-socket) horizontal-shaft kinetic block — like Create's
 * own Saw in its horizontal orientation (see SawBlock#hasShaftTowards), NOT
 * a pass-through relay: the shaft only connects on the face OPPOSITE of
 * FACING, matching Create's own convention that FACING is the block's
 * "front" and the drive comes in from directly behind it. Deliberately
 * horizontal-only (unlike Power Grid's Variac, whose shaft is vertical) —
 * FACING is restricted to the 4 horizontal directions, never up/down.
 * <p>
 * The shaft itself is never part of this block's own model — see
 * BrassHeaterRenderer/BrassHeaterVisual, which render a separate
 * AllPartialModels.SHAFT_HALF mesh through empty space left in the model at
 * the FACING-opposite face. Consumes "steam" fed into its bottom face and
 * reports a live 0-100% throttle to Cold Sweat (once wired up — see
 * BrassHeaterBlockEntity#getHeatFraction). The shaft's speed IS the
 * throttle: no manual UI, connect any Create rotational input (a shaft,
 * gearbox, or a hand-turned Valve Handle) and its speed directly sets how
 * much steam is drawn per tick.
 * <p>
 * HEAT_LEVEL is a coarse, 4-tier reflection of that same throttle, purely
 * for picking one of 4 textures/models — the real, continuous value lives on
 * the BlockEntity (see getHeatFraction), same split Blaze Burner uses
 * between its own coarse HeatLevel blockstate (for texture/model + light
 * level) and continuously-tracked internal state.
 */
public class BrassHeaterBlock extends KineticBlock implements IBE<BrassHeaterBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<HeatLevel> HEAT_LEVEL = EnumProperty.create("heat_level", HeatLevel.class);
    /**
     * A real fluid valve, not a throttle — mirrors Create's own
     * FluidValveBlock#ENABLED: spin the shaft one direction and it opens
     * (steam can be drawn in), the other direction and it closes. See
     * BrassHeaterBlockEntity#pointer.
     */
    public static final BooleanProperty OPEN = BooleanProperty.create("open");

    private static final VoxelShape SHAPE = Shapes.block();

    public BrassHeaterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH)
                .setValue(HEAT_LEVEL, HeatLevel.COLD)
                .setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, HEAT_LEVEL, OPEN);
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
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Prefer facing so the socket (FACING's opposite) lines up with an
        // already-present shaft-having neighbor, same idea as
        // DirectionalKineticBlock#getPreferredFacing but restricted to
        // horizontal since this block is never vertical.
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
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirrorIn) {
        return state.rotate(mirrorIn.getRotation(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                                net.minecraft.core.BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public Class<BrassHeaterBlockEntity> getBlockEntityClass() {
        return BrassHeaterBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BrassHeaterBlockEntity> getBlockEntityType() {
        return CIOBlockEntities.BRASS_HEATER.get();
    }

    public enum HeatLevel implements StringRepresentable {
        COLD, WARM, HOT, BLAZING;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
