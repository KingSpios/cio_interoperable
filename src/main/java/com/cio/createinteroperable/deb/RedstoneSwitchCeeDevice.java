package com.cio.createinteroperable.deb;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/**
 * The Electro Energetics side of a CEE Redstone Switch &mdash; the CEE analogue
 * of the two {@link org.patryk3211.powergrid.electricity.sim.SwitchedWire}s the
 * PG variant builds. Two independent poles, each a resistor between an input
 * node and its output node: near-zero when the switch is closed, effectively
 * open otherwise. Node ids match the PG terminal indices &mdash; {@code 0/1}
 * input +/&minus;, {@code 2/3} output +/&minus;.
 *
 * <p>{@code volatile} fields, split into "pushed by the BlockEntity" and "read
 * back by it", the same threading discipline as {@link DebCeeDevice}.</p>
 */
public class RedstoneSwitchCeeDevice extends SimpleElectricalDevice {

    private volatile boolean closed;

    private volatile double vIn;
    private volatile double vOut;
    private volatile double iPlus;
    private volatile double iMinus;

    public RedstoneSwitchCeeDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
        super(level, pos, deviceSD, type);
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    public double getInVolts() {
        return vIn;
    }

    public double getOutVolts() {
        return vOut;
    }

    public double getPlusCurrent() {
        return iPlus;
    }

    public double getMinusCurrent() {
        return iMinus;
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        super.preTick(bridges);
        double r = closed ? RedstoneSwitchStats.CONTACT_RESISTANCE : RedstoneSwitchStats.OPEN_RESISTANCE;
        BridgeCollector.Builder b = bridges.builder(pos);
        b.resistor(0, 2, r);
        b.resistor(1, 3, r);
    }

    @Override
    public void postTick(SimulationResults results) {
        super.postTick(results);
        vIn = Math.abs(results.getVoltageAt(pos, 0, 1));
        vOut = Math.abs(results.getVoltageAt(pos, 2, 3));
        iPlus = Math.abs(results.getCurrentThrough(pos, 0, 2));
        iMinus = Math.abs(results.getCurrentThrough(pos, 1, 3));
    }

    @Override
    public void read(CompoundTag tag) {
    }

    @Override
    public void write(CompoundTag tag) {
    }
}
