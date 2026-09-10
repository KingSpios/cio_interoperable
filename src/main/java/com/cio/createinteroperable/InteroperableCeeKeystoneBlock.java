package com.cio.createinteroperable;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** See InteroperablePgKeystoneBlock — same idea, placed at the CEE end instead. */
public class InteroperableCeeKeystoneBlock extends Block implements IWrenchable {
    public InteroperableCeeKeystoneBlock(Properties properties) {
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
