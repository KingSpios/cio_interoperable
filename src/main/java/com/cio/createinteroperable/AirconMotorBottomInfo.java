package com.cio.createinteroperable;

import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * The handful of read accessors {@link AirconMotorTopBlockEntity} (the fan)
 * and {@link AirconVenterBlockEntity} (the venters) actually need from
 * whichever motor bottom half is paired with/nearby them — implemented by
 * both {@link AirconMotorBottomBlockEntity} (Power Grid-wired) and
 * {@code CeeAirconMotorBottomBlockEntity} (Electro Energetics-wired).
 * <p>
 * This is what makes the fan and venters genuinely electrical-backend-
 * agnostic: neither class references either concrete bottom class (or
 * either mod's own types) directly anymore — they check
 * {@code instanceof AirconMotorBottomInfo} instead. Every method here is a
 * plain primitive-returning accessor, so this interface itself has zero
 * dependency on Power Grid or Electro Energetics and is always safe to
 * reference regardless of which (if either) is actually installed.
 */
public interface AirconMotorBottomInfo {
    /** 0 (unpowered/no voltage) .. 1 (running at the 120 V design optimum) — see {@code AirconMotorBottomBlockEntity#getSmoothedPerformance}. */
    float getSmoothedPerformance();

    /** Whether the slider is on any setting other than Off — the switch's own position, not whether voltage/current is currently present. */
    boolean isPoweredOn();

    /** Whether hot_air has genuinely been received back from the venter loop recently — goggle/particle-display information only. */
    boolean isReceivingHotAirSupply();

    /** How many °C a venter fed from this motor should treat as its own cooling ceiling right now — the current setting's real effect. */
    float getSettingDropC();

    /** The world face cold_air leaves through right now, or null if this block is not in a valid state. Flips with the slider's flow direction. */
    @Nullable
    Direction getColdOutSide();

    /** The world face hot_air is drawn in through right now, or null if this block is not in a valid state. */
    @Nullable
    Direction getHotInSide();

    /** The current setting's own relative intensity fraction (Off=0, Low=0.25, Mid=0.5, Max=1) — feeds a venter's own particle speed. */
    float getSettingIntensityFraction();
}
