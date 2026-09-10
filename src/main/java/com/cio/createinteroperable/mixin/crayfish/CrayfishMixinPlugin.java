package com.cio.createinteroperable.mixin.crayfish;

import com.cio.createinteroperable.grid.CrayfishCompat;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Gate for {@code createinteroperable.crayfish.mixin.json}: every mixin in that
 * config hard-references {@code com.mrcrayfish.*} types (fixture block entities,
 * the electricity node interfaces, Crayfish's own renderer), so the whole config
 * is a no-op unless MrCrayfish's Refurbished Furniture is installed.
 *
 * <p>Most of those mixins also target {@code com.mrcrayfish.*} classes directly,
 * so Mixin would skip them on its own when the target is absent ({@code
 * "required": false}). This plugin is the belt-and-braces for the few that
 * target a <em>third-party</em> class (a Let's Do / Beachparty appliance) while
 * still needing Crayfish's node interface &mdash; there the target can be present
 * while Crayfish is not.</p>
 */
public class CrayfishMixinPlugin implements IMixinConfigPlugin {

    private boolean crayfishPresent;

    @Override
    public void onLoad(String mixinPackage) {
        this.crayfishPresent = CrayfishCompat.present();
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return this.crayfishPresent;
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
