package com.cio.createinteroperable.mixin.letsdo;

import com.cio.createinteroperable.CIOConfig;
import com.cio.createinteroperable.letsdo.BathtubSupply;
import com.cio.createinteroperable.letsdo.LetsDoBathtub;
import com.cio.createinteroperable.letsdo.SinkStates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes Alpine Whispers' Arolla Pine Bathtub obey CIO's "needs a water supply"
 * rule, the same way Let's Do sinks do.
 *
 * <p>Alpine Whispers' bare-hand tap ({@code useItemOn}) normally calls
 * {@code startFilling(900)} and the tub then fills itself for free over 45&nbsp;s.
 * When {@link CIOConfig#SINK_REQUIRE_PIPE_SUPPLY} is on this intercepts that
 * gesture: each right-click pulls {@link CIOConfig#BATHTUB_FILL_STEP_MB} of water
 * out of a supply touching the tub and advances the fill proportionally, so
 * holding right-click fills it incrementally until full. No supply → a message,
 * nothing happens. Buckets, bottles, draining and sitting fall through to Alpine
 * Whispers untouched.
 *
 * <p>Targeted by name, non-required — a no-op without Alpine Whispers installed.
 */
@Mixin(targets = "net.satisfy.alpinewhispers.core.block.BathtubBlock")
public abstract class AlpineWhispersBathtubBlockMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true, require = 0)
    private void createinteroperable$requireSupply(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                  Player player, InteractionHand hand, BlockHitResult hit,
                                                  CallbackInfoReturnable<ItemInteractionResult> cir) {
        if (!CIOConfig.SINK_REQUIRE_PIPE_SUPPLY.get()) {
            return;
        }
        // Only the bare-hand fill gesture on a non-underside face. Buckets,
        // bottles, draining and sitting stay with Alpine Whispers.
        if (!stack.isEmpty() || hit.getDirection() == Direction.DOWN) {
            return;
        }
        BlockPos head = SinkStates.bathtubHead(state, pos);
        if (!(level.getBlockEntity(head) instanceof LetsDoBathtub tub)) {
            return;
        }
        if (Boolean.TRUE.equals(SinkStates.bool(level.getBlockState(head), "full")) || tub.cio$ratio() >= 0.999f) {
            return; // already full — let Alpine Whispers handle "sit in the tub"
        }
        if (level.isClientSide) {
            cir.setReturnValue(ItemInteractionResult.CONSUME);
            return;
        }

        IFluidHandler supply = BathtubSupply.findWater(level, head);
        if (supply == null) {
            player.displayClientMessage(Component.translatable("message.createinteroperable.fixture.no_supply"), true);
            cir.setReturnValue(ItemInteractionResult.CONSUME);
            return;
        }
        int step = Math.max(1, CIOConfig.BATHTUB_FILL_STEP_MB.get());
        FluidStack moved = supply.drain(step, IFluidHandler.FluidAction.EXECUTE);
        if (moved.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.createinteroperable.fixture.dry"), true);
            cir.setReturnValue(ItemInteractionResult.CONSUME);
            return;
        }
        int capacity = Math.max(1, CIOConfig.BATHTUB_CAPACITY_MB.get());
        int ticks = Math.max(1, Math.round(moved.getAmount() * 900f / capacity));
        tub.cio$feed(ticks);
        level.playSound(null, head, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.5F, 1.2F);
        cir.setReturnValue(ItemInteractionResult.SUCCESS);
    }
}
