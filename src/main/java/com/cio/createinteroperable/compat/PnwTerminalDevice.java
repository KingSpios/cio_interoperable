package com.cio.createinteroperable.compat;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Placeholder CEE device for a Pantographs &amp; Wires connector. It adds no
 * circuit components: the connector's node is joined to the network only by the
 * bridge's mirrored wires. It exists because CEE's chunk post-processing calls
 * {@code getDevice()} on every {@code DeviceBlock} it finds and crashes on null.
 */
public class PnwTerminalDevice extends SimpleElectricalDevice {
    public PnwTerminalDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
        super(level, pos, deviceSD, type);
    }
}
