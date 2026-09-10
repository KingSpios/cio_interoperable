package com.cio.createinteroperable.mixin.letsdo;

import com.cio.createinteroperable.CIOConfig;
import com.cio.createinteroperable.grid.ApplianceNode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hard-gates the Radio ({@link RadioNodeMixin}) at the block interaction: with
 * no power a right-click does nothing at all and pops the "Missing power"
 * action-bar message. Name-targeted, non-required &mdash; a no-op without Let's
 * Do Beachparty.
 */
@Mixin(targets = "net.satisfy.beachparty.core.block.RadioBlock")
public abstract class RadioBlockMixin {

    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void cio$blockUnpoweredUse(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit,
                                       CallbackInfoReturnable<InteractionResult> cir) {
        if (!CIOConfig.BEACHPARTY_APPLIANCES_REQUIRE_POWER.get()) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof ApplianceNode node && !node.appliancePowered()) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("gui.refurbished_furniture.no_power"), true);
            }
            cir.setReturnValue(InteractionResult.sidedSuccess(level.isClientSide));
        }
    }
}
