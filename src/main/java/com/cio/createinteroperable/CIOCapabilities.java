package com.cio.createinteroperable;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Exposes the Steam Outlet's, Brass Heater's, Multi Radiator's and Aircon
 * Venter's internal buffers as plain NeoForge IFluidHandler capabilities.
 * This is the ONLY thing needed for Create's pipe network to recognize them —
 * pipes look up this exact capability generically on whatever block they're
 * touching (confirmed via FluidPropagator#hasFluidCapability /
 * PumpBlockEntity#hasReachedValidEndpoint, both plain
 * {@code level.getCapability(Capabilities.FluidHandler.BLOCK, ...)} calls with
 * no Create-specific marker interface involved).
 *
 * <p>Only content that exists on every install belongs here. The PG / CEE
 * Aircon motor bottoms register theirs from their own module. <b>Never write a
 * lambda here whose inferred parameter type is a PG- or CEE-rooted
 * BlockEntity</b>: this class is {@code @EventBusSubscriber}, so NeoForge's
 * {@code AutomaticEventSubscriber} reflects over ALL its declared methods
 * (synthetic lambda bodies included) at mod construction, resolving that type
 * regardless of any runtime guard. That once crashed every CEE-only install
 * (NoClassDefFoundError for PG's ElectricBlockEntity); {@code LoadSafetyTest}
 * now fails the build on it.</p>
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
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CIOBlockEntities.AIRCON_VENTER.get(),
                (be, side) -> be.getFluidHandler(side));
    }
}
