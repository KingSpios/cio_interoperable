package com.cio.createinteroperable.mixin.letsdo;

import com.cio.createinteroperable.letsdo.LetsDoLampBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * Redirects the manual light toggle on Let's Do lamps into the CIO node's
 * switch (see {@link LetsDoLampBlockEntity#toggleSwitch()}), so the player's
 * on/off intent survives but the lamp still only lights when it also has power.
 * Made a deliberate <b>sneak</b>-right-click so casual clicks near a lamp do
 * nothing (Candlelight's plain-click toggle is folded into this).
 *
 * <p>Name-targeted, non-required &mdash; a no-op without those mods.
 */
@Mixin(targets = {
        "com.berksire.furniture.core.block.LampBlock",
        "com.berksire.furniture.core.block.LampWallBlock",
        "net.satisfy.candlelight.core.block.LampBlock"
})
public abstract class LetsDoLampToggleMixin {

    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void cio$switchLamp(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit,
                               CallbackInfoReturnable<InteractionResult> cir) {
        if (!player.isShiftKeyDown()) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof LetsDoLampBlockEntity lamp)) {
            return; // integration off (no BE) — leave the mod's own toggle alone
        }
        if (!level.isClientSide) {
            lamp.toggleSwitch();
            level.playSound(null, pos, SoundEvents.WOODEN_PRESSURE_PLATE_CLICK_ON, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        cir.setReturnValue(InteractionResult.sidedSuccess(level.isClientSide));
    }
}
