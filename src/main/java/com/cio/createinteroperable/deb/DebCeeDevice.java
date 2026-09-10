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
 * The Electro Energetics side of a CEE-wired Power Kit — the CEE analogue of the
 * Power Grid circuit {@link DebRectifierBlockEntity#buildCircuit builds} for a
 * CPG kit. One shared {@link SimulatedDeviceType} ({@code CIODevices.POWER_KIT})
 * serves every tier; the tier-specific feed topology is pushed in from the
 * BlockEntity each electrical tick (it already computes every one of these
 * numbers for the PG couplings) and replayed here.
 *
 * <p>Node ids match the kit's PG terminal indices exactly (see
 * {@code buildCircuit} in {@link DebRectifierBlockEntity} /
 * {@link PowerKitTier3BlockEntity}):</p>
 * <ul>
 *   <li><b>0/1</b> — intake. A single {@code resistor(0,1, primaryResistance)};
 *       {@code primaryResistance} already folds in every regulated feed's
 *       reflected draw, exactly as on the PG side.</li>
 *   <li><b>Passthrough feed</b> (tier 2's 120&nbsp;V, tier 3/4's HV) — strapped
 *       to the intake through {@code FEED_PASSTHROUGH_R}, a real parallel branch
 *       whose draw is metered but <em>not</em> reflected.</li>
 *   <li><b>Regulated feeds</b> (12&nbsp;V always; tier 3/4's 120&nbsp;V) —
 *       isolated {@code voltageSourceWithResistance} pairs, EMF = the same
 *       step-down the PG coupling uses. Their draw is reflected via
 *       {@code primaryResistance} one tick later, matching the PG kit's own
 *       one-tick feed-to-intake lag.</li>
 * </ul>
 *
 * <p>Fields are {@code volatile} and split into "pushed by the BE" and "read
 * back by the BE", the same threading discipline as {@link com.cio.createinteroperable.InteroperableDevice}
 * and {@code TelephoneDevice}.</p>
 */
public class DebCeeDevice extends SimpleElectricalDevice {

    // --- pushed from the BlockEntity each electricalTick -----------------
    private volatile double primaryResistance = 1.0e7;

    private volatile boolean passthrough;
    private volatile int passA = 2, passB = 3;
    private volatile double passResistance = 0.001;

    private volatile boolean lvFeed;
    private volatile int lvA = 4, lvB = 5;
    private volatile double lvEmf, lvResistance = 1.0;

    private volatile boolean mv120Feed;
    private volatile int mv120A = 4, mv120B = 5;
    private volatile double mv120Emf, mv120Resistance = 1.0;

    // --- solved values read back by the BlockEntity ---------------------
    private volatile double intakeVolts;
    private volatile double passCurrent;
    private volatile double lvCurrent, lvVolts;
    private volatile double mv120Current, mv120Volts;

    public DebCeeDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
        super(level, pos, deviceSD, type);
    }

    // --- BE -> device --------------------------------------------------

    /** The load the kit presents to the CEE grid (already includes reflected regulated-feed draw). */
    public void configure(double primaryResistance) {
        this.primaryResistance = primaryResistance;
    }

    /** Tier 2's 120&nbsp;V / tier 3-4's HV pass-through. {@code live=false} removes the strap entirely. */
    public void setPassthrough(boolean live, int a, int b, double resistance) {
        this.passthrough = live;
        this.passA = a;
        this.passB = b;
        this.passResistance = resistance;
    }

    /** The regulated 12&nbsp;V step-down feed (every tier). */
    public void setLvFeed(int a, int b, double emf, double resistance) {
        this.lvFeed = true;
        this.lvA = a;
        this.lvB = b;
        this.lvEmf = emf;
        this.lvResistance = resistance;
    }

    /** The regulated 120&nbsp;V step-down feed (tier 3/4 only; {@code present=false} on tiers 1-2). */
    public void setMv120Feed(boolean present, int a, int b, double emf, double resistance) {
        this.mv120Feed = present;
        this.mv120A = a;
        this.mv120B = b;
        this.mv120Emf = emf;
        this.mv120Resistance = resistance;
    }

    // --- device -> BE -------------------------------------------------

    public double getIntakeVolts() {
        return intakeVolts;
    }

    /** Current through the pass-through strap (0 when the strap is absent or nothing is wired to it). */
    public double getPassCurrent() {
        return passCurrent;
    }

    public double getLvCurrent() {
        return lvCurrent;
    }

    public double getLvVolts() {
        return lvVolts;
    }

    public double getMv120Current() {
        return mv120Current;
    }

    public double getMv120Volts() {
        return mv120Volts;
    }

    // --- CEE simulation hooks ---------------------------------------

    @Override
    public void preTick(BridgeCollector bridges) {
        super.preTick(bridges);
        BridgeCollector.Builder b = bridges.builder(pos);
        b.resistor(0, 1, primaryResistance);
        if (passthrough) {
            b.resistor(0, passA, passResistance);
            b.resistor(1, passB, passResistance);
        }
        if (lvFeed) {
            b.voltageSourceWithResistance(lvA, lvB, lvResistance, lvEmf);
        }
        if (mv120Feed) {
            b.voltageSourceWithResistance(mv120A, mv120B, mv120Resistance, mv120Emf);
        }
    }

    @Override
    public void postTick(SimulationResults results) {
        super.postTick(results);
        intakeVolts = Math.abs(results.getVoltageAt(pos, 0, 1));
        passCurrent = passthrough ? Math.abs(results.getCurrentThrough(pos, 0, passA)) : 0.0;
        lvCurrent = lvFeed ? Math.abs(results.getCurrentThrough(pos, lvA, lvB)) : 0.0;
        lvVolts = lvFeed ? Math.abs(results.getVoltageAt(pos, lvA, lvB)) : 0.0;
        mv120Current = mv120Feed ? Math.abs(results.getCurrentThrough(pos, mv120A, mv120B)) : 0.0;
        mv120Volts = mv120Feed ? Math.abs(results.getVoltageAt(pos, mv120A, mv120B)) : 0.0;
    }

    @Override
    public void read(CompoundTag tag) {
        intakeVolts = tag.getDouble("IntakeVolts");
    }

    @Override
    public void write(CompoundTag tag) {
        tag.putDouble("IntakeVolts", intakeVolts);
    }
}
