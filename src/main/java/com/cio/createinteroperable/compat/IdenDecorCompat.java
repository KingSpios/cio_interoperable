package com.cio.createinteroperable.compat;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;

/**
 * Single source of truth for "is Iden's Decor present". Gates CIO's Electric
 * variants of Iden's buttons / switches ({@code com.cio.createinteroperable.iden}),
 * which reuse Iden's own blockstate models and are crafted from Iden's items.
 * CIO never compiles against Iden's classes, so nothing here can fail to link
 * when it's absent &mdash; the gate only keeps blocks whose models and recipes
 * would be missing from registering.
 */
public final class IdenDecorCompat {

    public static final String MOD_ID = "iden_decor";

    private IdenDecorCompat() {
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
