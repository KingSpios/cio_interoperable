package com.cio.createinteroperable.mixin.urbandecor;

import net.minecraft.core.BlockPos;
import com.cio.createinteroperable.urbandecor.UrbanFixtures;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fixes an upstream Urban Decor bug that leaves faucets, sinks and showers
 * permanently "off": their {@code useWithoutItem} only runs on the client and
 * calls {@code state.setValue(ON, !state.getValue(ON))} while throwing the
 * result away (BlockState is immutable), so the {@code on} property never
 * changes on either side. This performs the toggle on the server with a real
 * {@code setBlock} ({@link UrbanFixtures#toggle}), and makes both the tap and a
 * glass bottle need real water (see {@code UrbanFixtureBlockEntity}).
 *
 * <p>Name-targeted and property-by-name (no compile dependency on Urban Decor),
 * non-required &mdash; a complete no-op when Urban Decor is absent.
 */
@Mixin(targets = {
        "net.yirmiri.urban_decor.common.block.appliances.FaucetBlock",
        "net.yirmiri.urban_decor.common.block.appliances.SinkBlock",
        "net.yirmiri.urban_decor.common.block.appliances.ShowerBlock"
})
public abstract class UrbanDecorWaterToggleMixin {

    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void cio$toggleOn(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit,
                              CallbackInfoReturnable<InteractionResult> cir) {
        // Same guard as the original: a bare-handed, non-sneaking click.
        if (player.isCrouching() || !player.getMainHandItem().isEmpty()) {
            return;
        }
        BooleanProperty on = UrbanFixtures.onProperty(state);
        if (on == null) {
            return; // not the shape we expect — leave the mod's own code alone
        }
        if (!level.isClientSide) {
            UrbanFixtures.toggle(level, pos, state, on, player);
        }
        cir.setReturnValue(InteractionResult.sidedSuccess(level.isClientSide));
    }

    /** A glass bottle takes real water from a running tap, or none at all. */
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true, require = 0)
    private void cio$bottleNeedsWater(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                      InteractionHand hand, BlockHitResult hit,
                                      CallbackInfoReturnable<ItemInteractionResult> cir) {
        BooleanProperty on = UrbanFixtures.onProperty(state);
        if (level.isClientSide || on == null || !state.getValue(on) || !player.getItemInHand(hand).is(Items.GLASS_BOTTLE)) {
            return;
        }
        if (!UrbanFixtures.payForBottle(level, pos, player)) {
            cir.setReturnValue(ItemInteractionResult.CONSUME);
        }
    }
}
