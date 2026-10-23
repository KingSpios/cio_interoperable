package com.cio.createinteroperable.mixin.mts;

import com.cio.createinteroperable.compat.ModGatedMixinPlugin;
import com.cio.createinteroperable.compat.Mods;

/**
 * Gate for {@code createinteroperable.mts.crayfish.mixin.json}: its mixins
 * target Immersive Vehicles classes <em>and</em> hard-reference MrCrayfish's
 * electricity interfaces, so they apply only with both mods installed.
 */
public class MtsCrayfishMixinPlugin extends ModGatedMixinPlugin {
    public MtsCrayfishMixinPlugin() {
        super(Mods.IMMERSIVE_VEHICLES, Mods.REFURBISHED_FURNITURE);
    }
}
