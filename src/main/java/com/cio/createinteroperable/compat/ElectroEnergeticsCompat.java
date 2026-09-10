package com.cio.createinteroperable.compat;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;

/**
 * Single source of truth for "is Electro Energetics present". See
 * {@link PowerGridCompat} for the full rationale — same idiom, other half of
 * the PG/CEE pair CIO bridges.
 */
public final class ElectroEnergeticsCompat {

    /** Electro Energetics' mod id ({@code com.george_vi.electroenergetics}). */
    public static final String MOD_ID = "electroenergetics";

    private ElectroEnergeticsCompat() {
    }

    /**
     * True when Electro Energetics is on the mod list. Safe from any point
     * after mod construction; uses {@link ModList} once it exists and falls
     * back to {@link LoadingModList} for very early calls.
     */
    public static boolean present() {
        ModList list = ModList.get();
        if (list != null) {
            return list.isLoaded(MOD_ID);
        }
        LoadingModList loading = LoadingModList.get();
        return loading != null && loading.getModFileById(MOD_ID) != null;
    }
}
