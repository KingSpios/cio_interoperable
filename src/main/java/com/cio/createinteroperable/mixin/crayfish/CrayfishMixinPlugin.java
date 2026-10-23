package com.cio.createinteroperable.mixin.crayfish;

import com.cio.createinteroperable.compat.ModGatedMixinPlugin;
import com.cio.createinteroperable.compat.Mods;

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
public class CrayfishMixinPlugin extends ModGatedMixinPlugin {
    public CrayfishMixinPlugin() {
        super(Mods.REFURBISHED_FURNITURE);
    }
}
