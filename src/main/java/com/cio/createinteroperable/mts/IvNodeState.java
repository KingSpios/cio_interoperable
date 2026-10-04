package com.cio.createinteroperable.mts;

/**
 * Duck interface mixed onto Immersive Vehicles' {@code BuilderTileEntity} (by
 * {@code mixin.mts.IvTileNodeMixin}), exposing the two node facts the
 * Crayfish adapter needs for its look-at "Missing power" label: whether this
 * node's lamps are allowed to light, and whether it has any lamps at all (a
 * bare pole or a Signal Controller never asks for power, so never shows the
 * label).
 *
 * <p>Lives outside the mixin package so plain code can reference it.</p>
 */
public interface IvNodeState {

    boolean cio$lightsPowered();

    boolean cio$needsPower();
}
