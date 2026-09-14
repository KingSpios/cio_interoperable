package com.cio.createinteroperable.compat;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;

/**
 * Single source of truth for "is Cold Sweat present" — mirrors
 * {@link com.cio.createinteroperable.grid.CrayfishCompat} exactly.
 * <p>
 * The heat-radiation integration (see {@link ColdSweatIntegration}) was
 * originally a pure datapack {@code block_temp} JSON, on the (wrong)
 * assumption that Cold Sweat's dynamic-registry JSON loader picks up
 * third-party mod-jar data automatically. A real in-game test showed
 * {@code "Loaded 0 block temperatures."} with no decode errors at all — the
 * JSON path never even found our files. Cold Sweat's own real Create
 * integration (see its bundled {@code CreateFluidTankTemp}/{@code CreateFluidPipeTemp})
 * uses plain Java {@code BlockTemp} registration via the public
 * {@code BlockTempRegisterEvent} instead, which is the actually-proven path —
 * see {@link ColdSweatIntegration}. That means Cold Sweat is now a real
 * compile-time dependency (see build.gradle), gated as {@code type="optional"}
 * in neoforge.mods.toml exactly like Power Grid/Electro Energetics/Crayfish.
 */
public final class ColdSweatCompat {

    /** Cold Sweat's own mod id (confirmed via its real {@code ColdSweat.MOD_ID} constant). */
    public static final String MOD_ID = "cold_sweat";

    private ColdSweatCompat() {
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
