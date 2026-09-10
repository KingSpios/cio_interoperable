package com.cio.createinteroperable.deb;

import net.minecraft.nbt.CompoundTag;

/**
 * The ON/OFF resolution shared by the Power Grid and Electro Energetics
 * Redstone Switch BlockEntities.
 *
 * <p>A redstone signal is the base state. A bare right-click sets a manual
 * override that holds until the redstone input next <em>changes</em> (rising
 * OR falling edge), at which point control reverts to the wire &mdash; exactly
 * the "redstone + manual override" behaviour requested.</p>
 */
final class RedstoneSwitchState {
    /** 0 = follow redstone, 1 = forced ON, 2 = forced OFF. */
    byte override;
    boolean lastRedstone;

    boolean effective() {
        return override == 1 || (override == 0 && lastRedstone);
    }

    /**
     * Feed the current neighbour-signal reading. Returns {@code true} when the
     * signal changed (and therefore {@link #effective()} may have too, and the
     * manual latch has just been released).
     */
    boolean pollRedstone(boolean signal) {
        if (signal == lastRedstone) {
            return false;
        }
        lastRedstone = signal;
        override = 0; // any edge releases the manual latch
        return true;
    }

    /** Manual right-click: force to the opposite of whatever the switch shows right now. */
    void toggle() {
        override = effective() ? (byte) 2 : (byte) 1;
    }

    void save(CompoundTag tag) {
        tag.putByte("SwitchOverride", override);
        tag.putBoolean("SwitchLastRedstone", lastRedstone);
    }

    void load(CompoundTag tag) {
        override = tag.getByte("SwitchOverride");
        lastRedstone = tag.getBoolean("SwitchLastRedstone");
    }
}
