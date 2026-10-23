package com.cio.createinteroperable.cee;

import com.cio.createinteroperable.CIODevices;
import com.cio.createinteroperable.ShapeRotation;
import com.cio.createinteroperable.modules.ElectroEnergeticsModule;
import com.cio.createinteroperable.telephone.cee.CeeTelephoneBlock;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Map;

/**
 * "Compact Transformer" &mdash; a CEE-only, quarter-size twin of Electro
 * Energetics' own Transformer block, rated 15 kW instead of 25 kW (see
 * {@link CeeCompactTransformerDevice}). Mirrors CEE's {@code TransformerBlock}
 * (placement, waterlogging, rotate/mirror, Ratio device data) on CIO's own
 * {@code cee_mini_transformer} model.
 *
 * <p>Windings, in the model's default (FACING=NORTH) frame: the PRIMARY (in)
 * pins are the south pair, the SECONDARY (out) pins the north pair &mdash; the
 * same layout CEE's Transformer uses, so power flows south &rarr; north. The
 * model marks the flow with an arrow on top and each node's hover label says
 * which winding it is.</p>
 */
public class CeeCompactTransformerBlock extends SimpleElectricalDeviceBlock<CeeCompactTransformerDevice>
        implements ProperWaterloggedBlock, IBE<CeeCompactTransformerBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    /** Nub centres (1/16 voxels, FACING=NORTH): 0/1 primary (south pair), 2/3 secondary (north pair), west then east — CEE's own id order. */
    private static final Vec3[] NODES = {
            new Vec3(6.1 / 16, 13.5 / 16, 10.0 / 16),
            new Vec3(10.1 / 16, 13.5 / 16, 10.0 / 16),
            new Vec3(6.1 / 16, 13.5 / 16, 6.0 / 16),
            new Vec3(10.1 / 16, 13.5 / 16, 6.0 / 16),
    };

    private static final Map<Direction, VoxelShape> SHAPES = ShapeRotation.forHorizontalFacing(
            new ShapeRotation.Box(5, 0, 5, 11, 2, 11),           // foot
            new ShapeRotation.Box(4, 2, 4, 12, 12, 12),          // body_box
            new ShapeRotation.Box(3, 2, 5, 4, 12, 11),           // west fin
            new ShapeRotation.Box(12, 2, 5, 13, 12, 11),         // east fin
            new ShapeRotation.Box(5.1, 11.5, 5, 7.1, 14.5, 7),   // north_left (secondary)
            new ShapeRotation.Box(9.1, 11.5, 5, 11.1, 14.5, 7),  // north_right (secondary)
            new ShapeRotation.Box(5.1, 11.5, 9, 7.1, 14.5, 11),  // south_left (primary)
            new ShapeRotation.Box(9.1, 11.5, 9, 11.1, 14.5, 11)  // south_right (primary)
    );

    public CeeCompactTransformerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, WATERLOGGED);
    }

    /** Seeds a freshly created device with the slider's ratio, same as CEE's TransformerBlock. */
    @Override
    public CompoundTag getDefaultDeviceData(Level level, BlockPos pos, BlockState state) {
        CompoundTag tag = new CompoundTag();
        if (level.getBlockEntity(pos) instanceof CeeCompactTransformerBlockEntity be) {
            tag.putDouble("Ratio", CeeCompactTransformerBlockEntity.indexToRatio(be.getRatioIndex()));
        }
        return tag;
    }

    /** CEE's Transformer placement: the primary faces the player; sneak-place to flip it. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction dir = context.getHorizontalDirection();
        boolean flip = context.getPlayer() != null && context.getPlayer().isShiftKeyDown();
        return withWater(defaultBlockState().setValue(FACING, flip ? dir.getOpposite() : dir), context);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        updateWater(level, state, pos);
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return fluidState(state);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("block.createinteroperable.cee_mini_transformer.tooltip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("block.createinteroperable.cee_mini_transformer.tooltip.max_power")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(" ").append(Component.translatable(
                "block.createinteroperable.cee_mini_transformer.tooltip.max_power_value"))
                .withStyle(ChatFormatting.GOLD));
    }

    // --- CEE device wiring -----------------------------------------

    @Override
    public SimulatedDeviceType<CeeCompactTransformerDevice> getDevice() {
        return CIODevices.COMPACT_TRANSFORMER.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        int angle = CeeTelephoneBlock.angleFor(state);
        return Map.of(
                0, CeeTelephoneBlock.rotateY(NODES[0], angle),
                1, CeeTelephoneBlock.rotateY(NODES[1], angle),
                2, CeeTelephoneBlock.rotateY(NODES[2], angle),
                3, CeeTelephoneBlock.rotateY(NODES[3], angle));
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        if (id < 0 || id >= NODES.length) {
            return null;
        }
        return CeeTelephoneBlock.rotateY(NODES[id], CeeTelephoneBlock.angleFor(state));
    }

    @Override
    public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int id) {
        return id == 0 || id == 1
                ? Component.translatable("createinteroperable.compact_transformer.node.primary")
                : Component.translatable("createinteroperable.compact_transformer.node.secondary");
    }

    /** 2 px nubs, 4 px apart: CEE's own size for its 2 px nubs (Capacitor, Diode) instead of the 4 px default. */
    @Override
    public float getNodeSize(Level level, BlockPos pos, BlockState state, int id) {
        return 0.125F;
    }

    // --- Create IBE ---------------------------------------------

    @Override
    public Class<CeeCompactTransformerBlockEntity> getBlockEntityClass() {
        return CeeCompactTransformerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CeeCompactTransformerBlockEntity> getBlockEntityType() {
        return ElectroEnergeticsModule.CEE_COMPACT_TRANSFORMER_BE.get();
    }
}
