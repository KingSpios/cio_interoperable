package com.cio.createinteroperable.thermal;

import com.cio.createinteroperable.CIOConfig;
import com.cio.createinteroperable.compat.Mods;

/**
 * Which temperature mod (if any) CIO's thermal features talk to. Today that is
 * only Cold Sweat; a second temperature mod would be checked here too.
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
}
