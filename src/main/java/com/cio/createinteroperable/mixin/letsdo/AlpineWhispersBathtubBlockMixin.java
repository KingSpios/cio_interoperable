package com.cio.createinteroperable.mixin.letsdo;

import com.cio.createinteroperable.CIOConfig;
import com.cio.createinteroperable.letsdo.AlpineFixture;
import com.cio.createinteroperable.letsdo.BathtubSupply;
import com.cio.createinteroperable.letsdo.SinkStates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The player side of the Alpine Whispers bathtub plumbing (see
 * {@code AlpineWhispersBathtubEntityMixin}), while
 * {@link CIOConfig#SINK_REQUIRE_PIPE_SUPPLY} is on. Alpine Whispers opens the
 * tap on a bare-hand click (or, oddly, an empty bottle) on the head; CIO:
 * <ul>
 *   <li>refuses to open it with no water piped in or in a tank touching the tub;</li>
 *   <li>closes a running tap on the same click, so the tub can be left part-full;</li>
 *   <li>turns a hand-poured water bucket into 1000&nbsp;mB of supply for the tap
 *       (or refuses it, per {@link CIOConfig#SINK_ALLOW_MANUAL_BUCKET_FILL})
 *       instead of Alpine Whispers' whole free fill.</li>
 * </ul>
 * A full tub (sitting, draining with a bucket or bottle) stays with Alpine Whispers.
 *
 * <p>Targeted by name, non-required — a no-op without Alpine Whispers installed.
 */
@Mixin(targets = "net.satisfy.alpinewhispers.core.block.BathtubBlock")
public abstract class AlpineWhispersBathtubBlockMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true, require = 0)
    private void createinteroperable$requireSupply(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                  Player player, InteractionHand hand, BlockHitResult hit,
                                                  CallbackInfoReturnable<ItemInteractionResult> cir) {
        if (level.isClientSide || !CIOConfig.SINK_REQUIRE_PIPE_SUPPLY.get()) {
            return;
        }
        BlockPos head = SinkStates.bathtubHead(state, pos);
        if (!(level.getBlockEntity(head) instanceof AlpineFixture.Bathtub tub)) {
            return;
        }
        if (Boolean.TRUE.equals(SinkStates.bool(level.getBlockState(head), "full")) || tub.cio$fillRatio() >= 0.999f) {
            return;
        }

        if (stack.is(Items.WATER_BUCKET)) {
            if (!CIOConfig.SINK_ALLOW_MANUAL_BUCKET_FILL.get()) {
                cir.setReturnValue(ItemInteractionResult.CONSUME); // CONSUME, not SKIP: SKIP would let the bucket pour onto the block
            } else if (!tub.cio$filling()) {
                tub.cio$supply().add(1000); // Alpine Whispers then takes the bucket and opens the tap
            }
            return;
        }

        boolean tapGesture = stack.isEmpty()
                ? hit.getBlockPos().equals(head) && hit.getDirection() != Direction.DOWN
                : stack.is(Items.GLASS_BOTTLE);
        if (!tapGesture) {
            return;
        }
        if (tub.cio$filling()) {
            if (stack.isEmpty()) {
                tub.cio$closeTap();
                level.playSound(null, head, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4F, 0.9F);
                cir.setReturnValue(ItemInteractionResult.CONSUME);
            }
            return;
        }
        if (!tub.cio$supply().isSupplied() && BathtubSupply.findWater(level, head) == null) {
            AlpineFixture.refuse(level, player, tub.cio$supply(), head);
            cir.setReturnValue(ItemInteractionResult.CONSUME);
        }
    }
}
