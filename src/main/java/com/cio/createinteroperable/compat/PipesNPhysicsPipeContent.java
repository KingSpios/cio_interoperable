package com.cio.createinteroperable.compat;

import com.cio.createinteroperable.CIOFluids;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import de.devin.pipesnphysics.engine.store.PipeFluidCell;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Reads Pipes n Physics' own REAL per-pipe-block steam content — the exact
 * mB currently sitting in one specific pipe segment, not an approximation —
 * so the open-end vent visual ({@code SteamOpenEndScanner}) can be gated on
 * "does THIS pipe block actually hold any steam right now" instead of only
 * "does the feeding Steam Outlet's own tank hold something."
 * <p>
 * <b>This is a deliberate, explicitly-requested exception to this project's
 * usual rule of never touching Pipes n Physics' internal engine classes.</b>
 * {@code de.devin.pipesnphysics.engine.store.PipeFluidCell} is NOT in PnP's
 * public {@code api} package — it's the internal engine interface every
 * {@code FluidTransportBehaviour} is mixed to implement
 * (see PnP's own {@code FluidTransportBehaviourMixin}), and PnP's own
 * {@code EndpointApi} doc says plainly: "the engine's own classes move
 * between packages between releases... a mixin into them breaks on update —
 * hard, at classload." There is no public-API equivalent for reading a
 * pipe's real content; this is the only way to get the signal at all.
 * <p>
 * Guarded specifically against that documented risk: every real access is
 * wrapped in a {@code catch (Throwable)}. If a future PnP release
 * restructures this interface incompatibly with what CIO was compiled
 * against, {@link #isDefinitelyEmpty} fails OPEN (returns {@code false} —
 * "not confirmed empty, don't suppress") rather than throwing — so the worst
 * case on a PnP update is silently falling back to the coarser
 * Outlet-buffer-only gate {@code SteamOutletBlockEntity} already has, never
 * a crash. Only ever called from code already gated on
 * {@link PipesNPhysicsCompat#present()} (this class itself has a hard
 * compile-time reference to the PnP-only interface, so it must never be
 * touched — not even classloaded — when PnP is absent, same rule as
 * {@link PipesNPhysicsIntegration}).
 */
public final class PipesNPhysicsPipeContent {
    private PipesNPhysicsPipeContent() {
    }

    /**
     * @return {@code true} ONLY when PnP's own real per-cell tracking positively confirms this
     * exact pipe block currently holds none of our steam — the one case it's safe to suppress the
     * vent visual on. {@code false} covers every other case: real steam confirmed present, the
     * pipe doesn't (yet, or ever) expose {@link PipeFluidCell}, or the read failed against an
     * incompatible PnP version — none of those should ever hide a visual that might be correct.
     */
    public static boolean isDefinitelyEmpty(FluidTransportBehaviour pipe) {
        try {
            if (pipe instanceof PipeFluidCell cell) {
                FluidStack content = cell.pipesnphysics$content();
                return content == null
                        || content.isEmpty()
                        || content.getAmount() <= 0
                        || content.getFluid() != CIOFluids.STEAM_STILL.get();
            }
        } catch (Throwable t) {
            // PnP's internal engine shape changed since this build was
            // compiled against it — fail open, see class doc.
        }
        return false;
    }
}
