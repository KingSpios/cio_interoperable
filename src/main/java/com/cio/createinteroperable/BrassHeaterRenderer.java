package com.cio.createinteroperable;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Classic (non-Flywheel) fallback renderer — only ever invoked when
 * Flywheel visualization isn't active (KineticBlockEntityRenderer#renderSafe
 * itself early-returns whenever VisualizationManager.supportsVisualization
 * is true, confirmed by reading its real source), so BrassHeaterVisual is
 * what actually renders the shaft in the common case. Both exist for the
 * same reason Power Grid's Variac ships TunedBlockRenderer AND
 * TunedBlockVisual — one real mesh, two rendering backends.
 * <p>
 * The default {@link KineticBlockEntityRenderer#getRotatedModel} renders the
 * block's OWN baked model as the rotating buffer — correct for a plain
 * Shaft/Cogwheel block whose entire mesh spins, wrong for a "machine with a
 * shaft socket" like this one, where only a small stub should spin while the
 * casing stays put. Overriding it to return a SHAFT_HALF partial instead
 * (exactly how PG's own TunedBlockRenderer overrides it for Variac) is what
 * makes that distinction.
 */
public class BrassHeaterRenderer extends KineticBlockEntityRenderer<BrassHeaterBlockEntity> {
    public BrassHeaterRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(BrassHeaterBlockEntity be, BlockState state) {
        Direction socket = state.getValue(BrassHeaterBlock.FACING).getOpposite();
        return CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, socket);
    }
}
