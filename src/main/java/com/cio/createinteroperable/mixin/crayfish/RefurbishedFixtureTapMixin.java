package com.cio.createinteroperable.mixin.crayfish;

import com.cio.createinteroperable.CIOConfig;
import com.cio.createinteroperable.letsdo.RefurbishedFixtureSupply;
import com.mrcrayfish.furniture.refurbished.blockentity.KitchenSinkBlockEntity;
import com.mrcrayfish.furniture.refurbished.blockentity.ToiletBlockEntity;
import com.mrcrayfish.furniture.refurbished.blockentity.fluid.FluidContainer;
import com.mrcrayfish.furniture.refurbished.blockentity.fluid.IFluidContainerBlock;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes Refurbished Furniture's water fixtures need a real supply.
 *
 * <p>RF's kitchen sink / bathroom sink / bath / toilet each call
 * {@code IFluidContainerBlock#tryAndFillWithFluid} from their own
 * {@code interact} whenever RF's {@code dispenseWater} option is on (the
 * default) — a bucket of water out of thin air. This intercepts the bare-hand
 * tap gesture at the HEAD of {@code interact}: when
 * {@link CIOConfig#RF_REQUIRE_SUPPLY} is set, it instead pulls
 * {@link CIOConfig#RF_FILL_STEP_MB} out of whatever fluid handler is touching
 * the fixture and pushes it into the fixture's own tank. Vanilla's use-repeat
 * (a held right-click re-fires {@code interact} every few ticks) turns that into
 * a continuous fill.
 *
 * <p>Anything with an item in hand falls straight through, so RF still handles
 * buckets, bottles and the toilet's item-flush. The free-fill that RF's
 * {@code interact} would otherwise still reach with a non-bucket item held is
 * killed separately, at its source, by {@link IFluidContainerBlockMixin} — so
 * RF's {@code dispenseWater} option is never in play while the gate is on.
 *
 * <p>Not blocked: a Create pump piping into the fixture's fluid-handler
 * capability still fills the tank directly (that path is RF's own capability,
 * untouched). Stopping automated fill without also breaking bucket-empty needs a
 * capability-level wrapper and is deliberately left for later.
 *
 * <p>Crayfish hard dependency: this whole file (and its config) is gated by
 * {@link CrayfishMixinPlugin} and its {@code targets} are {@code com.mrcrayfish.*},
 * so it is a complete no-op when Refurbished Furniture is absent.
 */
@Mixin(targets = {
        "com.mrcrayfish.furniture.refurbished.blockentity.KitchenSinkBlockEntity",
        "com.mrcrayfish.furniture.refurbished.blockentity.BasinBlockEntity",
        "com.mrcrayfish.furniture.refurbished.blockentity.BathBlockEntity",
        "com.mrcrayfish.furniture.refurbished.blockentity.ToiletBlockEntity"
}, remap = false)
public abstract class RefurbishedFixtureTapMixin {

    @Inject(method = "interact", at = @At("HEAD"), cancellable = true, remap = false)
    private void createinteroperable$requireSupply(Player player, InteractionHand hand, BlockHitResult hit,
                                                   CallbackInfoReturnable<ItemInteractionResult> cir) {
        if (!CIOConfig.RF_REQUIRE_SUPPLY.get()) {
            return;
        }
        BlockEntity self = (BlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null) {
            return;
        }
        ItemStack held = player.getItemInHand(hand);
        if (!held.isEmpty() || hit.getDirection() == Direction.DOWN) {
            return; // RF handles bucket / bottle; the DOWN face is never the tap
        }
        BlockPos pos = self.getBlockPos();
        FluidContainer tank = ((IFluidContainerBlock) (Object) this).getFluidContainer();
        if (tank == null) {
            return;
        }
        // toilet: preserve RF's "click the bowl to flush floating items"
        if (((Object) this) instanceof ToiletBlockEntity
                && (hit.getLocation().y - pos.getY()) > 0.625D && !tank.isEmpty()) {
            return;
        }
        if (level.isClientSide) {
            cir.setReturnValue(ItemInteractionResult.CONSUME);
            return;
        }

        long room = tank.getCapacity() - tank.getStoredAmount();
        if (room <= 0L) {
            cir.setReturnValue(ItemInteractionResult.CONSUME);
            return;
        }
        int step = (int) Math.min(Math.max(1, CIOConfig.RF_FILL_STEP_MB.get()), room);

        IFluidHandler supply = RefurbishedFixtureSupply.findSupply(level, pos);
        if (supply == null) {
            player.displayClientMessage(Component.translatable("message.createinteroperable.fixture.no_supply"), true);
            cir.setReturnValue(ItemInteractionResult.CONSUME);
            return;
        }
        FluidStack peek = supply.drain(step, IFluidHandler.FluidAction.SIMULATE);
        if (peek.isEmpty() || (!tank.isEmpty() && tank.getStoredFluid() != peek.getFluid())) {
            player.displayClientMessage(Component.translatable("message.createinteroperable.fixture.dry"), true);
            cir.setReturnValue(ItemInteractionResult.CONSUME);
            return;
        }
        FluidStack moved = supply.drain(new FluidStack(peek.getFluid(), step), IFluidHandler.FluidAction.EXECUTE);
        if (moved.isEmpty()) {
            cir.setReturnValue(ItemInteractionResult.CONSUME);
            return;
        }
        tank.push(moved.getFluid(), moved.getAmount(), false);
        if (((Object) this) instanceof KitchenSinkBlockEntity sink) {
            sink.playWaterAnimation();
        }
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.6F, 1.1F);
        cir.setReturnValue(ItemInteractionResult.SUCCESS);
    }
}
