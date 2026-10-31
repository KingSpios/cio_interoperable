package com.cio.createinteroperable.mixin.crn;

import com.cio.createinteroperable.CIOConfig;
import com.cio.createinteroperable.compat.CrnDisplayGlue;
import com.cio.createinteroperable.grid.ApplianceNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blanks a Create Train Navigator Advanced Display's text while it is a
 * freestanding, unpowered {@link ApplianceNode} (see {@link CrnDisplayNodeMixin}).
 *
 * <p>The display's physical block model still renders &mdash; only the block
 * entity renderer's text/table pass is suppressed, which is a path Create Train
 * Navigator already exercises for non-controller blocks, so cancelling it is
 * safe. A display assembled onto a moving contraption is left completely alone
 * ({@link #cio$isFreestanding}), and so is a world-placed display inside a Super
 * Glue region ({@link CrnDisplayGlue}) &mdash; gluing a board for a train makes it
 * show text before it is ever assembled.</p>
 *
 * <p>Also makes a powered display's text <em>emissive</em> (full-bright,
 * readable in the dark) &mdash; the same trick the CIO Telephone label uses
 * ({@code isPowered() ? FULL_BRIGHT : light}). Create Train Navigator already
 * funnels one {@code light} value into the render subtype (its own Glow Ink Sac
 * "glowing" feature does exactly this); this just forces that value when the
 * screen is grid-powered.</p>
 *
 * <p>Name-targeted with {@code remap = false}, non-required &mdash; a no-op
 * without Create Train Navigator.</p>
 */
@Mixin(targets = "de.mrjulsen.crn.client.ber.AdvancedDisplayRenderInstance", remap = false)
public abstract class CrnDisplayRenderMixin {

    @Unique private boolean cio$blank;
    @Unique private boolean cio$emissive;
    @Unique private boolean cio$glued;
    /** Ticks until the Super Glue check runs again; an entity query per display per tick is wasteful. */
    @Unique private int cio$glueCheckIn;

    @Inject(
            method = "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lde/mrjulsen/crn/block/blockentity/AdvancedDisplayBlockEntity;)V",
            at = @At("HEAD"), remap = false, require = 0)
    private void cio$evaluatePower(Level level, BlockPos pos, BlockState state,
                                   @Coerce BlockEntity blockEntity, CallbackInfo ci) {
        boolean freestanding = cio$isFreestanding(blockEntity);
        boolean powered = blockEntity instanceof ApplianceNode n && n.appliancePowered();
        // Contraption displays keep whatever CioPowered they were assembled with; only a live world display glows.
        this.cio$emissive = powered && freestanding;
        boolean gated = CIOConfig.CRN_DISPLAYS_REQUIRE_POWER.get()
                && blockEntity instanceof ApplianceNode && !powered && freestanding;
        if (!gated) {
            this.cio$glueCheckIn = 0;
            this.cio$blank = false;
            return;
        }
        if (--this.cio$glueCheckIn <= 0) {
            this.cio$glueCheckIn = 10;
            this.cio$glued = CrnDisplayGlue.isGlued(blockEntity);
        }
        this.cio$blank = !this.cio$glued;
    }

    @Inject(method = "render(Lde/mrjulsen/mcdragonlib/client/ber/BERGraphics;F)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void cio$gateText(CallbackInfo ci) {
        if (this.cio$blank) {
            ci.cancel();
        }
    }

    /** Force the display's text full-bright when the screen is grid-powered (matches the Telephone label). */
    @ModifyVariable(method = "render(Lde/mrjulsen/mcdragonlib/client/ber/BERGraphics;F)V",
            at = @At("STORE"), ordinal = 0, remap = false, require = 0)
    private int cio$emissiveText(int light) {
        return this.cio$emissive ? LightTexture.FULL_BRIGHT : light;
    }

    /**
     * True only for a real, world-placed display: the block entity lives in the
     * player's actual client level and is the one registered at its position.
     *
     * <p>Create 6 gives every contraption its own render level &mdash; an
     * anonymous {@code VirtualRenderWorld} subclass ({@code ClientContraption$1})
     * &mdash; and registers the contraption's block entities in it at their
     * local positions, so "the BE at my position is me" holds there too. An
     * earlier class-name blacklist missed that anonymous class and blanked every
     * display on a train. Comparing against the real level excludes contraption,
     * Ponder and schematic levels without naming any of them.</p>
     */
    @Unique
    private static boolean cio$isFreestanding(BlockEntity be) {
        Level level = be.getLevel();
        if (level == null || level != Minecraft.getInstance().level) {
            return false;
        }
        BlockPos pos = be.getBlockPos();
        return level.isLoaded(pos) && level.getBlockEntity(pos) == be;
    }
}
