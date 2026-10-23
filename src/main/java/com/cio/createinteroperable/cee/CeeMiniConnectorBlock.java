package com.cio.createinteroperable.cee;

import com.cio.createinteroperable.CIODevices;
import com.george_vi.electroenergetics.content.connector.DoubleConnectorDevice;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.NodeConfigurator;
import com.simibubi.create.AllShapes;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * "Mini Double Connector" &mdash; a CEE-only, 4&times;4 px double connector:
 * two separate 1 px pins (nodes 0 and 1) that are NOT joined to each other, so
 * the block never closes a circuit by itself. Electrically it is CEE's own
 * {@link DoubleConnectorDevice} (no bridges at all), registered under CIO's
 * own device type.
 *
 * <p>Mounts on any face like CEE's connectors ({@link #FACING} = the face it
 * was placed on; the pins point away from it), using CEE's blockstate rotation
 * convention and its {@link NodeConfigurator} so the pins and the wire attach
 * points always agree. Unlike CEE's double connector there is no ROLL: its
 * diagonal pins would only swap to the other diagonal, so the wrench always
 * cycles {@link #STYLE} instead.</p>
 */
public class CeeMiniConnectorBlock extends SimpleElectricalDeviceBlock<DoubleConnectorDevice> implements ProperWaterloggedBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final EnumProperty<Style> STYLE = EnumProperty.create("style", Style.class);

    /** Wrench-cycle order; each names its model and where its pin tops are (FACING=UP, 1/16 voxels). */
    public enum Style implements StringRepresentable {
        SHORT("short", "mini_connector_cee_1", 2, 2),
        TALL("tall", "mini_connector_cee_2", 3, 3),
        STACKED("stacked", "mini_connector_cee_3", 5, 5);

        private final String name;
        public final String model;
        final NodeConfigurator nodes;
        final VoxelShaper shape;

        Style(String name, String model, float pinTop, double shapeTop) {
            this.name = name;
            this.model = model;
            // pin_left (7..8, 7..8) and pin_right (8..9, 8..9): wires attach at the top centre of each
            this.nodes = new NodeConfigurator.Builder().add(7.5F, pinTop, 7.5F).add(8.5F, pinTop, 8.5F).simple();
            this.shape = new AllShapes.Builder(Block.box(6, 0, 6, 10, 1, 10)).add(7, 1, 7, 9, shapeTop, 9).forDirectional(Direction.UP);
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public CeeMiniConnectorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP).setValue(STYLE, Style.SHORT).setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, STYLE, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withWater(defaultBlockState().setValue(FACING, context.getClickedFace()), context);
    }

    /** The wrench cycles the style from any face (see class doc for why there is no roll). */
    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        return originalState.cycle(STYLE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(STYLE).shape.get(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
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

    // --- CEE device wiring -----------------------------------------

    @Override
    public SimulatedDeviceType<DoubleConnectorDevice> getDevice() {
        return CIODevices.MINI_CONNECTOR.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return state.getValue(STYLE).nodes.getNodes(state.getValue(FACING));
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        return state.getValue(STYLE).nodes.getNodePos(state.getValue(FACING), id);
    }

    /** Like CEE's double connector: a wire may run from one pin to the other. */
    @Override
    public boolean canSelfConnect(Level level, BlockPos pos, BlockState state, int id1, int id2) {
        return true;
    }

    @Override
    public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int id) {
        return Component.translatable(id == 0
                ? "createinteroperable.mini_connector.node.a"
                : "createinteroperable.mini_connector.node.b");
    }

    /** 1 px pins touching only at a corner: a 1 px pick box each. */
    @Override
    public float getNodeSize(Level level, BlockPos pos, BlockState state, int id) {
        return 0.0625F;
    }
}
