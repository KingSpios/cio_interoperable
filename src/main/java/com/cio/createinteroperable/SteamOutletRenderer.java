package com.cio.createinteroperable;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Classic (non-Flywheel) fallback renderer for the Steam Outlet's shaft —
 * same reasoning as BrassHeaterRenderer: only invoked when Flywheel
 * visualization isn't active. Renders a SHAFT_HALF stub pointing out of the
 * socket face (FACING's opposite) instead of the default (rotating the
 * whole static block model).
 */
public class SteamOutletRenderer extends KineticBlockEntityRenderer<SteamOutletBlockEntity> {
    public SteamOutletRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(SteamOutletBlockEntity be, BlockState state) {
        Direction socket = state.getValue(SteamOutletBlock.FACING).getOpposite();
        return CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, socket);
    }
}
