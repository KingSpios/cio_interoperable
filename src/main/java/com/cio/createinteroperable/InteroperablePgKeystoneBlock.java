package com.cio.createinteroperable;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A bare, state-identical marker block. Place two of these (top + bottom)
 * at the PG end of a 2x3 rectangle, a CEE keystone pair at the other end,
 * and filler pairs in between, then wrench any one of the 6 to assemble —
 * mirrors Power Grid's own TransformerCoreBlock, which is likewise a plain
 * Block with no properties, letting InteroperableAssembly do all the real
 * detection/placement work (see TransformerCoreBlock#locate2x2 for the
 * pattern this was adapted from).
 */
public class InteroperablePgKeystoneBlock extends Block implements IWrenchable {
    public InteroperablePgKeystoneBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        if (InteroperableAssembly.tryAssemble(level, pos)) {
            IWrenchable.playRotateSound(level, pos);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
