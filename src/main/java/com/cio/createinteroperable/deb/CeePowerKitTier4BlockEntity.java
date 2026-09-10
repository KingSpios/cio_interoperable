package com.cio.createinteroperable.deb;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * "CEE Industrial Power Kit" (tier 4) BlockEntity — CEE-only twin of
 * {@link PowerKitTier4BlockEntity}. Everything electrical, the three-tap
 * slider, the feeds and the thermal model are inherited from
 * {@link CeePowerKitTier3BlockEntity}; this only points {@link #tier()} at
 * {@link DebTier#TIER_4} and nudges the model-geometry hooks for the taller,
 * shallower tier-4 model.
 */
public class CeePowerKitTier4BlockEntity extends CeePowerKitTier3BlockEntity {

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
        return java.util.List.of(
                new ViewerSpec("120v", pct(getRailLoad(Pool.MV)), isRailFaulted(Pool.MV), 5f, b),
                new ViewerSpec("12v", pct(getRailLoad(Pool.LV)), isRailFaulted(Pool.LV), 11f, b),
                new ViewerSpec("1kv", at1kv ? intakeV : "OFF", false, 5f, b + 4f),
                new ViewerSpec("240v", at240 ? intakeV : "OFF", false, 11f, b + 4f),
                new ViewerSpec("USAGE", Integer.toString(Math.round(getTotalUsageWatts())), false, 5f, b + 8f),
                new ViewerSpec("TEMP", Math.round(getTemperatureC()) + "°",
                        getTemperatureC() >= overheatCelsius() * 0.8, 11f, b + 8f));
    }
}
