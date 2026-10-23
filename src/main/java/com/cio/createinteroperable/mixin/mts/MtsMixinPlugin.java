package com.cio.createinteroperable.mixin.mts;

import com.cio.createinteroperable.compat.ModGatedMixinPlugin;
import com.cio.createinteroperable.compat.Mods;

/**
 * Gate for {@code createinteroperable.mts.mixin.json}: every mixin there
 * targets (and hard-references) Immersive Vehicles classes, so the whole config
 * is a no-op unless Immersive Vehicles ({@code mts}) is installed.
 */
public class MtsMixinPlugin extends ModGatedMixinPlugin {
    public MtsMixinPlugin() {
        super(Mods.IMMERSIVE_VEHICLES);
    }
}
