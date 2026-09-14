package com.cio.createinteroperable;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.data.Iterate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
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
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Map;

/**
 * The water-output end of a Multi Radiator — mirrors {@link RadiatorValveNorthBlock}
 * exactly (see that class's doc for the shared FACING-is-shaft-only /
 * DOWN-is-pipe-only reasoning), except its DOWN face produces condensed
 * water instead of consuming steam (see
 * {@link RadiatorValveSouthBlockEntity#getWaterHandler}), fed by whatever the
 * North end's own valve actually condensed that tick. This end's own valve
 * independently throttles how much of that condensate is allowed to leave
 * toward a Create boiler, closing the steam/water loop.
 */
public class RadiatorValveSouthBlock extends KineticBlock implements IBE<RadiatorValveSouthBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ASSEMBLED = BooleanProperty.create("assembled");
    /** Drives the {@code #0} pipe sub-texture only — kept in sync with the North end's own tier, see RadiatorValveNorthBlockEntity. */
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<BrassHeaterBlock.HeatLevel> HEAT_LEVEL =
            net.minecraft.world.level.block.state.properties.EnumProperty.create("heat_level", BrassHeaterBlock.HeatLevel.class);
    /** See {@link RadiatorValveNorthBlock#HEAT_WARM} — same Cold-Sweat-only mirror booleans, unlisted in this block's blockstate JSON. */
    public static final BooleanProperty HEAT_WARM = BooleanProperty.create("heat_warm");
    public static final BooleanProperty HEAT_HOT = BooleanProperty.create("heat_hot");
    public static final BooleanProperty HEAT_BLAZING = BooleanProperty.create("heat_blazing");

    /**
     * Same real-geometry envelope as {@link RadiatorValveNorthBlock} (x4-12,
     * y0-14, z0-16 — confirmed identical across north/middle/south and every
     * heat tier), but this model's own un-rotated (blockstate "y": 0) state
     * already represents FACING=SOUTH, not NORTH — {@code multi_radiator_south.json}
     * has its valve plate at z14-16, matching this block's default FACING.
     */
    private static final Map<Direction, VoxelShape> SHAPES = ShapeRotation.forHorizontalFacing(
            Direction.SOUTH, new ShapeRotation.Box(4, 0, 0, 12, 14, 16));

    public RadiatorValveSouthBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.SOUTH).setValue(ASSEMBLED, false)
                .setValue(HEAT_LEVEL, BrassHeaterBlock.HeatLevel.COLD)
                .setValue(HEAT_WARM, false).setValue(HEAT_HOT, false).setValue(HEAT_BLAZING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, ASSEMBLED, HEAT_LEVEL, HEAT_WARM, HEAT_HOT, HEAT_BLAZING);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        for (Direction side : Iterate.horizontalDirections) {
            BlockState neighbor = context.getLevel().getBlockState(context.getClickedPos().relative(side));
            if (neighbor.getBlock() instanceof IRotate rotate
                    && rotate.hasShaftTowards(context.getLevel(), context.getClickedPos().relative(side), neighbor, side.getOpposite())) {
                return defaultBlockState().setValue(FACING, side);
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

    /** See {@link RadiatorValveNorthBlock#onWrenched} — identical assemble-first-else-rotate logic. */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!level.isClientSide && RadiatorAssembly.tryAssemble(level, pos)) {
            IWrenchable.playRotateSound(level, pos);
            return InteractionResult.SUCCESS;
        }
        return super.onWrenched(state, context);
    }

    @Override
    public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                                BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                                         BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** See {@link RadiatorValveNorthBlock#appendHoverText} — identical tooltip, shared lang key. */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("block.createinteroperable.multi_radiator.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public Class<RadiatorValveSouthBlockEntity> getBlockEntityClass() {
        return RadiatorValveSouthBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RadiatorValveSouthBlockEntity> getBlockEntityType() {
        return CIOBlockEntities.RADIATOR_VALVE_SOUTH.get();
    }
}
