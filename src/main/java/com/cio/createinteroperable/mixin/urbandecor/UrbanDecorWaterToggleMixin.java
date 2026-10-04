package com.cio.createinteroperable.mixin.urbandecor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
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
 * {@code setBlock}.
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

    private static final ResourceLocation CIO$FAUCET_TURN = ResourceLocation.parse("urban_decor:block.faucet.turn");

    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void cio$toggleOn(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit,
                              CallbackInfoReturnable<InteractionResult> cir) {
        // Same guard as the original: a bare-handed, non-sneaking click.
        if (player.isCrouching() || !player.getMainHandItem().isEmpty()) {
            return;
        }
        Property<?> prop = state.getBlock().getStateDefinition().getProperty("on");
        if (!(prop instanceof BooleanProperty on)) {
            return; // not the shape we expect — leave the mod's own code alone
        }
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(on, !state.getValue(on)), 3);
            level.playSound(null, pos,
                    BuiltInRegistries.SOUND_EVENT.getOptional(CIO$FAUCET_TURN).orElse(SoundEvents.LEVER_CLICK),
                    SoundSource.BLOCKS, 0.8F, 1.0F);
        }
        cir.setReturnValue(InteractionResult.sidedSuccess(level.isClientSide));
    }
}
