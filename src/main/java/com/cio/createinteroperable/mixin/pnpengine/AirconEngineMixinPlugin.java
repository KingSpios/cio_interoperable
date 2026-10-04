package com.cio.createinteroperable.mixin.pnpengine;

import com.cio.createinteroperable.compat.PipesNPhysicsCompat;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Gates the Aircon Motor pump mixins, which patch Pipes n Physics' INTERNAL solver classes (its
 * own docs warn these move between releases). They are applied only to the 3.2.x line they were
 * written against; any other version leaves the motor a plain tank rather than risking a broken
 * solver.
 */
public class AirconEngineMixinPlugin implements IMixinConfigPlugin {
    private static final String TESTED_VERSION_PREFIX = "3.2.";
    private boolean enabled;

    @Override
    public void onLoad(String mixinPackage) {
        String version = PipesNPhysicsCompat.version();
        enabled = version != null && version.startsWith(TESTED_VERSION_PREFIX);
    }

    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return enabled; }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
}
