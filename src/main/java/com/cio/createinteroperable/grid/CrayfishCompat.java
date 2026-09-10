package com.cio.createinteroperable.grid;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;

/**
 * Single source of truth for "is MrCrayfish's Refurbished Furniture present".
 *
 * <p>Create: Interoperable's furniture-power layer (the Domestic Electrical
 * Board and every third-party appliance/lamp integration) was originally built
 * directly on Crayfish's {@code IElectricityNode} graph, which made Crayfish a
 * hard dependency. Phase 1 of decoupling that keeps the exact same behaviour
 * <em>when Crayfish is installed</em> while letting the mod load without it: all
 * of the {@code com.mrcrayfish.*} coupling now lives behind this flag and behind
 * the {@code required=false} {@code createinteroperable.crayfish.mixin.json}
 * config (see {@link com.cio.createinteroperable.mixin.crayfish.CrayfishMixinPlugin}).</p>
 *
 * <p>Phase 2 adds a native appliance grid so the same features work with no
 * furniture-mod-side electricity system at all; {@link #present()} is what the
 * backend selector keys off.</p>
 */
public final class CrayfishCompat {

    /** Crayfish's mod id (Refurbished Furniture, {@code com.mrcrayfish.furniture.refurbished}). */
    public static final String MOD_ID = "refurbished_furniture";

    private CrayfishCompat() {
    }

    /**
     * True when Refurbished Furniture is on the mod list. Safe from any point
     * after mod construction; uses {@link ModList} once it exists and falls back
     * to {@link LoadingModList} for very early (mixin-plugin-era) calls.
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
