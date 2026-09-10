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
 * The Electro Energetics side of the Telephone's positive/negative nubs —
 * simpler than {@link InteroperableDevice} on purpose: that block is a real
 * bridge passing power BETWEEN two live networks (hence its source/sink role
 * switching), but the Telephone is a pure load on either protocol, never
 * both at once (see TelephoneBlock's WIRE_LOCK). So this just exposes the
 * exact same resistive load PG already sees
 * ({@link TelephoneBlockEntity#COIL_RESISTANCE}) to CEE's own solver, and
 * TelephoneBlockEntity reads back whichever protocol currently holds the
 * lock — no EMF injection, no role flag needed.
 *
 * Verified against george8188625/Create-Electro-Energetics via javap:
 * SimpleElectricalDevice's 4-arg constructor, BridgeCollector.Builder#resistor(int,int,double),
 * and SimulationResults#getVoltageAt(BlockPos, int, int) all match this file's usage.
 */
public class TelephoneDevice extends SimpleElectricalDevice {
    /** Near-zero pass-through resistance for a closed breaker — same convention as the Power Kit's Power Feed. */
    private static final double FEED_PASSTHROUGH_R = 0.001;
    /** Small resistance for the listener breaker when closed. */
    private static final double LISTENER_R = 0.05;
    /** Stands in for an open breaker — large enough that CEE's solver sees it as no real connection. */
    private static final double R_OPEN = 1.0e7;

    /** Last voltage CEE solved across this block's own node pair (0=positive, 1=negative). */
    private volatile double lastVoltage = 0;

    /**
     * True exactly while this telephone's own call is answered and ongoing —
     * only meaningful for {@link CeeTelephoneBlockEntity} (the CEE-only
     * phone), which is the only caller of {@link #setAnswered}. Gates the
     * bottom outlet (ids 3/4) and listener/breaker (id 5) node connections
     * below: merely being powered is not enough, matching the CPG phone's own
     * {@code SwitchedWire}-based breakers in {@code CpgTelephoneBlockEntity}.
     */
    private volatile boolean answered = false;

    public TelephoneDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
        super(level, pos, deviceSD, type);
    }

    /** Called from TelephoneBlockEntity#electricalTick() when CEE currently holds the wire lock. */
    public double getLastVoltage() {
        return lastVoltage;
    }

    /** Called from CeeTelephoneBlockEntity whenever its own answered/call state changes (or is re-synced each tick). */
    public void setAnswered(boolean answered) {
        this.answered = answered;
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        super.preTick(bridges);
        var builder = bridges.builder(pos);
        builder.resistor(0, 1, TelephoneBlockEntity.COIL_RESISTANCE);

        // Bottom Feed +/- (ids 3/4) and the middle listener/breaker (id 5) —
        // CEE-only phone's counterpart to CpgTelephoneBlockEntity's
        // SwitchedWire breakers. These ids are never exposed as physical
        // positions on the Interoperable TelephoneBlock, so for that variant
        // this is just an unreachable internal connection with no effect.
        double breakerR = answered ? FEED_PASSTHROUGH_R : R_OPEN;
        double listenerR = answered ? LISTENER_R : R_OPEN;
        builder.resistor(0, 3, breakerR);
        builder.resistor(1, 4, breakerR);
        builder.resistor(1, 5, listenerR);
    }

    @Override
    public void postTick(SimulationResults results) {
        super.postTick(results);
        lastVoltage = results.getVoltageAt(pos, 0, 1);
    }

    @Override
    public void read(CompoundTag tag) {
        lastVoltage = tag.getDouble("LastVoltage");
    }

    @Override
    public void write(CompoundTag tag) {
        tag.putDouble("LastVoltage", lastVoltage);
    }
}
