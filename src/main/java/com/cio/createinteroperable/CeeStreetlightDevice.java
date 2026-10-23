package com.cio.createinteroperable;

import com.george_vi.electroenergetics.CEESoundEvents;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.SendSparkPacket;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Electro Energetics side of the Streetlight: a 120 V / 25 W lamp between the
 * left pin (node 0) and the right pin (node 1).
 *
 * <ul>
 *   <li>While its photocell/mode switch is closed (see
 *   {@link CeeStreetlightBlockEntity#isSwitchedOn()}), the lamp is a
 *   {@link #LAMP_RESISTANCE} resistor; otherwise it is a 1 M&Omega; sense tap,
 *   which keeps the goggle voltage readout alive while drawing nothing.</li>
 *   <li>Below {@link #STRIKE_VOLTAGE} it is dark. From there up to
 *   {@link #RATED_VOLTAGE} it browns out: lit at 50&ndash;100% output with the
 *   {@code streetlight_brown} texture. From {@link #RATED_VOLTAGE} to {@link #MAX_VOLTAGE} it
 *   is fully on with the full-bright texture. A new state must hold for
 *   {@link #DEBOUNCE_TICKS} ticks before it shows, so a supply hovering on a
 *   threshold can't make it flicker every tick.</li>
 *   <li>Above {@link #MAX_VOLTAGE} while energised, the bulb burns out at once
 *   (CEE's own {@code short_arc} sound plus a medium spark) and stays
 *   open-circuit until it is replaced with a CEE Bulb (see {@link CeeStreetlightBlock}).</li>
 * </ul>
 *
 * <p>{@code getVoltageAt(pos, 0, 1)} is already the RMS over CEE's micro-ticks
 * (verified in the 1.1.1 jar), so AC supplies are measured correctly.</p>
 */
public class CeeStreetlightDevice extends SimpleElectricalDevice {
    public static final double RATED_VOLTAGE = 120.0;
    public static final double STRIKE_VOLTAGE = 100.0;
    public static final double MAX_VOLTAGE = 330.0;
    public static final double RATED_POWER = 25.0;
    /** 120&sup2; / 25 = 576 &Omega;. */
    public static final double LAMP_RESISTANCE = RATED_VOLTAGE * RATED_VOLTAGE / RATED_POWER;
    private static final double SENSE_RESISTANCE = 1.0E6;
    private static final int DEBOUNCE_TICKS = 10;

    public boolean broken;
    /** Last switch state pushed by the block entity; kept so the lamp behaves while its chunk is unloaded. */
    public boolean switchedOn;
    public double voltage;
    public double power;
    private CeeStreetlightBlock.Bulb shown = CeeStreetlightBlock.Bulb.OFF;
    private CeeStreetlightBlock.Bulb pending = CeeStreetlightBlock.Bulb.OFF;
    private int pendingTicks;
    private CeeStreetlightBlockEntity be;

    public CeeStreetlightDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
        super(level, pos, deviceSD, type);
    }

    private boolean energised() {
        return switchedOn && !broken;
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        CeeStreetlightBlockEntity found = blockEntity();
        if (found != null) {
            switchedOn = found.isSwitchedOn();
        }
        bridges.builder(pos).resistor(0, 1, energised() ? LAMP_RESISTANCE : SENSE_RESISTANCE);
    }

    @Override
    public void postTick(SimulationResults results) {
        voltage = Math.abs(results.getVoltageAt(pos, 0, 1));
        power = energised() ? voltage * voltage / LAMP_RESISTANCE : 0.0;

        if (broken) {
            show(CeeStreetlightBlock.Bulb.BROKEN, 0.0);
            return;
        }
        if (energised() && voltage > MAX_VOLTAGE) {
            broken = true;
            power = 0.0;
            if (level.isLoaded(pos)) {
                burnOutEffects();
            }
            show(CeeStreetlightBlock.Bulb.BROKEN, 0.0);
            return;
        }

        CeeStreetlightBlock.Bulb target = targetBulb();
        if (target == pending) {
            pendingTicks++;
        } else {
            pending = target;
            pendingTicks = 0;
        }
        if (pending != shown && pendingTicks >= DEBOUNCE_TICKS) {
            shown = pending;
        }
        show(shown, shown.lit() ? effectiveness(voltage) : 0.0);
    }

    /** A fresh bulb (see {@link CeeStreetlightBlock#useItemOn}): back to dark, re-strikes normally. */
    public void repair() {
        broken = false;
        shown = pending = CeeStreetlightBlock.Bulb.OFF;
        pendingTicks = 0;
    }

    private CeeStreetlightBlock.Bulb targetBulb() {
        if (!switchedOn || voltage < STRIKE_VOLTAGE) {
            return CeeStreetlightBlock.Bulb.OFF;
        }
        if (voltage >= RATED_VOLTAGE) {
            return CeeStreetlightBlock.Bulb.ON;
        }
        return CeeStreetlightBlock.Bulb.BROWN;
    }

    /** At the bulb: CEE's one-shot arcing sound (what its switches play) and a medium spark (arc hum, smoke, sparks). */
    private void burnOutEffects() {
        BlockState state = level.getBlockState(pos);
        Vec3 at = state.getBlock() instanceof CeeStreetlightBlock
                ? CeeStreetlightBlock.orient(state, CeeStreetlightBlock.BULB_CENTER).add(Vec3.atLowerCornerOf(pos))
                : Vec3.atCenterOf(pos);
        level.playSound(null, at.x, at.y, at.z, CEESoundEvents.SHORT_ARC.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        CatnipServices.NETWORK.sendToClientsAround((ServerLevel) level, at, 40.0,
                new SendSparkPacket(at, SendSparkPacket.SparkSize.MEDIUM));
    }

    /** 50% at the 100 V strike, rising linearly to 100% at the rated 120 V. */
    static double effectiveness(double voltage) {
        double t = (voltage - STRIKE_VOLTAGE) / (RATED_VOLTAGE - STRIKE_VOLTAGE);
        return 0.5 + 0.5 * Math.max(0.0, Math.min(1.0, t));
    }

    private void show(CeeStreetlightBlock.Bulb bulb, double effectiveness) {
        CeeStreetlightBlockEntity target = blockEntity();
        if (target != null) {
            target.applyOutput(bulb, effectiveness, voltage, power);
        }
    }

    private CeeStreetlightBlockEntity blockEntity() {
        if (be != null && be.isRemoved()) {
            be = null;
        }
        if (be == null && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof CeeStreetlightBlockEntity found) {
            be = found;
        }
        return be;
    }

    @Override
    public void read(CompoundTag tag) {
        broken = tag.getBoolean("Broken");
        switchedOn = tag.getBoolean("SwitchedOn");
        if (broken) {
            shown = pending = CeeStreetlightBlock.Bulb.BROKEN;
        }
    }

    @Override
    public void write(CompoundTag tag) {
        tag.putBoolean("Broken", broken);
        tag.putBoolean("SwitchedOn", switchedOn);
    }
}
