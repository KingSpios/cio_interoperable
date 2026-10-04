package com.cio.createinteroperable.mixin.mts;

import mcinterface1211.BuilderBlockTileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A light-level change is not a break. Immersive Vehicles' block-entity blocks
 * call {@code onBroken()} from {@code onRemove} unconditionally (twice: here and
 * in {@code BuilderBlock}), and for a pole that runs {@code destroy()}, which
 * spits every pole component out as an item. Vanilla calls {@code onRemove} on
 * <em>every</em> server-side state change, including IV's own {@code light}
 * property update whenever a pole's lamp output changes &mdash; so a street
 * light switching on or off with grid power popped itself (and anything else on
 * that pole) off as items.
 *
 * <p>When the block itself stays the same, vanilla's own {@code onRemove} is a
 * no-op ({@code BlockBehaviour} only drops the block entity when the block
 * changes), so skipping the whole chain loses nothing.</p>
 */
@Mixin(value = BuilderBlockTileEntity.class, remap = false)
public abstract class IvBlockStateChangeMixin {

    @Inject(method = "onRemove", at = @At("HEAD"), cancellable = true, remap = false)
    private void cio$notABreak(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving,
                               CallbackInfo ci) {
        if (state.is(newState.getBlock())) {
            ci.cancel();
        }
    }
}
