package com.cio.createinteroperable.mts;

import minecrafttransportsimulator.blocks.tileentities.components.ATileEntityBase;
import minecrafttransportsimulator.blocks.tileentities.components.ATileEntityPole_Component;
import minecrafttransportsimulator.blocks.tileentities.instances.TileEntityPole;
import minecrafttransportsimulator.blocks.tileentities.instances.TileEntityPole_StreetLight;
import minecrafttransportsimulator.blocks.tileentities.instances.TileEntityPole_TrafficSignal;
import minecrafttransportsimulator.blocks.tileentities.instances.TileEntitySignalController;
import minecrafttransportsimulator.entities.components.AEntityD_Definable;
import org.jetbrains.annotations.Nullable;

/**
 * Makes Immersive Vehicles' pole lights need Domestic Electrical Board power.
 *
 * <p>Unlike the AA Spotlight (an entity), IV poles and the Signal Controller
 * are real blocks, each with IV's own block entity ({@code BuilderTileEntity},
 * type {@code mts:builder_base}) at a real position. So that block entity
 * itself becomes the grid node ({@code mixin.mts.IvTileNodeMixin}) &mdash; you
 * wire the pole block, or the controller box &mdash; and only these two IV tile
 * kinds are nodes; roads, pumps and every other IV block stay untouched.</p>
 *
 * <ul>
 *   <li><b>Pole</b>: only a pole block that carries a lamp is a node (its
 *       wire nub sits on that block); it bills its lamps and lights them only
 *       while a live rail keeps feeding it, re-checked every tick. Bare pole
 *       segments are not nodes. Covers every pack's {@code street_light} and
 *       {@code traffic_signal} components (the Official Pack's Street Light,
 *       flashing red/yellow signals, Traffic Signal and Crossing Signal).
 *       Signs, even lit ones, are left alone.</li>
 *   <li><b>Signal Controller</b>: a node that draws nothing for now &mdash; it
 *       keeps cycling unpowered. Its signals go dark when <em>their own</em>
 *       pole has no power.</li>
 * </ul>
 *
 * <p>Unpowered, a lamp component's every light is zeroed just before it draws
 * ({@link #dimLights}) and a street light stops lighting the world
 * ({@link #blocksWorldLight}). Only ever loaded with Immersive Vehicles present
 * (its callers are the {@code MtsMixinPlugin}-gated {@code mixin.mts} mixins).</p>
 */
public final class IvPoleLights {

    /** A street-light-type component: an area light (sodium/LED street lamp class). */
    public static final double STREET_LIGHT_WATTS = 100.0;
    /** A traffic-signal-type component: one LED signal head (one lamp lit at a time). */
    public static final double TRAFFIC_SIGNAL_WATTS = 25.0;

    public enum Kind {
        NONE, POLE, CONTROLLER
    }

    private IvPoleLights() {
    }

    /**
     * What this IV tile is to the grid. Only a pole block that actually carries
     * a lamp is a node &mdash; a bare pole segment (or one with only signs) has
     * no business showing a wiring nub.
     */
    public static Kind kindOf(@Nullable ATileEntityBase<?> tile) {
        if (tile instanceof TileEntitySignalController) {
            return Kind.CONTROLLER;
        }
        if (tile instanceof TileEntityPole && lampWatts(tile) > 0.0) {
            return Kind.POLE;
        }
        return Kind.NONE;
    }

    /** A live pole block with no lamp on it: any wire it still holds must go. */
    public static boolean isLamplessPole(@Nullable ATileEntityBase<?> tile) {
        return tile instanceof TileEntityPole && lampWatts(tile) <= 0.0;
    }

    public static boolean isLamp(Object component) {
        return component instanceof TileEntityPole_StreetLight || component instanceof TileEntityPole_TrafficSignal;
    }

    /** Rated draw of every lamp component on this pole block (0 for anything else). */
    public static double lampWatts(@Nullable ATileEntityBase<?> tile) {
        if (!(tile instanceof TileEntityPole pole)) {
            return 0.0;
        }
        double watts = 0.0;
        for (ATileEntityPole_Component component : pole.components.values()) {
            if (component instanceof TileEntityPole_StreetLight) {
                watts += STREET_LIGHT_WATTS;
            } else if (component instanceof TileEntityPole_TrafficSignal) {
                watts += TRAFFIC_SIGNAL_WATTS;
            }
        }
        return watts;
    }

    /** True for a street light / traffic signal whose pole has no power. */
    public static boolean isUnpowered(AEntityD_Definable<?> entity) {
        return isLamp(entity)
                && ((ATileEntityPole_Component) entity).core instanceof IvPowerState state
                && !state.cio$ivPowered();
    }

    /** Client, after IV computes this frame's light levels: black out an unpowered lamp. */
    public static void dimLights(AEntityD_Definable<?> entity) {
        if (!entity.lightBrightnessValues.isEmpty() && isUnpowered(entity)) {
            entity.lightBrightnessValues.replaceAll((light, brightness) -> 0.0F);
        }
    }

    /** Street light block-light gate: an unpowered lamp lights nothing. */
    public static boolean blocksWorldLight(AEntityD_Definable<?> entity) {
        return isUnpowered(entity);
    }
}
