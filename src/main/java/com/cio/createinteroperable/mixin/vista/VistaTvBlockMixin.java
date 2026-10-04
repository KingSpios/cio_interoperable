package com.cio.createinteroperable.mixin.vista;

import com.cio.createinteroperable.CIOConfig;
import com.cio.createinteroperable.vista.VistaTvSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A freshly placed Vista TV isn't grid-registered yet ({@link
 * VistaTvNodeMixin}'s node fields default to "no power"), so it must start
 * dark regardless of whatever redstone signal happens to already be present
 * at the clicked position &mdash; otherwise a TV placed on already-live
 * redstone would show lit and stay that way (the node's own change-detection
 * in {@code setAppliancePowered} only re-touches the blockstate when its
 * verdict actually changes, and "unpowered" is already its default). Same
 * fix shape as the Let's Do street-lantern placement-flash fix.
 *
 * <p><b>Vista's own redstone response must be killed entirely, not just
 * out-raced</b> (real regression, fixed 2026-09-12: TVs worked with no grid
 * power at all). {@code VistaTvNodeMixin.reconcileAppliancePower()} only
 * rewrites the blockstate when <em>its own computed verdict changes</em> —
 * but Vista's un-suppressed {@code neighborChanged} kept independently
 * flipping {@code powered} straight from raw redstone the whole time. An
 * unwired TV's verdict is "off" both before and after a redstone signal
 * arrives (no DEB feed either way), so the verdict never changes and the
 * reconcile loop never notices vanilla had overwritten the blockstate to
 * "on" underneath it — the stray "on" then survived indefinitely. Same fix
 * shape as {@code BibliocraftLampBlockMixin} / {@code
 * AnotherFurnitureLampBlockMixin}: cancel the foreign block's own redstone
 * handling outright once this integration is active, so CIO's reconcile loop
 * is the <em>only</em> writer of {@code powered} — never rely on "did our
 * verdict change" when something else can also write the same property.
 * The cancelled method's other job, speaker detection, is replayed through
 * {@link VistaTvSupport#forwardSpeakerCheck}. Redstone itself is read by the
 * node every tick, on every tile of the wall (the kill switch).</p>
 */
@Mixin(targets = "net.mehvahdjukaar.vista.common.tv.TVBlock")
public abstract class VistaTvBlockMixin {

    @Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true, require = 0)
    private void cio$clearPowerOnPlacement(BlockPlaceContext context, CallbackInfoReturnable<BlockState> cir) {
        if (!CIOConfig.VISTA_TVS_REQUIRE_POWER.get()) {
            return;
        }
        BlockState placed = cir.getReturnValue();
        if (placed == null) {
            return;
        }
        Property<?> powerProp = placed.getBlock().getStateDefinition().getProperty("powered");
        if (powerProp == null) {
            return;
        }
        cir.setReturnValue(VistaTvSupport.withEnumByName(placed, powerProp, "off"));
    }

    @Inject(method = "neighborChanged", at = @At("HEAD"), cancellable = true, require = 0)
    private void cio$suppressRedstonePower(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                           BlockPos neighborPos, boolean movedByPiston, CallbackInfo ci) {
        if (!CIOConfig.VISTA_TVS_REQUIRE_POWER.get()) {
            return;
        }
        ci.cancel();
        // Vista's method also detects adjacent speakers; keep that part.
        if (neighborBlock != state.getBlock()) {
            VistaTvSupport.forwardSpeakerCheck(this, level, pos, state, neighborPos);
        }
    }
}
