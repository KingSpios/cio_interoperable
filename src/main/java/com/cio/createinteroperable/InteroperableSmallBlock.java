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
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.LevelTickAccess;
import org.patryk3211.powergrid.electricity.base.ElectricBlock;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.electricity.base.terminals.BlockStateTerminalCollection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Interim 1x1 bridge block, formed by wrenching an InteroperableCoreBlock.
 * Merges what the real multiblock splits across two blocks 2 apart
 * (InteroperablePgAssembledBlock/BlockEntity + InteroperableCeeAssembledBlock)
 * into a single block/BlockEntity pair: the PG terminal pair lives on the
 * PG_FACING face, the CEE node pair on the opposite face, same "CEE is
 * always opposite PG_FACING" convention the real multiblock already uses.
 *
 * Terminal/node coordinates below match the 4 corner cubes in
 * models/block/cio_transformer.json (renamed cpg_positive/cpg_negative/
 * cee_zero/cee_one in Blockbench). The model is now a custom 2-tall design
 * that deliberately bleeds a full block-height above its own cell, with the
 * 4 connector cubes living entirely in that bled region (y=26-29 in 1/16ths,
 * i.e. above the block's own y=0-16) — see DEFAULT_SHAPE below for why that
 * requires more than just moving these coordinates.
 */
public class InteroperableSmallBlock extends ElectricBlock
        implements IBE<InteroperableSmallBlockEntity>, ElectricalDeviceBlock<InteroperableDevice> {
    /**
     * The model's actual "cpg_positive"/"cpg_negative" nubs (x:1-4/12-15,
     * y:26-29, z:2-5) only protrude 1/16 past the main body's own
     * silhouette (x:2-14, y:0-28) in X and 1/16 in Y, and are fully
     * enclosed in Z — confirmed by comparing the coordinates directly.
     * Vanilla's raytrace returns the NEAREST surface hit along the ray, so
     * with the nub barely poking out, the much larger body surface wins
     * from almost every normal viewing angle and the ray rarely reaches
     * the nub's own surface — confirmed as the real cause of a
     * "connector selectable but won't complete, needs weird angles" report
     * (terminalIndexAt returns -1 when the hit lands on the body instead
     * of inside the tight terminal box, so onWire silently PASSes).
     *
     * Second fix attempt (this file's `buildShape()` carving the nub's
     * footprint out of the main body) made the nub the correct NEAREST
     * raytrace surface — confirmed indirectly (PG's own hover highlight
     * now shows right on the connector) — but the click STILL silently
     * failed at every orientation, with no chat message at all. Root cause
     * found by re-reading TerminalBoundingBox#check's actual comparison
     * opcodes: it's `dcmpg` + `ifge` on all 3 max-axis checks, meaning the
     * box is **min <= v < max — the max bound is EXCLUSIVE**. A ray hitting
     * a flat axis-aligned surface dead-on very naturally lands EXACTLY on
     * that surface's coordinate — and since the carved-out nub's own
     * max-faces (its top, its outward side) are now genuinely the closest
     * surface, that's precisely where hits were landing: right on the
     * excluded boundary. So detection (which face you hit) was fixed, but
     * the box being checked against was drawn exactly flush with the
     * shape's own surface, and flush-with-an-exclusive-bound is a
     * near-guaranteed miss for a straight-on hit.
     *
     * Fix: decouple the two. NUB_BOXES (below) stays tight to the model,
     * used for the raytrace SURFACE (what the ray can physically hit).
     * The TerminalBoundingBox used for check() gets an EXTRA margin
     * (CHECK_MARGIN) beyond NUB_BOXES on every side, so any point on the
     * nub's own surface — including its max-faces — lands safely inside
     * the (strictly larger) terminal box, never exactly on its boundary.
     */
    private static final double NUB_PAD = 1;
    private static final double CHECK_MARGIN = 1;

    /**
     * Third fix attempt (CHECK_MARGIN) was real but insufficient: it never
     * addressed the actual root cause. Minecraft's block-picking raytrace
     * (Level#clip / player.pick(), which both PG's onWire and CEE's
     * fallback hit-node path go through) walks the ray cell-by-cell through
     * the world grid and only ever tests a block's declared shape while the
     * traversal cursor is sitting in THAT block's own 1x1x1 cell — a shape
     * is allowed to bleed past its cell, but the bled part only gets tested
     * against the ray on the (viewing-angle-dependent) occasions the cursor
     * hasn't yet stepped into the next cell by the time it reaches that
     * region. Confirmed by the user: connections only completed when
     * aiming low, at "where the node would be for a 1x1 block" — i.e.
     * wherever forced the ray's cursor through this block's own cell first.
     *
     * The model was originally bled UPWARD (connectors entirely in the
     * cell above, y=26-29) — the user then re-authored it to bleed DOWNWARD
     * instead (whole model shifted -16 in Y): the connector nubs now sit at
     * y=10-13, ENTIRELY INSIDE this block's own cell, no bleed at all for
     * any interactive geometry — only the decorative body/side-beams bleed,
     * into the cell BELOW. This is the definitive fix, not a workaround: a
     * hit point that's geometrically inside cell P is, by construction,
     * always reached by a ray whose traversal cursor passes through cell P
     * at some step before the hit — no viewing-angle dependence survives.
     * (A downward bleed for the non-interactive geometry is also just
     * inherently more forgiving than an upward one ever was: aiming at
     * anything below eye level means the ray's cursor sweeps DOWN through
     * this block's own row on the way to the lower cell anyway.)
     */
    private static final TerminalBoundingBox PG_TERMINAL_POSITIVE_BASE =
            new TerminalBoundingBox(IDecoratedTerminal.POSITIVE,
                    1 - NUB_PAD - CHECK_MARGIN, 10 - CHECK_MARGIN, 2 - CHECK_MARGIN,
                    4 + CHECK_MARGIN, 13 + NUB_PAD + CHECK_MARGIN, 5 + CHECK_MARGIN)
                    .withColor(IDecoratedTerminal.RED);
    /** = cpg_negative's NUB_BOXES entry, expanded by CHECK_MARGIN on every side (see doc above). */
    private static final TerminalBoundingBox PG_TERMINAL_NEGATIVE_BASE =
            new TerminalBoundingBox(IDecoratedTerminal.NEGATIVE,
                    12 - CHECK_MARGIN, 10 - CHECK_MARGIN, 2 - CHECK_MARGIN,
                    15 + NUB_PAD + CHECK_MARGIN, 13 + NUB_PAD + CHECK_MARGIN, 5 + CHECK_MARGIN)
                    .withColor(IDecoratedTerminal.BLUE);

    /** Entirely inside y=0-16 (this block's own cell) — see doc above. X/Z match the model's cee_zero cube. */
    private static final Vec3 CEE_NODE_0_BASE = new Vec3(2.5 / 16, 11.5 / 16, 12.5 / 16);
    /** Entirely inside y=0-16 (this block's own cell) — see doc above. X/Z match the model's cee_one cube. */
    private static final Vec3 CEE_NODE_1_BASE = new Vec3(13.5 / 16, 11.5 / 16, 12.5 / 16);

    /**
     * A single crude box (even a non-full one) was the actual cause of the
     * first real playtest's "connectors recognized but don't click" bug —
     * NOT a limitation of bleeding upward specifically. Vanilla's raytrace
     * can only ever return a hit point lying ON the declared shape's own
     * surface; a flat rectangular prism's surface doesn't track where the
     * connector nubs/side beams actually protrude, so aiming at a visible
     * nub could resolve to a hit point well away from it (or not resolve to
     * this block at all). Bleeding the model *downward* instead would hit
     * the exact same problem — the fix is shape precision, not bleed
     * direction; going down would additionally be more likely to collide
     * with whatever's already below (floor/support), unlike up into
     * typically-empty air.
     *
     * Fix: build the real outline from the model's own element boxes
     * (verbatim from cio_transformer.json) via Shapes.or(...), instead of
     * one box standing in for the whole silhouette. Precomputed per
     * PG_FACING angle (not rebuilt per call) since shape queries can be
     * frequent.
     */
    private static AABB box16(double x1, double y1, double z1, double x2, double y2, double z2) {
        return new AABB(x1 / 16, y1 / 16, z1 / 16, x2 / 16, y2 / 16, z2 / 16);
    }

    /**
     * Outward padding alone (an earlier version of this fix, NUB_PAD=5 in
     * every direction) turned out to be the wrong approach — confirmed by
     * an actual server log line after a real test: both endpoints of a
     * failed connection attempt were "OwnedFloatingNode[...pos=..., n=0]"
     * TWICE, meaning both clicks resolved to the SAME terminal (index 0,
     * positive) despite the player aiming at what should have been two
     * different points. A 5-unit pad made the positive terminal's box
     * enormous (reaching x=-3, past the block's own x=0 edge into the
     * NEXT block's space) — oversized enough to dominate the raytrace
     * over a much wider area than intended, and to risk interfering with
     * whatever's placed next door. Reverted to a small NUB_PAD=1 (a little
     * tolerance margin, nothing more) — see below for the real fix.
     */
    private static final AABB[] BODY_BOXES = new AABB[] {
            box16(2, -16, 0, 14, 12, 16),   // main body
            box16(1, -13, 2, 2, 9, 14),     // west side beam
            box16(14, -13, 2, 15, 9, 14),   // east side beam
    };

    /** cpg_positive / cpg_negative / cee_zero / cee_one, lightly padded (NUB_PAD). */
    private static final AABB[] NUB_BOXES = new AABB[] {
            box16(1 - NUB_PAD, 10, 2, 4, 13 + NUB_PAD, 5),      // cpg_positive
            box16(12, 10, 2, 15 + NUB_PAD, 13 + NUB_PAD, 5),    // cpg_negative
            box16(1 - NUB_PAD, 10, 11, 4, 13 + NUB_PAD, 14),    // cee_zero
            box16(12, 10, 11, 15 + NUB_PAD, 13 + NUB_PAD, 14),  // cee_one
    };

    private static final AABB SLIDER_BOX = box16(0, 2, 5, 2, 6, 11);

    private static AABB rotateAABB(AABB box, int angle) {
        Vec3 p1 = rotateY(new Vec3(box.minX, 0, box.minZ), angle);
        Vec3 p2 = rotateY(new Vec3(box.maxX, 0, box.maxZ), angle);
        return new AABB(Math.min(p1.x, p2.x), box.minY, Math.min(p1.z, p2.z),
                Math.max(p1.x, p2.x), box.maxY, Math.max(p1.z, p2.z));
    }

    /**
     * The real fix: rather than making the nubs bigger, carve their
     * footprint OUT of the main body first (Shapes.join with SUBTRACT),
     * then add the (lightly padded) nubs back on top. Vanilla's raytrace
     * always returns the NEAREST surface — with the body's competing
     * surface physically removed where a nub sits, the ray has nothing
     * closer to hit in that column except the nub itself, without needing
     * an oversized hitbox that can swallow clicks meant for the OTHER
     * nub or bleed into neighboring blocks.
     */
    private static VoxelShape buildShape(int angle) {
        VoxelShape body = Shapes.empty();
        for (AABB box : BODY_BOXES)
            body = Shapes.or(body, Shapes.create(rotateAABB(box, angle)));

        VoxelShape nubs = Shapes.empty();
        for (AABB box : NUB_BOXES)
            nubs = Shapes.or(nubs, Shapes.create(rotateAABB(box, angle)));

        VoxelShape notchedBody = Shapes.join(body, nubs, BooleanOp.ONLY_FIRST);
        VoxelShape shape = Shapes.or(notchedBody, nubs);
        shape = Shapes.or(shape, Shapes.create(rotateAABB(SLIDER_BOX, angle)));
        return shape;
    }

    /** Index i -> angle i*90. Built once; rotateY (below) must be defined before this runs. */
    private static final VoxelShape[] ROTATED_SHAPES =
            { buildShape(0), buildShape(90), buildShape(180), buildShape(270) };

    private static VoxelShape shapeFor(BlockState state) {
        return ROTATED_SHAPES[angleFor(state) / 90];
    }

    public InteroperableSmallBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(CIOProperties.PG_FACING, Direction.NORTH));
        setTerminalCollection(BlockStateTerminalCollection.builder(this)
                .forAllStates(InteroperableSmallBlock::terminalsFor)
                .withShapeMapper(InteroperableSmallBlock::shapeFor)
                .build());
    }

    static int angleFor(BlockState state) {
        return switch (state.getValue(CIOProperties.PG_FACING)) {
            case SOUTH -> 0;
            case WEST -> 90;
            case NORTH -> 180;
            case EAST -> 270;
            default -> 0;
        };
    }

    /**
     * ElectricBlock (via IElectric extends IWrenchable) inherits Create's
     * own default onWrenched — confirmed by reading its real source —
     * which only rotates a HORIZONTAL_FACING-style property when the
     * wrenched face's axis is Y (i.e. you clicked the block's TOP or
     * BOTTOM). Click a SIDE face instead — the natural way to aim at a
     * 2-tall block with its connectors up top — and getRotatedBlockState
     * falls through to checking the full 6-way BlockStateProperties.FACING
     * (which this block doesn't have), returns the state UNCHANGED, and
     * wrenching silently does nothing: no rotation, no sound. Confirmed as
     * the real cause of a "non-sneak right click does nothing" report.
     *
     * Fix: override outright with unconditional rotation regardless of
     * clicked face. Still calls ElectricBlock.refreshConnectionEntities
     * (a public static method) afterward, same as ElectricBlock's own
     * onWrenched does on success — skipping that would leave any wires
     * already attached to this block visually stale after a rotate.
     */
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

    private static TerminalBoundingBox[] terminalsFor(BlockState state) {
        int angle = angleFor(state);
        return new TerminalBoundingBox[] {
                PG_TERMINAL_POSITIVE_BASE.rotateAroundY(angle),
                PG_TERMINAL_NEGATIVE_BASE.rotateAroundY(angle)
        };
    }

    /**
     * CEE's node Vec3s aren't PG TerminalBoundingBoxes, so they don't get
     * PG_TERMINAL_*.rotateAroundY(...) for free — this mirrors that same
     * rotation manually (about the block's horizontal center, 0.5/0.5) so
     * the CEE wire-attach points stay lined up with the model at every
     * PG_FACING, not just the unrotated default. Matches vanilla/Minecraft's
     * clockwise-from-above "y" blockstate rotation convention.
     */
    static Vec3 rotateY(Vec3 base, int angle) {
        double dx = base.x - 0.5, dz = base.z - 0.5;
        return switch (angle) {
            case 90 -> new Vec3(0.5 - dz, base.y, 0.5 + dx);
            case 180 -> new Vec3(0.5 - dx, base.y, 0.5 - dz);
            case 270 -> new Vec3(0.5 + dz, base.y, 0.5 - dx);
            default -> base;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CIOProperties.PG_FACING);
    }

    @Override
    public Class<InteroperableSmallBlockEntity> getBlockEntityClass() {
        return InteroperableSmallBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends InteroperableSmallBlockEntity> getBlockEntityType() {
        return CIOBlockEntities.SMALL.get();
    }

    @Override
    public SimulatedDeviceType<InteroperableDevice> getDevice() {
        return CIODevices.INTEROPERABLE.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        int angle = angleFor(state);
        return Map.of(0, rotateY(CEE_NODE_0_BASE, angle), 1, rotateY(CEE_NODE_1_BASE, angle));
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        int angle = angleFor(state);
        return switch (id) {
            case 0 -> rotateY(CEE_NODE_0_BASE, angle);
            case 1 -> rotateY(CEE_NODE_1_BASE, angle);
            default -> null;
        };
    }

    // Copied from CEE's own SimpleElectricalDeviceBlock — this block can't
    // extend that class since it also has to extend PG's ElectricBlock (see
    // InteroperableCeeAssembledBlock for the same copy, same reason).

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
        InfrastructureSavedData sd = InfrastructureSavedData.load(level);
        sd.registerOrUpdateNodes(pos, nodes);
        super.tick(state, level, pos, random);
    }
}
