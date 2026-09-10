package com.cio.createinteroperable;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Exposes the Steam Outlet's and Brass Heater's internal steam buffers as
 * plain NeoForge IFluidHandler capabilities. This is the ONLY thing needed
 * for Create's pipe network to recognize them — pipes look up this exact
 * capability generically on whatever block they're touching (confirmed via
 * FluidPropagator#hasFluidCapability / PumpBlockEntity#hasReachedValidEndpoint,
 * both plain {@code level.getCapability(Capabilities.FluidHandler.BLOCK, ...)}
 * calls with no Create-specific marker interface involved).
 */
@EventBusSubscriber(modid = CreateInteroperable.ID)
public class CIOCapabilities {
    @SubscribeEvent
    static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CIOBlockEntities.STEAM_OUTLET.get(),
                (be, side) -> be.getSteamHandler(side));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CIOBlockEntities.BRASS_HEATER.get(),
                (be, side) -> be.getSteamHandler(side));
    }
}
