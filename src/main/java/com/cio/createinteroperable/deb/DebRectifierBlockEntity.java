package com.cio.createinteroperable.deb;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
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
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.base.ThermalBehaviour;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.node.ProvidedVoltageSourceCoupling;
import org.patryk3211.powergrid.utility.sound.SoundScapes;

import java.util.List;

/**
 * The functional half of {@link DebRectifierBlock}.
 *
 * <p>Since the single-intake rewire the Kit is:</p>
 * <ul>
 *   <li>a Power Grid device with <b>one intake</b> (terminals 0/1) &mdash;
 *       120&nbsp;V on tiers 1&ndash;2, 240&nbsp;V (mode-switchable to 120&nbsp;V)
 *       on tier&nbsp;3 &mdash; carrying an aggregate resistive load recomputed
 *       every tick from the appliances linked to the board, plus Power Feed
 *       outputs (a 120&nbsp;V pass-through / step-down, a regulated 12&nbsp;V
 *       step-down, and on tier&nbsp;3 a high-voltage pass-through) whose draw is
 *       metered and folded back onto the intake;</li>
 *   <li>an {@link ApplianceSource} &mdash; appliances wrench-link to it, and the
 *       board decides each tick which pools to energise. The distribution itself
 *       runs on whichever appliance-grid backend is active (Crayfish's node graph
 *       via {@code com.cio.createinteroperable.mixin.crayfish.DebSourceNodeMixin}
 *       when Refurbished Furniture is installed; the native grid otherwise).</li>
 * </ul>
 *
 * <p>The load presented to CPG is
 * {@code primaryInternalResistance + Vprimary^2 / primaryWatts}, every appliance
 * a parallel load, so a full house pulls real current through the CPG grid's own
 * source and wire resistance and sags the whole grid. There is a one-tick lag
 * between the PG solve and the Crayfish energise decision (and between a feed's
 * draw and its reflection onto the intake), which matches every other
 * cross-solver bridge in this mod.</p>
 *
 * <p>The tier-specific bits are behind {@code protected} hooks
 * ({@link #primaryVolts()}, {@link #softCap}, {@link #meterFeeds()},
 * {@link #reflectedIdealFeedWatts()}, {@link #postElectricalTick}, &hellip;)
 * so {@link PowerKitTier3BlockEntity} can layer on its extra feed, its
 * mode switch and its thermal model without duplicating the tick loop.</p>
 */
public class DebRectifierBlockEntity extends ElectricBlockEntity implements ApplianceSource, IHaveGoggleInformation {

    /** Resistance (&Omega;) that stands in for "no load". Also the clamp ceiling on a computed load. */
    static final double R_OPEN = 1.0e7;

    /** Load fraction (linked / soft cap) at which the board starts venting a light haze. */
    public static final float HAZE_FRACTION = 0.85f;
    /** Ticks a pool may stay past its soft cap before the board detonates. Dropping back under the cap resets it. */
    public static final int FAULT_GRACE_TICKS = 200;

    /** Near-zero-resistance lead for a Power Feed pass-through. */
    static final float FEED_PASSTHROUGH_R = 0.001f;
    /** Default nominal primary (intake) voltage; overridden per tier / per mode. */
    private static final double PRIMARY_VOLTS = 120.0;
    /** Step-down efficiency: feed-side watts cost this much more off the intake (waste = the hum / the heat). */
    static final float STEP_DOWN_EFFICIENCY = 0.90f;
    /** Hum volume while a real 120&nbsp;V step-down (tier 3, 240&nbsp;V mode only) carries load. */
    private static final float MV_STEP_DOWN_HUM = 0.04f;
    /** Hum volume while the 12&nbsp;V step-down carries load — always real, so always the louder of the two. */
    private static final float LV_STEP_DOWN_HUM = 0.06f;

    /** This board's tier. Overridden by the tier-1 / tier-3 subclasses; returns a static constant so it's safe to call during the super constructor. */
    protected DebTier tier() {
        return DebTier.TIER_2;
    }

    /** true &rarr; the model has a physical needle gauge (tier 1); false &rarr; flat text viewers (tier 2). */
    public boolean usesNeedleGauge() {
        return false;
    }

    /** true &rarr; the model has the tier-3 five-plate viewer bank (HV%, temp, 120V%, 12V%, usage). */
    public boolean usesTier3Viewers() {
        return false;
    }

    // --- appliance-grid bookkeeping ------------------------------------
    /**
     * True while at least one pool is live this tick (set in {@link #electricalTick}).
     * The appliance-grid node adapter reports this as its {@code isNodePowered()}.
     */
    private boolean nodePowered;
    /** Native appliance-grid links (Crayfish's adapter carries its own set when it is active). */
    private final java.util.Set<com.cio.createinteroperable.grid.GridConnection> applianceConnections = new java.util.HashSet<>();
    /** True while the backend's last scan found another board sharing this network. See {@link ApplianceSource#setNetworkConflict}. */
    private boolean networkConflict;

    // --- PG circuit (assigned in buildCircuit) --------------------------
    /** The single grid feed. */
    protected FloatingNode intakePositive, intakeNegative;
    /** Aggregate appliance load across the intake; resistance rewritten every electricalTick. */
    protected ElectricWire primaryLoad;
    /** 120 V Power Feed pass-through lead (tier 2); its current is the downstream 120 V draw. */
    protected ElectricWire mvFeedWire;
    /** Regulated 12 V Power Feed output — an internal source held at {@link #stepDownRatio()} x intake volts. */
    protected ProvidedVoltageSourceCoupling lvFeed;

    // --- per-tick working state, indexed by Pool.ordinal() --------------
    // Written server-side (earlyNodeTick / electricalTick), round-tripped
    // through read/write so the readouts can be drawn on the client.
    /** Rated watts of the Crayfish appliances linked to each pool. */
    protected final double[] linkedWatts = new double[Pool.values().length];
    /** Count of Crayfish appliances linked to each pool (in range, recognised). Synced for the goggle readout. */
    protected final int[] linkedCount = new int[Pool.values().length];
    /** Measured watts drawn through each pool's Power Feed(s). Adds to the pool total. */
    protected final float[] externalWatts = new float[Pool.values().length];
    /** MV = solved intake voltage; LV = solved 12 V feed terminal voltage. */
    protected final float[] busVolts = new float[Pool.values().length];
    protected final boolean[] railFaulted = new boolean[Pool.values().length];
    protected final boolean[] railLive = new boolean[Pool.values().length];
    /** How long (ticks) a pool has been over its soft cap. 0 = healthy; >= {@link #FAULT_GRACE_TICKS} = detonation. */
    protected int faultTicks;
    protected boolean exploded;
    /** Core temperature (&deg;C) latched from {@link #thermalBehaviour} each server tick; synced for the readouts. */
    protected float temperatureC;

    // --- tier-1 needle gauge, client-smoothed (see DebRectifierRenderer) --
    /** Eased usage fraction the needle currently shows; 1.0 = a pool at its soft cap. */
    public float gauge;
    /** Previous tick's {@link #gauge}, for {@code Mth.lerp(partialTicks, ...)} in the renderer. */
    public float gaugePrev;

    public DebRectifierBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        // Ease the needle toward the current worst-pool load (0..~1.x). Runs
        // client-side for the smooth sweep; harmless on the server.
        gaugePrev = gauge;
        gauge += (getWorstLoad() - gauge) * 0.15f;
    }

    // ------------------------------------------------------------------ PG

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        // SmartBlockEntity's constructor calls addBehaviours() -> buildCircuit()
        // *before* this subclass's field initializers run, so the working-state
        // arrays are still null here. tier() is safe (static constant). Seed the
        // load resistor open and let the first electricalTick() drop in the
        // real value.
        boolean mv = tier().hasMediumVoltageFeed();
        // [0,1] intake; [2,3] 120 V Power Feed pass-through (tier 2 only); then the 12 V Power Feed pair.
        int lvFeedIdx = mv ? 4 : 2;
        builder.setTerminalCount(lvFeedIdx + 2);

        intakePositive = builder.terminalNode(0);
        intakeNegative = builder.terminalNode(1);
        primaryLoad = builder.connect((float) R_OPEN, intakePositive, intakeNegative);

        if (mv) {
            // 120 V Power Feed: a near-zero-R pass-through straight off the
            // intake, so whatever the player wires here is a real parallel
            // branch the solver already draws from the grid.
            FloatingNode mvFeedPositive = builder.terminalNode(2);
            FloatingNode mvFeedNegative = builder.terminalNode(3);
            mvFeedWire = builder.connect(FEED_PASSTHROUGH_R, intakePositive, mvFeedPositive);
            builder.connect(FEED_PASSTHROUGH_R, intakeNegative, mvFeedNegative);
        }

        // 12 V Power Feed: a regulated step-down source, EMF = ratio x solved
        // intake volts, isolated from the intake nodes (same isolated-outlet
        // pattern as the Interoperable Telephone). Its output power does NOT
        // come from the grid automatically — electricalTick reflects it onto
        // primaryLoad. Series resistance is the tier's stepDownResistance so
        // the 12 V sags under load.
        FloatingNode lvFeedPositive = builder.terminalNode(lvFeedIdx);
        FloatingNode lvFeedNegative = builder.terminalNode(lvFeedIdx + 1);
        lvFeed = builder.addInternalNode(ProvidedVoltageSourceCoupling.class,
                lvFeedPositive, lvFeedNegative, (float) tier().stepDownResistance());
        lvFeed.setVoltageProvider(this::stepDownEmf);
    }

    /** 12 V feed step-down ratio against the current (mode-aware) primary voltage. */
    protected double stepDownRatio() {
        return Pool.LV.nominalVoltage / primaryVolts();
    }

    /** Regulated 12 V feed EMF: a fixed fraction of whatever the intake solved to. */
    protected double stepDownEmf() {
        return Math.max(0.0, intakeVolts() * stepDownRatio());
    }

    /** Solved voltage across the intake terminals. */
    protected double intakeVolts() {
        if (intakePositive == null || intakeNegative == null) {
            return 0.0;
        }
        return Math.abs(intakePositive.getVoltage() - intakeNegative.getVoltage());
    }

    /** Solved voltage across the 12 V feed's terminals (sags below EMF under load). */
    protected double lvFeedTerminalVolts() {
        if (lvFeed == null) {
            return 0.0;
        }
        return Math.abs(lvFeed.getPositive().getVoltage() - lvFeed.getNegative().getVoltage());
    }

    /** Amps the 12 V feed is delivering — the PG coupling's solved current. */
    protected double lvFeedCurrent() {
        return lvFeed != null ? Math.abs(lvFeed.getCurrent()) : 0.0;
    }

    // --- tier hooks ----------------------------------------------------

    /** Nominal intake voltage. Tier 3 overrides (240 V, or 120 V in low-voltage mode). */
    protected double primaryVolts() {
        return PRIMARY_VOLTS;
    }

    /** Series loss (&Omega;) on the intake. Tier 3 varies it by mode. */
    protected double primaryInternalResistance() {
        return tier().primaryInternalResistance();
    }

    /**
     * The 120&nbsp;V pool's rail voltage for brownout / readout / powered
     * check. Tiers 1&ndash;2: the intake itself. Tier 3+: the regulated
     * 120&nbsp;V step-down output, <em>computed</em> from the intake and the
     * ratio (see {@link #electricalTick} — never read straight off a feed
     * coupling's node voltages, which are ~0 when nothing is wired to that
     * Power Feed nub).
     */
    protected double mvRailVolts() {
        return intakeVolts();
    }

    /**
     * The 12&nbsp;V rail voltage: the intake stepped down by
     * {@link #stepDownRatio()}, minus the series-R sag from whatever the
     * 12&nbsp;V feed is actually delivering. Computed, not read from
     * {@link #lvFeed}'s node voltages.
     */
    protected double lvRailVolts() {
        double emf = intakeVolts() * stepDownRatio();
        double sag = lvFeedCurrent() * tier().stepDownResistance();
        return Math.max(0.0, emf - sag);
    }

    /** Voltage shown on the goggle "Intake:" line. Synced-backed so it reads client-side. Tier 3 overrides with its own synced 240&nbsp;V value. */
    protected double intakeDisplayVolts() {
        return busVolts[Pool.MV.ordinal()];
    }

    /** This pool's soft cap (W). Instance method so tier 3 can switch it by mode. */
    protected double softCap(Pool pool) {
        return tier().softCapWatts(pool);
    }

    /** This pool's hard cap (W). Instance method so tier 3 can switch it by mode. */
    protected double hardCap(Pool pool) {
        return tier().hardCapWatts(pool);
    }

    /**
     * Divide {@code linkedWatts[MV]} by this before referring it to the
     * primary. {@code 1.0} (default, tiers 1-2 and tier 3 in 120&nbsp;V mode) —
     * the linked 120&nbsp;V appliances sit directly on the primary, no
     * conversion. Tier 3 in 240&nbsp;V mode overrides to
     * {@link #STEP_DOWN_EFFICIENCY}: those appliances are downstream of the
     * real 240&rarr;120 step-down, same as the regulated 120&nbsp;V feed.
     */
    protected double mvLinkedReflectionFactor() {
        return 1.0;
    }

    /**
     * Watts actually flowing through a <em>real</em> voltage step-down to the
     * 120&nbsp;V rail (as opposed to a straight pass-through/1:1 strap) — the
     * trigger for the 120&nbsp;V transformer hum. {@code 0} on tiers 1-2
     * (nothing is stepped: appliances are direct on the 120&nbsp;V primary,
     * and tier 2's Power Feed is a bare wire) and on tier 3 in 120&nbsp;V mode
     * (also a 1:1 strap — nothing to indicate). Tier 3 in 240&nbsp;V mode
     * overrides with the real 240&rarr;120 draw.
     */
    protected double mvStepDownWatts() {
        return 0.0;
    }

    /**
     * Watts pulled from <em>ideal internal sources</em> (the regulated feeds)
     * that must be reflected onto the intake resistor because the solver does
     * not draw them from the grid automatically. Base: the 12 V side. Tier 3
     * adds its regulated 120 V feed. Real pass-through feeds (tier 2's 120 V,
     * tier 3's HV) are NOT here — they are already parallel branches.
     */
    protected double reflectedIdealFeedWatts() {
        double lvReflected = linkedWatts[Pool.LV.ordinal()] + externalWatts[Pool.LV.ordinal()];
        return lvReflected / STEP_DOWN_EFFICIENCY;
    }

    /**
     * Recompute {@link #externalWatts} from the feed wires / couplings. Base
     * handles tier 2's 120 V pass-through + the 12 V feed; tier 3 overrides
     * for its HV pass-through + regulated 120 V + 12 V layout.
     */
    protected void meterFeeds() {
        double mvFeedCurrent = mvFeedWire != null ? Math.abs(mvFeedWire.current()) : 0.0;
        externalWatts[Pool.MV.ordinal()] = (float) (intakeVolts() * mvFeedCurrent);

        externalWatts[Pool.LV.ordinal()] = (float) (lvFeedTerminalVolts() * lvFeedCurrent());
    }

    // --- thermal (every tier) ----------------------------------------

    /**
     * Every Kit runs a real {@link ThermalBehaviour}: its own dissipation
     * ({@link #thermalHeatWatts()}) heats a core with the tier's overheat point
     * and steady-state power ({@link DebTier#thermalOverheatCelsius()} /
     * {@link DebTier#thermalMaxPowerWatts()}). A Create encased fan cools it for
     * free &mdash; PG's {@code AirCurrent} mixin adds a cooling multiplier to any
     * {@code ThermalBehaviour} in a fan's path &mdash; and past the overheat
     * point {@link #onThermalOverheat()} detonates the board.
     */
    @Override
    public ThermalBehaviour specifyThermalBehaviour() {
        return ThermalBehaviour
                .forMaxPower(this, (float) tier().thermalOverheatCelsius(), (float) tier().thermalMaxPowerWatts())
                .overheatCallback(this::onThermalOverheat);
    }

    /** Overheat past the tier limit = detonation, same terminal outcome as a gross overload. */
    protected void onThermalOverheat() {
        if (level instanceof ServerLevel server && !exploded) {
            explode(server);
        }
    }

    /**
     * The Kit's own dissipation this tick (W): primary I&sup2;R at the intake
     * plus the 12&nbsp;V step-down conversion loss. Shared with tier 3, which
     * adds its 240&rarr;120 step-down loss on top.
     */
    protected double thermalHeatWatts() {
        double v = Math.max(1.0, primaryVolts());
        double intakeCurrent = primaryWatts() / v;
        double primaryLoss = primaryInternalResistance() * intakeCurrent * intakeCurrent;

        double lvSideWatts = linkedWatts[Pool.LV.ordinal()] + externalWatts[Pool.LV.ordinal()];
        double conversionLoss = (1.0 / STEP_DOWN_EFFICIENCY - 1.0) * lvSideWatts;

        return Math.max(0.0, primaryLoss + conversionLoss);
    }

    /** Current core temperature (&deg;C). Client-safe (synced). */
    public float getTemperatureC() {
        return temperatureC;
    }

    /** This tier's overheat point (&deg;C) &mdash; drives the goggle colour ramp and the tier-3/4 temperature viewer. */
    public double overheatCelsius() {
        return tier().thermalOverheatCelsius();
    }

    /**
     * Hook run once per server {@code electricalTick}, after the fault logic and
     * before the periodic sync. Base: feed this tick's dissipation to the
     * {@link ThermalBehaviour} and latch the temperature for the readouts.
     * Tier 3 also syncs its intake voltage here.
     */
    protected void postElectricalTick(ServerLevel server) {
        if (thermalBehaviour != null) {
            thermalBehaviour.applyTickPower(thermalHeatWatts());
            temperatureC = thermalBehaviour.getTemperature();
        }
    }

    @Override
    public void electricalTick() {
        super.electricalTick();
        if (exploded || !(level instanceof ServerLevel server)) {
            return;
        }

        // Native appliance grid: run our own distribution pass (search the linked
        // network, bill recognised appliances, energise live pools). With Crayfish
        // installed its ElectricityTicker calls the adapter instead, so skip.
        if (!com.cio.createinteroperable.grid.CrayfishCompat.present()) {
            distributeAppliancePower();
            if ((server.getGameTime() % 40L) == 0L && !applianceConnections().isEmpty()) {
                com.cio.createinteroperable.CreateInteroperable.LOGGER.info(
                        "[grid] DEB {} links={} linkedW LV/MV={}/{} count LV/MV={}/{}",
                        worldPosition, applianceConnections().size(),
                        linkedWatts[Pool.LV.ordinal()], linkedWatts[Pool.MV.ordinal()],
                        linkedCount[Pool.LV.ordinal()], linkedCount[Pool.MV.ordinal()]);
            }
        }

        // Meter the Power Feeds (downstream fixtures on the output nubs).
        meterFeeds();

        // Push this tick's aggregate load onto the single intake resistor
        // (consumed by PG's next solve). Harmless no-op on a CEE kit — the stub
        // circuit is isolated and nothing reads it.
        if (primaryLoad != null) {
            primaryLoad.setResistance((float) primaryResistance());
        }

        // Bus voltages, computed from the intake + the step-down ratios — never
        // read straight off the feed couplings: an unwired Power Feed nub is a
        // floating island in PG's solver whose node voltages read ~0, which
        // would make a grid-powered board look completely unpowered.
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

        // Gross overload (>hard cap) is an instant detonation. A pool merely
        // past its soft cap dies (its appliances lose power), vents smoke, and
        // arms a {@link #FAULT_GRACE_TICKS} fuse — drop back under the cap and
        // the fuse resets.
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

    /** Crayfish appliances + metered Power Feed draw on this pool (W). Client-safe. */
    public double totalWatts(Pool pool) {
        int i = pool.ordinal();
        return linkedWatts[i] + externalWatts[i];
    }

    /** Total draw across both pools, including the Power Feeds (W). Client-safe. */
    public float getTotalUsageWatts() {
        return (float) (totalWatts(Pool.LV) + totalWatts(Pool.MV));
    }

    /** True while the intake or the 12 V feed is delivering voltage — gates the face readouts. Client-safe. */
    public boolean isPowered() {
        return busVolts[Pool.MV.ordinal()] >= 1f || busVolts[Pool.LV.ordinal()] >= 1f;
    }

    /**
     * This pool's load as a fraction of its rated (soft-cap) capacity:
     * {@code 0} idle, {@code 1.0} exactly at the limit, {@code >1.0}
     * overloaded/faulting. Client-safe (backed by synced fields). Returns
     * {@code 0} for a pool this tier does not have.
     */
    public float getRailLoad(Pool pool) {
        double soft = softCap(pool);
        return soft <= 0 ? 0f : (float) (totalWatts(pool) / soft);
    }

    /** The higher of the two pools' {@link #getRailLoad} — drives the single body gauge / hum / smoke rate. */
    public float getWorstLoad() {
        return Math.max(getRailLoad(Pool.LV), getRailLoad(Pool.MV));
    }

    /** True while this pool is past its soft cap and its appliances are cut. */
    public boolean isRailFaulted(Pool pool) {
        return railFaulted[pool.ordinal()];
    }

    /** 0 &rarr; 1 progress of the fault fuse toward detonation ({@code faultTicks / FAULT_GRACE_TICKS}). */
    public float getFaultProgress() {
        return Mth.clamp(faultTicks / (float) FAULT_GRACE_TICKS, 0f, 1f);
    }

    /** This pool's solved bus voltage (V). Client-safe. */
    public float getBusVolts(Pool pool) {
        return busVolts[pool.ordinal()];
    }

    /**
     * Total watts the intake must actually supply this tick, all referred to
     * the primary: the linked 120&nbsp;V appliances (divided by
     * {@link #mvLinkedReflectionFactor()} — 1.0 unless they are themselves
     * behind a real step-down, tier 3 in 240&nbsp;V mode) plus every regulated
     * feed's draw referred through its own step-down
     * ({@link #reflectedIdealFeedWatts()}). Real pass-through feeds are already
     * parallel branches on the intake, so they are NOT added here. Shared by
     * {@link #primaryResistance()} and (on tier 3) the thermal heat calc, so
     * the two never drift apart.
     */
    protected double primaryWatts() {
        return linkedWatts[Pool.MV.ordinal()] / mvLinkedReflectionFactor() + reflectedIdealFeedWatts();
    }

    /** The load the Kit presents to CPG: {@code primaryInternalResistance + Vprimary^2 / primaryWatts()}. */
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

    /**
     * The distribution logic (search the reachable appliance network, split by
     * pool, bill drawing appliances, energise live pools) lives on whichever
     * grid backend is active: with Refurbished Furniture installed it is
     * {@code com.cio.createinteroperable.mixin.crayfish.DebSourceNodeMixin},
     * which drives Crayfish's node graph; the Phase 2 native grid will drive its
     * own. Both feed results back through these accessors.
     */

    @Override
    public BlockEntity applianceOwner() {
        return this;
    }

    @Override
    public java.util.Set<com.cio.createinteroperable.grid.GridConnection> applianceConnections() {
        return this.applianceConnections;
    }

    @Override
    public net.minecraft.world.phys.AABB applianceNodeBox() {
        return DebNodeBox.forState(getBlockState());
    }

    @Override
    public com.cio.createinteroperable.grid.GridAffinity applianceGridHint() {
        return com.cio.createinteroperable.grid.GridAffinity.CPG;
    }

    @Override
    public boolean appliancePowered() {
        return nodePowered;
    }

    @Override
    public void setAppliancePowered(boolean powered) {
        this.nodePowered = powered;
    }

    // The board is a source, never a sink; receiving-power is unused but the
    // interface requires it. Kept as a plain field so nothing NPEs.
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

    /** Whichever backend distributes power reports its per-pool link tally here each server tick. */
    @Override
    public void reportLinkedAppliances(double[] wattsByPool, int[] countByPool) {
        System.arraycopy(wattsByPool, 0, linkedWatts, 0, linkedWatts.length);
        System.arraycopy(countByPool, 0, linkedCount, 0, linkedCount.length);
    }


    /**
     * Client-only visual + audio escalation, driven off the synced readout
     * fields. Two independent transformer-hum voices, additive (both busy =
     * louder than either alone): a quiet one for a <em>real</em> 120&nbsp;V
     * step-down carrying load ({@link #mvStepDownWatts()} — silent on tiers
     * 1-2 and on tier 3 in 120&nbsp;V mode, since there nothing is actually
     * being stepped) and a louder one for the 12&nbsp;V step-down, which is
     * always real. On top of that: a light haze from {@link #HAZE_FRACTION}
     * of rated capacity, thick smoke once a pool is faulted, the hum ramping
     * with {@link #getFaultProgress()} as the fuse burns down (this escalation
     * overrides the transformer hums, it doesn't add to them — an overloaded
     * board doesn't need both sounds at once).
     */
    @Override
    public void tickAudio() {
        super.tickAudio();
        if (level == null || !level.isClientSide) {
            return;
        }

        boolean faulted = railFaulted[0] || railFaulted[1];
        float load = getWorstLoad();
        boolean mvStepDownBusy = isPowered() && mvStepDownWatts() > 0.5;
        boolean lvStepDownBusy = isPowered() && totalWatts(Pool.LV) > 0.5;

        float hum = (mvStepDownBusy ? MV_STEP_DOWN_HUM : 0f) + (lvStepDownBusy ? LV_STEP_DOWN_HUM : 0f);
        if (faulted) {
            hum = Math.max(hum, 0.25f + 0.75f * getFaultProgress());
        } else if (load >= HAZE_FRACTION) {
            hum = Math.max(hum, Mth.clamp((load - HAZE_FRACTION) / (1f - HAZE_FRACTION), 0f, 1f) * 0.2f);
        }
        if (hum > 0f) {
            SoundScapes.play(SoundScapes.AmbienceGroup.HUM, worldPosition, 1f, hum);
        }

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

    /**
     * Live goggle readout. Header is the block's own (tier-specific) name; then
     * the Crayfish-serves line, the intake voltage, each pool's load vs soft
     * cap + state and solved bus voltage, the total draw, the fault-fuse
     * countdown if it is burning, {@link #appendGoggleExtras} (tier 3: intake
     * mode + HV feed) and the core temperature vs its overheat limit.
     * <p>Holding Sneak adds the wrench link range and, per pool, the linked
     * appliance count and the share of the draw coming from CPG fixtures on the
     * Power Feed nub.
     */
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
                        Component.literal("CPG").withStyle(ChatFormatting.AQUA)))
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

    /** Tier-3 hook: append the intake-mode line and (in HV taps) the HV-feed line. */
    protected void appendGoggleExtras(List<Component> tooltip) {
    }

    /** Core-temperature goggle line (current / overheat limit), shared by every tier. Colour ramps toward the limit. */
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

        // Sneak detail: how many Crayfish appliances are linked to this pool and
        // how much of its draw is CPG fixtures on the Power Feed nub (vs. the
        // wrench-linked appliances).
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

    // No teardown on setRemoved/destroy: that fires on chunk unload too. Links
    // are pruned lazily by surviving nodes only when the far position is loaded
    // and confirmed to be gone (see ApplianceNode#pruneApplianceConnections,
    // called from distributeAppliancePower()).
}
