package com.cio.createinteroperable.powerkit.cpg;

import com.cio.createinteroperable.Lang;
import com.cio.createinteroperable.grid.Pool;
import com.cio.createinteroperable.powerkit.DebTier;
import com.cio.createinteroperable.powerkit.PowerKitPanel;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Tier-4 ("Industrial") substation BlockEntity. Everything electrical, the
 * three-tap slider, the feeds and the thermal model are inherited from
 * {@link PowerKitTier3BlockEntity} (which reads its taps from
 * {@link DebTier#substation()}); this only points {@link #tier()} at
 * {@link DebTier#TIER_4} and nudges the model-geometry hooks for the taller,
 * shallower tier-4 model (front face at z=6, viewers at z=6.5), and opens the
 * {@link PowerKitPanel} control panel (step sound: a button press per detent).
 */
public class PowerKitTier4BlockEntity extends PowerKitTier3BlockEntity implements PowerKitPanel {

    public PowerKitTier4BlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected DebTier tier() {
        return DebTier.TIER_4;
    }

    @Override
    protected Vec3 sliderSlotBase() {
        return VecHelper.voxelSpace(8, 2.0, 6.1); // tier-4 model, front face ~z=6
    }

    @Override
    public float viewerPlateZ() {
        return (6.5f + 0.05f) / 16f;
    }

    /**
     * Tier-4 model: a 6-plate 2&times;3 grid (columns cx 5 / 11; rows y4.1 /
     * 8.1 / 12.1, bottom to top), matching the model's own plate names:
     * <pre>
     *   USAGE W  | TEMP °C       (top,    viewer_usage / viewer_temperature)
     *   1kv      | 240v          (mid,    viewer_1kv   / viewer_240v)
     *   120v %   | 12v %         (bottom, viewer_120v  / viewer_12v)
     * </pre>
     * The {@code 1kv} / {@code 240v} plates are high-tap indicators: the one
     * matching the selected intake tap shows the live intake voltage, the
     * other reads {@code OFF}.
     */
    @Override
    public java.util.List<ViewerSpec> viewerSpecs() {
        float b = 4.1f;
        double v = primaryVolts();
        boolean at1kv = v >= 1000.0;
        boolean at240 = v >= 240.0 && v < 1000.0;
        String intakeV = Integer.toString(Math.round((float) intakeDisplayVolts()));
        String off = Lang.component("common.off_caps").getString();
        return java.util.List.of(
                // bottom row — pool loads
                new ViewerSpec("120v", pct(getRailLoad(Pool.MV)), isRailFaulted(Pool.MV), 5f, b),
                new ViewerSpec("12v", pct(getRailLoad(Pool.LV)), isRailFaulted(Pool.LV), 11f, b),
                // mid row — high-tap indicators
                new ViewerSpec("1kv", at1kv ? intakeV : off, false, 5f, b + 4f),
                new ViewerSpec("240v", at240 ? intakeV : off, false, 11f, b + 4f),
                // top row — meta
                new ViewerSpec(Lang.component("powerkit.usage").getString(),
                        isSwitchedOff() ? off : Integer.toString(Math.round(getTotalUsageWatts())),
                        isSwitchedOff(), 5f, b + 8f),
                new ViewerSpec("TEMP", Math.round(getTemperatureC()) + "°",
                        getTemperatureC() >= overheatCelsius() * 0.8, 11f, b + 8f));
    }

    // --- control panel ---------------------------------------------------

    @Override
    protected void appendGoggleExtras(java.util.List<Component> tooltip) {
        super.appendGoggleExtras(tooltip);
        CreateLang.builder()
                .add(Component.translatable("createinteroperable.goggle.power_kit.open_panel")
                        .withStyle(ChatFormatting.GOLD))
                .forGoggles(tooltip, 1);
    }

    @Override
    public BlockPos panelPos() {
        return worldPosition;
    }

    @Override
    public Component panelTitle() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public void setIntakeModeIndex(int index) {
        setModeIndex(index);
    }

    @Override
    public float panelIntakeVolts() {
        return (float) intakeDisplayVolts();
    }

    @Override
    public void playLeverSound(boolean towardOff) {
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS,
                    0.3f, towardOff ? 0.5f : 0.6f);
        }
    }

    @Override
    public void panelChanged() {
        setChanged();
        sendData();
    }
}
