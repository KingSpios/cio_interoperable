package com.cio.createinteroperable.compat;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;

/**
 * Single source of truth for "is Power Grid present".
 *
 * <p>Create: Interoperable bridges Power Grid (PG) and Electro Energetics
 * (CEE); either one alone, or both together, is a supported configuration —
 * only "neither" is unsupported. Every CIO class that {@code extends}/
 * {@code implements} a PG type must never be touched (constructed, or have a
 * live method reference taken) on a code path that runs when PG is absent, or
 * the JVM throws {@code NoClassDefFoundError} the instant that class loads.
 * {@link #present()} is the guard used at every such registration/call
 * site.</p>
 */
public final class PowerGridCompat {

    /** Power Grid's mod id ({@code org.patryk3211.powergrid}). */
    public static final String MOD_ID = "powergrid";

    private PowerGridCompat() {
    }

    /**
     * True when Power Grid is on the mod list. Safe from any point after mod
     * construction; uses {@link ModList} once it exists and falls back to
     * {@link LoadingModList} for very early calls.
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
