package com.cio.createinteroperable.iden;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Electric twin of an Iden's Decor lever-style block (Emergency Lever, Light /
 * Power / Valve Switch, Blast Lever &mdash; all plain vanilla {@link LeverBlock}s
 * on Iden's side, so vanilla's shapes are already theirs). Same blockstate
 * properties, so it renders with Iden's own blockstate/models; but instead of a
 * redstone signal, its {@code powered} state opens and closes an appliance-grid
 * switch ({@link ElectricSwitchBlockEntity}).
 */
public class ElectricLeverBlock extends LeverBlock implements EntityBlock {

    public ElectricLeverBlock(Properties properties) {
        super(properties);
    }

    /** Vanilla's use, minus the client-side redstone-dust puff. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        this.pull(state, level, pos, player);
        return InteractionResult.CONSUME;
    }

    /** Toggle the contacts. No neighbour updates: this block drives no redstone. */
    @Override
    public void pull(BlockState state, Level level, BlockPos pos, @Nullable Player player) {
        BlockState next = state.cycle(POWERED);
        level.setBlock(pos, next, 3);
        this.playPullSound(next, level, pos, player);
        level.gameEvent(player, next.getValue(POWERED) ? GameEvent.BLOCK_ACTIVATE : GameEvent.BLOCK_DEACTIVATE, pos);
    }

    protected void playPullSound(BlockState state, Level level, BlockPos pos, @Nullable Player player) {
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3F, state.getValue(POWERED) ? 0.6F : 0.5F);
    }

    // --- no redstone ---------------------------------------------------

    @Override
    protected boolean isSignalSource(BlockState state) {
        return false;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 0;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // no redstone-dust particles
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // Skip LeverBlock's neighbour update; BlockBehaviour's default still drops the block entity.
        if (state.hasBlockEntity() && !state.is(newState.getBlock())) {
            level.removeBlockEntity(pos);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElectricSwitchBlockEntity(pos, state);
    }
}
