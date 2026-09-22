package com.cio.createinteroperable;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
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
 */
public class CIODoubleConnectorBlock extends AbstractConnectorBlock {
    private static final double CHECK_MARGIN = 1;

    private static final TerminalBoundingBox NODE_1_DOWN =
            new TerminalBoundingBox(IDecoratedTerminal.CONNECTOR,
                    6 - CHECK_MARGIN, 6 - CHECK_MARGIN, 2 - CHECK_MARGIN,
                    10 + CHECK_MARGIN, 10 + CHECK_MARGIN, 6 + CHECK_MARGIN);
    private static final TerminalBoundingBox NODE_0_DOWN =
            new TerminalBoundingBox(IDecoratedTerminal.CONNECTOR,
                    6 - CHECK_MARGIN, 6 - CHECK_MARGIN, 10 - CHECK_MARGIN,
                    10 + CHECK_MARGIN, 10 + CHECK_MARGIN, 14 + CHECK_MARGIN);

    /** The 6 "insulator post" elements of cpg_double_connector.json, under each node cube. */
    private static final VoxelShape BODY_DOWN = Shapes.or(
            box(6, 0, 2, 10, 4, 6),
            box(5, 1, 1, 11, 3, 7),
            box(5, 4, 1, 11, 6, 7),
            box(6, 0, 10, 10, 4, 14),
            box(5, 1, 9, 11, 3, 15),
            box(5, 4, 9, 11, 6, 15));
    private static final VoxelShaper BODY_SHAPER = VoxelShaper.forDirectional(BODY_DOWN, Direction.DOWN);

    public CIODoubleConnectorBlock(Properties settings) {
        super(settings);
        // Overwrites the (1-terminal) collection AbstractConnectorBlock's own
        // constructor already set via super(settings) above.
        setTerminalCollection(BlockStateTerminalCollection
                .builder(this)
                .forAllStates(CIODoubleConnectorBlock::terminalsFor)
                .withShapeMapper(state -> BODY_SHAPER.get(state.getValue(FACING)))
                .build()
        );
    }

    private static TerminalBoundingBox[] terminalsFor(BlockState state) {
        return switch (state.getValue(FACING)) {
            case UP -> new TerminalBoundingBox[] {
                    NODE_1_DOWN.rotateAroundX(180), NODE_0_DOWN.rotateAroundX(180) };
            case DOWN -> new TerminalBoundingBox[] { NODE_1_DOWN, NODE_0_DOWN };
            case NORTH -> new TerminalBoundingBox[] {
                    NODE_1_DOWN.rotateAroundX(-90), NODE_0_DOWN.rotateAroundX(-90) };
            case SOUTH -> new TerminalBoundingBox[] {
                    NODE_1_DOWN.rotateAroundX(90), NODE_0_DOWN.rotateAroundX(90) };
            case EAST -> new TerminalBoundingBox[] {
                    NODE_1_DOWN.rotateAroundX(90).rotateAroundY(-90), NODE_0_DOWN.rotateAroundX(90).rotateAroundY(-90) };
            case WEST -> new TerminalBoundingBox[] {
                    NODE_1_DOWN.rotateAroundX(90).rotateAroundY(90), NODE_0_DOWN.rotateAroundX(90).rotateAroundY(90) };
        };
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
     * property this block doesn't have. That reads as "the wrench does nothing"
     * for the natural case of repeatedly wrenching the same face to cycle
     * orientations. Override with an unconditional cycle instead — same
     * convention as {@code InteroperableSmallBlock}/{@code InteroperableCouplerBlock}'s
     * own {@code onWrenched} overrides for their (4-way) {@code PG_FACING}.
     */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState rotated = state.cycle(FACING);
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
        return CIOBlockEntities.DOUBLE_CONNECTOR.get();
    }
}
