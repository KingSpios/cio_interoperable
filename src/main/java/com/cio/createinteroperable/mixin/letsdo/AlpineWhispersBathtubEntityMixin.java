package com.cio.createinteroperable.mixin.letsdo;

import com.cio.createinteroperable.CIOConfig;
import com.cio.createinteroperable.letsdo.AlpineFixture;
import com.cio.createinteroperable.letsdo.BathtubSupply;
import com.cio.createinteroperable.letsdo.PipeFluidBuffer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Plumbs Alpine Whispers' bathtub ({@code BathtubBlockEntity}). Its tap runs a
 * timed fill ({@code progress} up to {@code total} ticks) that Alpine Whispers
 * animates (pouring water, sloshing, steam). With
 * {@link CIOConfig#SINK_REQUIRE_PIPE_SUPPLY} on, every tick of that fill now
 * costs {@code capacityMb / total} of water, taken from what pipes delivered
 * ({@link PipeFluidBuffer}) or else from a tank touching the tub
 * ({@link BathtubSupply}). When neither has enough the tap closes where it is,
 * and opening it again resumes from that level instead of Alpine Whispers'
 * restart from empty.
 *
 * <p>Targeted by name, non-required — a no-op without Alpine Whispers.
 */
@Mixin(targets = "net.satisfy.alpinewhispers.core.block.entity.BathtubBlockEntity", remap = false)
public abstract class AlpineWhispersBathtubEntityMixin implements AlpineFixture.Bathtub {

    @Shadow
    private int total;
    @Shadow
    private int progress;
    @Shadow
    private boolean filling;

    @Unique
    private PipeFluidBuffer cio$supply;
    /** Water owed for fill ticks already run, carried between ticks (fractions of a mB). */
    @Unique
    private float cio$owed;

    @Unique
    private BlockEntity cio$self() {
        return (BlockEntity) (Object) this;
    }

    @Override
    public PipeFluidBuffer cio$supply() {
        if (cio$supply == null) {
            cio$supply = new PipeFluidBuffer(cio$self());
        }
        return cio$supply;
    }

    @Override
    public boolean cio$filling() {
        return filling;
    }

    @Override
    public float cio$fillRatio() {
        return total <= 0 ? 0f : Math.min(1f, progress / (float) total);
    }

    @Override
    public void cio$closeTap() {
        if (!filling) {
            return;
        }
        filling = false;
        cio$sync();
    }

    @Unique
    private void cio$sync() {
        BlockEntity self = cio$self();
        self.setChanged();
        Level level = self.getLevel();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(self.getBlockPos(), self.getBlockState(), self.getBlockState(), 2);
        }
    }

    @Override
    public boolean cio$tickSupply() {
        if (!filling || total <= 0 || progress >= total) {
            return true;
        }
        cio$owed += Math.max(1, CIOConfig.BATHTUB_CAPACITY_MB.get()) / (float) total;
        int want = (int) cio$owed;
        if (want <= 0) {
            return true;
        }
        BlockEntity self = cio$self();
        int got = cio$supply().take(want);
        if (got < want) {
            got += BathtubSupply.drainWater(self.getLevel(), self.getBlockPos(), want - got);
        }
        cio$owed -= got;
        if (got < want) {
            cio$closeTap();
            AlpineFixture.runDry(self.getLevel(), self.getBlockPos());
            return false;
        }
        return true;
    }

    /** Re-opening a part-filled tub carries on from its level; Alpine Whispers would restart from empty. */
    @Inject(method = "startFilling", at = @At("HEAD"), cancellable = true, require = 0)
    private void createinteroperable$resume(int totalTicks, CallbackInfo ci) {
        if (CIOConfig.SINK_REQUIRE_PIPE_SUPPLY.get() && total > 0 && progress > 0 && progress < total) {
            filling = true;
            cio$sync();
            ci.cancel();
        }
    }

    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true, require = 0)
    private static void createinteroperable$payForWater(Level level, BlockPos pos, BlockState state,
                                                         @Coerce BlockEntity tub, CallbackInfo ci) {
        if (CIOConfig.SINK_REQUIRE_PIPE_SUPPLY.get() && tub instanceof AlpineFixture fixture
                && !fixture.cio$tickSupply()) {
            ci.cancel();
        }
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void createinteroperable$saveSupply(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        cio$supply().save(tag);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void createinteroperable$loadSupply(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        cio$supply().load(tag);
    }
}
