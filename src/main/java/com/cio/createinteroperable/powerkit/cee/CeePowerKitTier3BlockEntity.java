package com.cio.createinteroperable.powerkit.cee;

import com.cio.createinteroperable.powerkit.PowerKitReadout;
import com.cio.createinteroperable.Lang;
import com.cio.createinteroperable.grid.Pool;
import com.cio.createinteroperable.powerkit.DebTier;
import com.cio.createinteroperable.computer.ComputerLinkBehaviour;
import com.cio.createinteroperable.powerkit.PowerKitComputerAccess;
import com.cio.createinteroperable.powerkit.PowerKitControls;
import com.cio.createinteroperable.powerkit.PowerKitGeometry;
import com.cio.createinteroperable.powerkit.PowerKitSignalNub;
import com.cio.createinteroperable.powerkit.cpg.PowerKitTier3BlockEntity;
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

import java.util.List;
import java.util.function.IntFunction;

/**
 * The CEE-only <b>substation</b> BlockEntity (tier 3 "Commercial", tier 4
 * "Industrial") — CEE-only twin of {@link PowerKitTier3BlockEntity}. Same
 * multi-tap intake slider and three-feed layout, driven entirely through
 * {@link DebCeeDevice} instead of Power Grid couplings.
 */
public class CeePowerKitTier3BlockEntity extends CeeDebRectifierBlockEntity implements PowerKitComputerAccess {

    /** Slider: index into {@link DebTier.Substation#modes()}; index 0 is the default tap. */
    private ScrollValueBehaviour intakeMode;

    private float hvFeedWatts;
    private float mv120FeedWatts;
    private float intakeVoltsSynced;

    /** Series resistance currently dimming the HV pass-through, split over its two legs. Transient. */
    private double hvDimmerOhms;
    /** The control-panel levers (tier 4 only). Created on first use: the super constructor calls into this class. */
    private PowerKitControls controls;

    public CeePowerKitTier3BlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
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

    private DebTier.Substation sub() {
        return tier().substation();
    }

    // --- computers (PowerKitComputerAccess) -------------------------------

    @Override
    public int kitTier() {
        return tier() == DebTier.TIER_4 ? 4 : tier() == DebTier.TIER_3 ? 3 : 2;
    }

    @Override
    public String kitGrid() {
        return "cee";
    }

    @Override
    public boolean hasSignalNub() {
        return PowerKitSignalNub.present() && getBlockState().getBlock() instanceof CeeDebRectifierBlock kit && kit.signalNubBox() != null;
    }

    @Override
    public float intakeVoltage() {
        return (float) intakeDisplayVolts();
    }

    @Override
    public double poolSoftCapWatts(Pool pool) {
        return softCap(pool);
    }

    @Override
    public double poolHardCapWatts(Pool pool) {
        return hardCap(pool);
    }

    @Override
    public int linkedApplianceCount(Pool pool) {
        return linkedCount[pool.ordinal()];
    }

    @Override
    public void controlsChanged() {
        setChanged();
        sendData();
    }

    @Override
    public PowerKitControls controls() {
        if (controls == null) {
            controls = new PowerKitControls();
        }
        return controls;
    }

    /** The main lever at OFF switches the kit off exactly like tier 2/3's switch. */
    @Override
    public boolean isSwitchedOff() {
        return super.isSwitchedOff() || controls().feedPower(PowerKitControls.Lever.MAIN) <= 0.0;
    }

    @Override
    protected double poolPowerFactor(Pool pool) {
        return controls().feedPower(pool == Pool.LV ? PowerKitControls.Lever.LV : PowerKitControls.Lever.MV);
    }

    /** Moves the intake tap's Create slider (which then syncs itself), e.g. from the control panel. */
    public void setModeIndex(int index) {
        if (intakeMode != null) {
            intakeMode.setValue(Mth.clamp(index, 0, sub().modes().length - 1));
        }
    }

    public int intakeModeCount() {
        return sub().modes().length;
    }

    public String intakeModeDisplay(int index) {
        return modeLabelStr(index);
    }

    public double intakeModeVolts(int index) {
        return sub().modes()[Mth.clamp(index, 0, sub().modes().length - 1)].volts();
    }

    private double hvFeedPower() {
        return hasHvFeed() && mode().hvFeedLive() && !isSwitchedOff()
                ? controls().feedPower(PowerKitControls.Lever.HV) : 0.0;
    }

    public int modeIndex() {
        if (intakeMode == null) {
            return 0;
        }
        return Mth.clamp(intakeMode.getValue(), 0, sub().modes().length - 1);
    }

    protected DebTier.Substation.Mode mode() {
        return sub().modes()[modeIndex()];
    }

    private String modeLabelStr(int i) {
        DebTier.Substation.Mode[] modes = sub().modes();
        return modes[Mth.clamp(i, 0, modes.length - 1)].display();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        IntFunction<String> labels = this::modeLabelStr;
        intakeMode = new IntakeModeScrollBehaviour(
                Lang.component("powerkit.intake_tap"),
                this, PowerKitGeometry.intakeSlot(sliderSlotBase()), tapOptions())
                .between(0, sub().modes().length - 1);
        intakeMode.withFormatter(labels::apply);
        super.addBehaviours(behaviours);
        behaviours.add(intakeMode);
        behaviours.add(new ComputerLinkBehaviour(this));
    }

    private INamedIconOptions[] tapOptions() {
        DebTier.Substation.Mode[] modes = sub().modes();
        INamedIconOptions[] opts = new INamedIconOptions[modes.length];
        for (int i = 0; i < modes.length; i++) {
            String lbl = modes[i].display();
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
            return new ValueSettingsBoard(label, max, 1, ImmutableList.of(Lang.component("powerkit.intake")),
                    new ScrollOptionSettingsFormatter(options));
        }
    }

    /** Slider slot base (voxel space), overridden per model. */
    protected Vec3 sliderSlotBase() {
        return VecHelper.voxelSpace(8, 1.5, 7.6); // tier-3 model, front face ~z=7.5
    }

    /** Regulated 120 V feed EMF = 120 / tap volts x solved intake volts. */
    protected double mv120Emf() {
        return Math.max(0.0, DebTier.regulatedEmf(120.0, intakeVolts(), primaryVolts(), tier().brownoutFraction(), poolVoltageFactor(Pool.MV)));
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

    @Override
    protected double mvRailVolts() {
        double emf = DebTier.regulatedEmf(120.0, intakeVolts(), primaryVolts(), tier().brownoutFraction(), poolVoltageFactor(Pool.MV));
        double current = ceeDevice != null ? ceeDevice.getMv120Current() : 0.0;
        return Math.max(0.0, emf - current * sub().mvStepDownResistance());
    }

    @Override
    protected double mvLinkedReflectionFactor() {
        return mvFeedEfficiency();
    }

    @Override
    protected double mvStepDownWatts() {
        return primaryVolts() > 120.0 ? linkedWatts[Pool.MV.ordinal()] + mv120FeedWatts : 0.0;
    }

    @Override
    protected double intakeDisplayVolts() {
        return intakeVoltsSynced;
    }

    /**
     * Substation layout for the CEE port: HV pass-through strap ({@code [2,3]})
     * + regulated 120 V feed ({@code [4,5]}) on the MV pool, regulated 12 V
     * feed ({@code [6,7]}) on the LV pool.
     */
    @Override
    protected void meterFeeds() {
        double passCurrent = ceeDevice != null ? ceeDevice.getPassCurrent() : 0.0;
        double hvPower = hvFeedPower();
        // Watts delivered past the dimmer (its own series drop is not the load's).
        hvFeedWatts = (float) (hvPower > 0.0
                ? Math.max(0.0, intakeVolts() * passCurrent - passCurrent * passCurrent * hvDimmerOhms) : 0.0);
        // A pass-through can't have its voltage scaled, so the HV lever dims it with
        // a series resistance sized to the load it just saw (applied next tick).
        hvDimmerOhms = hvPower > 0.0 ? PowerKitControls.dimmerResistance(
                intakeVolts(), passCurrent, 2 * FEED_PASSTHROUGH_R, hvDimmerOhms, hvPower) : 0.0;

        double mv120Current = ceeDevice != null ? ceeDevice.getMv120Current() : 0.0;
        mv120FeedWatts = (float) (mvRailVolts() * mv120Current);

        float lvWatts = (float) (lvRailVolts() * lvFeedCurrent());

        externalWatts[Pool.MV.ordinal()] = hvFeedWatts + mv120FeedWatts;
        externalWatts[Pool.LV.ordinal()] = lvWatts;
    }

    /**
     * Substation feed topology: {@code [0,1]} intake, {@code [2,3]} HV
     * pass-through strap (only in taps whose {@code hvFeedLive}), {@code [4,5]}
     * regulated 120 V feed, {@code [6,7]} regulated 12 V feed. Switched off,
     * every feed is removed from the circuit.
     */
    @Override
    protected void configureCeeFeeds(DebCeeDevice device) {
        boolean live = !isSwitchedOff();
        int mvIdx = hasHvFeed() ? 4 : 2;
        // The dimmer is split over the strap's two legs.
        device.setPassthrough(hvFeedPower() > 0.0, 2, 3, FEED_PASSTHROUGH_R + hvDimmerOhms / 2.0);
        device.setMv120Feed(live && poolPowerFactor(Pool.MV) > 0.0, mvIdx, mvIdx + 1, mv120Emf(), sub().mvStepDownResistance());
        device.setLvFeed(live && poolPowerFactor(Pool.LV) > 0.0, mvIdx + 2, mvIdx + 3, stepDownEmf(), tier().stepDownResistance());
    }

    /** true &rarr; the model has the HV pass-through terminal pair (tiers 3/4); the tier-2 Domestic kit does not. */
    public boolean hasHvFeed() {
        return true;
    }

    /** Efficiency of the regulated 120&nbsp;V feed at the current tap: real step-down above 120&nbsp;V, a lossless 1:1 strap at it. */
    protected double mvFeedEfficiency() {
        return primaryVolts() > 120.0 ? STEP_DOWN_EFFICIENCY : 1.0;
    }

    @Override
    protected double reflectedIdealFeedWatts() {
        return super.reflectedIdealFeedWatts() + mv120FeedWatts / mvFeedEfficiency();
    }

    // ---------------------------------------------------- thermal

    @Override
    protected double thermalHeatWatts() {
        return super.thermalHeatWatts()
                + (1.0 / mvFeedEfficiency() - 1.0) * mv120FeedWatts;
    }

    @Override
    protected void postElectricalTick(ServerLevel server) {
        intakeVoltsSynced = (float) intakeVolts();
        super.postElectricalTick(server);
    }

    public float getHvFeedWatts() {
        return hvFeedWatts;
    }

    public String modeLabelText() {
        return mode().label();
    }

    // --- viewer layout (per model) -------------------------------

    public record ViewerSpec(String label, String value, boolean fault, float cx, float baseY) {}

    public float viewerPlateZ() {
        return (7.5f + 0.05f) / 16f;
    }

    static String pct(float load) {
        return PowerKitReadout.percent(load) + "%";
    }

    public java.util.List<ViewerSpec> viewerSpecs() {
        float b = 3.1f;
        return java.util.List.of(
                new ViewerSpec("HV", Integer.toString(Math.round(getHvFeedWatts())), false, 5f, b),
                new ViewerSpec("12v", pct(getRailLoad(Pool.LV)), isRailFaulted(Pool.LV), 11f, b),
                new ViewerSpec("TEMP", Math.round(getTemperatureC()) + "°",
                        getTemperatureC() >= overheatCelsius() * 0.8, 5f, b + 4f),
                new ViewerSpec("120v", pct(getRailLoad(Pool.MV)), isRailFaulted(Pool.MV), 11f, b + 4f),
                new ViewerSpec(Lang.component("powerkit.usage").getString(),
                        isSwitchedOff() ? Lang.component("common.off_caps").getString()
                                : Integer.toString(Math.round(getTotalUsageWatts())),
                        isSwitchedOff(), 8f, b + 8f));
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
    }

    @Override
    protected void read(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        hvFeedWatts = tag.getFloat("HvFeedWatts");
        mv120FeedWatts = tag.getFloat("Mv120FeedWatts");
        intakeVoltsSynced = tag.getFloat("IntakeVolts");
        controls().read(tag);
    }

    @Override
    protected void write(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("HvFeedWatts", hvFeedWatts);
        tag.putFloat("Mv120FeedWatts", mv120FeedWatts);
        tag.putFloat("IntakeVolts", intakeVoltsSynced);
        controls().write(tag);
    }
}
