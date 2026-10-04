package com.cio.createinteroperable.deb;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
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
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.base.ThermalBehaviour;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.utility.sound.SoundScapes;

import java.util.List;

/**
 * The functional half of {@link RedstoneSwitchBlock}. Four Power Grid
 * terminals ({@code 0/1} input +/&minus;, {@code 2/3} output +/&minus;) bridged
 * by two independent {@link SwitchedWire}s that open and close together with
 * the effective ON/OFF state (redstone, or the manual override &mdash; see
 * {@link RedstoneSwitchState}).
 *
 * <p>Each tick it meters the power crossing the closed contacts, runs the same
 * soft-cap / hard-cap / fault-fuse escalation an overloaded Domestic Power Kit
 * does, and feeds the contacts' I&sup2;R loss to a real {@link ThermalBehaviour}
 * (fan-coolable, detonates past its overheat point). See
 * {@link RedstoneSwitchStats} for every number.</p>
 */
public class RedstoneSwitchBlockEntity extends ElectricBlockEntity
        implements IHaveGoggleInformation, RedstoneSwitchDisplay {

    private final RedstoneSwitchState switchState = new RedstoneSwitchState();

    private IElectricNode inPlus, inMinus, outPlus, outMinus;
    private SwitchedWire polePlus, poleMinus;

    // --- synced working state ---
    private float throughputWatts;
    private float acrossVolts;
    private boolean faulted;
    private int faultTicks;
    private boolean exploded;
    private float temperatureC;

    // --- client-smoothed contact slide (0 = OFF/up, 1 = ON/dropped) ---
    private float slide;
    private float slidePrev;

    public RedstoneSwitchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Bare right-click on the block: flip the manual override. */
    void manualToggle() {
        switchState.toggle();
        applyEffectiveState();
        setChanged();
    }

    /** Keep the {@code POWERED} blockstate (what the renderer reads) in step with the resolved ON/OFF state. */
    private void applyEffectiveState() {
        if (level == null) {
            return;
        }
        boolean eff = switchState.effective();
        BlockState state = getBlockState();
        if (state.hasProperty(RedstoneSwitchBlock.POWERED) && state.getValue(RedstoneSwitchBlock.POWERED) != eff) {
            level.setBlock(worldPosition, state.setValue(RedstoneSwitchBlock.POWERED, eff), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public ThermalBehaviour specifyThermalBehaviour() {
        return ThermalBehaviour
                .forMaxPower(this, (float) RedstoneSwitchStats.OVERHEAT_CELSIUS,
                        (float) RedstoneSwitchStats.THERMAL_MAX_POWER_WATTS)
                .overheatCallback(this::onOverheat);
    }

    private void onOverheat() {
        if (level instanceof ServerLevel server && !exploded) {
            explode(server);
        }
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        builder.setTerminalCount(4);
        inPlus = builder.terminalNode(0);
        inMinus = builder.terminalNode(1);
        outPlus = builder.terminalNode(2);
        outMinus = builder.terminalNode(3);
        // Two independent poles — no internal link between the + and - sides,
        // so one rail can be routed through on its own.
        polePlus = builder.connectSwitch((float) RedstoneSwitchStats.CONTACT_RESISTANCE, inPlus, outPlus, false);
        poleMinus = builder.connectSwitch((float) RedstoneSwitchStats.CONTACT_RESISTANCE, inMinus, outMinus, false);
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    @Override
    public void electricalTick() {
        super.electricalTick();
        applyPower(null);
        if (exploded || !(level instanceof ServerLevel server)) {
            return;
        }

        // Poll redstone + release the manual latch on any edge (same polling
        // idiom the Telephone uses for its wire lock).
        if (switchState.pollRedstone(server.hasNeighborSignal(worldPosition))) {
            applyEffectiveState();
            setChanged();
        }

        boolean closed = switchState.effective();
        if (polePlus != null) {
            polePlus.setState(closed);
        }
        if (poleMinus != null) {
            poleMinus.setState(closed);
        }

        double vIn = finite(Math.abs(inPlus.getVoltage() - inMinus.getVoltage()));
        double vOut = finite(Math.abs(outPlus.getVoltage() - outMinus.getVoltage()));
        acrossVolts = (float) Math.max(vIn, vOut);

        double iPlus = polePlus != null ? finite(Math.abs(polePlus.current())) : 0.0;
        double iMinus = poleMinus != null ? finite(Math.abs(poleMinus.current())) : 0.0;
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

        if (thermalBehaviour != null) {
            double heat = RedstoneSwitchStats.CONTACT_RESISTANCE * (iPlus * iPlus + iMinus * iMinus);
            thermalBehaviour.applyTickPower(Double.isFinite(heat) ? heat : 0.0);
            temperatureC = thermalBehaviour.getTemperature();
        }

        if ((server.getGameTime() & 7L) == 0L) {
            notifyUpdate();
        }
    }

    private static double finite(double v) {
        return Double.isFinite(v) ? v : 0.0;
    }

    private void explode(ServerLevel server) {
        exploded = true;
        server.explode(null, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
                2.5f, Level.ExplosionInteraction.BLOCK);
        server.destroyBlock(worldPosition, false);
    }

    @Override
    public void tick() {
        super.tick();
        slidePrev = slide;
        float target = getBlockState().hasProperty(RedstoneSwitchBlock.POWERED)
                && getBlockState().getValue(RedstoneSwitchBlock.POWERED) ? 1f : 0f;
        slide += (target - slide) * 0.35f;
        if (Math.abs(target - slide) < 0.001f) {
            slide = target;
        }
    }

    /** Light transformer-style hum + haze once the switch runs hot or its overload fuse is burning. */
    @Override
    public void tickAudio() {
        if (level == null || !level.isClientSide) {
            return;
        }
        float load = throughputWatts / (float) RedstoneSwitchStats.RATED_WATTS;
        float hum = 0f;
        if (faulted) {
            hum = 0.25f + 0.75f * faultProgress();
        } else if (load >= RedstoneSwitchStats.HAZE_FRACTION) {
            hum = Mth.clamp((load - RedstoneSwitchStats.HAZE_FRACTION) / (1f - RedstoneSwitchStats.HAZE_FRACTION), 0f, 1f) * 0.2f;
        }
        if (hum > 0f) {
            SoundScapes.play(SoundScapes.AmbienceGroup.HUM, worldPosition, 1f, hum);
        }
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
                getBlockState().hasProperty(RedstoneSwitchBlock.POWERED) && getBlockState().getValue(RedstoneSwitchBlock.POWERED),
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
