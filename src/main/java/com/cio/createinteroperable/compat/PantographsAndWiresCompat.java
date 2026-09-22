package com.cio.createinteroperable.compat;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;

/** Presence gate for the experimental Pantographs & Wires / CEE bridge. */
public final class PantographsAndWiresCompat {
    public static final String MOD_ID = "pantographsandwires";

    private PantographsAndWiresCompat() {
    }

    public static boolean present() {
        ModList list = ModList.get();
        if (list != null) {
            return list.isLoaded(MOD_ID);
        }
        LoadingModList loading = LoadingModList.get();
        return loading != null && loading.getModFileById(MOD_ID) != null;
    }
}
