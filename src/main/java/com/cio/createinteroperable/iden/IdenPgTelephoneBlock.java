package com.cio.createinteroperable.iden;

import com.cio.createinteroperable.CIOBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.ElectricBlock;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.electricity.base.terminals.BlockStateTerminalCollection;

/**
 * CIO's stand-in for Iden's Decor's telephone on a Power Grid install (swapped
 * in at Iden's own registration &mdash; see {@link IdenTelephones}). Power Grid
 * only puts wire terminals on an {@link ElectricBlock}, which Iden's block can't
 * be made into with a mixin, hence the swap. One terminal: the CPG tap nub on
 * the back of the base. Everything else is {@link IdenPhoneBlocks}.
 */
public class IdenPgTelephoneBlock extends ElectricBlock implements IBE<IdenPgTelephoneBlockEntity> {

    /** A hair of check() tolerance beyond the 1&times;1 nub (PG's max bound is exclusive). */
    private static final double NUB_EXPAND = 0.02;

    private static final TerminalBoundingBox CPG_TAP = new TerminalBoundingBox(com.cio.createinteroperable.TelephoneLabels.tap(),
            5.5, 1, 12, 6.5, 2, 13, NUB_EXPAND);

    public IdenPgTelephoneBlock() {
        super(IdenPhoneBlocks.properties());
        registerDefaultState(IdenPhoneBlocks.defaultState(stateDefinition.any()));
        setTerminalCollection(BlockStateTerminalCollection.builder(this)
                .forAllStates(state -> new TerminalBoundingBox[] {
                        CPG_TAP.rotateAroundY(IdenPhoneBlocks.angleFor(state)) })
                .build());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        IdenPhoneBlocks.addProperties(builder);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return IdenPhoneBlocks.placement(defaultBlockState(), context);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(IdenPhoneBlocks.FACING, rotation.rotate(state.getValue(IdenPhoneBlocks.FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(IdenPhoneBlocks.FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return IdenPhoneBlocks.shape(state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return IdenPhoneBlocks.useWithoutItem(state, level, pos, player);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        return IdenPhoneBlocks.useItemOn(stack, state, level, pos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos,
                                   boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        IdenPhoneBlocks.neighborChanged(state, level, pos);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return IdenPhoneBlocks.comparator(state);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return IdenPhoneBlocks.isSignalSource(state);
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return IdenPhoneBlocks.weakSignal(state);
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return IdenPhoneBlocks.directSignal(state, direction);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        IdenPhoneBlocks.onRemove(state, level, pos, newState, movedByPiston);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public Class<IdenPgTelephoneBlockEntity> getBlockEntityClass() {
        return IdenPgTelephoneBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends IdenPgTelephoneBlockEntity> getBlockEntityType() {
        return CIOBlockEntities.IDEN_PG_TELEPHONE.get();
    }
}
