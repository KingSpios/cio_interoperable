package com.cio.createinteroperable.mixin;

import com.cio.createinteroperable.CIOFluids;
import com.simibubi.create.content.fluids.FluidNetwork;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Raises the floor on how much steam a plain (un-pumped) Create pipe run can
 * carry per tick, confirmed by reading {@code FluidNetwork#tick} directly:
 * <pre>
 * transferSpeed = (int) Math.max(1, pipeConnection.pressure.get(true) / 2f);
 * </pre>
 * With no real Mechanical Pump anywhere on the run — our Steam Outlet is
 * deliberately a plain {@code RESERVOIR}, never a pump (see
 * {@code SteamOutletBlockEntity#addBehaviours}'s own doc on why a dead-end
 * source can't legally be a Pipes n Physics pump either) — nothing ever calls
 * {@code addPressure} on these connections, so {@code pressure.get(true)}
 * stays 0 forever and this floor of exactly 1 mB/tick is what actually runs,
 * regardless of a pipe's own buffer size. This is the literal, sourced cause
 * of a real reported bug: "pipes hold 250mb of steam and deplete at 1mb
 * tick" — 250/1 is a multi-minute drain for one pipe segment, nowhere near
 * enough to feed a Multi Radiator's Steam Hearth.
 * <p>
 * Only raises the floor for OUR steam fluid (checked against the network's
 * own {@code fluid} field) — every other fluid keeps vanilla's exact 1 mB/t
 * unpumped floor unchanged.
 * <p>
 * Whether this mixin actually fires depends on whether vanilla's own
 * {@code FluidTransportBehaviour.tick()} (the call chain that reaches this
 * method) still runs at all: Pipes n Physics' own {@code PipeHeartbeatMixin}
 * cancels that vanilla tick entirely once its hydraulic engine is enabled
 * (confirmed by reading its real source, including its own doc comment on
 * why), replacing per-tick fluid movement with its own graph solve — in that
 * case this mixin is inert (harmless, but this specific floor is no longer
 * what's limiting throughput; PnP's own solve governs it instead, via
 * {@code PipesNPhysicsConfig}'s {@code pipeConductance} and our fluid's own
 * registered viscosity — see {@code CIOFluids.STEAM_TYPE}'s viscosity doc for
 * the other, PnP-side half of this same fix). This mixin's value is for
 * vanilla Create pipes (Pipes n Physics absent, or its engine disabled), and
 * costs nothing when it isn't the active code path.
 */
@Mixin(FluidNetwork.class)
public abstract class FluidNetworkSteamFlowMixin {
    @Shadow
    FluidStack fluid;

    /**
     * mB/tick a plain, unpumped run carries our steam at, replacing vanilla's
     * hardcoded floor of 1 for this fluid only — enough to comfortably feed a
     * multi-segment Multi Radiator run (see RadiatorValveNorthBlockEntity's
     * own throughput scale) without needing a real Mechanical Pump anywhere
     * on the line.
     */
    private static final float STEAM_MIN_TRANSFER_MB_PER_TICK = 64f;

    @Redirect(
            method = "tick()V",
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F")
    )
    private float createinteroperable$boostSteamTransferFloor(float a, float b) {
        if (fluid != null && !fluid.isEmpty() && fluid.getFluid() == CIOFluids.STEAM_STILL.get()) {
            return Math.max(STEAM_MIN_TRANSFER_MB_PER_TICK, b);
        }
        return Math.max(a, b);
    }
}
