package com.cio.createinteroperable.mixin.pnp;

import org.spongepowered.asm.mixin.Mixin;

/**
 * Keeps Pipes n Physics from resolving Electro Energetics' Electric Pump into a
 * turbine.
 * <p>
 * CEE's pump extends Create's {@code PumpBlockEntity}, so PnP hands it the
 * Mechanical Pump's role dial, which a freshly placed pump gets set to AUTO.
 * AUTO decides "turbine" for any pump that is not driven, and "driven" means a
 * kinetic source or pressure the pump publishes to its own pipe connections.
 * An electric pump has neither: it has no shaft source, and PnP's engine
 * cancels Create's pressure distribution, so nothing is ever published. It
 * therefore always resolved to TURBINE and became a passive restriction that
 * never pumped, however much power it had.
 * <p>
 * The pump's strength itself is already read correctly (PnP falls back to the
 * pump's {@code getSpeed()}, which CEE derives from the circuit voltage), so
 * only the role needs answering. This override is picked up by virtual
 * dispatch over the {@code pipesnphysics$isTurbine} PnP merges into
 * {@code PumpBlockEntity}; it deliberately names no PnP type, so it does not
 * depend on PnP's internal API. An electric pump has no rotation to give back,
 * so it is never a turbine, whatever the dial says.
 */
@Mixin(targets = "com.george_vi.electroenergetics.content.electric_pump.ElectricPumpBlockEntity", remap = false)
public abstract class CeeElectricPumpNeverTurbineMixin {
    public boolean pipesnphysics$isTurbine() {
        return false;
    }
}
