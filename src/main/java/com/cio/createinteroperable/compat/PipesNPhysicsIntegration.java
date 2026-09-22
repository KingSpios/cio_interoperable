package com.cio.createinteroperable.compat;

import com.cio.createinteroperable.CIOBlocks;
import de.devin.pipesnphysics.api.FluidHandlerApi;
import de.devin.pipesnphysics.api.FluidHandlerRole;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Declares this mod's fluid-handler blocks' roles to Pipes n Physics — the
 * mod's own documented, supported integration surface for a third party
 * (see its real {@code de.devin.pipesnphysics.api.FluidHandlerApi}), not a
 * mixin into its internal engine classes (which the API package's own doc
 * explicitly warns move between releases).
 * <p>
 * Every block here exposes a real, capacity-bounded {@code FluidTank} (see
 * {@code CIOCapabilities}) — a normal reservoir, not a paired/passthrough
 * relay. Left undeclared, PnP's own {@code RelayDetector} learns block
 * ROLES purely from behaviour: a position whose contents grow WITHOUT PnP
 * itself having filled it — "spontaneous gain" — is its signature for a
 * relay (a docking connector, a hose), and after
 * {@code RelayDetector.STRIKES_TO_DEMOTE} (5) such ticks it permanently
 * demotes that position to a bottomless, drain-priority RELAY for the rest
 * of the session.
 * <p>
 * The Steam Outlet's {@code steamTank} and the South Radiator Valve's
 * {@code waterTank} both fill themselves every tick straight from
 * production (boiler heat / condensing steam — see
 * {@code SteamOutletBlockEntity#tick}/{@code RadiatorValveSouthBlockEntity#tick}),
 * entirely outside any PnP-mediated transfer — exactly the "spontaneous
 * gain" pattern the detector is built to catch, even though neither is a
 * relay. An explicit role (tag OR code — see PnP's real
 * {@code HandlerRoles#isExempt}) vetoes the learned detector outright, so
 * declaring RESERVOIR here removes the misclassification risk instead of
 * relying on it never accruing 5 strikes. The Brass Heater and North
 * Radiator Valve only ever DRAIN their own buffers (a spontaneous LOSS,
 * which forgives a strike rather than accruing one), so they were never at
 * real risk — declared anyway for the same explicit, undefined-heuristic-free
 * treatment as the rest of the family.
 */
public final class PipesNPhysicsIntegration {
    private PipesNPhysicsIntegration() {
    }

    /** Call only when {@link PipesNPhysicsCompat#present()} — see CreateInteroperable's constructor. */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(PipesNPhysicsIntegration::commonSetup);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            FluidHandlerApi.setRole(CIOBlocks.STEAM_OUTLET.get(), FluidHandlerRole.RESERVOIR);
            FluidHandlerApi.setRole(CIOBlocks.BRASS_HEATER.get(), FluidHandlerRole.RESERVOIR);
            FluidHandlerApi.setRole(CIOBlocks.RADIATOR_VALVE_NORTH.get(), FluidHandlerRole.RESERVOIR);
            FluidHandlerApi.setRole(CIOBlocks.RADIATOR_VALVE_SOUTH.get(), FluidHandlerRole.RESERVOIR);
            // Aircon — both self-fill their own internal tanks every tick
            // outside any PnP-mediated transfer (cold_air/water production,
            // local cold->hot conversion), same "spontaneous gain" reasoning
            // as the rest of this family.
            if (CIOBlocks.AIRCON_MOTOR_BOTTOM != null) {
                FluidHandlerApi.setRole(CIOBlocks.AIRCON_MOTOR_BOTTOM.get(), FluidHandlerRole.RESERVOIR);
            }
            FluidHandlerApi.setRole(CIOBlocks.AIRCON_VENTER.get(), FluidHandlerRole.RESERVOIR);
        });
    }
}
