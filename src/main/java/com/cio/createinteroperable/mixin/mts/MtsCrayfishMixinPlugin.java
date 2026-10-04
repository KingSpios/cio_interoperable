package com.cio.createinteroperable.mixin.mts;

import com.cio.createinteroperable.compat.ImmersiveVehiclesCompat;
import com.cio.createinteroperable.grid.CrayfishCompat;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Gate for {@code createinteroperable.mts.crayfish.mixin.json}: its mixins
 * target Immersive Vehicles classes <em>and</em> hard-reference MrCrayfish's
 * electricity interfaces, so they apply only with both mods installed.
 */
public class MtsCrayfishMixinPlugin implements IMixinConfigPlugin {

    private boolean present;

    @Override
    public void onLoad(String mixinPackage) {
        this.present = ImmersiveVehiclesCompat.present() && CrayfishCompat.present();
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return this.present;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
