package com.cio.createinteroperable.deb;

/**
 * The two load pools a Power Kit tracks.
 * <p>
 * As of the single-intake rewire the Kit takes <em>one</em> 120&nbsp;V grid
 * feed and steps it down internally; the pools are no longer separate circuit
 * ports, they are load-aggregation buckets for the Crayfish appliances
 * wrench-linked to the board (plus the metered Power Feed draw). Appliance
 * &rarr; pool assignment is by appliance <em>type</em> (see
 * {@link ApplianceLoads}), not by which nub a wire touches.
 * <ul>
 *   <li>{@link #LV} &mdash; the 12&nbsp;V side: lights, doorbell, TV, computer,
 *       lightswitch, and whatever the player hangs off the 12&nbsp;V Power
 *       Feed. Fed from the internal step-down.</li>
 *   <li>{@link #MV} &mdash; the 120&nbsp;V side: fans, range hood, workbench,
 *       the fridge, the kitchen heavy loads, and the 120&nbsp;V Power Feed. Fed
 *       straight off the intake. Every tier serves this pool
 *       ({@link DebTier#servesMediumVoltage()}); tier&nbsp;1 just gives it a
 *       small budget and no physical Power Feed pass-through
 *       ({@link DebTier#hasMediumVoltageFeed()}).</li>
 * </ul>
 * Each pool keeps its own soft / hard cap and brownout threshold
 * ({@link DebTier}); the grid-facing resistance is a single 120&nbsp;V-referred
 * number ({@code DebRectifierBlockEntity#primaryResistance}).
 */
public enum Pool {
    /** "Low voltage" 12&nbsp;V side. */
    LV(12.0),
    /** "Medium voltage" 120&nbsp;V side (the intake voltage). */
    MV(120.0);

    /** Design voltage of the side. Brownout / step-down / resistance maths are relative to this. */
    public final double nominalVoltage;

    Pool(double nominalVoltage) {
        this.nominalVoltage = nominalVoltage;
    }
}
