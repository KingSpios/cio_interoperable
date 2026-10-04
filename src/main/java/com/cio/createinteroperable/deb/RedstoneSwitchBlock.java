package com.cio.createinteroperable.deb;

import com.cio.createinteroperable.CIOBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.DirectionalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;

/**
 * "Redstone Switch" &mdash; a Power Grid inline break. Two poles (+ and &minus;)
 * that open and close together but are electrically independent, so a single
 * rail can be routed through one pole with the other left unused.
 *
 * <p>A redstone signal drives it (powered = closed); a bare right-click flips
 * the current state and that manual override holds until the redstone input
 * next changes (see {@link RedstoneSwitchState}). Throughput caps and the
 * thermal model are calibrated against the tier-2 Domestic Power Kit &mdash;
 * see {@link RedstoneSwitchStats}. Push more than {@link RedstoneSwitchStats#RATED_WATTS}
 * through it and the contacts arc; sustain it and the block detonates, exactly
 * like an overloaded Power Kit.</p>
 *
 * <p>6-way {@code FACING} (wall / floor / ceiling), authored in the NORTH
 * reference orientation like {@link DebRectifierBlock}. The moving contact is
 * drawn by {@link RedstoneSwitchRenderer}, sliding toward the bottom (voltage)
 * viewer in whatever orientation the block sits &mdash; not blindly along
 * world Y.</p>
 */
public class RedstoneSwitchBlock extends DirectionalElectricBlock implements IBE<RedstoneSwitchBlockEntity> {

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    private static final double NUB_EXPAND = 0.02;

    // Index order MUST match RedstoneSwitchBlockEntity.buildCircuit:
    //   0/1 = input +/-  (mv_positive_120v / mv_negative_120v, bottom nubs)
    //   2/3 = output +/- (lv_positive_120v_out / lv_negative_120v_out, top nubs)
    private static final TerminalBoundingBox[] TERMINALS = {
            new TerminalBoundingBox(Component.literal("In +"), 9, 1, 0, 10, 2, 1, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.RED),
            new TerminalBoundingBox(Component.literal("In −"), 6, 1, 0, 7, 2, 1, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.BLUE),
            new TerminalBoundingBox(Component.literal("Out +"), 9, 15, 1, 10, 16, 2, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.RED),
            new TerminalBoundingBox(Component.literal("Out −"), 6, 15, 1, 7, 16, 2, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.BLUE),
    };

    static final VoxelShape SHAPE = PowerKitGeometry.REDSTONE_SWITCH_SHAPE;
    private static final VoxelShaper SHAPE_SHAPER =
            VoxelShaper.forDirectional(SHAPE, Direction.NORTH).withVerticalShapes(SHAPE);

    public RedstoneSwitchBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWERED, false));
        setTerminalCollection(DirectionalElectricBlock.directionalNorthTerminals(this, TERMINALS, SHAPE, SHAPE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean signal = context.getLevel().hasNeighborSignal(context.getClickedPos());
        return defaultBlockState()
                .setValue(FACING, context.getClickedFace().getOpposite())
                .setValue(POWERED, signal);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE_SHAPER.get(state.getValue(FACING));
    }

    /** Fixed by its mount &mdash; a plain wrench must not spin it. Sneak-wrench pickup is unaffected. */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RedstoneSwitchBlockEntity be) {
            be.manualToggle();
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public Class<RedstoneSwitchBlockEntity> getBlockEntityClass() {
        return RedstoneSwitchBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RedstoneSwitchBlockEntity> getBlockEntityType() {
        return CIOBlockEntities.REDSTONE_SWITCH.get();
    }
}
