package com.cio.createinteroperable.compat;

import com.cio.createinteroperable.CreateInteroperable;
import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.content.railway_electrification.pantograph.PantographType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * CEE pantograph type describing Pantographs &amp; Wires' pantograph, so CEE's
 * server-side contact logic ({@code CatenaryModule}) collects current through
 * it exactly as through its own. CEE saves a train's pantographs by this
 * registry id, so it must be registered, not just constructed.
 *
 * <p>CEE accepts a wire between {@code topOffset} and {@code topOffset + reach}
 * above the pantograph block's bottom, within {@code sidewaysReach} to either
 * side. PnW's head sweeps from 13 px (0.8165) to 0.8165 + 3.6 blocks above the
 * block bottom and is 2.5 blocks wide, so the window matches what PnW animates.</p>
 */
public final class PnwCeePantographTypes {
    private static final DeferredRegister<PantographType> TYPES = DeferredRegister.create(CEERegistries.PANTOGRAPH_TYPE, CreateInteroperable.ID);

    /** PnW's {@code PantographBlockEntity.MIN_HEIGHT}: 13 px plus one pixel of a pixel. */
    private static final float PNW_MIN_HEIGHT = (13f + 1f / 16f) / 16f;
    /** PnW's {@code PantographBlockEntity.MAX_HEIGHT}. */
    private static final float PNW_TRAVEL = 3.6f;
    /** Half of PnW's {@code PantographBlockEntity.MAX_WIDTH} (2.5). */
    private static final float PNW_HALF_WIDTH = 1.25f;

    // (reach, backOffset, topOffset, sidewaysReach)
    public static final DeferredHolder<PantographType, PantographType> PNW_PANTOGRAPH = TYPES.register("pnw_pantograph",
            () -> new PantographType(PNW_TRAVEL, 0f, PNW_MIN_HEIGHT, PNW_HALF_WIDTH));

    private PnwCeePantographTypes() {
    }

    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
    }
}
