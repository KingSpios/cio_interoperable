package com.cio.createinteroperable.compat;

import com.cio.createinteroperable.CreateInteroperable;
import com.george_vi.electroenergetics.CEEPartialModels;
import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.CEEWireTypes;
import com.george_vi.electroenergetics.simulation.WireType;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * CEE wire types that exist only as hidden stand-ins for PnW wires.
 *
 * <p>CEE's pantographs (server contact in {@code CatenaryModule}, and the
 * client arm animation) only ride wires whose type has zero sag, and every
 * built-in conductor sags. PnW's contact wire is drawn perfectly taut, so its
 * mirror gets this taut copper type. It never drops an item: a mirror that
 * burns out must not hand the player free CEE wire (PnW handles its own drop).</p>
 */
public final class PnwCeeWireTypes {
    private static final DeferredRegister<WireType> WIRE_TYPES = DeferredRegister.create(CEERegistries.WIRE_TYPE, CreateInteroperable.ID);

    public static final DeferredHolder<WireType, WireType> CONTACT_WIRE = WIRE_TYPES.register("pnw_contact_wire",
            () -> new WireType.Builder(CEEPartialModels.COPPER_WIRE_SEGMENT)
                    .resistance(() -> CEEWireTypes.COPPER.get().getResistance())
                    .maxTemperature(() -> CEEWireTypes.COPPER.get().getMaxTemperature())
                    .maxInsulationVoltage(() -> CEEWireTypes.COPPER.get().maxInsulationVoltage())
                    .droppedItem(() -> Items.AIR)
                    .sag(0f)
                    .build());

    private PnwCeeWireTypes() {
    }

    public static void register(IEventBus modEventBus) {
        WIRE_TYPES.register(modEventBus);
    }
}
