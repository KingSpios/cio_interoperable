package com.cio.createinteroperable.mixin.pnpengine;

import com.cio.createinteroperable.compat.ModGatedMixinPlugin;
import com.cio.createinteroperable.compat.Mods;

/**
 * Gates the Aircon Motor pump mixins, which patch Pipes n Physics' INTERNAL solver classes (its
 * own docs warn these move between releases). They are applied only to the 3.2.x line they were
 * written against; any other version leaves the motor a plain tank rather than risking a broken
 * solver.
 */
public class AirconEngineMixinPlugin extends ModGatedMixinPlugin {
    private static final String TESTED_VERSION_PREFIX = "3.2.";

    public AirconEngineMixinPlugin() {
        super(Mods.PIPES_N_PHYSICS);
    }

    @Override
    protected boolean extraCondition() {
        String version = Mods.PIPES_N_PHYSICS.version();
        return version != null && version.startsWith(TESTED_VERSION_PREFIX);
    }
}
