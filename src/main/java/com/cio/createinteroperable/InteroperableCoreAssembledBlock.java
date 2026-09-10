package com.cio.createinteroperable;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * The middle column of an assembled Interoperable Transformer. Purely
 * structural/visual — no Power Grid or Electro Energetics integration at
 * all, no BlockEntity. PG_FACING/TOP are only carried here so the model
 * can pick the right orientation and top/bottom variant; nothing reads
 * them for any functional purpose.
 *
 * Never placed directly by a player — only created by
 * InteroperableAssembly when a full rectangle is wrenched together, same
 * as Power Grid's own TransformerMediumBlock is never itemized.
 */
public class InteroperableCoreAssembledBlock extends Block {
    public InteroperableCoreAssembledBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(CIOProperties.PG_FACING, Direction.NORTH)
                .setValue(CIOProperties.TOP, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CIOProperties.PG_FACING, CIOProperties.TOP);
    }
}
