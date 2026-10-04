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

import java.util.List;
import java.util.function.IntFunction;

/**
 * The CEE-only <b>substation</b> BlockEntity (tier 3 "Commercial", tier 4
 * "Industrial") — CEE-only twin of {@link PowerKitTier3BlockEntity}. Same
 * multi-tap intake slider and three-feed layout, driven entirely through
 * {@link DebCeeDevice} instead of Power Grid couplings.
 */
public class CeePowerKitTier3BlockEntity extends CeeDebRectifierBlockEntity {

    /** Slider: index into {@link DebTier.Substation#modes()}; index 0 is the default tap. */
    private ScrollValueBehaviour intakeMode;

    private float hvFeedWatts;
    private float mv120FeedWatts;
    private float intakeVoltsSynced;

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
                Component.literal("Intake Tap"),
                this, PowerKitGeometry.intakeSlot(sliderSlotBase()), tapOptions())
                .between(0, sub().modes().length - 1);
        intakeMode.withFormatter(labels::apply);
        super.addBehaviours(behaviours);
        behaviours.add(intakeMode);
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
            return new ValueSettingsBoard(label, max, 1, ImmutableList.of(Component.literal("Intake")),
                    new ScrollOptionSettingsFormatter(options));
        }
    }

    /** Slider slot base (voxel space), overridden per model. */
    protected Vec3 sliderSlotBase() {
        return VecHelper.voxelSpace(8, 1.5, 7.6); // tier-3 model, front face ~z=7.5
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

    @Override
    protected double mvRailVolts() {
        double emf = intakeVolts() * (120.0 / primaryVolts());
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
        hvFeedWatts = (float) (hasHvFeed() && mode().hvFeedLive() ? intakeVolts() * passCurrent : 0.0);

        double mv120Current = ceeDevice != null ? ceeDevice.getMv120Current() : 0.0;
        mv120FeedWatts = (float) (mvRailVolts() * mv120Current);

        float lvWatts = (float) (lvRailVolts() * lvFeedCurrent());

        externalWatts[Pool.MV.ordinal()] = hvFeedWatts + mv120FeedWatts;
        externalWatts[Pool.LV.ordinal()] = lvWatts;
    }

    /**
     * Substation feed topology: {@code [0,1]} intake, {@code [2,3]} HV
     * pass-through strap (only in taps whose {@code hvFeedLive}), {@code [4,5]}
     * regulated 120 V feed, {@code [6,7]} regulated 12 V feed.
     */
    @Override
    protected void configureCeeFeeds(DebCeeDevice device) {
        int mvIdx = hasHvFeed() ? 4 : 2;
        device.setPassthrough(hasHvFeed() && mode().hvFeedLive(), 2, 3, FEED_PASSTHROUGH_R);
        device.setMv120Feed(true, mvIdx, mvIdx + 1, mv120Emf(), sub().mvStepDownResistance());
        device.setLvFeed(mvIdx + 2, mvIdx + 3, stepDownEmf(), tier().stepDownResistance());
    }

    /** true &rarr; the model has the HV pass-through terminal pair (tiers 3/4); the tier-2 Domestic kit does not. */
    protected boolean hasHvFeed() {
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
        return Math.round(load * 100f) + "%";
    }

    public java.util.List<ViewerSpec> viewerSpecs() {
        float b = 3.1f;
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
    }

    @Override
    protected void read(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        hvFeedWatts = tag.getFloat("HvFeedWatts");
        mv120FeedWatts = tag.getFloat("Mv120FeedWatts");
        intakeVoltsSynced = tag.getFloat("IntakeVolts");
    }

    @Override
    protected void write(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("HvFeedWatts", hvFeedWatts);
        tag.putFloat("Mv120FeedWatts", mv120FeedWatts);
        tag.putFloat("IntakeVolts", intakeVoltsSynced);
    }
}
