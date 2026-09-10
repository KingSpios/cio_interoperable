package com.cio.createinteroperable;

import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/**
 * The Electro Energetics side of the bridge — one-way, direction switchable
 * via {@link InteroperableSmallBlockEntity}'s slider.
 *
 * Earlier revision had BOTH sides always actively injecting an EMF equal to
 * "what the other side last measured," forming a closed feedback loop (PG
 * reads CEE's loaded output -> feeds it to CEE -> CEE reads PG's loaded
 * output -> feeds it back to PG -> ...). Real playtesting showed this decays
 * and even oscillates in sign under load — a co-simulation stability
 * artifact, not real AC. Fixed by making exactly one side "source" (senses
 * its own real circuit passively, EMF pinned to 0 so it doesn't fight
 * whatever's actually driving it) and the other "sink" (actively injects
 * the source's last reading). Direction is data, not structure — the
 * resistance/topology never changes, only which value each side's EMF
 * provider reads, so no circuit rebuild is needed when the slider flips.
 *
 * Verified against george8188625/Create-Electro-Energetics: SimpleElectricalDevice,
 * BridgeCollector.Builder#voltageSourceWithResistance, and
 * SimulationResults#getVoltageAt(BlockPos, int, int) all match this file's usage.
 */
public class InteroperableDevice extends SimpleElectricalDevice {
    /**
     * Real playtest (2026-08-26): 120V PG generator, through a 30 Ohm
     * external resistor, into this block's PG side (acting as source) —
     * CEE side read 0.2V, with no load at all on the CEE side. Root cause:
     * both roles (passively sensing the source's own circuit, and actively
     * delivering power into the sink's circuit) were sharing ONE fixed
     * small resistance (was 0.05 Ohm) for the coupling. Used as the SOURCE
     * side's resistance, a small value is exactly wrong — the transformer's
     * two terminals sit in series in the source's own loop (generator ->
     * external resistor -> transformer -> back to generator), so a small
     * coupling resistance turns the transformer into a near dead-short:
     * nearly all the loop's current (and therefore nearly all the voltage
     * drop) happens across the EXTERNAL resistor instead, and the tiny
     * sliver actually appearing across the transformer's own resistance is
     * what got sensed and forwarded — 120 * (0.05 / (30 + 0.05)) is 0.1997V,
     * matching the observed 0.2V almost exactly.
     *
     * A real voltmeter reads a source's voltage by drawing negligible
     * current — i.e. presenting very HIGH resistance, not low. Fix: split
     * into two resistances applied by ROLE, not one shared constant. The
     * SENSE side (whichever side is currently acting as source) uses
     * SENSE_RESISTANCE — large enough that it doesn't meaningfully load any
     * realistic external circuit, so it reads close to the true open-circuit
     * source voltage. The DELIVERY side (whichever side is currently the
     * sink, actively injecting the sensed voltage as its own EMF) keeps a
     * small resistance so it doesn't itself waste much voltage driving a
     * real load. Both InteroperableSmallBlockEntity's
     * ProvidedVoltageSourceCoupling (PG side, via #setResistanceProvider)
     * and this class's own #preTick (CEE side) switch between these two by
     * the same pgIsSource() role check already used for the EMF value —
     * see both call sites.
     */
    public static final float SENSE_RESISTANCE = 1_000_000f;
    /** See SENSE_RESISTANCE's doc above — this is the old shared value, now only used for the actively-delivering side. */
    public static final float DELIVERY_RESISTANCE = 0.05f;

    /** Last voltage Power Grid's side solved for this block, only actually used as an EMF when PG is source. */
    private volatile double powerGridVoltage = 0;

    /** Last voltage CEE solved for this block, for Power Grid's side to read back when CEE is source. */
    private volatile double lastVoltage = 0;

    /**
     * Last current CEE solved through this block's own two nodes — only
     * meaningful when CEE is actually the delivery/sink side (DELIVERY_RESISTANCE,
     * real load current); when CEE is sensing (SENSE_RESISTANCE) this stays
     * near zero by design. Read by InteroperableSmallBlockEntity's transformer
     * hum (tickAudio) when PG is the source and CEE is delivering, since PG's
     * own coupling current is deliberately near-zero in that direction and
     * can't be used as the "how much is actually transferring" signal.
     */
    private volatile double lastCurrent = 0;

    /** true = PG is source, CEE is sink (default, "CPG -> CEE"); false = CEE is source, PG is sink. */
    private volatile boolean pgIsSource = true;

    /**
     * Resistance this device presents while it is the SOURCE (i.e. {@code !pgIsSource}).
     * Defaults to {@link #SENSE_RESISTANCE} — a pure voltmeter tap that doesn't
     * load the CEE grid — which is what {@link InteroperableSmallBlockEntity},
     * {@link InteroperablePgAssembledBlockEntity} and the single
     * {@link InteroperableCouplerBlockEntity} all leave it at. Only
     * {@link InteroperableDoubleCouplerBlockEntity} drives it down each tick to
     * {@code V_sensed / I_delivered} (reflected impedance) so a sink-side load
     * draws real current back through the source grid — see its
     * {@code electricalTick()}.
     */
    private volatile float sourceResistance = SENSE_RESISTANCE;

    /**
     * When false, this side neither injects an EMF nor presents a delivery-low
     * resistance regardless of {@link #pgIsSource} — it just sits passive
     * (SENSE_RESISTANCE, 0 EMF) so no power crosses the bridge. Used by
     * {@link InteroperableCouplerBlockEntity} to hard-cut transfer while its
     * gauge needle is mid-swing between directions (the 3s "diode reversal"
     * dead time) and whenever its mode is OFF. Defaults true, and
     * {@link InteroperableSmallBlockEntity}/{@link InteroperablePgAssembledBlockEntity}
     * never touch it, so their behaviour is unchanged.
     */
    private volatile boolean transferEnabled = true;

    public InteroperableDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
        super(level, pos, deviceSD, type);
    }

    /** Called from {@link InteroperableSmallBlockEntity} after PG's solve, on the main thread. */
    public void setPowerGridVoltage(double voltage) {
        this.powerGridVoltage = voltage;
    }

    /** Called from {@link InteroperableSmallBlockEntity} every tick to mirror the slider's current direction. */
    public void setPgIsSource(boolean pgIsSource) {
        this.pgIsSource = pgIsSource;
    }

    /** Called from {@link InteroperableCouplerBlockEntity} every tick — see {@link #transferEnabled}. */
    public void setTransferEnabled(boolean transferEnabled) {
        this.transferEnabled = transferEnabled;
    }

    /** Reflected-impedance resistance for the CEE-is-source role — see {@link #sourceResistance}. */
    public void setSourceResistance(float sourceResistance) {
        this.sourceResistance = sourceResistance;
    }

    /** Called from {@link InteroperableSmallBlockEntity} before PG's next solve, on the main thread. */
    public double getLastVoltage() {
        return lastVoltage;
    }

    /** Called from {@link InteroperableSmallBlockEntity}'s tickAudio() when CEE is the delivery side — see lastCurrent's doc. */
    public double getLastCurrent() {
        return lastCurrent;
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        super.preTick(bridges);
        // Sink (PG is source): inject PG's last reading, low resistance so
        // little voltage is wasted driving a real load. Source (PG is
        // sink): pin EMF to 0 and use SENSE_RESISTANCE so this side reads
        // its own circuit like a voltmeter instead of loading it down.
        // Role-by-role (same split as before the transferEnabled gate was added):
        //  - PG is source  -> CEE is the SINK: inject PG's reading as EMF, and
        //    present the small DELIVERY resistance so little voltage is wasted.
        //  - PG is sink     -> CEE is the SOURCE: EMF 0, and sourceResistance —
        //    which is SENSE_RESISTANCE (pure voltmeter) unless the Double Coupler
        //    has driven it down to V_sensed/I_delivered so the CEE source grid
        //    feels the reflected sink-side load. Using DELIVERY here was the
        //    cause of "12V battery + 1ohm -> other side reads 0.55V".
        //  - transferEnabled == false (needle mid-swing / mode OFF): fully passive
        //    either way -> 0 EMF + SENSE_RESISTANCE, nothing crosses.
        double emf = (transferEnabled && pgIsSource) ? powerGridVoltage : 0.0;
        float resistance = !transferEnabled ? SENSE_RESISTANCE
                : pgIsSource ? DELIVERY_RESISTANCE
                : sourceResistance;
        bridges.builder(pos)
                .voltageSourceWithResistance(0, 1, resistance, emf);
    }

    @Override
    public void postTick(SimulationResults results) {
        super.postTick(results);
        lastVoltage = results.getVoltageAt(pos, 0, 1);
        lastCurrent = Math.abs(results.getCurrentThrough(pos, 0, 1));
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
