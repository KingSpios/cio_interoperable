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
 * Flywheel visual for the Steam Outlet's shaft — mirrors BrassHeaterVisual
 * (itself mirroring Create's own SawVisual) exactly: a single SHAFT_HALF
 * instance oriented out of the socket face (FACING's opposite).
 */
public class SteamOutletVisual extends KineticBlockEntityVisual<SteamOutletBlockEntity> {
    private final RotatingInstance shaft;

    public SteamOutletVisual(VisualizationContext context, SteamOutletBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);

        Direction socket = blockState.getValue(SteamOutletBlock.FACING).getOpposite();
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
