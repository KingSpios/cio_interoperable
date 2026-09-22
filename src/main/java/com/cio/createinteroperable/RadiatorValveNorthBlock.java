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
 * The steam-intake end of a Multi Radiator (see {@link RadiatorAssembly} for
 * the wrench-assembly mechanic, and {@link RadiatorValveSouthBlock} for the
 * water-output end this pairs with). Unlike {@link BrassHeaterBlock} (a
 * dead-end socket on FACING's OPPOSITE face), the shaft here connects on
 * FACING itself — the same convention {@link SteamOutletBlock} uses — because
 * the valve has to sit on the exposed, outward-facing end of the run, not
 * buried inside it. The steam pipe connection is deliberately NOT on that
 * same face though — it's fixed to straight DOWN instead (see
 * {@link RadiatorValveNorthBlockEntity#getSteamHandler}), same convention
 * Brass Heater itself uses: FACING is shaft/valve only, so a player can run
 * a shaft into the exposed end while a pipe comes up from below, with no
 * risk of the two fighting over which item/side does what.
 * <p>
 * "does not render the shaft" — implemented by simply not registering any
 * BlockEntityRenderer/Visual for this block's shaft socket (unlike Brass
 * Heater/Steam Outlet, which both render an AllPartialModels.SHAFT_HALF
 * stub — see BrassHeaterRenderer/Visual). The valve_plate_north element
 * already baked into {@code multi_radiator_north.json} reads as a complete
 * mechanism on its own, so nothing extra needs to spin. If that turns out to
 * look wrong in-game (no visual cue a shaft is actually attached while
 * stationary), the documented fallback is to add a
 * RadiatorValveNorthRenderer/Visual pair identical to BrassHeaterRenderer/Visual,
 * with {@code state.getValue(FACING)} instead of {@code .getOpposite()} as
 * the socket direction (matching SteamOutletRenderer's own convention).
 * <p>
 * {@link #ASSEMBLED} gates whether the BlockEntity does anything at all — a
 * freshly-placed North block sits inert (no consumption, no output) until
 * {@link RadiatorAssembly#tryAssemble} finds a valid South partner and flips
 * it true, same role {@code TOP}/keystone-vs-assembled states play in the
 * real Interoperable multiblock.
 */
public class RadiatorValveNorthBlock extends KineticBlock implements IBE<RadiatorValveNorthBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ASSEMBLED = BooleanProperty.create("assembled");
    /** Drives the {@code #0} pipe sub-texture only (see multi_radiator_north_*.json) — value pushed by RadiatorValveNorthBlockEntity. */
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<BrassHeaterBlock.HeatLevel> HEAT_LEVEL =
            net.minecraft.world.level.block.state.properties.EnumProperty.create("heat_level", BrassHeaterBlock.HeatLevel.class);
    /**
     * Mirrors of {@link #HEAT_LEVEL} as plain booleans, unlisted in this
     * block's blockstate JSON (a wildcard there, same trick
     * {@code CIOProperties.CALL_ACTIVE} uses) — see
     * {@link BrassHeaterBlock#HEAT_WARM} for why: Cold Sweat's own
     * state-predicate matcher can't reliably compare against an
     * enum-valued property, only a Boolean-valued one.
     */
    public static final BooleanProperty HEAT_WARM = BooleanProperty.create("heat_warm");
    public static final BooleanProperty HEAT_HOT = BooleanProperty.create("heat_hot");
    public static final BooleanProperty HEAT_BLAZING = BooleanProperty.create("heat_blazing");

    /**
     * Derived from the real {@code multi_radiator_north.json} geometry (and
     * confirmed identical across all 5 heat-tier variants): every element
     * fits inside x4-12, y0-14, z0-16 — a single box, narrower than a full
     * cube in X, full-depth in Z (its own north/south connection faces to
     * neighbors in the assembled run). A full cube here would incorrectly
     * cull east/west neighbors. Authored at FACING=NORTH and rotated
     * per-facing via ShapeRotation.
     */
    private static final Map<Direction, VoxelShape> SHAPES = ShapeRotation.forHorizontalFacing(
            new ShapeRotation.Box(4, 0, 0, 12, 14, 16));

    public RadiatorValveNorthBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(ASSEMBLED, false)
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
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
     * Wrenching first tries to complete/extend a radiator run (see
     * {@link RadiatorAssembly#tryAssemble}); only if that fails (no valid
     * South partner found in this block's inward direction) does it fall back
     * to the normal kinetic-block rotate {@code super.onWrenched} already
     * provides for free — so a lone North block with nothing to assemble
     * against still behaves like any other standalone kinetic block instead
     * of silently doing nothing.
     */
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

    /** Plain BlockItem#appendHoverText forwards straight into Block#appendHoverText (same pattern InteroperableDoubleCouplerBlock/TelephoneBlock use), so overriding it here reaches the item tooltip with no custom Item subclass. */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("block.createinteroperable.multi_radiator.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public Class<RadiatorValveNorthBlockEntity> getBlockEntityClass() {
        return RadiatorValveNorthBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RadiatorValveNorthBlockEntity> getBlockEntityType() {
        return CIOBlockEntities.RADIATOR_VALVE_NORTH.get();
    }
}
