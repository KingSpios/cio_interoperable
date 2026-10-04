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

    public static void add(TelephoneNode be) {
        LOADED.add(be);
    }

    /**
     * Every loaded phone that lives on a server level. Server thread only (LOADED is
     * unsynchronised); singleplayer's client copies are filtered out.
     */
    public static java.util.List<TelephoneNode> serverPhones() {
        java.util.List<TelephoneNode> out = new java.util.ArrayList<>();
        for (TelephoneNode node : LOADED) {
            if (node instanceof net.minecraft.world.level.block.entity.BlockEntity be
                    && be.getLevel() instanceof net.minecraft.server.level.ServerLevel) {
                out.add(node);
            }
        }
        return out;
    }

    public static void remove(TelephoneNode be) {
        LOADED.remove(be);
    }

    @Nullable
    public static TelephoneNode findByNumber(TelephoneNode caller, String number) {
        for (TelephoneNode be : LOADED) {
            if (sameSide(caller, be) && !be.isBusy() && caller.canReach(be) && number.equals(be.ownNumber())) {
                return be;
            }
        }
        return null;
    }

    /** Same lookup as {@link #findByNumber}, but without the busy filter — for display purposes (e.g. hover tooltips), not actual dialing. */
    @Nullable
    public static TelephoneNode findAnyByNumber(TelephoneNode caller, String number) {
        for (TelephoneNode be : LOADED) {
            if (sameSide(caller, be) && caller.canReach(be) && number.equals(be.ownNumber())) {
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
    public static boolean isNumberTaken(TelephoneNode self, int areaCode, String numberText) {
        if (numberText.isEmpty()) {
            return false;
        }
        for (TelephoneNode be : LOADED) {
            if (be != self && sameSide(self, be) && areaCode == be.getAreaCode() && numberText.equals(be.getOwnNumberText())) {
                return true;
            }
        }
        return false;
    }

    /**
     * In singleplayer the client and the integrated server share this one static
     * set, so every phone is in it twice (its client and its server block
     * entity). Without this, a server-side lookup could resolve to the client
     * copy &mdash; and {@link #isNumberTaken} would see a phone's own client copy
     * as a clash, refusing to re-save an unchanged number.
     */
    private static boolean sameSide(TelephoneNode a, TelephoneNode b) {
        if (a instanceof net.minecraft.world.level.block.entity.BlockEntity ba
                && b instanceof net.minecraft.world.level.block.entity.BlockEntity bb
                && ba.getLevel() != null && bb.getLevel() != null) {
            return ba.getLevel().isClientSide == bb.getLevel().isClientSide;
        }
        return true;
    }

    @Nullable
    public static TelephoneNode get(@Nullable Level level, @Nullable BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof TelephoneNode be ? be : null;
    }
}
