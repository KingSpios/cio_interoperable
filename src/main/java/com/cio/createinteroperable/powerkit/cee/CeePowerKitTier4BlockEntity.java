package com.cio.createinteroperable.powerkit.cee;

import com.cio.createinteroperable.Lang;
import com.cio.createinteroperable.grid.Pool;
import com.cio.createinteroperable.powerkit.DebTier;
import com.cio.createinteroperable.powerkit.PowerKitPanel;
import com.cio.createinteroperable.powerkit.cpg.PowerKitTier4BlockEntity;
import com.george_vi.electroenergetics.CEESoundEvents;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * "CEE Industrial Power Kit" (tier 4) BlockEntity — CEE-only twin of
 * {@link PowerKitTier4BlockEntity}. Everything electrical, the three-tap
 * slider, the feeds and the thermal model are inherited from
 * {@link CeePowerKitTier3BlockEntity}; this only points {@link #tier()} at
 * {@link DebTier#TIER_4} and nudges the model-geometry hooks for the taller,
 * shallower tier-4 model, and opens the {@link PowerKitPanel} control panel
 * (step sound: Electro Energetics' Voltage Regulator "steps").
 */
public class CeePowerKitTier4BlockEntity extends CeePowerKitTier3BlockEntity implements PowerKitPanel {

    public CeePowerKitTier4BlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
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

    @Override
    public java.util.List<ViewerSpec> viewerSpecs() {
        float b = 4.1f;
        double v = primaryVolts();
        boolean at1kv = v >= 1000.0;
        boolean at240 = v >= 240.0 && v < 1000.0;
        String intakeV = Integer.toString(Math.round((float) intakeDisplayVolts()));
        String off = Lang.component("common.off_caps").getString();
        return java.util.List.of(
                new ViewerSpec("120v", pct(getRailLoad(Pool.MV)), isRailFaulted(Pool.MV), 5f, b),
                new ViewerSpec("12v", pct(getRailLoad(Pool.LV)), isRailFaulted(Pool.LV), 11f, b),
                new ViewerSpec("1kv", at1kv ? intakeV : off, false, 5f, b + 4f),
                new ViewerSpec("240v", at240 ? intakeV : off, false, 11f, b + 4f),
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

    /** Same volume / pitch Electro Energetics' own Voltage Regulator plays it at. */
    @Override
    public void playLeverSound(boolean towardOff) {
        if (level != null) {
            level.playSound(null, worldPosition, CEESoundEvents.VOLTAGE_REGULATOR.get(), SoundSource.BLOCKS, 0.5f, 1.0f);
        }
    }

    @Override
    public void panelChanged() {
        setChanged();
        sendData();
    }
}
