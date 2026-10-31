package com.cio.createinteroperable.letsdo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Finds a drainable <em>water</em> supply touching an Alpine Whispers bathtub —
 * a fluid tank, a drum, or any block exposing a NeoForge fluid handler on the
 * face toward the tub. Scans all six faces; other Alpine fixtures are skipped so
 * two can't drink from one another. (Pipes feed the tub's own
 * {@link PipeFluidBuffer} instead; this keeps tubs set up next to a tank working.)
 */
public final class BathtubSupply {
    private BathtubSupply() {
    }

    @Nullable
    public static IFluidHandler findWater(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos side = pos.relative(dir);
            BlockEntity neighbour = level.getBlockEntity(side);
            if (neighbour instanceof AlpineFixture) {
                continue;
            }
            IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, side, dir.getOpposite());
            if (handler == null) {
                continue;
            }
            FluidStack peek = handler.drain(1, IFluidHandler.FluidAction.SIMULATE);
            if (!peek.isEmpty() && peek.getFluid().defaultFluidState().is(FluidTags.WATER)) {
                return handler;
            }
        }
        return null;
    }

    /** Drains up to {@code mb} of water from an adjacent supply; returns the amount drained. */
    public static int drainWater(Level level, BlockPos pos, int mb) {
        IFluidHandler supply = findWater(level, pos);
        if (supply == null) {
            return 0;
        }
        FluidStack want = supply.drain(mb, IFluidHandler.FluidAction.SIMULATE);
        if (want.isEmpty() || !want.getFluid().defaultFluidState().is(FluidTags.WATER)) {
            return 0;
        }
        return supply.drain(want, IFluidHandler.FluidAction.EXECUTE).getAmount();
    }
}
