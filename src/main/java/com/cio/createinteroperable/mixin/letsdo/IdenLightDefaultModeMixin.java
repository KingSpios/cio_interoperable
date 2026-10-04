package com.cio.createinteroperable.mixin.letsdo;

import com.cio.createinteroperable.CIOConfig;
import com.cio.createinteroperable.grid.CrayfishCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Iden's Decor lights (every block its Pliers can configure &mdash; LED lamps,
 * light bulb, industrial red lamp, fluorescent light, ceiling lamp; one
 * {@code LightBlockEntity}) place in its "refurbished_energy" mode instead of
 * "manual". That is the only mode in which Iden's own Crayfish node takes links
 * (0 max connections otherwise, which read as "0/0" on the wrench), so a new
 * light is grid-ready out of the box. The Pliers still cycle all three modes.
 *
 * <p>Only a new block entity's starting value changes: an existing light's
 * saved {@code activationMode} is read back over it in {@code loadAdditional},
 * so placed lights keep whatever mode they had.</p>
 *
 * <p>Refurbished mode only exists when Refurbished Furniture is installed (Iden
 * adds it with its own Crayfish-gated mixin), hence the Crayfish check. Also
 * gated on {@code lamps.requirePower}. Name-targeted, non-required &mdash; a
 * no-op without Iden's Decor.</p>
 */
@Mixin(targets = "net.identidade.iden_decor.blockentity.LightBlockEntity", remap = false)
public abstract class IdenLightDefaultModeMixin {

    /** Iden's index of "refurbished_energy" in its {@code availableModes}. */
    @Unique
    private static final int CIO$REFURBISHED_MODE = 2;

    @Shadow
    private int activationMode;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void cio$defaultToRefurbishedMode(CallbackInfo ci) {
        if (CrayfishCompat.present() && CIOConfig.LETSDO_LAMPS_REQUIRE_POWER.get()) {
            this.activationMode = CIO$REFURBISHED_MODE;
        }
    }
}
