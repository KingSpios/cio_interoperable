package com.cio.createinteroperable;

import com.cio.createinteroperable.thermal.TemperatureMods;
import com.simibubi.create.AllCreativeModeTabs;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The one and only creative tab for all of Create: Interoperable. Modules
 * (see {@link com.cio.createinteroperable.modules.CIOModules}) never make a tab
 * of their own: they {@link #add} their items to a {@link Section} of this one
 * while registering, so the tab only ever lists items whose mods are present.
 *
 * <p>Pattern lifted from CEE's own CEECreativeTab (decompiled): a
 * DeferredRegister keyed on Registries.CREATIVE_MODE_TAB, positioned via
 * withTabsBefore relative to one of Create's own tabs, and an explicit
 * displayItems list rather than a registry scan.</p>
 *
 * <p>Sections keep the order the tab had before modules existed: inside a
 * section, entries appear in registration order (core first, then Power Grid,
 * then Electro Energetics, as {@code CIOModules} registers them). The icon is
 * the highest-priority {@link #offerIcon offer}: {@link CIOItems#TAB_ICON} (an
 * enlarged Domestic Power Kit) when either grid mod's Power Kits exist, else the
 * Steam Outlet. The Brass Heater is registered
 * content (reachable via /give) but deliberately not listed, and so are the
 * CPG / CEE Redstone Switches (experimental branch only). The Steam Hearth and
 * Aircon parts are listed only while {@link TemperatureMods#climateBlocksEnabled()}
 * (see {@link #CLIMATE_BLOCKS}).</p>
 */
public class CIOCreativeTab {
    private static final DeferredRegister<CreativeModeTab> REGISTER =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateInteroperable.ID);

    /** Tab layout, top to bottom. */
    public enum Section {
        /** Steam Outlet, radiators, aircon. */
        CLIMATE,
        /** PG<->CEE bridge content (needs both grid mods). */
        BRIDGE,
        /** Power Kits, telephones, connectors, transformer, streetlight. */
        DEVICES,
        /** Iden's Decor Electric buttons & switches. */
        SWITCHES,
        /** Copycat Pipe Casing. */
        DECORATION
    }

    private record Entry(Section section, Supplier<? extends ItemLike> item, BooleanSupplier shown) {
    }

    /** Visibility of the Steam Hearth and Aircon parts; pass to {@link #add(Section, Supplier, BooleanSupplier)}. */
    public static final BooleanSupplier CLIMATE_BLOCKS = TemperatureMods::climateBlocksEnabled;

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static Supplier<? extends ItemLike> icon = CIOItems.STEAM_OUTLET;
    private static int iconPriority = 0;

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
            REGISTER.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.createinteroperable"))
                    .withTabsBefore(AllCreativeModeTabs.PALETTES_CREATIVE_TAB.getId())
                    .icon(() -> new ItemStack(icon.get()))
                    .displayItems((params, output) -> ENTRIES.stream()
                            .filter(entry -> entry.shown().getAsBoolean())
                            .sorted(Comparator.comparing(Entry::section)) // stable: keeps registration order
                            .forEach(entry -> output.accept(entry.item().get())))
                    .build());

    /** Lists an item in this tab. Call while registering (mod construction). */
    public static void add(Section section, Supplier<? extends ItemLike> item) {
        add(section, item, () -> true);
    }

    /** Lists an item in this tab while {@code shown} holds, checked each time the tab's contents are built. */
    public static void add(Section section, Supplier<? extends ItemLike> item, BooleanSupplier shown) {
        ENTRIES.add(new Entry(section, item, shown));
    }

    /** Proposes the tab icon; the highest priority wins (the Steam Outlet default is 0). */
    public static void offerIcon(int priority, Supplier<? extends ItemLike> item) {
        if (priority > iconPriority) {
            iconPriority = priority;
            icon = item;
        }
    }

    public static void register(IEventBus modEventBus) {
        add(Section.CLIMATE, CIOItems.STEAM_OUTLET);
        add(Section.CLIMATE, CIOItems.RADIATOR_VALVE_NORTH, CLIMATE_BLOCKS);
        add(Section.CLIMATE, CIOItems.RADIATOR_VALVE_SOUTH, CLIMATE_BLOCKS);
        add(Section.CLIMATE, CIOItems.AIRCON_VENTER, CLIMATE_BLOCKS);
        add(Section.CLIMATE, CIOItems.AIRCON_MOTOR_TOP, CLIMATE_BLOCKS);
        add(Section.DECORATION, CIOItems.COPYCAT_PIPE_CASING);
        REGISTER.register(modEventBus);
    }
}
