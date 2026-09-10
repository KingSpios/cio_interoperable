package com.cio.createinteroperable.deb;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter.ScrollOptionSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.electricity.base.ThermalBehaviour;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.node.ProvidedVoltageSourceCoupling;

import java.util.List;
import java.util.function.IntFunction;

/**
 * The shared <b>substation</b> BlockEntity (tier 3 "Commercial", tier 4
 * "Industrial"). On top of {@link DebRectifierBlockEntity} it adds:
 * <ul>
 *   <li><b>A multi-tap intake.</b> A Create slider ({@code intakeMode}) picks
 *       one of {@link DebTier.Substation#modes()}. Every voltage-dependent
 *       hook ({@link #primaryVolts()}, {@link #primaryInternalResistance()},
 *       {@link #softCap}/{@link #hardCap}, {@link #mvStepDownWatts()}, &hellip;)
 *       reads {@link #mode()}.</li>
 *   <li><b>Three feeds.</b> An HV pass-through ({@link SwitchedWire}, live only
 *       in taps that {@link DebTier.Substation.Mode#hvFeedLive()}), a regulated
 *       120&nbsp;V step-down and the 12&nbsp;V step-down.</li>
 *   <li><b>The temperature viewer.</b> The {@link ThermalBehaviour} itself now
 *       lives on {@link DebRectifierBlockEntity} (every tier is fan-coolable);
 *       tier 3 only adds its 240&rarr;120 step-down loss to
 *       {@link #thermalHeatWatts()} and paints {@link #getTemperatureC()} on the
 *       temperature plate.</li>
 * </ul>
 * {@link PowerKitTier4BlockEntity} is a thin subclass — it only changes
 * {@link #tier()} and a couple of model-geometry hooks.
 */
public class PowerKitTier3BlockEntity extends DebRectifierBlockEntity {

    /** Slider: index into {@link DebTier.Substation#modes()}; index 0 is the default tap. */
    private ScrollValueBehaviour intakeMode;

    // --- circuit (substation layout) -------------------------------
    /** HV pass-through positive lead; open in taps whose {@code hvFeedLive} is false. */
    private SwitchedWire hvSwitch;
    /** Regulated intake&rarr;120 V step-down feed (ideal source, reflected onto the intake). */
    private ProvidedVoltageSourceCoupling mv120Feed;

    // --- synced readout state --------------------------------------
    private float hvFeedWatts;
    private float mv120FeedWatts;
    private float intakeVoltsSynced;
    // temperatureC lives on DebRectifierBlockEntity (every tier is thermal now)

    public PowerKitTier3BlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected DebTier tier() {
        return DebTier.TIER_3;
    }

    @Override
    public boolean usesTier3Viewers() {
        return true;
    }

    // ------------------------------------------------------- mode switch

    private DebTier.Substation sub() {
        return tier().substation();
    }

    /** Selected tap index, clamped to the mode list. */
    public int modeIndex() {
        if (intakeMode == null) {
            return 0;
        }
        return Mth.clamp(intakeMode.getValue(), 0, sub().modes().length - 1);
    }

    /** The selected intake tap. */
    protected DebTier.Substation.Mode mode() {
        return sub().modes()[modeIndex()];
    }

    private String modeLabelStr(int i) {
        DebTier.Substation.Mode[] modes = sub().modes();
        return modes[Mth.clamp(i, 0, modes.length - 1)].label();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // Create before super.addBehaviours(): PG's ElectricBlockEntity runs
        // buildCircuit() from inside addBehaviours, and the feed suppliers
        // read mode().
        IntFunction<String> labels = this::modeLabelStr;
        intakeMode = new IntakeModeScrollBehaviour(
                Component.literal("Intake Tap"),
                this, new ModeSlot(sliderSlotBase()), tapOptions())
                .between(0, sub().modes().length - 1);
        intakeMode.withFormatter(labels::apply);
        super.addBehaviours(behaviours);
        behaviours.add(intakeMode);
    }

    /**
     * Each intake tap as an {@link INamedIconOptions} so the value-settings
     * screen runs in "icon mode" — one auto-sized panel that fits the widest
     * tap label ("240 V" / "1 kV") with a single centred label instead of
     * cramped overlapping bubbles, while a plain scroll step still moves exactly
     * one tap. The translation key is just the tap's own display string, which
     * Minecraft renders verbatim when it isn't a registered lang key.
     */
    private INamedIconOptions[] tapOptions() {
        DebTier.Substation.Mode[] modes = sub().modes();
        INamedIconOptions[] opts = new INamedIconOptions[modes.length];
        for (int i = 0; i < modes.length; i++) {
            String lbl = modes[i].label();
            AllIcons icon = modes[i].volts() >= 1000.0 ? AllIcons.I_PRIORITY_VERY_HIGH
                    : modes[i].volts() >= 240.0 ? AllIcons.I_PRIORITY_HIGH
                    : AllIcons.I_PRIORITY_LOW;
            opts[i] = new INamedIconOptions() {
                @Override
                public AllIcons getIcon() {
                    return icon;
                }

                @Override
                public String getTranslationKey() {
                    return lbl;
                }
            };
        }
        return opts;
    }

    private static class IntakeModeScrollBehaviour extends ScrollValueBehaviour {
        private final INamedIconOptions[] options;

        IntakeModeScrollBehaviour(Component label, SmartBlockEntity be, ValueBoxTransform slot,
                                  INamedIconOptions[] options) {
            super(label, be, slot);
            this.options = options;
        }

        @Override
        public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
            return new ValueSettingsBoard(label, max, 1, ImmutableList.of(Component.literal("Intake")),
                    new ScrollOptionSettingsFormatter(options));
        }
    }

    /** Slider slot base (voxel space), overridden per model. */
    protected Vec3 sliderSlotBase() {
        return VecHelper.voxelSpace(8, 1.5, 7.6); // tier-3 model, front face ~z=7.5
    }

    private static class ModeSlot extends ValueBoxTransform {
        private final Vec3 base;

        ModeSlot(Vec3 base) {
            this.base = base;
        }

        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            return PowerKitGeometry.rotateY(base, PowerKitGeometry.angleFor(state));
        }

        @Override
        public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
            TransformStack.of(ms).rotateYDegrees(180 + PowerKitGeometry.angleFor(state));
        }

        @Override
        public float getScale() {
            return 4 / 16f;
        }
    }

    // -------------------------------------------------------------- PG

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        // Substation layout: [0,1] intake, [2,3] HV feed, [4,5] 120 V feed, [6,7] 12 V feed.
        builder.setTerminalCount(8);

        intakePositive = builder.terminalNode(0);
        intakeNegative = builder.terminalNode(1);
        primaryLoad = builder.connect((float) R_OPEN, intakePositive, intakeNegative);

        // HV Feed — switched pass-through straight off the intake. Real
        // parallel branch, so its draw is metered but NOT reflected.
        FloatingNode hvPos = builder.terminalNode(2);
        FloatingNode hvNeg = builder.terminalNode(3);
        hvSwitch = builder.connectSwitch(FEED_PASSTHROUGH_R, intakePositive, hvPos, true);
        builder.connect(FEED_PASSTHROUGH_R, intakeNegative, hvNeg);

        // 120 V Feed — regulated step-down (ratio = 120 / tap volts). Ideal
        // source: electricalTick reflects its draw onto primaryLoad.
        FloatingNode mv120Pos = builder.terminalNode(4);
        FloatingNode mv120Neg = builder.terminalNode(5);
        mv120Feed = builder.addInternalNode(ProvidedVoltageSourceCoupling.class,
                mv120Pos, mv120Neg, (float) sub().mvStepDownResistance());
        mv120Feed.setVoltageProvider(this::mv120Emf);

        // 12 V Feed — regulated step-down against the live tap voltage.
        FloatingNode lvPos = builder.terminalNode(6);
        FloatingNode lvNeg = builder.terminalNode(7);
        lvFeed = builder.addInternalNode(ProvidedVoltageSourceCoupling.class,
                lvPos, lvNeg, (float) tier().stepDownResistance());
        lvFeed.setVoltageProvider(this::stepDownEmf);
    }

    /** Regulated 120 V feed EMF = 120 / tap volts x solved intake volts. */
    protected double mv120Emf() {
        return Math.max(0.0, intakeVolts() * (120.0 / primaryVolts()));
    }

    // ---------------------------------------------------- tier hooks

    @Override
    protected double primaryVolts() {
        return mode().volts();
    }

    @Override
    protected double primaryInternalResistance() {
        return mode().primaryInternalResistance();
    }

    @Override
    protected double softCap(Pool pool) {
        return pool == Pool.MV ? mode().softCapWatts() : tier().softCapWatts(pool);
    }

    @Override
    protected double hardCap(Pool pool) {
        return pool == Pool.MV ? mode().hardCapWatts() : tier().hardCapWatts(pool);
    }

    /**
     * MV pool's rail voltage = the 120 V feed's commanded EMF
     * ({@code intake x 120/tapVolts}) minus its own load sag. Computed from the
     * intake, NOT read off {@link #mv120Feed}'s node voltages: with nothing
     * wired to the 120 V Feed nubs that coupling is a floating island in PG's
     * solver and reads ~0, which made the whole board look unpowered.
     */
    @Override
    protected double mvRailVolts() {
        double emf = intakeVolts() * (120.0 / primaryVolts());
        double current = mv120Feed != null ? Math.abs(mv120Feed.getCurrent()) : 0.0;
        return Math.max(0.0, emf - current * sub().mvStepDownResistance());
    }

    /** Above 120 V the linked appliances sit behind the real step-down (÷ efficiency); at the 120 V tap it's a 1:1 strap. */
    @Override
    protected double mvLinkedReflectionFactor() {
        return primaryVolts() > 120.0 ? STEP_DOWN_EFFICIENCY : 1.0;
    }

    /** The 120 V step-down is real hardware doing work only above the 120 V tap; at 120 V it's a bare strap (no hum). */
    @Override
    protected double mvStepDownWatts() {
        return primaryVolts() > 120.0 ? linkedWatts[Pool.MV.ordinal()] + mv120FeedWatts : 0.0;
    }

    @Override
    protected double intakeDisplayVolts() {
        return intakeVoltsSynced;
    }

    @Override
    protected void meterFeeds() {
        boolean hvLive = mode().hvFeedLive();
        if (hvSwitch != null) {
            hvSwitch.setState(hvLive);
        }

        double hvCurrent = (hvSwitch != null && hvLive) ? Math.abs(hvSwitch.current()) : 0.0;
        hvFeedWatts = (float) (intakeVolts() * hvCurrent);

        double mv120Current = mv120Feed != null ? Math.abs(mv120Feed.getCurrent()) : 0.0;
        mv120FeedWatts = (float) (mvRailVolts() * mv120Current);

        double lvCurrent = lvFeed != null ? Math.abs(lvFeed.getCurrent()) : 0.0;
        float lvWatts = (float) (lvRailVolts() * lvCurrent);

        // Both HV pass-through and the 120 V feed count against the MV pool.
        externalWatts[Pool.MV.ordinal()] = hvFeedWatts + mv120FeedWatts;
        externalWatts[Pool.LV.ordinal()] = lvWatts;
    }

    @Override
    protected double reflectedIdealFeedWatts() {
        // Base handles the 12 V side. Add the regulated 120 V feed (ideal
        // source). The HV pass-through is a real branch — not reflected.
        return super.reflectedIdealFeedWatts() + mv120FeedWatts / STEP_DOWN_EFFICIENCY;
    }

    // ---------------------------------------------------- thermal

    /** Base heat ({@link DebRectifierBlockEntity#thermalHeatWatts()}) + this substation's 240&rarr;120 step-down conversion loss. */
    @Override
    protected double thermalHeatWatts() {
        return super.thermalHeatWatts()
                + (1.0 / STEP_DOWN_EFFICIENCY - 1.0) * mv120FeedWatts;
    }

    @Override
    protected void postElectricalTick(ServerLevel server) {
        intakeVoltsSynced = (float) intakeVolts();
        super.postElectricalTick(server); // applies thermalHeatWatts() to the ThermalBehaviour
    }

    /** HV pass-through feed draw (W). Client-safe (synced). */
    public float getHvFeedWatts() {
        return hvFeedWatts;
    }

    /** Short label of the selected tap ("240 V", "1 kV", …), for the HV viewer plate. */
    public String modeLabelText() {
        return mode().label();
    }

    // --- viewer layout (per model) -------------------------------

    /** One face-plate readout: what text, where on the model. */
    public record ViewerSpec(String label, String value, boolean fault, float cx, float baseY) {}

    /** Viewer plates' front-face Z (block units). Tier 3: z=7.5; tier 4: z=6.5. */
    public float viewerPlateZ() {
        return (7.5f + 0.05f) / 16f;
    }

    static String pct(float load) {
        return Math.round(load * 100f) + "%";
    }

    /**
     * The face-plate readouts for this model, in draw order. Tier 3 is a
     * 5-plate cross ({@code deb_rectifier_tier_3}); tier 4 overrides with its
     * 6-plate 2&times;3 grid.
     */
    public java.util.List<ViewerSpec> viewerSpecs() {
        float b = 3.1f; // tier-3 bottom row; mid +4, top +8
        return java.util.List.of(
                new ViewerSpec("HV", Integer.toString(Math.round(getHvFeedWatts())), false, 5f, b),
                new ViewerSpec("12v", pct(getRailLoad(Pool.LV)), isRailFaulted(Pool.LV), 11f, b),
                new ViewerSpec("TEMP", Math.round(getTemperatureC()) + "°",
                        getTemperatureC() >= overheatCelsius() * 0.8, 5f, b + 4f),
                new ViewerSpec("120v", pct(getRailLoad(Pool.MV)), isRailFaulted(Pool.MV), 11f, b + 4f),
                new ViewerSpec("USAGE", Integer.toString(Math.round(getTotalUsageWatts())), false, 8f, b + 8f));
    }

    // ---------------------------------------------------- goggle + NBT

    private static final String GK = "createinteroperable.goggle.power_kit.";

    @Override
    protected void appendGoggleExtras(List<Component> tooltip) {
        CreateLang.builder()
                .add(Component.translatable(GK + "intake_mode",
                        Component.literal(mode().label()).withStyle(ChatFormatting.AQUA)))
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);

        if (mode().hvFeedLive()) {
            CreateLang.builder()
                    .add(Component.translatable(GK + "hv_feed",
                            Component.literal(mode().label()).withStyle(ChatFormatting.AQUA),
                            Component.literal(Integer.toString(Math.round(hvFeedWatts))).withStyle(ChatFormatting.AQUA)))
                    .style(ChatFormatting.GRAY)
                    .forGoggles(tooltip, 1);
        }
        // The temperature line is emitted by DebRectifierBlockEntity#appendTemperatureTooltip for every tier.
    }

    @Override
    protected void read(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        hvFeedWatts = tag.getFloat("HvFeedWatts");
        mv120FeedWatts = tag.getFloat("Mv120FeedWatts");
        intakeVoltsSynced = tag.getFloat("IntakeVolts");
        // TemperatureC round-trips in DebRectifierBlockEntity#read/#write
    }

    @Override
    protected void write(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("HvFeedWatts", hvFeedWatts);
        tag.putFloat("Mv120FeedWatts", mv120FeedWatts);
        tag.putFloat("IntakeVolts", intakeVoltsSynced);
    }
}
