package com.cio.createinteroperable.deb;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The Electro Energetics twin of {@link RedstoneSwitchBlockEntity}. Same
 * effective-state resolution, throughput metering and overload escalation, but
 * it extends Create's {@link SmartBlockEntity} directly (no Power Grid class in
 * its hierarchy), drives its two poles through {@link RedstoneSwitchCeeDevice},
 * and runs the small hand-rolled thermal tracker ported from
 * {@link CeeDebRectifierBlockEntity} instead of Power Grid's fan-coolable
 * {@code ThermalBehaviour}.
 */
public class CeeRedstoneSwitchBlockEntity extends SmartBlockEntity
        implements IHaveGoggleInformation, RedstoneSwitchDisplay {

    private static final float THERMAL_MASS = 40.0f;
    private static final float STANDARD_TEMPERATURE = 22.0f;
    private static final int OVERHEAT_TICKS = 2;

    private final RedstoneSwitchState switchState = new RedstoneSwitchState();
    protected RedstoneSwitchCeeDevice ceeDevice;

    // --- synced working state ---
    private float throughputWatts;
    private float acrossVolts;
    private boolean faulted;
    private int faultTicks;
    private boolean exploded;
    private float temperatureC;

    // --- hand-rolled thermal tracker ---
    private float temperature;
    private float prevTemperature;
    private float ambientTemp;
    private boolean thermalFirstTick = true;
    private int overheatTicks;

    // --- client-smoothed contact slide ---
    private float slide;
    private float slidePrev;

    public CeeRedstoneSwitchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    void manualToggle() {
        switchState.toggle();
        applyEffectiveState();
        setChanged();
    }

    private void applyEffectiveState() {
        if (level == null) {
            return;
        }
        boolean eff = switchState.effective();
        BlockState state = getBlockState();
        if (state.hasProperty(CeeRedstoneSwitchBlock.POWERED) && state.getValue(CeeRedstoneSwitchBlock.POWERED) != eff) {
            level.setBlock(worldPosition, state.setValue(CeeRedstoneSwitchBlock.POWERED, eff), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void tick() {
        if (level != null && !level.isClientSide) {
            serverTick();
        }
        super.tick();
        if (level != null && level.isClientSide) {
            tickAudio();
        }
        slidePrev = slide;
        float target = getBlockState().hasProperty(CeeRedstoneSwitchBlock.POWERED)
                && getBlockState().getValue(CeeRedstoneSwitchBlock.POWERED) ? 1f : 0f;
        slide += (target - slide) * 0.35f;
        if (Math.abs(target - slide) < 0.001f) {
            slide = target;
        }
    }

    private void serverTick() {
        if (exploded || !(level instanceof ServerLevel server)) {
            return;
        }

        if (switchState.pollRedstone(server.hasNeighborSignal(worldPosition))) {
            applyEffectiveState();
            setChanged();
        }
        boolean closed = switchState.effective();

        if (ceeDevice == null || !ceeDevice.isValid()) {
            ceeDevice = DevicesSavedData.load(server).getDevice(worldPosition, RedstoneSwitchCeeDevice.class);
        }
        double iPlus = 0.0;
        double iMinus = 0.0;
        if (ceeDevice != null) {
            ceeDevice.setClosed(closed);
            acrossVolts = (float) Math.max(ceeDevice.getInVolts(), ceeDevice.getOutVolts());
            iPlus = finite(ceeDevice.getPlusCurrent());
            iMinus = finite(ceeDevice.getMinusCurrent());
        } else {
            acrossVolts = 0f;
        }
        double watts = closed
                ? Mth.clamp(acrossVolts * Math.max(iPlus, iMinus), 0.0, RedstoneSwitchStats.MAX_PLAUSIBLE_WATTS)
                : 0.0;
        throughputWatts = (float) watts;

        boolean grossOverload = closed && watts > RedstoneSwitchStats.HARD_WATTS;
        boolean nowFaulted = closed && watts > RedstoneSwitchStats.RATED_WATTS;
        if (nowFaulted != faulted) {
            faulted = nowFaulted;
            setChanged();
        }
        if (grossOverload) {
            explode(server);
            return;
        }
        if (faulted) {
            if (++faultTicks >= RedstoneSwitchStats.FAULT_GRACE_TICKS) {
                explode(server);
                return;
            }
        } else if (faultTicks > 0) {
            faultTicks = 0;
        }

        double heat = RedstoneSwitchStats.CONTACT_RESISTANCE * (iPlus * iPlus + iMinus * iMinus);
        tickThermal(server, Double.isFinite(heat) ? heat : 0.0);

        if ((server.getGameTime() & 7L) == 0L) {
            notifyUpdate();
        }
    }

    private static double finite(double v) {
        return Double.isFinite(v) ? v : 0.0;
    }

    private double dissipationFactor() {
        return RedstoneSwitchStats.THERMAL_MAX_POWER_WATTS
                / (RedstoneSwitchStats.OVERHEAT_CELSIUS - 25.0 - STANDARD_TEMPERATURE);
    }

    private void tickThermal(ServerLevel server, double heatWatts) {
        if (thermalFirstTick) {
            thermalFirstTick = false;
            ambientTemp = 13.65f * server.getBiome(worldPosition).value().getBaseTemperature() + 7.1f;
            temperature = ambientTemp;
            prevTemperature = ambientTemp;
            temperatureC = temperature;
            return;
        }
        float dissipated = (float) (dissipationFactor() * (temperature - ambientTemp));
        temperature -= dissipated / 20f / THERMAL_MASS;
        if (dissipated > 0 && temperature < ambientTemp) {
            temperature = ambientTemp;
        }
        if (Double.isFinite(heatWatts)) {
            temperature += (float) (heatWatts / 20.0 / THERMAL_MASS);
        }
        if (!Float.isFinite(temperature)) {
            temperature = ambientTemp;
        }
        float delta = temperature - prevTemperature;
        prevTemperature = temperature;
        temperatureC = temperature;

        if (temperature >= RedstoneSwitchStats.OVERHEAT_CELSIUS) {
            if (delta > 0 && ++overheatTicks >= OVERHEAT_TICKS) {
                if (!exploded) {
                    explode(server);
                }
            } else if (delta <= 0) {
                overheatTicks = 0;
            }
        } else {
            overheatTicks = 0;
        }
    }

    private void explode(ServerLevel server) {
        exploded = true;
        server.explode(null, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
                2.5f, Level.ExplosionInteraction.BLOCK);
        server.destroyBlock(worldPosition, false);
    }

    public void tickAudio() {
        if (level == null || !level.isClientSide) {
            return;
        }
        float load = throughputWatts / (float) RedstoneSwitchStats.RATED_WATTS;
        if (!faulted && load < RedstoneSwitchStats.HAZE_FRACTION) {
            return;
        }
        float intensity = faulted
                ? 0.5f + 0.5f * faultProgress()
                : Mth.clamp((load - RedstoneSwitchStats.HAZE_FRACTION) / (1f - RedstoneSwitchStats.HAZE_FRACTION), 0f, 1f) * 0.3f;
        if (level.random.nextFloat() < intensity) {
            level.addParticle(faulted ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE,
                    worldPosition.getX() + 0.3 + level.random.nextDouble() * 0.4,
                    worldPosition.getY() + 0.55 + level.random.nextDouble() * 0.3,
                    worldPosition.getZ() + 0.3 + level.random.nextDouble() * 0.4,
                    0.0, 0.02 + 0.04 * intensity, 0.0);
        }
    }

    private float faultProgress() {
        return Mth.clamp(faultTicks / (float) RedstoneSwitchStats.FAULT_GRACE_TICKS, 0f, 1f);
    }

    // --- RedstoneSwitchDisplay ---

    @Override
    public float switchSlide() {
        return slide;
    }

    @Override
    public float switchSlidePrev() {
        return slidePrev;
    }

    @Override
    public float switchThroughputWatts() {
        return throughputWatts;
    }

    @Override
    public float switchAcrossVolts() {
        return acrossVolts;
    }

    @Override
    public boolean switchFaulted() {
        return faulted;
    }

    @Override
    public boolean switchHasReadout() {
        return acrossVolts >= 1f;
    }

    // --- goggles ---

    private static final String GK = "createinteroperable.goggle.redstone_switch.";

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        RedstoneSwitchGoggles.append(tooltip, GK,
                getBlockState().getBlock().getName(),
                getBlockState().hasProperty(CeeRedstoneSwitchBlock.POWERED) && getBlockState().getValue(CeeRedstoneSwitchBlock.POWERED),
                switchState.override != 0,
                throughputWatts, acrossVolts, faulted, faultTicks, faultProgress(), temperatureC);
        return true;
    }

    // --- NBT ---

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        switchState.load(tag);
        throughputWatts = tag.getFloat("ThroughputWatts");
        acrossVolts = tag.getFloat("AcrossVolts");
        faulted = tag.getBoolean("Faulted");
        faultTicks = tag.getInt("FaultTicks");
        temperatureC = tag.getFloat("TemperatureC");
        exploded = tag.getBoolean("Exploded");
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        switchState.save(tag);
        tag.putFloat("ThroughputWatts", throughputWatts);
        tag.putFloat("AcrossVolts", acrossVolts);
        tag.putBoolean("Faulted", faulted);
        tag.putInt("FaultTicks", faultTicks);
        tag.putFloat("TemperatureC", temperatureC);
        tag.putBoolean("Exploded", exploded);
    }
}
