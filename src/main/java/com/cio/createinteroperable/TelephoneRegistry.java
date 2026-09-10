package com.cio.createinteroperable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

/**
 * Every currently-loaded Telephone, regardless of which of the three
 * variants (Interoperable, CPG-only, CEE-only) it is — all three implement
 * {@link TelephoneNode}, which is all this registry (and dialing) needs.
 * {@code add}/{@code remove} run unguarded from {@code setLevel}/{@code remove},
 * so in practice this is two independent instances of this same static set —
 * one per side, each only ever containing that side's own loaded block
 * entities. {@link #findByNumber} is used server-side to find "every
 * Telephone reachable from this one" for a real dial attempt — reachability
 * is {@link TelephoneNode#canReach}. {@link #findAnyByNumber} is the
 * client-side equivalent used for hover-tooltip label lookups, where a busy
 * phone's label is still worth showing.
 */
public class TelephoneRegistry {
    private static final Set<TelephoneNode> LOADED = Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    static void add(TelephoneNode be) {
        LOADED.add(be);
    }

    static void remove(TelephoneNode be) {
        LOADED.remove(be);
    }

    @Nullable
    static TelephoneNode findByNumber(TelephoneNode caller, String number) {
        for (TelephoneNode be : LOADED) {
            if (!be.isBusy() && caller.canReach(be) && number.equals(be.ownNumber())) {
                return be;
            }
        }
        return null;
    }

    /** Same lookup as {@link #findByNumber}, but without the busy filter — for display purposes (e.g. hover tooltips), not actual dialing. */
    @Nullable
    static TelephoneNode findAnyByNumber(TelephoneNode caller, String number) {
        for (TelephoneNode be : LOADED) {
            if (caller.canReach(be) && number.equals(be.ownNumber())) {
                return be;
            }
        }
        return null;
    }

    /**
     * Global — every loaded phone regardless of network, per "the addon
     * should keep track of all area codes and all phone numbers in use."
     * An empty numberText never conflicts (an un-configured phone isn't
     * really "in use" yet), so freshly-placed phones can coexist before
     * anyone sets a real number.
     */
    static boolean isNumberTaken(TelephoneNode self, int areaCode, String numberText) {
        if (numberText.isEmpty()) {
            return false;
        }
        for (TelephoneNode be : LOADED) {
            if (be != self && areaCode == be.getAreaCode() && numberText.equals(be.getOwnNumberText())) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    static TelephoneNode get(@Nullable Level level, @Nullable BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof TelephoneNode be ? be : null;
    }
}
