package com.cio.createinteroperable.compat;

import com.cio.createinteroperable.BrassHeaterBlock;
import com.momosoftworks.coldsweat.api.temperature.block_temp.BlockTemp;
import com.momosoftworks.coldsweat.api.util.Temperature;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * One heat-tier's worth of Cold Sweat radiation for the steam-heating family
 * (Brass Heater, Multi Radiator) — see {@link ColdSweatIntegration} for where
 * these get built and registered, and {@link ColdSweatCompat} for why this is
 * plain Java rather than the {@code block_temp} datapack JSON this project
 * originally tried (confirmed not to load third-party data at all).
 * <p>
 * One instance per active tier (WARM/HOT/BLAZING) per block family, each
 * with its own temperature/range, matching the original JSON design's
 * per-tier progression — {@link #isValid} gates on {@code tierExtractor}
 * returning exactly this instance's tier, so only one of the 3 per-family
 * instances ever contributes at a time. {@code tierExtractor} exists (rather
 * than a single shared {@code EnumProperty} reference) because
 * {@link BrassHeaterBlock#HEAT_LEVEL} and the 3 Multi Radiator blocks'
 * {@code HEAT_LEVEL} fields are 4 separate property instances, not one
 * shared property — see those classes.
 */
class ColdSweatHeatTemp extends BlockTemp {
    private final BrassHeaterBlock.HeatLevel tier;
    private final Function<BlockState, BrassHeaterBlock.HeatLevel> tierExtractor;
    private final double temperatureMc;

    /**
     * @param temperatureC how much this tier should warm the player, in real-world °C (converted to MC units here).
     * @param rangeBlocks  how far this tier's warmth reaches — same numbers the original JSON design used (6/9/12 for warm/hot/blazing).
     */
    ColdSweatHeatTemp(BrassHeaterBlock.HeatLevel tier, Function<BlockState, BrassHeaterBlock.HeatLevel> tierExtractor,
                       double temperatureC, double rangeBlocks, Block... blocks) {
        super(0, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Temperature.convert(30, Temperature.Units.C, Temperature.Units.MC, true),
                rangeBlocks, true, false, blocks);
        this.tier = tier;
        this.tierExtractor = tierExtractor;
        this.temperatureMc = Temperature.convert(temperatureC, Temperature.Units.C, Temperature.Units.MC, false);
    }

    @Override
    public boolean isValid(Level level, BlockPos pos, BlockState state) {
        return tierExtractor.apply(state) == tier;
    }

    @Override
    public double getTemperature(Level level, @Nullable LivingEntity entity, BlockState state, BlockPos pos, double distance) {
        return temperatureMc;
    }
}
