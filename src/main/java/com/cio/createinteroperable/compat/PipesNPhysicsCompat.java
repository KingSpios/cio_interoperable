package com.cio.createinteroperable.compat;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;

/**
 * Single source of truth for "is Create: Pipes n Physics present" — same
 * idiom as {@link ColdSweatCompat}/{@link PowerGridCompat}.
 */
public final class PipesNPhysicsCompat {

    /** Pipes n Physics' own mod id ({@code de.devin.pipesnphysics}, confirmed via its real neoforge.mods.toml). */
    public static final String MOD_ID = "pipesnphysics";

    private PipesNPhysicsCompat() {
    }

    public static boolean present() {
        ModList list = ModList.get();
        if (list != null) {
            return list.isLoaded(MOD_ID);
        }
        LoadingModList loading = LoadingModList.get();
        return loading != null && loading.getModFileById(MOD_ID) != null;
    }

    /**
     * The installed Pipes n Physics version string, or null if it is absent or unreadable. Reads
     * {@link LoadingModList}, which exists from the earliest mixin-plugin load onward.
     */
    public static String version() {
        LoadingModList loading = LoadingModList.get();
        if (loading == null) {
            return null;
        }
        var file = loading.getModFileById(MOD_ID);
        if (file == null || file.getMods().isEmpty()) {
            return null;
        }
        return file.getMods().get(0).getVersion().toString();
    }
}
