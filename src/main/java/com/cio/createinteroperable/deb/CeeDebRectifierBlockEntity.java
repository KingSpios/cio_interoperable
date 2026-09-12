package com.cio.createinteroperable.deb;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The CEE-only twin of {@link DebRectifierBlockEntity}. Same pools / fault
 * detection / goggle readouts / appliance-grid link, but this family has NO
 * Power Grid class anywhere in its hierarchy (extends Create's own
 * {@link SmartBlockEntity} directly, not PG's {@code ElectricBlockEntity}) —
 * so a CEE-only install never touches Power Grid at all when using a
 * CEE-wired Power Kit.
 *
 * <p>Two real Power Grid pieces the old shared class leaned on could not be
 * reused here at all, since merely referencing them would force PG's classes
 * to load: PG's {@code ThermalBehaviour} (replaced with the small hand-rolled
 * tracker below — overheating still works, just without PG's fan-cooling
 * hook) and PG's {@code SoundScapes} looping-hum ambience system (dropped
 * entirely for this variant — smoke/haze particles still play, there's just
 * no transformer hum). Everything else (pools, caps, thermal-explode,
 * goggles, viewer specs, the appliance-grid link) is a straight port.</p>
 */
public class CeeDebRectifierBlockEntity extends SmartBlockEntity implements ApplianceSource, IHaveGoggleInformation {

    /** Resistance (&Omega;) that stands in for "no load". Also the clamp ceiling on a computed load. */
    static final double R_OPEN = 1.0e7;

    public static final float HAZE_FRACTION = 0.85f;
    public static final int FAULT_GRACE_TICKS = 200;

    static final float FEED_PASSTHROUGH_R = 0.001f;
    private static final double PRIMARY_VOLTS = 120.0;
    static final float STEP_DOWN_EFFICIENCY = 0.90f;

    /** Hand-rolled thermal tracker's thermal mass — controls heating/cooling speed only, not the equilibrium point. */
    private static final float THERMAL_MASS = 40.0f;
    /** Reference temperature (&deg;C) the dissipation factor is derived against, mirroring PG's own STANDARD_TEMPERATURE. */
    private static final float STANDARD_TEMPERATURE = 22.0f;
    private static final int OVERHEAT_TICKS = 2;

    protected DebTier tier() {
        return DebTier.TIER_2;
    }

    public boolean usesNeedleGauge() {
        return false;
    }

    public boolean usesTier3Viewers() {
        return false;
    }

    // --- appliance-grid bookkeeping ------------------------------------
    private boolean nodePowered;
    private final java.util.Set<com.cio.createinteroperable.grid.GridConnection> applianceConnections = new java.util.HashSet<>();
    /** True while the backend's last scan found another board sharing this network. See {@link ApplianceSource#setNetworkConflict}. */
    private boolean networkConflict;

    // --- CEE grid port ---------------------------------------------------
    /** This kit's Electro Energetics device; {@code null} until first looked up. */
    protected DebCeeDevice ceeDevice;

    // --- per-tick working state, indexed by Pool.ordinal() --------------
    protected final double[] linkedWatts = new double[Pool.values().length];
    protected final int[] linkedCount = new int[Pool.values().length];
    protected final float[] externalWatts = new float[Pool.values().length];
    protected final float[] busVolts = new float[Pool.values().length];
    protected final boolean[] railFaulted = new boolean[Pool.values().length];
    protected final boolean[] railLive = new boolean[Pool.values().length];
    protected int faultTicks;
    protected boolean exploded;
    protected float temperatureC;

    // --- hand-rolled thermal tracker -----------------------------------
    private float temperature;
    private float prevTemperature;
    private float ambientTemp;
    private boolean thermalFirstTick = true;
    private int overheatTicks;

    // --- tier-1 needle gauge, client-smoothed --------------------------
    public float gauge;
    public float gaugePrev;

    public CeeDebRectifierBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void tick() {
        if (level != null && (!level.isClientSide || isVirtual())) {
            serverTick();
        }
        super.tick();
        if (level != null && level.isClientSide) {
            tickAudio();
        }
        gaugePrev = gauge;
        gauge += (getWorstLoad() - gauge) * 0.15f;
    }

    /** 12 V feed step-down ratio against the current (mode-aware) primary voltage. */
    protected double stepDownRatio() {
        return Pool.LV.nominalVoltage / primaryVolts();
    }

    /** Regulated 12 V feed EMF: a fixed fraction of whatever the intake solved to. */
    protected double stepDownEmf() {
        return Math.max(0.0, intakeVolts() * stepDownRatio());
    }

    /** Solved voltage across the CEE intake node pair. */
    protected double intakeVolts() {
        return ceeDevice != null ? ceeDevice.getIntakeVolts() : 0.0;
    }

    /** Solved voltage across the 12 V feed's CEE node pair (sags below EMF under load). */
    protected double lvFeedTerminalVolts() {
        return ceeDevice != null ? ceeDevice.getLvVolts() : 0.0;
    }

    /** Amps the 12 V feed is delivering. */
    protected double lvFeedCurrent() {
        return ceeDevice != null ? ceeDevice.getLvCurrent() : 0.0;
    }

    // --- tier hooks ----------------------------------------------------

    protected double primaryVolts() {
        return PRIMARY_VOLTS;
    }

    protected double primaryInternalResistance() {
        return tier().primaryInternalResistance();
    }

    protected double mvRailVolts() {
        return intakeVolts();
    }

    protected double lvRailVolts() {
        double emf = intakeVolts() * stepDownRatio();
        double sag = lvFeedCurrent() * tier().stepDownResistance();
        return Math.max(0.0, emf - sag);
    }

    protected double intakeDisplayVolts() {
        return busVolts[Pool.MV.ordinal()];
    }

    protected double softCap(Pool pool) {
        return tier().softCapWatts(pool);
    }

    protected double hardCap(Pool pool) {
        return tier().hardCapWatts(pool);
    }

    protected double mvLinkedReflectionFactor() {
        return 1.0;
    }

    protected double mvStepDownWatts() {
        return 0.0;
    }

    protected double reflectedIdealFeedWatts() {
        double lvReflected = linkedWatts[Pool.LV.ordinal()] + externalWatts[Pool.LV.ordinal()];
        return lvReflected / STEP_DOWN_EFFICIENCY;
    }

    /** Tiers 1-2 layout: an optional 120 V pass-through strap (tier 2 only) + the 12 V regulated feed. */
    protected void meterFeeds() {
        double passCurrent = ceeDevice != null ? ceeDevice.getPassCurrent() : 0.0;
        externalWatts[Pool.MV.ordinal()] = (float) (intakeVolts() * passCurrent);
        externalWatts[Pool.LV.ordinal()] = (float) (lvFeedTerminalVolts() * lvFeedCurrent());
    }

    // ---------------------------------------------------- CEE grid port

    /** Look up (once) this kit's {@link DebCeeDevice} and push it this tick's load + feed set. */
    private void syncCeeDevice(ServerLevel server) {
        if (ceeDevice == null || !ceeDevice.isValid()) {
            ceeDevice = DevicesSavedData.load(server).getDevice(worldPosition, DebCeeDevice.class);
        }
        if (ceeDevice == null) {
            return;
        }
        ceeDevice.configure(primaryResistance());
        configureCeeFeeds(ceeDevice);
    }

    /**
     * Describe this tier's feed topology to the {@link DebCeeDevice}. Base
     * layout (tiers 1-2): {@code [0,1]} intake, an optional {@code [2,3]}
     * 120 V pass-through strap (tier 2 only), then the 12 V regulated feed
     * pair. {@link CeePowerKitTier3BlockEntity} overrides for the substation
     * layout.
     */
    protected void configureCeeFeeds(DebCeeDevice device) {
        boolean mv = tier().hasMediumVoltageFeed();
        int lvIdx = mv ? 4 : 2;
        device.setPassthrough(mv, 2, 3, FEED_PASSTHROUGH_R);
        device.setLvFeed(lvIdx, lvIdx + 1, stepDownEmf(), tier().stepDownResistance());
        device.setMv120Feed(false, 0, 0, 0.0, 0.0);
    }

    // --- thermal (hand-rolled, every tier) ------------------------------

    /** Steady dissipation whose equilibrium (against the fixed reference temperature) is 25 below the overheat point. */
    private double dissipationFactor() {
        return tier().thermalMaxPowerWatts() / (overheatCelsius() - 25.0 - STANDARD_TEMPERATURE);
    }

    /** This kit's own dissipation this tick (W): primary I^2R at the intake plus the 12 V step-down conversion loss. */
    protected double thermalHeatWatts() {
        double v = Math.max(1.0, primaryVolts());
        double intakeCurrent = primaryWatts() / v;
        double primaryLoss = primaryInternalResistance() * intakeCurrent * intakeCurrent;

        double lvSideWatts = linkedWatts[Pool.LV.ordinal()] + externalWatts[Pool.LV.ordinal()];
        double conversionLoss = (1.0 / STEP_DOWN_EFFICIENCY - 1.0) * lvSideWatts;

        return Math.max(0.0, primaryLoss + conversionLoss);
    }

    public float getTemperatureC() {
        return temperatureC;
    }

    public double overheatCelsius() {
        return tier().thermalOverheatCelsius();
    }

    /** Dissipate, apply this tick's heat, and detonate if stuck overheated and still climbing. */
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

        if (temperature >= overheatCelsius()) {
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

    protected void postElectricalTick(ServerLevel server) {
        tickThermal(server, thermalHeatWatts());
    }

    private void serverTick() {
        if (exploded || !(level instanceof ServerLevel server)) {
            return;
        }

        if (!com.cio.createinteroperable.grid.CrayfishCompat.present()) {
            distributeAppliancePower();
        }

        syncCeeDevice(server);
        meterFeeds();

        busVolts[Pool.MV.ordinal()] = (float) mvRailVolts();
        busVolts[Pool.LV.ordinal()] = (float) lvRailVolts();

        boolean anyLive = false;
        boolean anyFaulted = false;
        boolean grossOverload = false;
        for (Pool pool : Pool.values()) {
            int i = pool.ordinal();
            if (pool == Pool.MV && !tier().servesMediumVoltage()) {
                railFaulted[i] = false;
                railLive[i] = false;
                continue;
            }
            double total = totalWatts(pool);
            grossOverload |= total > hardCap(pool);
            railFaulted[i] = total > softCap(pool);
            railLive[i] = !railFaulted[i] && busVolts[i] >= tier().brownoutVolts(pool);
            anyLive |= railLive[i];
            anyFaulted |= railFaulted[i];
        }
        nodePowered = anyLive;

        if (grossOverload) {
            explode(server);
            return;
        }
        if (anyFaulted) {
            if (++faultTicks >= FAULT_GRACE_TICKS) {
                explode(server);
                return;
            }
        } else if (faultTicks > 0) {
            faultTicks = 0;
        }

        postElectricalTick(server);

        if ((level.getGameTime() & 7L) == 0L) {
            notifyUpdate();
        }
    }

    // ------------------------------------------------------ capacity readout

    public double totalWatts(Pool pool) {
        int i = pool.ordinal();
        return linkedWatts[i] + externalWatts[i];
    }

    public float getTotalUsageWatts() {
        return (float) (totalWatts(Pool.LV) + totalWatts(Pool.MV));
    }

    public boolean isPowered() {
        return busVolts[Pool.MV.ordinal()] >= 1f || busVolts[Pool.LV.ordinal()] >= 1f;
    }

    public float getRailLoad(Pool pool) {
        double soft = softCap(pool);
        return soft <= 0 ? 0f : (float) (totalWatts(pool) / soft);
    }

    public float getWorstLoad() {
        return Math.max(getRailLoad(Pool.LV), getRailLoad(Pool.MV));
    }

    public boolean isRailFaulted(Pool pool) {
        return railFaulted[pool.ordinal()];
    }

    public float getFaultProgress() {
        return Mth.clamp(faultTicks / (float) FAULT_GRACE_TICKS, 0f, 1f);
    }

    public float getBusVolts(Pool pool) {
        return busVolts[pool.ordinal()];
    }

    protected double primaryWatts() {
        return linkedWatts[Pool.MV.ordinal()] / mvLinkedReflectionFactor() + reflectedIdealFeedWatts();
    }

    protected double primaryResistance() {
        double watts = primaryWatts();
        double v = primaryVolts();
        double loadResistance = watts <= 0.0
                ? R_OPEN
                : (v * v) / watts;
        return primaryInternalResistance() + Math.min(loadResistance, R_OPEN);
    }

    protected void explode(ServerLevel server) {
        exploded = true;
        server.explode(null,
                worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
                3.0f, Level.ExplosionInteraction.BLOCK);
        server.destroyBlock(worldPosition, false);
    }

    // --------------------------------------------- ApplianceSource seam

    @Override
    public BlockEntity applianceOwner() {
        return this;
    }

    @Override
    public java.util.Set<com.cio.createinteroperable.grid.GridConnection> applianceConnections() {
        return this.applianceConnections;
    }

    @Override
    public com.cio.createinteroperable.grid.GridAffinity applianceGridHint() {
        return com.cio.createinteroperable.grid.GridAffinity.CEE;
    }

    @Override
    public boolean appliancePowered() {
        return nodePowered;
    }

    @Override
    public void setAppliancePowered(boolean powered) {
        this.nodePowered = powered;
    }

    @Override
    public boolean applianceReceivingPower() {
        return false;
    }

    @Override
    public void setApplianceReceivingPower(boolean receiving) {
    }

    @Override
    public int sourceRangeBlocks() {
        return tier().rangeBlocks();
    }

    @Override
    public boolean sourceExploded() {
        return exploded;
    }

    @Override
    public boolean sourceRailLive(Pool pool) {
        return railLive[pool.ordinal()];
    }

    @Override
    public void setNetworkConflict(boolean conflict) {
        this.networkConflict = conflict;
    }

    @Override
    public boolean sourceHasNetworkConflict() {
        return networkConflict;
    }

    @Override
    public void reportLinkedAppliances(double[] wattsByPool, int[] countByPool) {
        System.arraycopy(wattsByPool, 0, linkedWatts, 0, linkedWatts.length);
        System.arraycopy(countByPool, 0, linkedCount, 0, linkedCount.length);
    }

    /**
     * Client-only visual escalation, driven off the synced readout fields. No
     * transformer-hum audio for this variant (see class javadoc) — smoke/haze
     * particles still play, same thresholds as the PG-wired kits.
     */
    public void tickAudio() {
        if (level == null || !level.isClientSide) {
            return;
        }

        boolean faulted = railFaulted[0] || railFaulted[1];
        float load = getWorstLoad();

        if (!faulted && load < HAZE_FRACTION) {
            return;
        }
        float intensity = faulted
                ? 0.55f + 0.45f * getFaultProgress()
                : Mth.clamp((load - HAZE_FRACTION) / (1f - HAZE_FRACTION), 0f, 1f) * 0.35f;

        RandomSource rand = level.random;
        int puffs = faulted ? 1 + rand.nextInt(1 + Math.round(intensity * 4f))
                : (rand.nextFloat() < intensity ? 1 : 0);
        for (int p = 0; p < puffs; p++) {
            double x = worldPosition.getX() + 0.30 + rand.nextDouble() * 0.40;
            double y = worldPosition.getY() + 0.85 + rand.nextDouble() * 0.15;
            double z = worldPosition.getZ() + 0.30 + rand.nextDouble() * 0.40;
            double vy = 0.015 + 0.045 * intensity;
            level.addParticle(faulted ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE,
                    x, y, z, (rand.nextDouble() - 0.5) * 0.01, vy, (rand.nextDouble() - 0.5) * 0.01);
        }
    }

    // -------------------------------------------------------------- Goggles

    private static final String GK = "createinteroperable.goggle.power_kit.";

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.builder()
                .add(getBlockState().getBlock().getName().withStyle(ChatFormatting.WHITE))
                .forGoggles(tooltip);

        CreateLang.builder()
                .add(Component.translatable("block.createinteroperable.power_kit.serves")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC))
                .forGoggles(tooltip, 1);

        CreateLang.builder()
                .add(Component.translatable(GK + "wiring",
                        Component.literal("CEE").withStyle(ChatFormatting.AQUA)))
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);

        if (networkConflict) {
            CreateLang.builder()
                    .add(Component.translatable(GK + "network_conflict").withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                    .forGoggles(tooltip, 1);
        }

        if (isPlayerSneaking) {
            CreateLang.builder()
                    .add(Component.translatable(GK + "range",
                            Component.literal(Integer.toString(tier().rangeBlocks())).withStyle(ChatFormatting.AQUA)))
                    .style(ChatFormatting.GRAY)
                    .forGoggles(tooltip, 1);
        }

        CreateLang.builder()
                .add(Component.translatable(GK + "intake",
                        Component.literal(Double.toString(Math.round(intakeDisplayVolts() * 10.0) / 10.0))
                                .withStyle(ChatFormatting.AQUA)))
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);

        railTooltip(tooltip, Pool.LV, GK + "rail_lv", isPlayerSneaking);
        if (tier().servesMediumVoltage()) {
            railTooltip(tooltip, Pool.MV, GK + "rail_mv", isPlayerSneaking);
        }

        CreateLang.builder()
                .add(Component.translatable(GK + "total",
                        Component.literal(Long.toString(Math.round(getTotalUsageWatts()))).withStyle(ChatFormatting.AQUA)))
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);

        if (faultTicks > 0) {
            CreateLang.builder()
                    .add(Component.translatable(GK + "fault",
                            Component.literal(Integer.toString(Math.round(getFaultProgress() * 100f))).withStyle(ChatFormatting.RED)))
                    .style(ChatFormatting.GRAY)
                    .forGoggles(tooltip, 1);
        }
        appendGoggleExtras(tooltip);
        appendTemperatureTooltip(tooltip);

        if (!isPlayerSneaking) {
            CreateLang.builder()
                    .add(Component.translatable(GK + "sneak_hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC))
                    .forGoggles(tooltip, 1);
        }
        return true;
    }

    protected void appendGoggleExtras(List<Component> tooltip) {
    }

    protected void appendTemperatureTooltip(List<Component> tooltip) {
        double over = overheatCelsius();
        ChatFormatting tempColor = temperatureC >= over * 0.8 ? ChatFormatting.RED
                : temperatureC >= over * 0.55 ? ChatFormatting.GOLD : ChatFormatting.GREEN;
        CreateLang.builder()
                .add(Component.translatable(GK + "temperature",
                        Component.literal(Integer.toString(Math.round(temperatureC))).withStyle(tempColor),
                        Component.literal(Integer.toString((int) Math.round(over))).withStyle(ChatFormatting.GRAY)))
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);
    }

    private void railTooltip(List<Component> tooltip, Pool pool, String railNameKey, boolean sneaking) {
        int i = pool.ordinal();
        double used = totalWatts(pool);
        int softCapW = (int) Math.round(softCap(pool));
        int percent = Math.round(getRailLoad(pool) * 100f);
        boolean faulted = railFaulted[i];
        boolean live = railLive[i];

        ChatFormatting loadColor = faulted ? ChatFormatting.RED
                : (percent >= Math.round(HAZE_FRACTION * 100f) ? ChatFormatting.GOLD : ChatFormatting.GREEN);
        Component stateSuffix = faulted ? Component.translatable(GK + "state_overload")
                : live ? Component.empty()
                : used > 0 ? Component.translatable(GK + "state_brownout")
                : Component.translatable(GK + "state_idle");

        Component value = Component.translatable(GK + "rail_value",
                percent, Math.round(used), softCapW, stateSuffix).withStyle(loadColor);

        CreateLang.builder()
                .add(Component.translatable(GK + "rail", Component.translatable(railNameKey), value))
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);

        CreateLang.builder()
                .add(Component.translatable(GK + "bus", Component.translatable(railNameKey),
                        Component.literal(Double.toString(Math.round(busVolts[i] * 10.0) / 10.0)).withStyle(ChatFormatting.AQUA)))
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);

        if (sneaking && (linkedCount[i] > 0 || externalWatts[i] >= 0.5f)) {
            CreateLang.builder()
                    .add(Component.translatable(GK + "rail_detail",
                            Component.literal(Integer.toString(linkedCount[i])).withStyle(ChatFormatting.AQUA),
                            Component.literal(Integer.toString(Math.round(externalWatts[i]))).withStyle(ChatFormatting.AQUA)))
                    .style(ChatFormatting.DARK_GRAY)
                    .forGoggles(tooltip, 1);
        }
    }

    // ------------------------------------------------------------------ NBT

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        for (Pool pool : Pool.values()) {
            int i = pool.ordinal();
            linkedWatts[i] = tag.getDouble("LinkedWatts" + i);
            linkedCount[i] = tag.getInt("LinkedCount" + i);
            externalWatts[i] = tag.getFloat("ExternalWatts" + i);
            busVolts[i] = tag.getFloat("BusVolts" + i);
            railFaulted[i] = tag.getBoolean("RailFaulted" + i);
            railLive[i] = tag.getBoolean("RailLive" + i);
        }
        faultTicks = tag.getInt("FaultTicks");
        temperatureC = tag.getFloat("TemperatureC");
        networkConflict = tag.getBoolean("NetworkConflict");
        if (!com.cio.createinteroperable.grid.CrayfishCompat.present()) {
            readApplianceNbt(tag);
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        for (Pool pool : Pool.values()) {
            int i = pool.ordinal();
            tag.putDouble("LinkedWatts" + i, linkedWatts[i]);
            tag.putInt("LinkedCount" + i, linkedCount[i]);
            tag.putFloat("ExternalWatts" + i, externalWatts[i]);
            tag.putFloat("BusVolts" + i, busVolts[i]);
            tag.putBoolean("RailFaulted" + i, railFaulted[i]);
            tag.putBoolean("RailLive" + i, railLive[i]);
        }
        tag.putInt("FaultTicks", faultTicks);
        tag.putFloat("TemperatureC", temperatureC);
        tag.putBoolean("NetworkConflict", networkConflict);
        if (!com.cio.createinteroperable.grid.CrayfishCompat.present()) {
            writeApplianceNbt(tag);
        }
    }

    // --------------------------------------------- native appliance grid

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (!com.cio.createinteroperable.grid.CrayfishCompat.present()) {
            com.cio.createinteroperable.grid.ApplianceGrid.get(level).addNode(this);
        }
    }
}
