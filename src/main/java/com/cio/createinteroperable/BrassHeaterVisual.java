package com.cio.createinteroperable;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;
import net.minecraft.core.Direction;

import java.util.function.Consumer;

/**
 * Flywheel visual — renders only when Flywheel visualization is active (the
 * common case), mirroring exactly what Create's own SawVisual does for a
 * Saw's horizontal orientation: a single AllPartialModels.SHAFT_HALF
 * instance, oriented to point out of the socket face (FACING's opposite),
 * rotating at the block's kinetic speed. No extra visible part beyond the
 * shaft stub itself — the heater's own throttle/heat state is communicated
 * entirely through the static HEAT_LEVEL-driven block model/texture, not
 * through this rotating instance.
 */
public class BrassHeaterVisual extends KineticBlockEntityVisual<BrassHeaterBlockEntity> {
    private final RotatingInstance shaft;

    public BrassHeaterVisual(VisualizationContext context, BrassHeaterBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);

        Direction socket = blockState.getValue(BrassHeaterBlock.FACING).getOpposite();
        shaft = instancerProvider()
                .instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.SHAFT_HALF))
                .createInstance()
                .rotateTo(0, 0, 1, socket.getStepX(), socket.getStepY(), socket.getStepZ());
        shaft.setup(blockEntity)
                .setPosition(getVisualPosition());
        shaft.setChanged();
    }

    @Override
    public void update(float pt) {
        shaft.setup(blockEntity)
                .setChanged();
    }

    @Override
    public void updateLight(float partialTick) {
        relight(shaft);
    }

    @Override
    protected void _delete() {
        shaft.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        consumer.accept(shaft);
    }
}
