package com.cio.createinteroperable.iden;

import com.cio.createinteroperable.CIOBlockEntities;
import com.cio.createinteroperable.CIODevices;
import com.cio.createinteroperable.compat.PnwTerminalDevice;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * CIO's stand-in for Iden's Decor's telephone on an Electro-Energetics-only
 * install: CEE's own {@link SimpleElectricalDeviceBlock} (node registration
 * for free) with one node, the CEE tap. See {@link IdenPgTelephoneBlock} for
 * the Power Grid variants; behaviour lives in {@link IdenPhoneBlocks}.
 */
public class IdenCeeTelephoneBlock extends SimpleElectricalDeviceBlock<PnwTerminalDevice> implements IBE<IdenCeeTelephoneBlockEntity> {

    public IdenCeeTelephoneBlock() {
        super(IdenPhoneBlocks.properties());
        registerDefaultState(IdenPhoneBlocks.defaultState(stateDefinition.any()));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        IdenPhoneBlocks.addProperties(builder);
    }

    @Override
    public SimulatedDeviceType<PnwTerminalDevice> getDevice() {
        return CIODevices.PNW_TERMINAL.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return Map.of(IdenPhoneBlocks.CEE_TAP_NODE, IdenPhoneBlocks.ceeTapPoint(state));
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        return id == IdenPhoneBlocks.CEE_TAP_NODE ? IdenPhoneBlocks.ceeTapPoint(state) : null;
    }

    @Override
    public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int id) {
        return com.cio.createinteroperable.TelephoneLabels.tap();
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
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        IdenPhoneBlocks.onRemove(state, level, pos, newState, movedByPiston);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public Class<IdenCeeTelephoneBlockEntity> getBlockEntityClass() {
        return IdenCeeTelephoneBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends IdenCeeTelephoneBlockEntity> getBlockEntityType() {
        return CIOBlockEntities.IDEN_CEE_TELEPHONE.get();
    }
}
