package com.cio.createinteroperable.mixin.pnw;

import com.cio.createinteroperable.compat.ModGatedMixinPlugin;
import com.cio.createinteroperable.compat.Mods;

/** Loads the experimental bridge only when both optional mods are installed. */
public class PnwMixinPlugin extends ModGatedMixinPlugin {
    public PnwMixinPlugin() {
        super(Mods.PANTOGRAPHS_AND_WIRES, Mods.ELECTRO_ENERGETICS);
    }
}
