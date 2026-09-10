package com.cio.createinteroperable;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
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
 * Interoperable Coupler — the standalone, directly-placeable one-way bridge
 * between a Power Grid circuit and an Electro Energetics circuit. Successor to
 * the interim {@link InteroperableSmallBlock}: same PG-terminal-pair + CEE-node-pair
 * electrical shape (and the same reused {@link InteroperableDevice} /
 * {@link CIODevices#INTEROPERABLE} device), but with a fixed CPG end and a fixed
 * CEE end, a 3-position flow control (CPG&rarr;CEE / OFF / CEE&rarr;CPG), and a
 * gauge-needle animation with a deliberate ~3s dead time on every direction
 * change (see {@link InteroperableCouplerBlockEntity}).
 *
 * Geometry is taken from {@code models/block/coupler.json} (the user's
 * {@code interoperable_coupler.json} minus the two animated pointer plates).
 * Convention matches {@link InteroperableSmallBlock}: at {@code PG_FACING=SOUTH}
 * (angle 0) the CPG end faces north (low Z), the CEE end faces south (high Z),
 * and {@link InteroperableSmallBlock#angleFor}/{@link InteroperableSmallBlock#rotateY}
 * are reused verbatim for per-facing rotation.
 *
 * NOTE (coordinates): the model's {@code cpg_node}/{@code cee_node} cubes sit
 * near y&asymp;15.7 and bleed slightly past this block's own cell (up, and in Z).
 * Per the hard lesson recorded for {@link InteroperableSmallBlock} (vanilla's
 * cell-stepping raytrace only tests a block's shape while the ray cursor is in
 * that block's own cell), the interactive terminal/node boxes below are pulled
 * fully inside y=0..16 / z=0..16 — a bit lower than where the wire visually
 * plugs in, an accepted trade for reliable clicking. Tune in-game.
 */
public class InteroperableCouplerBlock extends ElectricBlock
        implements IBE<InteroperableCouplerBlockEntity>, ElectricalDeviceBlock<InteroperableDevice> {

    // --- PG terminal pair (CPG end, low Z at angle 0) ---
    // 2026-09-02 nudge (matches double_coupler): +2px up; CPG pair +2px north (-Z).
    private static final double CHECK_MARGIN = 1;
    private static final TerminalBoundingBox PG_TERMINAL_POSITIVE_BASE =
            new TerminalBoundingBox(IDecoratedTerminal.POSITIVE,
                    4.5 - CHECK_MARGIN, 11 - CHECK_MARGIN, -1 - CHECK_MARGIN,
                    8 + CHECK_MARGIN, 15 + CHECK_MARGIN, 3 + CHECK_MARGIN)
                    .withColor(IDecoratedTerminal.RED);
    private static final TerminalBoundingBox PG_TERMINAL_NEGATIVE_BASE =
            new TerminalBoundingBox(IDecoratedTerminal.NEGATIVE,
                    8 - CHECK_MARGIN, 11 - CHECK_MARGIN, -1 - CHECK_MARGIN,
                    11.5 + CHECK_MARGIN, 15 + CHECK_MARGIN, 3 + CHECK_MARGIN)
                    .withColor(IDecoratedTerminal.BLUE);

    // --- CEE node pair (CEE end, high Z at angle 0) — +2px up, +2px south (+Z) ---
    private static final Vec3 CEE_NODE_0_BASE = new Vec3(6.0 / 16, 13.5 / 16, 14.0 / 16);
    private static final Vec3 CEE_NODE_1_BASE = new Vec3(10.0 / 16, 13.5 / 16, 14.0 / 16);

    private static AABB box16(double x1, double y1, double z1, double x2, double y2, double z2) {
        return new AABB(x1 / 16, y1 / 16, z1 / 16, x2 / 16, y2 / 16, z2 / 16);
    }

    /** Coarse silhouette of coupler.json: flat body bar + the two leaning end towers + the hinge bar. */
    private static final AABB[] BODY_BOXES = new AABB[] {
            box16(4, 0, 0, 12, 3, 16),    // body_box
            box16(5, 2, 0, 11, 15, 7),    // CPG end tower (leans north/out; clipped to cell)
            box16(5, 2, 9, 11, 15, 16),   // CEE end tower (leans south/out; clipped to cell)
            box16(2, 1, 6, 14, 5, 10),    // hinge bar (45deg, bbox)
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

    public InteroperableCouplerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(CIOProperties.PG_FACING, Direction.NORTH));
        setTerminalCollection(BlockStateTerminalCollection.builder(this)
                .forAllStates(InteroperableCouplerBlock::terminalsFor)
                .withShapeMapper(InteroperableCouplerBlock::shapeFor)
                .build());
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = super.getStateForPlacement(ctx);
        if (state == null)
            state = defaultBlockState();
        // PG_FACING points from the CEE end toward the CPG end; put the CPG end
        // toward the player's back (so the CEE end faces the player when placing).
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
     * Same as {@link InteroperableSmallBlock#onWrenched}: rotate {@code PG_FACING}
     * clockwise unconditionally (Create's default only rotates on a top/bottom
     * click), then refresh attached wires so they don't visually desync.
     */
    /**
     * Heavy-duty PG terminals — accept every wire tier (iron wire and the
     * heaviest gauges), like PG's own {@code HeavyConnectorBlock}, not just the
     * {@code LIGHT_WIRES} tag. CEE nodes need no equivalent (no per-node
     * wire-tier gate in CEE).
     */
    @Override
    public boolean accepts(ItemStack wireStack) {
        return true;
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
    public Class<InteroperableCouplerBlockEntity> getBlockEntityClass() {
        return InteroperableCouplerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends InteroperableCouplerBlockEntity> getBlockEntityType() {
        return CIOBlockEntities.COUPLER.get();
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

    // Copied from CEE's SimpleElectricalDeviceBlock — can't extend it since
    // ElectricBlock is already the parent (see InteroperableSmallBlock).

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
