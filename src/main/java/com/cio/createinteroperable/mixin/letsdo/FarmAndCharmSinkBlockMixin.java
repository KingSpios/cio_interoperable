package com.cio.createinteroperable.mixin.letsdo;

import com.cio.createinteroperable.CIOConfig;
import com.cio.createinteroperable.letsdo.SinkBlockEntity;
import com.cio.createinteroperable.letsdo.SinkStates;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two changes to every Farm &amp; Charm–style {@code SinkBlock} — Farm &amp;
 * Charm's own (also used by Candlelight and Bakery) <em>and</em> Alpine Whispers'
 * standalone copy {@code net.satisfy.alpinewhispers.core.block.SinkBlock} (the
 * Arolla Pine Sink / Washbasin), which extends vanilla {@code Block} directly
 * rather than Farm &amp; Charm's class:
 *
 * <ol>
 *   <li>Makes it an {@link EntityBlock} so a {@link SinkBlockEntity} is attached
 *       to its lower half — Create's fluid pipe code
 *       ({@code FluidPropagator.hasFluidCapability},
 *       {@code FlowSource$FluidHandler.manageSource}) ignores blocks with no
 *       block entity, so a bare-block capability is invisible to pipes.</li>
 *   <li>Suppresses the vanilla water-drip particles unless the sink actually has
 *       a live supply in its buffer (see {@link SinkBlockEntity#isSupplied()}).</li>
 * </ol>
 *
 * <p>Targeted by name and kept non-required, so this is a no-op when none of
 * those mods are installed. Each target class independently declares
 * {@code animateTick} and neither declares {@code newBlockEntity} or implements
 * {@code EntityBlock}, so one mixin applies cleanly to both.
 */
@Mixin(targets = {
        "net.satisfy.farm_and_charm.core.block.SinkBlock",
        "net.satisfy.alpinewhispers.core.block.SinkBlock"
})
public abstract class FarmAndCharmSinkBlockMixin implements EntityBlock {

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return SinkStates.isLowerHalf(state) ? new SinkBlockEntity(pos, state) : null;
    }

    @Inject(method = "animateTick", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void createinteroperable$dripsOnlyWhenSupplied(BlockState state, Level level, BlockPos pos, RandomSource random,
                                                          CallbackInfo ci) {
        if (!CIOConfig.SINK_REQUIRE_PIPE_SUPPLY.get()) {
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            be = level.getBlockEntity(pos.below());
        }
        if (be instanceof SinkBlockEntity sink && !sink.isSupplied()) {
            ci.cancel();
        }
    }
}
