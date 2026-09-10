package com.cio.createinteroperable;

import com.cio.createinteroperable.compat.ElectroEnergeticsCompat;
import com.cio.createinteroperable.compat.PowerGridCompat;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Interim stand-in for the real multiblock's keystones/filler: a bare
 * placeholder block, cloned from Power Grid's own TransformerCoreBlock
 * (same cube_all model/texture pattern).
 *
 * cio_transformer.json's decorative geometry now bleeds DOWNWARD into the
 * cell below the block's own position (see InteroperableSmallBlock's
 * PG_TERMINAL_*_BASE doc — that's the fix for the raytrace-cell-cursor bug).
 * A lone core wrenching itself in place, like the original 1-core design,
 * would form on top of whatever's actually below it (floor, another block)
 * with nothing reserved for that bleed — a real clipping risk. Fix: require
 * TWO cores stacked vertically before forming at all, so the player has to
 * reserve both cells the model actually occupies. Wrenching EITHER core of
 * the pair works — matches this project's own "wrench any of the 6" real
 * multiblock convention (InteroperableAssembly) rather than requiring a
 * specific one — the pair always resolves the same way: the TOP position
 * becomes the functional InteroperableSmallBlock (its bleed then fills
 * exactly the (now-air) bottom position, nothing further down), the BOTTOM
 * position clears to air. No failure feedback for an unpaired lone core,
 * matching PG's own precedent (Medium Transformer's failed locate2x2 is
 * also silent).
 *
 * Facing is taken from the player's look direction at wrench time, same
 * source PG uses for its own small-transformer fallback
 * (UseOnContext#getHorizontalDirection()).
 */
public class InteroperableCoreBlock extends Block implements IWrenchable {
    public InteroperableCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        // SMALL (the wrench-formed result) needs both Power Grid and Electro
        // Energetics — silently no-op with only one installed, same as an
        // unpaired lone core.
        if (!PowerGridCompat.present() || !ElectroEnergeticsCompat.present()) {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        BlockPos topPos;
        if (level.getBlockState(pos.above()).is(this)) {
            topPos = pos.above();
        } else if (level.getBlockState(pos.below()).is(this)) {
            topPos = pos;
        } else {
            return InteractionResult.PASS;
        }
        BlockPos bottomPos = topPos.below();

        if (!level.isClientSide) {
            level.setBlockAndUpdate(bottomPos, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(topPos, CIOBlocks.SMALL.get().defaultBlockState()
                    .setValue(CIOProperties.PG_FACING, context.getHorizontalDirection()));
        }
        IWrenchable.playRotateSound(level, topPos);
        return InteractionResult.SUCCESS;
    }
}
