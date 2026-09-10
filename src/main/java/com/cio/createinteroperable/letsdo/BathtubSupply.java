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
 * face toward the tub (a Create pipe end included). Scans all six faces; other
 * managed bathtubs are skipped so two can't drink from one another.
 */
public final class BathtubSupply {
    private BathtubSupply() {
    }

    @Nullable
    public static IFluidHandler findWater(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos side = pos.relative(dir);
            BlockEntity neighbour = level.getBlockEntity(side);
            if (neighbour instanceof LetsDoBathtub) {
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
}
