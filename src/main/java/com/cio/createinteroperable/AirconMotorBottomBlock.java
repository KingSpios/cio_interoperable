package com.cio.createinteroperable;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
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
import org.patryk3211.powergrid.collections.ModdedTags;
import org.patryk3211.powergrid.electricity.base.ElectricBlock;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.electricity.base.terminals.BlockStateTerminalCollection;

import java.util.Map;

/**
 * The bottom half of the Aircon Motor multiblock (see {@link AirconMotorTopBlock}
 * / {@link AirconMotorAssembly} for the wrench pairing). The real "engine":
 * carries the two CPG wire terminals (matching {@code aircon_motor_bottom.json}'s
 * {@code power_pin_1_positive}/{@code power_pin_2_negative} cubes) and exposes
 * 3 Create-pipe faces — NORTH is always water OUT; WEST/EAST are cold_air OUT
 * / hot_air IN, swapped if the player wires reversed polarity (see
 * AirconMotorBottomBlockEntity#isReversed) — same reversing-valve idea a real
 * heat pump uses.
 * <p>
 * Model authored at FACING=NORTH (its literal N/W/E faces map 1:1 to the
 * unrotated state) — {@link #rotate(Direction, Direction)} remaps any of
 * those base directions to the actual world direction for any other FACING,
 * using the same clockwise "y" blockstate rotation convention as
 * {@link ShapeRotation}/{@code TerminalBoundingBox#rotateAroundY}.
 */
public class AirconMotorBottomBlock extends ElectricBlock
        implements IBE<AirconMotorBottomBlockEntity>, AirconMotorBottomMarker {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Shared with {@link AirconMotorTopBlock} and the CEE-wired bottom variant — see {@link CIOProperties#AIRCON_ASSEMBLED}'s own doc. */
    public static final BooleanProperty ASSEMBLED = CIOProperties.AIRCON_ASSEMBLED;

    /** Terminal indices, referenced by {@link AirconMotorBottomBlockEntity#buildCircuit}. */
    public static final int POSITIVE_TERMINAL = 0;
    public static final int NEGATIVE_TERMINAL = 1;

    private static final double CHECK_MARGIN = 1;

    /** = aircon_motor_bottom.json's "power_pin_1_positive" cube (x9-11,y0.1-2.1,z0-2), padded. */
    private static final TerminalBoundingBox POSITIVE_TERMINAL_BASE =
            new TerminalBoundingBox(IDecoratedTerminal.POSITIVE,
                    9 - CHECK_MARGIN, 0.1 - CHECK_MARGIN, 0 - CHECK_MARGIN,
                    11 + CHECK_MARGIN, 2.1 + CHECK_MARGIN, 2 + CHECK_MARGIN)
                    .withColor(IDecoratedTerminal.RED);
    /** = aircon_motor_bottom.json's "power_pin_2_negative" cube (x5-7,y0.1-2.1,z0-2), padded. */
    private static final TerminalBoundingBox NEGATIVE_TERMINAL_BASE =
            new TerminalBoundingBox(IDecoratedTerminal.NEGATIVE,
                    5 - CHECK_MARGIN, 0.1 - CHECK_MARGIN, 0 - CHECK_MARGIN,
                    7 + CHECK_MARGIN, 2.1 + CHECK_MARGIN, 2 + CHECK_MARGIN)
                    .withColor(IDecoratedTerminal.BLUE);

    /** Rough envelope of body_box + fin + pin elements — not pixel-perfect, just enough to avoid the full-cube neighbor-culling bug (see ShapeRotation's doc). */
    private static final Map<Direction, VoxelShape> SHAPES = ShapeRotation.forHorizontalFacing(
            new ShapeRotation.Box(0, 0, 1, 16, 16, 15),
            new ShapeRotation.Box(4, 0, 0, 12, 3, 2));

    public AirconMotorBottomBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(ASSEMBLED, false));
        setTerminalCollection(BlockStateTerminalCollection.builder(this)
                .forAllStates(AirconMotorBottomBlock::terminalsFor)
                .withShapeMapper(state -> SHAPES.get(state.getValue(FACING)))
                .build());
    }

    /** NORTH=0, EAST=1, SOUTH=2, WEST=3 clockwise steps — same convention {@link ShapeRotation} uses internally. */
    private static int clockwiseSteps(Direction facing) {
        return switch (facing) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }

    /** @return which real world direction the model's {@code base} face (as authored at FACING=NORTH) points to at the given FACING. */
    static Direction rotate(Direction base, Direction facing) {
        Direction d = base;
        for (int i = 0, steps = clockwiseSteps(facing); i < steps; i++) {
            d = d.getClockWise();
        }
        return d;
    }

    private static TerminalBoundingBox[] terminalsFor(BlockState state) {
        int angle = clockwiseSteps(state.getValue(FACING)) * 90;
        return new TerminalBoundingBox[] {
                POSITIVE_TERMINAL_BASE.rotateAroundY(angle),
                NEGATIVE_TERMINAL_BASE.rotateAroundY(angle)
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, ASSEMBLED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
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

    /**
     * Tries to pair with an {@link AirconMotorTopBlock} directly above first
     * (see {@link AirconMotorAssembly}); falls back to a plain facing-cycle
     * rotate, same "assemble-first-else-rotate" shape as
     * {@code RadiatorValveNorthBlock#onWrenched}. An already-assembled pair
     * refuses to rotate in place (would desync the top half) — unwrench
     * conceptually isn't supported yet, matching this project's existing
     * "no failure feedback / no disassembly" precedent for other multiblocks.
     */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (AirconMotorAssembly.tryAssemble(level, pos)) {
            IWrenchable.playRotateSound(level, pos);
            return InteractionResult.SUCCESS;
        }
        if (state.getValue(ASSEMBLED)) {
            return InteractionResult.PASS;
        }
        BlockState rotated = state.setValue(FACING, state.getValue(FACING).getClockWise());
        if (!level.isClientSide) {
            level.setBlockAndUpdate(pos, rotated);
            ElectricBlock.refreshConnectionEntities(level, pos);
        }
        IWrenchable.playRotateSound(level, pos);
        return InteractionResult.SUCCESS;
    }

    /**
     * Accepts any real PG wire, not just the default light-tier subset
     * ({@code IElectric#accepts}'s own default is {@code LIGHT_WIRES} only —
     * copper/golden/insulated-copper, confirmed by reading real PG source,
     * {@code ModdedItems}: those are rated 24/12/16 A). This motor's own
     * resistive load is a genuinely heavy draw (~2400 W at its 120 V design
     * point, i.e. ~20 A continuous, more under overvoltage) — real reported
     * symptom was players' ordinary (light) wires burning out under that
     * load. PG's own {@code iron_wire} is rated 64 A specifically for this
     * kind of load and is deliberately NOT tagged {@code LIGHT_WIRES} (it's
     * tagged {@code FUSE_RESETTING} instead) — so it was being silently
     * rejected by the inherited default. Widened to the broad
     * {@code WIRES} tag (every real PG wire, light or heavy) rather than
     * hand-picking iron specifically, so a player using an even heavier
     * future tier isn't blocked the same way again; nothing stops a player
     * from still using a light wire here and having it burn, same as wiring
     * a real appliance with too-thin a cable.
     */
    @Override
    public boolean accepts(ItemStack wireStack) {
        return wireStack.is(ModdedTags.Item.WIRES.tag);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public Class<AirconMotorBottomBlockEntity> getBlockEntityClass() {
        return AirconMotorBottomBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends AirconMotorBottomBlockEntity> getBlockEntityType() {
        return CIOBlockEntities.AIRCON_MOTOR_BOTTOM.get();
    }
}
