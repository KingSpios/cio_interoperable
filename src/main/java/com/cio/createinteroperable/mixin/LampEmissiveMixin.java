package com.cio.createinteroperable.mixin;

import com.cio.createinteroperable.letsdo.LetsDoLampBlockEntity;
import com.cio.createinteroperable.letsdo.LetsDoLampStates;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dims a CIO-powered lamp whose block carries no {@code lit} blockstate &mdash;
 * currently only Alpine Whispers' fairy lights. Their model is registered with
 * {@code emissiveRendering((s, l, p) -&gt; true)}, a rendering flag entirely
 * separate from light emission: it forces the faces to draw full-bright
 * regardless of the surrounding light. {@code AlpineFairyLightsBlockMixin}
 * already cuts the emitted light to zero when unpowered, but without this the
 * string still <em>looks</em> identical on and off. Here, when the string's
 * {@link LetsDoLampBlockEntity} reports it is not lit, {@code emissiveRendering}
 * returns false so the model draws at ambient light and visibly goes dark.
 *
 * <p>The {@link LetsDoLampStates#noLitLampBlocks()} lookup is an immutable
 * {@code HashSet} contains-check, so blocks that are not a no-lit CIO lamp
 * (i.e. every block, almost always) bail on the first line.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class LampEmissiveMixin {

    @Inject(method = "emissiveRendering", at = @At("HEAD"), cancellable = true, require = 0)
    private void cio$dimUnpoweredLamp(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockState self = (BlockState) (Object) this;
        if (!LetsDoLampStates.noLitLampBlocks().contains(self.getBlock())) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof LetsDoLampBlockEntity lamp && !lamp.isLampLit()) {
            cir.setReturnValue(false);
        }
    }
}
