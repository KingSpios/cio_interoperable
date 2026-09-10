package com.cio.createinteroperable.mixin.letsdo;

import com.cio.createinteroperable.letsdo.LetsDoLampBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Redirects Another Furniture's plain-click lamp toggle into the CIO node's
 * switch ({@link LetsDoLampBlockEntity#toggleSwitch()}), so the player's on/off
 * intent survives but the lamp still only lights when it also has power.
 *
 * <p>The two other {@code useItemOn} paths are left alone: a crouch-click still
 * cycles the lamp's brightness {@code LEVEL} (that is not an on/off action), and
 * a click holding a lamp item still stacks (the {@code another_furniture:lamps}
 * tag).
 *
 * <p>Name-targeted, non-required &mdash; a no-op without Another Furniture.
 */
@Mixin(targets = "com.starfish_studios.another_furniture.block.LampBlock")
public abstract class AnotherFurnitureLampToggleMixin {

    private static final TagKey<Item> CIO$LAMPS =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("another_furniture", "lamps"));

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true, require = 0)
    private void cio$switchLamp(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                               InteractionHand hand, BlockHitResult hitResult,
                               CallbackInfoReturnable<ItemInteractionResult> cir) {
        if (player.isCrouching() || stack.is(CIO$LAMPS)) {
            return; // brightness cycle / stacking — leave Another Furniture's own handling
        }
        if (!(level.getBlockEntity(pos) instanceof LetsDoLampBlockEntity lamp)) {
            return; // integration off (no BE) — leave the mod's own toggle alone
        }
        if (!level.isClientSide) {
            lamp.toggleSwitch();
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        cir.setReturnValue(ItemInteractionResult.sidedSuccess(level.isClientSide));
    }
}
