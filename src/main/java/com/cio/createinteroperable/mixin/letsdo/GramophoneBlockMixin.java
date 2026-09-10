package com.cio.createinteroperable.mixin.letsdo;

import com.cio.createinteroperable.grid.ApplianceNode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * When a disc goes into an unpowered Gramophone ({@link GramophoneNodeMixin}),
 * pop the "Missing power" action-bar message &mdash; reusing Crayfish's own
 * translation key so it reads identically whichever backend is in play.
 * Name-targeted, non-required &mdash; a no-op without Let's Do Furniture.
 */
@Mixin(targets = "com.berksire.furniture.core.block.GramophoneBlock")
public abstract class GramophoneBlockMixin {

    @Inject(method = "useItemOn", at = @At("RETURN"), require = 0)
    private void cio$noPowerNotice(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                   InteractionHand hand, BlockHitResult hit,
                                   CallbackInfoReturnable<ItemInteractionResult> cir) {
        if (level.isClientSide || cir.getReturnValue() != ItemInteractionResult.CONSUME) {
            return;
        }
        BlockPos basePos = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER
                ? pos.below() : pos;
        if (level.getBlockEntity(basePos) instanceof ApplianceNode node && !node.appliancePowered()) {
            player.displayClientMessage(Component.translatable("gui.refurbished_furniture.no_power"), true);
        }
    }
}
