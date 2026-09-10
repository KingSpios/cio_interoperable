package com.cio.createinteroperable.mixin.letsdo;

import com.cio.createinteroperable.CIOConfig;
import com.cio.createinteroperable.letsdo.LetsDoLampBlockEntity;
import com.cio.createinteroperable.letsdo.LetsDoLampStates;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives Another Furniture's Lamp the same treatment as the Let's Do lamps: a
 * {@link LetsDoLampBlockEntity} (a Crayfish {@link
 * com.mrcrayfish.furniture.refurbished.electricity.IModuleNode}) rides on every
 * {@code LampBlock}, so it only lights while powered from a network that, via a
 * CIO Power Kit, reaches Power Grid. The {@code LampConnectorBlock} pole
 * segments stay dumb &mdash; Another Furniture swaps a stacked lamp between the
 * two block classes in {@code updateShape}, so exactly one {@code LampBlock}
 * (the head) sits at the top of any stack and the block swap drives the node's
 * whole lifecycle for free (no {@code neighborChanged} reconcile needed, unlike
 * the Let's Do lamps, which keep one block and mutate a {@code type} property).
 *
 * <p><b>Redstone.</b> A vanilla Another Furniture lamp mirrors its
 * {@code POWERED} (redstone) state onto {@code LIT} in {@code neighborChanged}.
 * Once the CIO node owns the lamp that is cancelled outright &mdash; the node,
 * not redstone, decides the light. Set {@code lamps.requirePower = false} to
 * hand the lamp (and its redstone behaviour) straight back to Another Furniture.
 *
 * <p>Name-targeted, non-required &mdash; a no-op without Another Furniture.
 */
@Mixin(targets = "com.starfish_studios.another_furniture.block.LampBlock")
public abstract class AnotherFurnitureLampBlockMixin implements EntityBlock {

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return CIOConfig.LETSDO_LAMPS_REQUIRE_POWER.get()
                ? new LetsDoLampBlockEntity(pos, state)
                : null;
    }

    /**
     * Place the lamp <b>dark</b>. {@code LampBlock} defaults {@code LIT=true}, so
     * without this it flashes lit for the tick or two before the node's first
     * {@code updateNodePoweredState()}. {@code applyLit()} repaints every tick
     * regardless, so this only needs to win the first frame.
     */
    @Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true, require = 0)
    private void cio$placeUnlit(BlockPlaceContext context, CallbackInfoReturnable<BlockState> cir) {
        BlockState placed = cir.getReturnValue();
        if (placed != null
                && CIOConfig.LETSDO_LAMPS_REQUIRE_POWER.get()
                && LetsDoLampStates.isLit(placed) == Boolean.TRUE) {
            cir.setReturnValue(LetsDoLampStates.withLit(placed, false));
        }
    }

    @Inject(method = "neighborChanged", at = @At("HEAD"), cancellable = true, require = 0)
    private void cio$suppressRedstone(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
                                      boolean isMoving, CallbackInfo ci) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof LetsDoLampBlockEntity) {
            ci.cancel();
        }
    }
}
