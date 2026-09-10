package com.cio.createinteroperable.grid;

/**
 * Which Create electrical grid an appliance node belongs to, for the native
 * (no-Crayfish) linking UI. A CPG connector in hand reveals / links {@link #CPG}
 * nodes and the wider network of any CPG Domestic Electrical Board; a CEE
 * connector does the same for {@link #CEE}. An unconnected appliance is
 * {@link #NONE} and shows for either connector so it can be wired up.
 */
public enum GridAffinity {
    CPG,
    CEE,
    NONE;

    public boolean matches(GridAffinity held) {
        return this == NONE || this == held;
    }
}
