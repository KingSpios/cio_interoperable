package com.cio.createinteroperable.thermal;

import com.cio.createinteroperable.CIOConfig;
import com.cio.createinteroperable.compat.Mods;

/**
 * Which temperature mod (if any) CIO's thermal features talk to. Today that is
 * only Cold Sweat; a second temperature mod would be checked here too.
 *
 * <p>The climate blocks themselves (Steam Outlet, Steam Hearth, Aircon) need no
 * temperature mod: they register, tick, move steam / air and show their heat
 * tiers on every install. A temperature mod only adds what they do to the
 * world's temperature and to players, behind its own presence check.</p>
 */
public final class TemperatureMods {

    private TemperatureMods() {
    }

    /** A temperature mod is installed. */
    public static boolean present() {
        return Mods.COLD_SWEAT.present();
    }

    /**
     * Whether the temperature mod's ambient temperature should reach Power
     * Grid and Electro Energetics devices' own heat model: a temperature mod
     * is installed AND the {@code temperatureMods.affectGridDevices} config
     * option is on (off by default). False before the config has loaded.
     */
    public static boolean affectGridDevices() {
        return present() && CIOConfig.SPEC.isLoaded() && CIOConfig.TEMPERATURE_MODS_AFFECT_GRID_DEVICES.get();
    }

    /**
     * Whether the Steam Hearth and the Aircon parts are offered (creative tab,
     * recipes via {@link ClimateBlocksCondition}): always with a temperature mod,
     * otherwise per {@code temperatureMods.climateBlocksWithoutTemperatureMod}
     * (on by default). True before the config has loaded.
     */
    public static boolean climateBlocksEnabled() {
        return present() || !CIOConfig.SPEC.isLoaded() || CIOConfig.CLIMATE_BLOCKS_WITHOUT_TEMPERATURE_MOD.get();
    }
}
