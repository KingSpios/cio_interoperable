package com.cio.createinteroperable.mixin.letsdo;

import com.cio.createinteroperable.letsdo.LetsDoBathtub;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Gives Alpine Whispers' {@code BathtubBlockEntity} a CIO entry point
 * ({@link LetsDoBathtub}) so {@code AlpineWhispersBathtubBlockMixin} can advance
 * its private {@code progress}/{@code total} counter from a right-click.
 *
 * <p>Alpine Whispers' own timed auto-fill lives in {@code serverTick}, gated on
 * the {@code filling} flag; {@link #cio$feed} never raises that flag, so once CIO
 * is driving the fill the vanilla auto-advance simply never runs — no need to
 * touch {@code serverTick} at all. Buckets and bottles are left to Alpine
 * Whispers.
 *
 * <p>Targeted by name, non-required — a no-op without Alpine Whispers installed.
 */
@Mixin(targets = "net.satisfy.alpinewhispers.core.block.entity.BathtubBlockEntity", remap = false)
public abstract class AlpineWhispersBathtubEntityMixin implements LetsDoBathtub {

    @Shadow
    private int total;
    @Shadow
    private int progress;
    @Shadow
    private boolean filling;

    @Shadow
    protected abstract void setFullFlag(boolean full);

    @Override
    public float cio$ratio() {
        return total <= 0 ? 0f : Math.min(1f, progress / (float) total);
    }

    @Override
    public void cio$feed(int progressTicks) {
        if (progressTicks <= 0) {
            return;
        }
        if (total <= 0) {
            total = 900;
        }
        progress = Math.min(total, progress + progressTicks);
        if (progress >= total) {
            filling = false;
            setFullFlag(true);
        }
        BlockEntity self = (BlockEntity) (Object) this;
        self.setChanged();
        Level level = self.getLevel();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(self.getBlockPos(), self.getBlockState(), self.getBlockState(), 2);
        }
    }
}
