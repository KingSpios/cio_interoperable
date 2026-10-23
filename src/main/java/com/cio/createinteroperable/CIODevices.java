package com.cio.createinteroperable;

import com.cio.createinteroperable.compat.PnwTerminalDevice;
import com.cio.createinteroperable.compat.Mods;
import com.cio.createinteroperable.deb.DebCeeDevice;
import com.cio.createinteroperable.deb.RedstoneSwitchCeeDevice;
import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.content.connector.DoubleConnectorDevice;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registered against Electro Energetics' own SIMULATED_DEVICE_TYPE registry
 * (CEERegistries.SIMULATED_DEVICE_TYPE) — same pattern as CEESimulatedDevices
 * in the Electro Energetics source itself.
 *
 * <p>This whole class must only ever be touched when Electro Energetics is
 * present — enforced by guarding its one call site in
 * {@link CreateInteroperable}'s constructor, not by anything in here.
 * INTEROPERABLE backs the PG&lt;-&gt;CEE bridge features (Coupler, Double
 * Coupler, the interim Small block, the assembled multiblock), which only
 * ever register when Power Grid is ALSO present (see CIOBlocks/
 * CIOBlockEntities) — so that entry is additionally gated here on
 * {@link Mods#POWER_GRID}. TELEPHONE backs both the Interoperable
 * Telephone (needs both) AND the CEE-only Telephone (needs only CEE), so —
 * like POWER_KIT — it's unconditional in this already-CEE-gated file.</p>
 */
public class CIODevices {
    /** Registered by ElectroEnergeticsModule; this gate is what LoadSafetyTest checks it against. */
    public static final String REQUIRES = com.cio.createinteroperable.compat.Mods.Id.ELECTRO_ENERGETICS;

    private static final DeferredRegister<SimulatedDeviceType<?>> DEVICES =
            DeferredRegister.create(CEERegistries.SIMULATED_DEVICE_TYPE, CreateInteroperable.ID);

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<InteroperableDevice>> INTEROPERABLE =
            Mods.POWER_GRID.present() ? DEVICES.register("interoperable", () -> new SimulatedDeviceType<>(CreateInteroperable.rl("interoperable"),
                    ((type, level, pos, sd) -> new InteroperableDevice(level, pos, sd, type)))) : null;

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<TelephoneDevice>> TELEPHONE =
            DEVICES.register("telephone", () -> new SimulatedDeviceType<>(CreateInteroperable.rl("telephone"),
                    ((type, level, pos, sd) -> new TelephoneDevice(level, pos, sd, type))));

    /**
     * Shared by all four CEE-wired Power Kit tiers (see
     * {@link com.cio.createinteroperable.deb.CeeDebRectifierBlock}) &mdash; the
     * per-tier feed topology is pushed in from the BlockEntity each tick, so one
     * device type covers every tier.
     */
    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<DebCeeDevice>> POWER_KIT =
            DEVICES.register("power_kit", () -> new SimulatedDeviceType<>(CreateInteroperable.rl("power_kit"),
                    ((type, level, pos, sd) -> new DebCeeDevice(level, pos, sd, type))));

    /** Electro Energetics side of the CEE Redstone Switch (two independent pole resistors). */
    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<RedstoneSwitchCeeDevice>> REDSTONE_SWITCH =
            DEVICES.register("redstone_switch", () -> new SimulatedDeviceType<>(CreateInteroperable.rl("redstone_switch"),
                    ((type, level, pos, sd) -> new RedstoneSwitchCeeDevice(level, pos, sd, type))));

    /** Electro Energetics side of the CEE Aircon Motor (a single dynamic-resistance load). */
    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<AirconCeeDevice>> AIRCON_MOTOR =
            DEVICES.register("aircon_motor", () -> new SimulatedDeviceType<>(CreateInteroperable.rl("aircon_motor"),
                    ((type, level, pos, sd) -> new AirconCeeDevice(level, pos, sd, type))));

    /** Electro Energetics side of the CEE-only Compact Transformer (a 15 kW port of CEE's own Transformer). */
    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<CeeCompactTransformerDevice>> COMPACT_TRANSFORMER =
            DEVICES.register("compact_transformer", () -> new SimulatedDeviceType<>(CreateInteroperable.rl("compact_transformer"),
                    ((type, level, pos, sd) -> new CeeCompactTransformerDevice(level, pos, sd, type))));

    /** Electro Energetics side of the CEE-only Streetlight (a 120 V / 25 W photocell lamp). */
    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<CeeStreetlightDevice>> STREETLIGHT =
            DEVICES.register("streetlight", () -> new SimulatedDeviceType<>(CreateInteroperable.rl("streetlight"),
                    ((type, level, pos, sd) -> new CeeStreetlightDevice(level, pos, sd, type))));

    /** CEE-only Mini Double Connector: CEE's own DoubleConnectorDevice (two separate nodes, no bridges) under CIO's type. */
    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<DoubleConnectorDevice>> MINI_CONNECTOR =
            DEVICES.register("mini_connector", () -> new SimulatedDeviceType<>(CreateInteroperable.rl("mini_connector"),
                    ((type, level, pos, sd) -> new DoubleConnectorDevice(level, pos, sd, type))));

    /** Inert device for Pantographs &amp; Wires connectors; see {@link PnwTerminalDevice}. */
    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<PnwTerminalDevice>> PNW_TERMINAL =
            DEVICES.register("pnw_terminal", () -> new SimulatedDeviceType<>(CreateInteroperable.rl("pnw_terminal"),
                    ((type, level, pos, sd) -> new PnwTerminalDevice(level, pos, sd, type))));

    public static void register(IEventBus modEventBus) {
        DEVICES.register(modEventBus);
    }
}
