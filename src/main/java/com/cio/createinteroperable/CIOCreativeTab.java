package com.cio.createinteroperable;

import com.cio.createinteroperable.compat.ElectroEnergeticsCompat;
import com.cio.createinteroperable.compat.IdenDecorCompat;
import com.cio.createinteroperable.compat.PowerGridCompat;
import com.simibubi.create.AllCreativeModeTabs;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Previously this mod had no CreativeModeTab at all (a known, documented gap
 * — every item was only reachable via /give). Pattern here is lifted from
 * CEE's own real, working CEECreativeTab (decompiled — the only local addon
 * actually built against this exact NeoForge/MC version, so the most
 * trustworthy reference for the real API shape): a DeferredRegister keyed on
 * Registries.CREATIVE_MODE_TAB, a single tab positioned via withTabsBefore
 * relative to one of Create's own tabs (AllCreativeModeTabs is public,
 * confirmed by CEE referencing it directly the same way), and an explicit
 * displayItems list rather than a registry scan.
 *
 * Icon is the Domestic Power Kit (DEB_RECTIFIER on PG, CEE_DEB_RECTIFIER as
 * the CEE-only fallback, STEAM_OUTLET if neither grid mod is present) — CORE
 * used to be the icon, but the whole Transformer/Coupler/connector "bridge
 * extras" family is disabled (see CIOBlocks.BRIDGE_EXTRAS): real, current
 * content is the Steam Outlet + Radiator end caps (grid-independent, always
 * shown), the Grid (Double) Coupler, the three Telephone variants, and the
 * DEB/Power Kit family. Brass Heater and both Redstone Switches (CPG + CEE) are real,
 * registered content (still reachable via /give) but deliberately left out
 * of this list — not ready to surface to players yet.
 */
public class CIOCreativeTab {
    private static final DeferredRegister<CreativeModeTab> REGISTER =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateInteroperable.ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
            REGISTER.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.createinteroperable"))
                    .withTabsBefore(AllCreativeModeTabs.PALETTES_CREATIVE_TAB.getId())
                    .icon(() -> new ItemStack(iconItem()))
                    .displayItems((params, output) -> {
                        boolean pg = PowerGridCompat.present();
                        boolean cee = ElectroEnergeticsCompat.present();
                        output.accept(CIOItems.STEAM_OUTLET.get());
                        output.accept(CIOItems.RADIATOR_VALVE_NORTH.get());
                        output.accept(CIOItems.RADIATOR_VALVE_SOUTH.get());
                        output.accept(CIOItems.AIRCON_VENTER.get());
                        output.accept(CIOItems.AIRCON_MOTOR_TOP.get());
                        if (pg) {
                            output.accept(CIOItems.AIRCON_MOTOR_BOTTOM.get());
                            output.accept(CIOItems.AIRCON_MOTOR_BOTTOM_240.get());
                        }
                        if (cee) {
                            output.accept(CIOItems.CEE_AIRCON_MOTOR_BOTTOM.get());
                            output.accept(CIOItems.CEE_AIRCON_MOTOR_BOTTOM_240.get());
                        }
                        if (pg && cee) {
                            output.accept(CIOItems.DOUBLE_COUPLER.get());
                            output.accept(CIOItems.TELEPHONE.get());
                        }
                        if (pg) {
                            output.accept(CIOItems.DEB_RECTIFIER_TIER1.get());
                            output.accept(CIOItems.DEB_RECTIFIER.get());
                            output.accept(CIOItems.DEB_RECTIFIER_TIER3.get());
                            output.accept(CIOItems.DEB_RECTIFIER_TIER4.get());
                            output.accept(CIOItems.CPG_TELEPHONE.get());
                            output.accept(CIOItems.CIO_DOUBLE_CONNECTOR.get());
                        }
                        if (cee) {
                            output.accept(CIOItems.CEE_DEB_RECTIFIER_TIER1.get());
                            output.accept(CIOItems.CEE_DEB_RECTIFIER.get());
                            output.accept(CIOItems.CEE_DEB_RECTIFIER_TIER3.get());
                            output.accept(CIOItems.CEE_DEB_RECTIFIER_TIER4.get());
                            output.accept(CIOItems.CEE_TELEPHONE.get());
                        }
                        if (IdenDecorCompat.present()) {
                            output.accept(CIOItems.ELECTRIC_HEAVY_BUTTON.get());
                            output.accept(CIOItems.ELECTRIC_GATE_BUTTON.get());
                            output.accept(CIOItems.ELECTRIC_HEAVY_LEVER.get());
                            output.accept(CIOItems.ELECTRIC_EMERGENCY_LEVER.get());
                            output.accept(CIOItems.ELECTRIC_LIGHT_SWITCH.get());
                            output.accept(CIOItems.ELECTRIC_POWER_SWITCH.get());
                            output.accept(CIOItems.ELECTRIC_VALVE_SWITCH.get());
                            output.accept(CIOItems.ELECTRIC_BLAST_LEVER.get());
                            output.accept(CIOItems.ELECTRIC_CORE_BUTTON_CONTROL_PANEL.get());
                            output.accept(CIOItems.ELECTRIC_CORE_LEVER_CONTROL_PANEL.get());
                        }
                    })
                    .build());

    /** CPG Domestic Power Kit on a PG install, its CEE twin otherwise — never null in any of the 3 supported configs. */
    private static Item iconItem() {
        if (PowerGridCompat.present()) {
            return CIOItems.DEB_RECTIFIER.get();
        }
        if (ElectroEnergeticsCompat.present()) {
            return CIOItems.CEE_DEB_RECTIFIER.get();
        }
        return CIOItems.STEAM_OUTLET.get();
    }

    public static void register(IEventBus modEventBus) {
        REGISTER.register(modEventBus);
    }
}
