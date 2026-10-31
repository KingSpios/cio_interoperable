package com.cio.createinteroperable.streetlight.cee;

import com.cio.createinteroperable.streetlight.Streetlight;
import com.cio.createinteroperable.streetlight.StreetlightLamp;
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
 * Electro Energetics side of the Streetlight: the shared 120 V / 25 W
 * {@link StreetlightLamp} between the left pin (node 0) and the right pin
 * (node 1). Energised, the lamp is a {@link Streetlight#LAMP_RESISTANCE}
 * resistor; otherwise a 1 M&Omega; sense tap, which keeps the goggle voltage
 * readout alive while drawing nothing. A bulb burning out plays CEE's own
 * {@code short_arc} sound plus a medium spark.
 *
 * <p>{@code getVoltageAt(pos, 0, 1)} is already the RMS over CEE's micro-ticks
 * (verified in the 1.1.1 jar), so AC supplies are measured correctly.</p>
 */
public class CeeStreetlightDevice extends SimpleElectricalDevice {
    private static final double SENSE_RESISTANCE = 1.0E6;

    private final StreetlightLamp lamp = new StreetlightLamp();
    private CeeStreetlightBlockEntity be;

    public CeeStreetlightDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
        super(level, pos, deviceSD, type);
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        CeeStreetlightBlockEntity found = blockEntity();
        if (found != null) {
            lamp.setSwitchedOn(found.isSwitchedOn());
        }
        bridges.builder(pos).resistor(0, 1, lamp.energised() ? Streetlight.LAMP_RESISTANCE : SENSE_RESISTANCE);
    }

    @Override
    public void postTick(SimulationResults results) {
        double voltage = Math.abs(results.getVoltageAt(pos, 0, 1));
        double power = lamp.power(voltage);
        if (lamp.update(voltage) && level.isLoaded(pos)) {
            burnOutEffects();
        }
        CeeStreetlightBlockEntity target = blockEntity();
        if (target != null) {
            target.applyOutput(lamp.bulb(), lamp.effectiveness(voltage), voltage, lamp.broken() ? 0.0 : power);
        }
    }

    /** A fresh bulb (see {@link CeeStreetlightBlock#useItemOn}): back to dark, re-strikes normally. */
    public void repair() {
        lamp.repair();
    }

    /** At the bulb: CEE's one-shot arcing sound (what its switches play) and a medium spark (arc hum, smoke, sparks). */
    private void burnOutEffects() {
        BlockState state = level.getBlockState(pos);
        Vec3 at = state.hasProperty(Streetlight.STYLE)
                ? Streetlight.orient(state, Streetlight.BULB_CENTER).add(Vec3.atLowerCornerOf(pos))
                : Vec3.atCenterOf(pos);
        level.playSound(null, at.x, at.y, at.z, CEESoundEvents.SHORT_ARC.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        CatnipServices.NETWORK.sendToClientsAround((ServerLevel) level, at, 40.0,
                new SendSparkPacket(at, SendSparkPacket.SparkSize.MEDIUM));
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
        lamp.read(tag);
    }

    @Override
    public void write(CompoundTag tag) {
        lamp.write(tag);
    }
}
