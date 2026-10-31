package com.cio.createinteroperable.cpg;

import com.cio.createinteroperable.modules.PowerGridModule;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.collections.ModdedTags;
import org.patryk3211.powergrid.electricity.base.ElectricBlock;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.electricity.base.terminals.BlockStateTerminalCollection;
import org.patryk3211.powergrid.electricity.wireconnector.AbstractConnectorBlock;
import org.patryk3211.powergrid.electricity.wireconnector.ConnectorBlockEntity;

/**
 * A plain Power Grid connector with TWO independent, unconnected terminal
 * points (cpg_double_connector.json's "cpg_node_1"/"cpg_node_0" cubes)
 * instead of AbstractConnectorBlock's usual one — lets two separate wire
 * runs terminate at the same block position without their wires visually
 * clashing, instead of needing two adjacent Connector blocks. Not
 * CEE-compatible (PG-only, same reasoning as CIOConnectorBlock/CIOConnectorGlassBlock).
 * <p>
 * Terminal boxes are padded 1px past the model's own node cubes
 * (CHECK_MARGIN, same fix used by InteroperableCouplerBlock/InteroperableSmallBlock):
 * a node cube's own max face sits exactly where a straight-on click naturally
 * lands, and TerminalBoundingBox#check()'s max bound is exclusive (confirmed
 * via bytecode) — an unpadded box flush with the visual cube would silently
 * fail to confirm on that click, a real bug hit twice before on this project.
 * <p>
 * {@link #TURNED} turns it a quarter about the way it faces (its own model,
 * {@code cpg_double_connector_turned}): on a wall the two nodes sit one above
 * the other unturned and side by side turned; on a floor or ceiling, north to
 * south unturned and east to west turned. Placed, they line up with the way the
 * player looks on a floor or ceiling and stand upright on a wall; sneaking
 * places them the other way. A wrench on its front or back turns it, on any
 * other face swings it to the next facing.
 */
public class CIODoubleConnectorBlock extends AbstractConnectorBlock {
    private static final double CHECK_MARGIN = 1;
    /** Turned a quarter about its facing (see the class notes). */
    public static final BooleanProperty TURNED = BooleanProperty.create("turned");

    private static final TerminalBoundingBox NODE_1_DOWN =
            new TerminalBoundingBox(IDecoratedTerminal.CONNECTOR,
                    6 - CHECK_MARGIN, 6 - CHECK_MARGIN, 2 - CHECK_MARGIN,
                    10 + CHECK_MARGIN, 10 + CHECK_MARGIN, 6 + CHECK_MARGIN);
    private static final TerminalBoundingBox NODE_0_DOWN =
            new TerminalBoundingBox(IDecoratedTerminal.CONNECTOR,
                    6 - CHECK_MARGIN, 6 - CHECK_MARGIN, 10 - CHECK_MARGIN,
                    10 + CHECK_MARGIN, 10 + CHECK_MARGIN, 14 + CHECK_MARGIN);

    /**
     * The 6 "insulator post" elements of cpg_double_connector.json, under each
     * node cube, as boxes turned exactly like the terminals (so the outline can
     * never drift from them).
     */
    private static final TerminalBoundingBox[] BODY_DOWN = {
            body(6, 0, 2, 10, 4, 6),
            body(5, 1, 1, 11, 3, 7),
            body(5, 4, 1, 11, 6, 7),
            body(6, 0, 10, 10, 4, 14),
            body(5, 1, 9, 11, 3, 15),
            body(5, 4, 9, 11, 6, 15)};

    private static TerminalBoundingBox body(double x1, double y1, double z1, double x2, double y2, double z2) {
        return new TerminalBoundingBox(Component.empty(), x1, y1, z1, x2, y2, z2, 0);
    }

    public CIODoubleConnectorBlock(Properties settings) {
        super(settings);
        registerDefaultState(defaultBlockState().setValue(TURNED, false));
        // Overwrites the (1-terminal) collection AbstractConnectorBlock's own
        // constructor already set via super(settings) above.
        setTerminalCollection(BlockStateTerminalCollection
                .builder(this)
                .forAllStates(CIODoubleConnectorBlock::terminalsFor)
                .withShapeMapper(CIODoubleConnectorBlock::shapeFor)
                .build()
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(TURNED);
    }

    private static TerminalBoundingBox[] terminalsFor(BlockState state) {
        return new TerminalBoundingBox[] { place(NODE_1_DOWN, state), place(NODE_0_DOWN, state) };
    }

    private static VoxelShape shapeFor(BlockState state) {
        VoxelShape shape = Shapes.empty();
        for (TerminalBoundingBox box : BODY_DOWN) {
            shape = Shapes.or(shape, place(box, state).getShape());
        }
        return shape.optimize();
    }

    /** A box drawn standing on the floor, turned the way the blockstate turns the model for {@code state}. */
    private static TerminalBoundingBox place(TerminalBoundingBox down, BlockState state) {
        TerminalBoundingBox box = state.getValue(TURNED) ? down.rotateAroundY(90) : down;
        return switch (state.getValue(FACING)) {
            case UP -> box.rotateAroundX(180);
            case DOWN -> box;
            case NORTH -> box.rotateAroundX(-90);
            case SOUTH -> box.rotateAroundX(90);
            case EAST -> box.rotateAroundX(90).rotateAroundY(-90);
            case WEST -> box.rotateAroundX(90).rotateAroundY(90);
        };
    }

    /**
     * Facing the clicked face as before; turned when that lines the nodes up
     * with where the player looks on a floor or ceiling, or lays them flat on
     * a wall when sneaking.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) {
            return null;
        }
        Player player = context.getPlayer();
        boolean sneaking = player != null && player.isShiftKeyDown();
        boolean turned = state.getValue(FACING).getAxis().isVertical()
                ? (context.getHorizontalDirection().getAxis() == Direction.Axis.X) != sneaking
                : sneaking;
        return state.setValue(TURNED, turned);
    }

    /**
     * DirectionalElectricBlock's inherited {@code onWrenched} (from
     * ElectricBlock -> IElectric -> Create's own {@code IWrenchable} default)
     * does rotate a genuine 6-way {@code FACING} property like this block's —
     * its "axis mismatch" branch operates on {@code DirectionalKineticBlock.FACING},
     * which is the exact same {@code DirectionProperty} instance as PG's own
     * {@code DirectionalElectricBlock.FACING} (both just alias
     * {@code BlockStateProperties.FACING}, confirmed by reading Create's real
     * source) — but it's a deliberate no-op whenever the clicked face's axis
     * matches the block's CURRENT facing axis (e.g. wrenching straight down onto
     * a connector already facing down), since that branch exists to spin a block
     * around its own facing axis via {@code AXIS_ALONG_FIRST_COORDINATE}, a
     * property this block doesn't have ({@link #TURNED} is CIO's own). So the
     * override does both itself: a wrench on the front or back (the facing's
     * axis) turns the connector a quarter about it, any other face cycles the
     * facing — the same split as Create's default, now with something to turn.
     */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState rotated = context.getClickedFace().getAxis() == state.getValue(FACING).getAxis()
                ? state.cycle(TURNED) : state.cycle(FACING);
        if (!rotated.canSurvive(level, pos))
            return InteractionResult.PASS;

        if (!level.isClientSide) {
            level.setBlockAndUpdate(pos, rotated);
            ElectricBlock.refreshConnectionEntities(level, pos);
            IWrenchable.playRotateSound(level, pos);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * {@code IElectric#accepts}'s default only checks PG's own
     * {@code LIGHT_WIRES} tag — deliberately excludes {@code IRON_WIRE}
     * (confirmed by reading PG's real {@code ModdedItems}: Iron Wire is
     * tagged {@code WIRES}/{@code FUSE_RESETTING}/{@code wires("iron")}, but
     * NOT {@code LIGHT_WIRES}). Broaden to the generic {@code WIRES} tag
     * (covers every PG wire tier: Copper, Iron, Golden, Insulated Copper),
     * same idea as {@code HeavyConnectorBlock}'s own {@code accepts} override
     * (which goes further and accepts any item at all) — this block is a
     * plain utility connector, not tier-restricted like a bare light-wire
     * device.
     */
    @Override
    public boolean accepts(ItemStack wireStack) {
        return wireStack.is(ModdedTags.Item.WIRES.tag);
    }

    // getBlockEntityClass() is NOT overridden: AbstractConnectorBlock commits to
    // IBE<ConnectorBlockEntity>, so that method's return type is fixed to
    // Class<ConnectorBlockEntity> — a Class<CIODoubleConnectorBlockEntity> isn't
    // a valid covariant override of it (Class<T> is invariant in T). The
    // inherited ConnectorBlockEntity.class is still correct here: IBE only ever
    // uses it for an isInstance() check, and CIODoubleConnectorBlockEntity IS a
    // ConnectorBlockEntity.

    @Override
    public BlockEntityType<? extends ConnectorBlockEntity> getBlockEntityType() {
        return PowerGridModule.DOUBLE_CONNECTOR_BE.get();
    }
}
