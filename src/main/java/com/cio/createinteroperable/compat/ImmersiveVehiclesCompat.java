package com.cio.createinteroperable.compat;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;

/**
 * Single source of truth for "is Immersive Vehicles (formerly Minecraft
 * Transport Simulator, mod id {@code mts}) present". Gates the AA Base Plate /
 * Spotlight power integration ({@code com.cio.createinteroperable.mts}) and its
 * mixin config ({@code MtsMixinPlugin}). Safe from mixin-plugin time onwards,
 * like {@link IdenDecorCompat}.
 *
 * <p><b>The whole Immersive Vehicles integration is purely experimental</b>: it
 * lives on the {@code experimental} branch only, patches IV internals through
 * mixins, and has not been tested in real play.
 */
public final class ImmersiveVehiclesCompat {

    public static final String MOD_ID = "mts";

    private ImmersiveVehiclesCompat() {
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
