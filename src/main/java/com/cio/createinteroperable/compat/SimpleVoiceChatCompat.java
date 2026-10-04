package com.cio.createinteroperable.compat;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;

/** Single source of truth for "is Simple Voice Chat present" — same idiom as {@link PipesNPhysicsCompat}. */
public final class SimpleVoiceChatCompat {

    /** Simple Voice Chat's own mod id. */
    public static final String MOD_ID = "voicechat";

    private SimpleVoiceChatCompat() {
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
