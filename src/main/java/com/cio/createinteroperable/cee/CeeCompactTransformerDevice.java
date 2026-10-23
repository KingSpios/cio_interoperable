package com.cio.createinteroperable.cee;

import com.george_vi.electroenergetics.content.transmission_distribution.transformer.TransformerBehaviour;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.ElectricalDevice;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/**
 * Electro Energetics side of the Compact Transformer. A line-for-line port of
 * CEE's own {@code TransformerDevice} (1.1.1, read from the jar): the same
 * {@link TransformerBehaviour} coupled-winding bridge (primary = nodes 0/1,
 * secondary = nodes 2/3, ratio = primary:secondary), the same RMS averaging and
 * the same {@link ElectricalDevice#updateTemp} heat model &mdash; only the power
 * rating differs.
 *
 * <p>CEE's Transformer is rated 25 kW: it feeds {@code min(70000, |P|) / 10}
 * into updateTemp, whose steady state for a heat {@code h} is
 * {@code 1000 * ((h + 30) / 33.3 - 1)}, so it smokes above 62000 (~20.7 kW)
 * and burns above 76000 (~25.3 kW). Scaling the throughput fed into that same
 * curve by 25/15 moves both thresholds to ~12.4 kW (smoke) and ~15.2 kW (burns)
 * without touching CEE's own temperature constants.</p>
 */
public class CeeCompactTransformerDevice extends SimpleElectricalDevice {
    /** Nameplate rating (watts), shown on the item tooltip. */
    public static final double MAX_POWER = 15000.0;
    /** CEE's own Transformer rating, which its heat curve is tuned to. */
    private static final double CEE_TRANSFORMER_MAX_POWER = 25000.0;

    public TransformerBehaviour.TransformerBehaviourDataHolder transformerData;
    public float temp;
    public double ratio;
    private CeeCompactTransformerBlockEntity be;

    public CeeCompactTransformerDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
        super(level, pos, deviceSD, type);
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        double ratio = this.ratio == 0.0 ? 1.0 : this.ratio;
        TransformerBehaviour.preTick(TransformerBehaviour.setupStandardNodes(pos), ratio, pos, bridges, transformerData);
    }

    @Override
    public void postTick(SimulationResults results) {
        double power = TransformerBehaviour.postTick(TransformerBehaviour.setupStandardNodes(pos), results, transformerData);
        double heatPower = Math.abs(power) * (CEE_TRANSFORMER_MAX_POWER / MAX_POWER);
        temp = ElectricalDevice.updateTemp(temp, (float) Math.min(70000.0, heatPower) / 10.0F);

        if (be == null && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof CeeCompactTransformerBlockEntity found) {
            be = found;
        }
        if (be != null) {
            if (be.isRemoved()) {
                be = null;
            } else {
                be.setReadings(Math.abs(power), transformerData.lastPrimaryVoltage, transformerData.lastSecondaryVoltage);
            }
        }

        // Same smoke (62000) / burn-to-fire (76000) thresholds as CEE's Transformer.
        ElectricalDevice.handleTemp(level, pos, deviceSD, temp, 62000.0F, 76000.0F);
    }

    @Override
    public void read(CompoundTag tag) {
        temp = tag.getFloat("Temp");
        ratio = tag.getDouble("Ratio");
        transformerData = new TransformerBehaviour.TransformerBehaviourDataHolder(tag.getCompound("TransformerData"));
    }

    @Override
    public void write(CompoundTag tag) {
        tag.putFloat("Temp", temp);
        tag.putDouble("Ratio", ratio);
        tag.put("TransformerData", transformerData.write());
    }
}
