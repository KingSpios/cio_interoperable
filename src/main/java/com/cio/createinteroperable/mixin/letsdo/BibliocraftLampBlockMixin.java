package com.cio.createinteroperable.mixin.letsdo;

import com.cio.createinteroperable.CIOConfig;
import com.cio.createinteroperable.letsdo.LetsDoLampBlockEntity;
import com.cio.createinteroperable.letsdo.LetsDoLampStates;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
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
 * Gives Bibliocraft's Fancy Lamp the same treatment as the Let's Do / Another
 * Furniture lamps: a {@link LetsDoLampBlockEntity} (a Crayfish {@code
 * IModuleNode}, via {@code LetsDoLampNodeMixin}) rides on the block, so it
 * only lights while powered from a network that, via a CIO Power Kit, reaches
 * Power Grid. Never a stacking block (see {@code STACKING_CLASSES} in
 * {@link LetsDoLampStates}, which this relies on to keep {@code
 * isHeadSegment} from misreading its unrelated {@code type} orientation
 * property as a stacking-position one), so unlike the Let's Do lamps this
 * needs no {@code neighborChanged} node-lifecycle reconcile.
 *
 * <p><b>Redstone.</b> A vanilla Fancy Lamp is lit exactly when it has
 * <em>no</em> redstone signal: {@code neighborChanged} turns it off the
 * instant a signal appears, and schedules a delayed {@code tick} to turn it
 * back on once the signal is gone. Once the CIO node owns the lamp both are
 * suppressed outright &mdash; the node, not redstone, decides the light (
 * {@link LetsDoLampBlockEntity#applyLit()} runs every tick regardless, so
 * even a stale scheduled tick from before the node existed is harmless to
 * leave running, but cancelling it too avoids a one-tick flicker). Set
 * {@code lamps.requirePower = false} to hand the lamp (and its redstone
 * behaviour) straight back to Bibliocraft.
 *
 * <p>Name-targeted, non-required &mdash; a no-op without Bibliocraft.
 */
@Mixin(targets = "com.github.minecraftschurlimods.bibliocraft.content.fancylight.FancyLampBlock")
public abstract class BibliocraftLampBlockMixin implements EntityBlock {

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return CIOConfig.LETSDO_LAMPS_REQUIRE_POWER.get() && LetsDoLampStates.isHeadSegment(state)
                ? new LetsDoLampBlockEntity(pos, state)
                : null;
    }

    /**
     * Place the lamp <b>dark</b>. A vanilla Fancy Lamp places lit whenever it
     * has no neighbor redstone signal yet, so without this it would flash lit
     * for the tick or two before the node's first {@code
     * updateNodePoweredState()}. {@code applyLit()} repaints every tick
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
    private void cio$suppressRedstoneOff(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
                                         boolean isMoving, CallbackInfo ci) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof LetsDoLampBlockEntity) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 0)
    private void cio$suppressRedstoneOn(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos,
                                        RandomSource random, CallbackInfo ci) {
        if (level.getBlockEntity(pos) instanceof LetsDoLampBlockEntity) {
            ci.cancel();
        }
    }
}
