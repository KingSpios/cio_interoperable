package com.cio.createinteroperable.mixin.pnw;

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
 * Exposes every PnW connector/catenary attachment as CEE node 0. PnW keeps
 * ownership of the block; no CEE simulated device is created for it.
 */
@Mixin(targets = "de.mrjulsen.paw.block.abstractions.AbstractRotatableWireConnectorBlock", remap = false)
abstract class PnwConnectorBlockMixin implements ElectricalDeviceBlock<SimulatedDevice> {
    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return Map.of(0, new Vec3(0.5, 0.5, 0.5));
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        return id == 0 ? new Vec3(0.5, 0.5, 0.5) : null;
    }

    @Override
    public SimulatedDeviceType<SimulatedDevice> getDevice() {
        // These are passive terminals. The bridge creates their CEE nodes when
        // a PnW conductive edge is placed, so no ticking device is required.
        return null;
    }
}
