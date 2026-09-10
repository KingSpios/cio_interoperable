package com.cio.createinteroperable.letsdo;

import com.mrcrayfish.furniture.refurbished.blockentity.fluid.IFluidContainerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Finds a drainable fluid supply touching a Refurbished Furniture water fixture,
 * used by {@code com.cio.createinteroperable.mixin.crayfish.RefurbishedFixtureTapMixin}.
 *
 * <p>Scans all six faces (the fixture's own connection points, wherever they
 * are — not just underneath). Any other {@link IFluidContainerBlock} neighbour
 * is skipped so two fixtures placed next to each other can't drink from one
 * another.
 *
 * <p>Lives in the ordinary {@code letsdo} package rather than the Mixin-owned
 * {@code mixin.crayfish} package: a non-mixin helper class in a declared mixin
 * package throws {@code IllegalClassLoadError} when referenced from transformed
 * code. It still only classloads when Crayfish is present, because its sole
 * caller is a mixin gated on {@code CrayfishCompat#present()}.
 */
public final class RefurbishedFixtureSupply {
    private RefurbishedFixtureSupply() {
    }

    /** @return a neighbouring fluid handler that currently has something to give, or {@code null}. */
    @Nullable
    public static IFluidHandler findSupply(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos side = pos.relative(dir);
            BlockEntity neighbour = level.getBlockEntity(side);
            if (neighbour instanceof IFluidContainerBlock) {
                continue;
            }
            IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, side, dir.getOpposite());
            if (handler != null && !handler.drain(1, IFluidHandler.FluidAction.SIMULATE).isEmpty()) {
                return handler;
            }
        }
        return null;
    }
}
