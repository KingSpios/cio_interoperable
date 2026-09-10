package com.cio.createinteroperable;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.LevelTickAccess;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBlock;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.electricity.base.terminals.BlockStateTerminalCollection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Interoperable <b>Double</b> Coupler — the real, current-carrying one-way bridge
 * between a Power Grid circuit and an Electro Energetics circuit.
 *
 * Unlike the single {@link InteroperableCouplerBlock} (a to-ground signal relay —
 * one point per side, no loop), this block exposes a genuine <b>two-terminal
 * winding per side</b>: a PG {@code +}/{@code -} pair ({@code cpg_node_pos} on the
 * right unit, {@code cpg_node_neg} on the left) and a CEE node pair
 * ({@code cee_node_0} left, {@code cee_node_1} right). Both mods model electricity
 * as loop current, so only with two points per side can real current flow — which
 * is what lets a post-bridge load reflect back onto the source grid's generation
 * (via {@link InteroperableDevice}'s SENSE/DELIVERY resistance split).
 *
 * Flow control, the 3&nbsp;s gauge-needle reversal dead time, and the transfer
 * hard-cut all live in {@link InteroperableDoubleCouplerBlockEntity}; the four
 * needle plates are animated by {@link InteroperableDoubleCouplerRenderer}.
 *
 * Model: {@code models/block/double_coupler.json} (the user's
 * {@code interoperable_double_coupler.json} minus the four pointer plates). It is
 * two single-coupler units side by side on one full-block footprint. Convention
 * matches {@link InteroperableSmallBlock}: at {@code facing=south} (angle 0) the
 * CPG terminals face north (low Z), the CEE nodes face south (high Z);
 * {@link InteroperableSmallBlock#angleFor}/{@link InteroperableSmallBlock#rotateY}
 * are reused for per-facing rotation. As with the single coupler the interactive
 * boxes are pulled inside the block's own cell (the model's nubs bleed up/out) —
 * tune in-game.
 */
public class InteroperableDoubleCouplerBlock extends ElectricBlock
        implements IBE<InteroperableDoubleCouplerBlockEntity>, ElectricalDeviceBlock<InteroperableDevice> {

    private static final double CHECK_MARGIN = 1;

    // 2026-09-02 nudge: all four nubs +2px up; CPG pair +2px north (-Z),
    // CEE pair +2px south (+Z) — pulls each hitbox back toward its visible
    // model nub. The CPG boxes now start ~2px past the cell's north edge;
    // their centres stay inside the cell so straight-on clicks still resolve.

    /** cpg_node_pos — right unit (x~12), CPG end (low Z at angle 0). */
    private static final TerminalBoundingBox PG_TERMINAL_POSITIVE_BASE =
            new TerminalBoundingBox(IDecoratedTerminal.POSITIVE,
                    10 - CHECK_MARGIN, 12 - CHECK_MARGIN, -1 - CHECK_MARGIN,
                    14 + CHECK_MARGIN, 16 + CHECK_MARGIN, 3 + CHECK_MARGIN)
                    .withColor(IDecoratedTerminal.RED);
    /** cpg_node_neg — left unit (x~4), CPG end. */
    private static final TerminalBoundingBox PG_TERMINAL_NEGATIVE_BASE =
            new TerminalBoundingBox(IDecoratedTerminal.NEGATIVE,
                    2 - CHECK_MARGIN, 12 - CHECK_MARGIN, -1 - CHECK_MARGIN,
                    6 + CHECK_MARGIN, 16 + CHECK_MARGIN, 3 + CHECK_MARGIN)
                    .withColor(IDecoratedTerminal.BLUE);

    /** cee_node_0 — left unit (x~4), CEE end (high Z at angle 0). */
    private static final Vec3 CEE_NODE_0_BASE = new Vec3(4.0 / 16, 14.0 / 16, 15.0 / 16);
    /** cee_node_1 — right unit (x~12), CEE end. */
    private static final Vec3 CEE_NODE_1_BASE = new Vec3(12.0 / 16, 14.0 / 16, 15.0 / 16);

    private static AABB box16(double x1, double y1, double z1, double x2, double y2, double z2) {
        return new AABB(x1 / 16, y1 / 16, z1 / 16, x2 / 16, y2 / 16, z2 / 16);
    }

    private static final AABB[] BODY_BOXES = new AABB[] {
            box16(0, 0, 0, 16, 3, 16),     // body (both units)
            box16(1, 2, 0, 7, 15, 7),      // left  CPG tower
            box16(1, 2, 9, 7, 15, 16),     // left  CEE tower
            box16(9, 2, 0, 15, 15, 7),     // right CPG tower
            box16(9, 2, 9, 15, 15, 16),    // right CEE tower
            box16(0, 1, 6, 16, 5, 10),     // hinge bars (both, bbox)
    };

    private static AABB rotateAABB(AABB box, int angle) {
        Vec3 p1 = InteroperableSmallBlock.rotateY(new Vec3(box.minX, 0, box.minZ), angle);
        Vec3 p2 = InteroperableSmallBlock.rotateY(new Vec3(box.maxX, 0, box.maxZ), angle);
        return new AABB(Math.min(p1.x, p2.x), box.minY, Math.min(p1.z, p2.z),
                Math.max(p1.x, p2.x), box.maxY, Math.max(p1.z, p2.z));
    }

    private static VoxelShape buildShape(int angle) {
        VoxelShape shape = Shapes.empty();
        for (AABB box : BODY_BOXES)
            shape = Shapes.or(shape, Shapes.create(rotateAABB(box, angle)));
        return shape;
    }

    private static final VoxelShape[] ROTATED_SHAPES =
            { buildShape(0), buildShape(90), buildShape(180), buildShape(270) };

    private static VoxelShape shapeFor(BlockState state) {
        return ROTATED_SHAPES[InteroperableSmallBlock.angleFor(state) / 90];
    }

    public InteroperableDoubleCouplerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(CIOProperties.PG_FACING, Direction.NORTH));
        setTerminalCollection(BlockStateTerminalCollection.builder(this)
                .forAllStates(InteroperableDoubleCouplerBlock::terminalsFor)
                .withShapeMapper(InteroperableDoubleCouplerBlock::shapeFor)
                .build());
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = super.getStateForPlacement(ctx);
        if (state == null)
            state = defaultBlockState();
        return state.setValue(CIOProperties.PG_FACING, ctx.getHorizontalDirection().getOpposite());
    }

    private static TerminalBoundingBox[] terminalsFor(BlockState state) {
        int angle = InteroperableSmallBlock.angleFor(state);
        return new TerminalBoundingBox[] {
                PG_TERMINAL_POSITIVE_BASE.rotateAroundY(angle),
                PG_TERMINAL_NEGATIVE_BASE.rotateAroundY(angle)
        };
    }

    /**
     * Heavy-duty PG terminals — accept every wire tier (iron wire and the
     * heaviest gauges), not just the {@code LIGHT_WIRES} tag PG's plain
     * connector allows. Same override PG's own {@code HeavyConnectorBlock}
     * uses. (The CEE nodes need no equivalent — CEE has no per-node wire-tier
     * gate, so they already take iron/bus/rail spools.)
     */
    @Override
    public boolean accepts(ItemStack wireStack) {
        return true;
    }

    /**
     * Item tooltip. Plain {@code BlockItem#appendHoverText} forwards straight
     * into {@code Block#appendHoverText} (confirmed via bytecode, same as
     * {@link DebRectifierBlock}/{@code TelephoneBlock}), so overriding it here
     * reaches the item tooltip with no custom Item subclass. Only ever invoked
     * client-side (tooltip rendering), so {@link Screen#hasShiftDown()} is safe.
     */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("block.createinteroperable.double_coupler.tooltip")
                .withStyle(ChatFormatting.GRAY));
        if (Screen.hasShiftDown()) {
            tooltip.add(Component.translatable("block.createinteroperable.double_coupler.tooltip.detail_1")
                    .withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("block.createinteroperable.double_coupler.tooltip.detail_2")
                    .withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("block.createinteroperable.double_coupler.tooltip.detail_3")
                    .withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("block.createinteroperable.double_coupler.tooltip.detail_4")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("block.createinteroperable.double_coupler.tooltip.hold_shift")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState rotated = state.setValue(CIOProperties.PG_FACING,
                state.getValue(CIOProperties.PG_FACING).getClockWise());
        if (!rotated.canSurvive(level, pos))
            return InteractionResult.PASS;
        if (!level.isClientSide) {
            level.setBlockAndUpdate(pos, rotated);
            ElectricBlock.refreshConnectionEntities(level, pos);
            IWrenchable.playRotateSound(level, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CIOProperties.PG_FACING);
    }

    @Override
    public Class<InteroperableDoubleCouplerBlockEntity> getBlockEntityClass() {
        return InteroperableDoubleCouplerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends InteroperableDoubleCouplerBlockEntity> getBlockEntityType() {
        return CIOBlockEntities.DOUBLE_COUPLER.get();
    }

    @Override
    public SimulatedDeviceType<InteroperableDevice> getDevice() {
        return CIODevices.INTEROPERABLE.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        int angle = InteroperableSmallBlock.angleFor(state);
        return Map.of(0, InteroperableSmallBlock.rotateY(CEE_NODE_0_BASE, angle),
                1, InteroperableSmallBlock.rotateY(CEE_NODE_1_BASE, angle));
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        int angle = InteroperableSmallBlock.angleFor(state);
        return switch (id) {
            case 0 -> InteroperableSmallBlock.rotateY(CEE_NODE_0_BASE, angle);
            case 1 -> InteroperableSmallBlock.rotateY(CEE_NODE_1_BASE, angle);
            default -> null;
        };
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        LevelTickAccess<Block> blockTicks = level.getBlockTicks();
        if (!blockTicks.hasScheduledTick(pos, this))
            level.scheduleTick(pos, this, 1);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        List<Integer> nodes = new ArrayList<>(getNodePositions(level, pos, state).keySet());
        InfrastructureSavedData.load(level).registerOrUpdateNodes(pos, nodes);
        super.tick(state, level, pos, random);
    }
}
