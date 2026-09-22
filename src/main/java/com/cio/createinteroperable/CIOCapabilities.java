package com.cio.createinteroperable;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Exposes the Steam Outlet's, Brass Heater's, and Multi Radiator's internal
 * steam/water buffers as
 * plain NeoForge IFluidHandler capabilities. This is the ONLY thing needed
 * for Create's pipe network to recognize them — pipes look up this exact
 * capability generically on whatever block they're touching (confirmed via
 * FluidPropagator#hasFluidCapability / PumpBlockEntity#hasReachedValidEndpoint,
 * both plain {@code level.getCapability(Capabilities.FluidHandler.BLOCK, ...)}
 * calls with no Create-specific marker interface involved).
 * <p>
 * <b>Never write a lambda here whose inferred parameter type is a
 * PG-extending BlockEntity</b> (e.g. one whose class {@code extends}
 * {@code org.patryk3211.powergrid...ElectricBlockEntity}) — this class is
 * {@code @EventBusSubscriber}, so NeoForge's {@code AutomaticEventSubscriber}
 * reflects over ALL its declared methods (including synthetic lambda-
 * desugared ones) at mod construct time, which resolves that PG type
 * regardless of any runtime {@code != null} guard around the registration
 * call itself. See {@link AirconCapabilitySupport}'s own doc for the real
 * incident this caused and the fix (route through a plain, non-subscriber
 * helper class instead).
 */
@EventBusSubscriber(modid = CreateInteroperable.ID)
public class CIOCapabilities {
    @SubscribeEvent
    static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CIOBlockEntities.STEAM_OUTLET.get(),
                (be, side) -> be.getSteamHandler(side));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CIOBlockEntities.BRASS_HEATER.get(),
                (be, side) -> be.getSteamHandler(side));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CIOBlockEntities.RADIATOR_VALVE_NORTH.get(),
                (be, side) -> be.getSteamHandler(side));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CIOBlockEntities.RADIATOR_VALVE_SOUTH.get(),
                (be, side) -> be.getWaterHandler(side));

        if (CIOBlockEntities.AIRCON_MOTOR_BOTTOM != null) {
            // Routed through a plain (non-@EventBusSubscriber) helper class —
            // see AirconCapabilitySupport's own doc for why the lambda can't
            // live directly in this class.
            AirconCapabilitySupport.registerPgMotorBottom(event);
        }
        if (CIOBlockEntities.CEE_AIRCON_MOTOR_BOTTOM != null) {
            event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CIOBlockEntities.CEE_AIRCON_MOTOR_BOTTOM.get(),
                    (be, side) -> be.getFluidHandler(side));
        }
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CIOBlockEntities.AIRCON_VENTER.get(),
                (be, side) -> be.getFluidHandler(side));
    }
}
