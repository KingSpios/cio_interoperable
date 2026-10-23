package com.cio.createinteroperable.mixin.pnw;

import com.cio.createinteroperable.CIODevices;
import com.cio.createinteroperable.compat.PnwCeeNodes;
import com.george_vi.electroenergetics.devices.device.SimulatedDevice;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.Map;

/**
 * Exposes every PnW connector as CEE nodes: node 0 for insulators, one node per
 * sub-cantilever (at its contact wire tip) for cantilevers. PnW keeps
 * ownership of the block; it only carries an inert placeholder CEE device.
 */
@Mixin(targets = "de.mrjulsen.paw.block.abstractions.AbstractRotatableWireConnectorBlock", remap = false)
abstract class PnwConnectorBlockMixin implements ElectricalDeviceBlock<SimulatedDevice> {
    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return PnwCeeNodes.nodePositions(level, pos, state);
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        return PnwCeeNodes.nodePositions(level, pos, state).get(id);
    }

    @Override
    @SuppressWarnings("unchecked")
    public SimulatedDeviceType<SimulatedDevice> getDevice() {
        // CEE's chunk post-processing calls this for every DeviceBlock and
        // NPEs on null, so the connectors get an inert placeholder device.
        return (SimulatedDeviceType<SimulatedDevice>) (SimulatedDeviceType<?>) CIODevices.PNW_TERMINAL.get();
    }
}
