package com.cio.createinteroperable.compat;

import com.cio.createinteroperable.BrassHeaterBlock;
import com.momosoftworks.coldsweat.api.temperature.block_temp.SimpleBlockTemp;
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
 * <p>
 * Extends {@code SimpleBlockTemp}, not {@code BlockTemp} directly: Cold Sweat
 * 2.4.3 deleted {@code BlockTemp}'s (minEffect, maxEffect, minTemp, maxTemp,
 * range, fade, logarithmic, blocks...) constructor outright (only the
 * bare-{@code Block...} one survives, everything else moved to per-call
 * getter overrides) and introduced {@code SimpleBlockTemp} as the static-value
 * convenience subclass with that exact old constructor signature preserved —
 * confirmed by reading Cold Sweat's real source (commit 3579254, "Make block
 * temp properties dynamic instead of statically defined as fields", shipped
 * in 2.4.3). Building against Cold Sweat 2.4.2 while a user runs 2.4.3+ (or
 * vice versa) throws {@code NoSuchMethodError}/{@code NoClassDefFoundError}
 * the instant {@link ColdSweatIntegration#onBlockTempRegister} tries to
 * construct one of these — a real reported crash, 2026-09-22, CIO 0.1.50
 * against Cold Sweat 2.4.3.1. This project now targets 2.4.3+ only (see
 * build.gradle/gradle.properties); older Cold Sweat installs are refused via
 * the raised {@code cold_sweat_version_range} floor rather than silently
 * crashing on this constructor.
 */
class ColdSweatHeatTemp extends SimpleBlockTemp {
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
