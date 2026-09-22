package com.cio.createinteroperable;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/**
 * The Electro Energetics side of the CEE Aircon Motor &mdash; the CEE
 * analogue of the {@link org.patryk3211.powergrid.electricity.sim.SwitchedWire}
 * the PG variant's {@code AirconMotorBottomBlockEntity} drives directly:
 * a single dynamic-resistance load across the 2 terminal nodes (matching
 * {@code AirconMotorBottomBlock#POSITIVE_TERMINAL}/{@code NEGATIVE_TERMINAL}'s
 * own {@code 0}/{@code 1} ids), toggled live/dead each tick from the
 * BlockEntity's own Off/Low/Mid/Max setting.
 * <p>
 * Unlike PG's {@code SwitchedWire} (a genuine open/closed-switch primitive),
 * CEE has no direct equivalent — "Off" is modeled the same way
 * {@code DebCeeDevice#setPassthrough} already models a dead feed in this
 * codebase: {@link #preTick} simply skips the {@code resistor(...)} call
 * entirely while {@link #live} is false, leaving that node pair genuinely
 * unwired for the tick rather than approximating disconnection with a very
 * large resistance value.
 * <p>
 * {@code volatile} fields, split into "pushed by the BlockEntity each tick"
 * ({@link #live}/{@link #resistance}) and "read back by it"
 * ({@link #intakeVolts}) — same threading discipline as {@link
 * com.cio.createinteroperable.deb.DebCeeDevice}/{@code RedstoneSwitchCeeDevice}
 * in this codebase, required because CEE's simulation runs on its own
 * tick/thread separate from the Minecraft server tick.
 */
public class AirconCeeDevice extends SimpleElectricalDevice {
    /** Matches {@code AirconMotorBottomBlock#POSITIVE_TERMINAL}/{@code NEGATIVE_TERMINAL}'s own node ids. */
    private static final int POSITIVE_NODE = 0;
    private static final int NEGATIVE_NODE = 1;

    private volatile boolean live;
    private volatile double resistance = 6.0;

    /** Signed — {@code V(positive) - V(negative)}, negative if wired backwards. Kept signed (unlike {@code RedstoneSwitchCeeDevice}'s own {@code Math.abs} reads) so {@code CeeAirconMotorBottomBlockEntity} can derive the same reversed-polarity reversing-valve behavior the PG variant has. */
    private volatile double intakePotentialDifference;

    public AirconCeeDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
        super(level, pos, deviceSD, type);
    }

    /** Pushed every tick from {@code CeeAirconMotorBottomBlockEntity} — matches the PG side's own {@code loadSwitch.setState(!off)} + {@code setResistance(...)} pair. */
    public void configure(boolean live, double resistance) {
        this.live = live;
        this.resistance = resistance;
    }

    /** The solved, SIGNED potential difference across the 2 terminal nodes, in real volts — {@code V(positive) - V(negative)}. */
    public double getIntakePotentialDifference() {
        return intakePotentialDifference;
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        super.preTick(bridges);
        if (live) {
            bridges.builder(pos).resistor(POSITIVE_NODE, NEGATIVE_NODE, resistance);
        }
    }

    @Override
    public void postTick(SimulationResults results) {
        super.postTick(results);
        double v = results.getVoltageAt(pos, POSITIVE_NODE, NEGATIVE_NODE);
        intakePotentialDifference = Double.isFinite(v) ? v : 0.0;
    }

    @Override
    public void read(CompoundTag tag) {
    }

    @Override
    public void write(CompoundTag tag) {
    }
}
