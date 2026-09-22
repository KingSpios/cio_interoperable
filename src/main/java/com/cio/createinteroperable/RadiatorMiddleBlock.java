package com.cio.createinteroperable;

import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * A plain extender segment for the Multi Radiator (see {@link RadiatorAssembly}):
 * never placed directly (no item — see CIOItems), only ever produced by
 * wrenching a valid north/south run with 1-3 raw copper blocks in between.
 * Deliberately inert — "the middle one does not accept pipes or valves": no
 * kinetics, no fluid capability, purely a visual link in the chain. {@link #AXIS}
 * only exists to pick the right model rotation so the pipe run's texture
 * lines up with whichever way the assembled radiator is oriented (X or Z) —
 * it carries no other meaning.
 * <p>
 * DOES carry a minimal {@link RadiatorMiddleBlockEntity} now, added purely so
 * goggles show something when looking at a middle segment — see that class's
 * own doc for why a BlockEntity is required at all for that. It still ticks,
 * stores, and does nothing beyond reporting {@link #HEAT_LEVEL}.
 * <p>
 * TODO(design): mining an assembled middle segment currently drops nothing
 * (see CIOBlocks — registered with {@code .noLootTable()}, same as the real
 * Interoperable multiblock's own assembled-only positions). Undecided whether
 * it should hand back a copper block, a dedicated item, or stay unminable.
 */
public class RadiatorMiddleBlock extends Block implements IBE<RadiatorMiddleBlockEntity> {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    /** Drives the {@code #0} pipe sub-texture only — kept in sync with the run's tier, see RadiatorValveNorthBlockEntity. */
    public static final EnumProperty<BrassHeaterBlock.HeatLevel> HEAT_LEVEL =
            EnumProperty.create("heat_level", BrassHeaterBlock.HeatLevel.class);
    /** See {@link RadiatorValveNorthBlock#HEAT_WARM} — same Cold-Sweat-only mirror booleans, unlisted in this block's blockstate JSON. */
    public static final BooleanProperty HEAT_WARM = BooleanProperty.create("heat_warm");
    public static final BooleanProperty HEAT_HOT = BooleanProperty.create("heat_hot");
    public static final BooleanProperty HEAT_BLAZING = BooleanProperty.create("heat_blazing");

    /**
     * Same real geometry envelope as {@link RadiatorValveNorthBlock}/
     * {@link RadiatorValveSouthBlock} (x4-12, y0-14, z0-16 — confirmed
     * identical across the whole north/middle/south, all-5-tiers family).
     * This block previously had no shape override at all, which meant it
     * silently defaulted to vanilla's own full-cube shape — the same
     * neighbor-culling bug as the other 3, just less visible since it was
     * never explicit. Authored at AXIS=Z (this block's own blockstate "y": 0
     * default) and rotated via ShapeRotation.
     */
    private static final Map<Direction.Axis, VoxelShape> SHAPES = ShapeRotation.forHorizontalAxis(
            new ShapeRotation.Box(4, 0, 0, 12, 14, 16));

    public RadiatorMiddleBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.Z).setValue(HEAT_LEVEL, BrassHeaterBlock.HeatLevel.COLD)
                .setValue(HEAT_WARM, false).setValue(HEAT_HOT, false).setValue(HEAT_BLAZING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(AXIS, HEAT_LEVEL, HEAT_WARM, HEAT_HOT, HEAT_BLAZING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(AXIS));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(AXIS));
    }

    @Override
    public Class<RadiatorMiddleBlockEntity> getBlockEntityClass() {
        return RadiatorMiddleBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RadiatorMiddleBlockEntity> getBlockEntityType() {
        return CIOBlockEntities.RADIATOR_MIDDLE.get();
    }
}
