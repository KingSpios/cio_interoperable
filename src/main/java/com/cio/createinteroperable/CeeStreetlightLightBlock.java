package com.cio.createinteroperable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The invisible, air-like light a lit {@link CeeStreetlightBlock} projects
 * below itself &mdash; the same idea as Power Grid's
 * {@code FactoryLightLightBlock} (air, replaceable, no collision, no drops).
 *
 * <p>Power Grid gives each of its light blocks a ticking block entity to check
 * for its lamp; this uses a scheduled block tick instead (no block entity, so a
 * 20-block column costs nothing between checks). Every {@link #CHECK_INTERVAL}
 * ticks it looks up through any light blocks above it: if the first other
 * block is a lit Streetlight within reach it stays, otherwise it removes
 * itself. That covers every way a lamp can stop owning it &mdash; switched off
 * while unloaded, a block placed in the column, the lamp removed by a world
 * edit.</p>
 */
public class CeeStreetlightLightBlock extends Block {
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 1, 15);
    private static final int CHECK_INTERVAL = 40;

    public CeeStreetlightLightBlock(Properties properties) {
        super(properties
                .lightLevel(state -> state.getValue(LEVEL))
                .noCollission()
                .noLootTable()
                .noOcclusion()
                .replaceable()
                .noTerrainParticles()
                .pushReaction(PushReaction.DESTROY)
                .air());
        registerDefaultState(defaultBlockState().setValue(LEVEL, 15));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(this)) {
            level.scheduleTick(pos, this, CHECK_INTERVAL);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (hasLitLampAbove(level, pos)) {
            level.scheduleTick(pos, this, CHECK_INTERVAL);
        } else {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private boolean hasLitLampAbove(ServerLevel level, BlockPos pos) {
        for (int i = 1; i <= CeeStreetlightBlockEntity.MAX_REACH; i++) {
            BlockPos above = pos.above(i);
            if (!level.isLoaded(above)) {
                return true; // can't tell yet; check again later
            }
            BlockState state = level.getBlockState(above);
            if (state.is(this)) {
                continue;
            }
            return state.getBlock() instanceof CeeStreetlightBlock && state.getValue(CeeStreetlightBlock.LIGHT) > 0;
        }
        return false;
    }
}
