package com.cio.createinteroperable.mixin.pnp;

import com.cio.createinteroperable.compat.ModGatedMixinPlugin;
import com.cio.createinteroperable.compat.Mods;

/** Applies the CEE electric pump fix only when both Electro Energetics and Pipes n Physics are installed. */
public class PnpCeePumpMixinPlugin extends ModGatedMixinPlugin {
    public PnpCeePumpMixinPlugin() {
        super(Mods.ELECTRO_ENERGETICS, Mods.PIPES_N_PHYSICS);
    }
}
