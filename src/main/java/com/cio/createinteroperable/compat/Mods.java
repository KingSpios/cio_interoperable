package com.cio.createinteroperable.compat;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;
import org.jetbrains.annotations.Nullable;

/**
 * Single source of truth for "is optional mod X present". CIO requires only
 * Create; every other mod it talks to is optional, and either Power Grid or
 * Electro Energetics alone (or both) is a supported setup.
 *
 * <p>Every CIO class that {@code extends}/{@code implements} an optional mod's
 * type must never be touched on a code path that runs when that mod is
 * absent, or the JVM throws {@code NoClassDefFoundError} the instant the class
 * loads. {@link #present()} is the guard; gated content itself lives in
 * modules (see {@link com.cio.createinteroperable.modules.CIOModules}), whose
 * {@code REQUIRES} strings are built from {@link Id} — compile-time constants,
 * which javac inlines, so checking them never loads the module.</p>
 *
 * <p>Uses FML types only, so it is safe from mixin-config-plugin time onward
 * (before {@link ModList} exists it reads {@link LoadingModList}). A result is
 * cached once a mod list was actually available: the mod set cannot change
 * after discovery.</p>
 */
public enum Mods {
    /** Create: Power Grid ({@code org.patryk3211.powergrid}). */
    POWER_GRID(Id.POWER_GRID),
    /** Create: Electro Energetics ({@code com.george_vi.electroenergetics}). */
    ELECTRO_ENERGETICS(Id.ELECTRO_ENERGETICS),
    /**
     * MrCrayfish's Refurbished Furniture ({@code com.mrcrayfish.furniture.refurbished}).
     * CIO's furniture-power layer (the Domestic Electrical Board and every
     * third-party appliance/lamp integration) was originally built directly on
     * Crayfish's {@code IElectricityNode} graph, which made Crayfish a hard
     * dependency. Decoupled since: all {@code com.mrcrayfish.*} coupling lives
     * behind this gate and the {@code required=false} *crayfish* mixin configs
     * (see {@link com.cio.createinteroperable.mixin.crayfish.CrayfishMixinPlugin}),
     * and a native appliance grid does the same job without Crayfish.
     */
    REFURBISHED_FURNITURE(Id.REFURBISHED_FURNITURE),
    /**
     * Cold Sweat. The heat-radiation integration (see {@link ColdSweatIntegration})
     * was originally a pure datapack {@code block_temp} JSON, on the (wrong)
     * assumption that Cold Sweat's dynamic-registry loader picks up third-party
     * mod-jar data: a real in-game test showed {@code "Loaded 0 block
     * temperatures."} with no decode errors at all. Cold Sweat's own Create
     * integration ({@code CreateFluidTankTemp}/{@code CreateFluidPipeTemp}) uses
     * plain Java {@code BlockTemp} registration via the public
     * {@code BlockTempRegisterEvent} instead — the actually-proven path — which
     * is why Cold Sweat is a real compile-time dependency.
     */
    COLD_SWEAT(Id.COLD_SWEAT),
    /**
     * Iden's Decor. Gates CIO's Electric variants of Iden's buttons / switches
     * and the swapped-in Iden telephone, which reuse Iden's own blockstate
     * models and are crafted from Iden's items. CIO never compiles against
     * Iden's classes, so nothing can fail to link when it's absent: the gate
     * keeps blocks whose models and recipes would be missing from registering.
     */
    IDEN_DECOR(Id.IDEN_DECOR),
    /**
     * Immersive Vehicles (formerly Minecraft Transport Simulator). <b>Purely
     * experimental</b>: experimental branch only, patches IV internals through
     * mixins, untested in real play.
     */
    IMMERSIVE_VEHICLES(Id.IMMERSIVE_VEHICLES),
    /** Pantographs &amp; Wires, for the experimental PnW / CEE conductor bridge. */
    PANTOGRAPHS_AND_WIRES(Id.PANTOGRAPHS_AND_WIRES),
    /** Create: Pipes n Physics ({@code de.devin.pipesnphysics}). */
    PIPES_N_PHYSICS(Id.PIPES_N_PHYSICS),
    /** Simple Voice Chat (the telephone voice relay). */
    SIMPLE_VOICE_CHAT(Id.SIMPLE_VOICE_CHAT);

    /**
     * Mod ids as compile-time constants, for module {@code REQUIRES} strings.
     * Each constant is named exactly like its {@link Mods} entry (LoadSafetyTest
     * relies on that to map a mixin plugin's gate to mod ids).
     */
    public static final class Id {
        public static final String POWER_GRID = "powergrid";
        public static final String ELECTRO_ENERGETICS = "electroenergetics";
        public static final String REFURBISHED_FURNITURE = "refurbished_furniture";
        /** Confirmed via Cold Sweat's own {@code ColdSweat.MOD_ID}. */
        public static final String COLD_SWEAT = "cold_sweat";
        public static final String IDEN_DECOR = "iden_decor";
        public static final String IMMERSIVE_VEHICLES = "mts";
        public static final String PANTOGRAPHS_AND_WIRES = "pantographsandwires";
        /** Confirmed via Pipes n Physics' own neoforge.mods.toml. */
        public static final String PIPES_N_PHYSICS = "pipesnphysics";
        public static final String SIMPLE_VOICE_CHAT = "voicechat";

        private Id() {
        }
    }

    private final String modId;
    private volatile Boolean present;

    Mods(String modId) {
        this.modId = modId;
    }

    public String modId() {
        return modId;
    }

    /** True when this mod is installed. Cheap after the first call. */
    public boolean present() {
        Boolean cached = present;
        if (cached != null) {
            return cached;
        }
        ModList list = ModList.get();
        if (list != null) {
            return present = list.isLoaded(modId);
        }
        LoadingModList loading = LoadingModList.get();
        if (loading == null) {
            return false; // too early to know; don't cache
        }
        return present = loading.getModFileById(modId) != null;
    }

    /**
     * The installed version string, or null if the mod is absent or its
     * version is unreadable. Reads {@link LoadingModList}, which exists from
     * the earliest mixin-plugin load onward.
     */
    @Nullable
    public String version() {
        LoadingModList loading = LoadingModList.get();
        if (loading == null) {
            return null;
        }
        var file = loading.getModFileById(modId);
        if (file == null || file.getMods().isEmpty()) {
            return null;
        }
        return file.getMods().get(0).getVersion().toString();
    }

    /** {@link #present()} for any mod id, cached for the ones listed here. */
    public static boolean isLoaded(String modId) {
        for (Mods mod : values()) {
            if (mod.modId.equals(modId)) {
                return mod.present();
            }
        }
        ModList list = ModList.get();
        if (list != null) {
            return list.isLoaded(modId);
        }
        LoadingModList loading = LoadingModList.get();
        return loading != null && loading.getModFileById(modId) != null;
    }
}
