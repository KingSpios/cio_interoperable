package com.cio.createinteroperable.compat;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Base for CIO's mixin config plugins: the whole config applies only when
 * every listed mod is installed (and {@link #extraCondition()} holds). A
 * config's plugin is then just a public no-arg constructor naming its mods.
 *
 * <p>Why a plugin at all, when the configs are already {@code required=false}:
 * Mixin skips a mixin whose <em>target</em> is missing, but many CIO mixins
 * target one mod while hard-referencing another (a Let's Do lamp that
 * implements Crayfish's node interface, say). There the target can be present
 * while the referenced mod is not, and only the plugin stops it.</p>
 *
 * <p>This class lives in {@code compat}, not in a {@code mixin.*} package: no
 * non-mixin class may live in a package a mixin config declares (Mixin throws
 * {@code IllegalClassLoadError} when such a class is referenced). The concrete
 * plugins stay in their config's package, which Mixin allows for the declared
 * plugin. Runs at mixin-config load, so it may only touch FML types (see
 * {@link Mods}). LoadSafetyTest reads each plugin's gate from the
 * {@link Mods} constants it names.</p>
 */
public abstract class ModGatedMixinPlugin implements IMixinConfigPlugin {

    private final Mods[] required;
    private boolean enabled;

    protected ModGatedMixinPlugin(Mods... required) {
        this.required = required;
    }

    /** A further condition beyond presence (e.g. a tested version range). */
    protected boolean extraCondition() {
        return true;
    }

    @Override
    public void onLoad(String mixinPackage) {
        boolean all = true;
        for (Mods mod : required) {
            all &= mod.present();
        }
        this.enabled = all && extraCondition();
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return this.enabled;
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
