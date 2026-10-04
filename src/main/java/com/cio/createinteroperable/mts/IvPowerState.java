package com.cio.createinteroperable.mts;

/**
 * Duck interface mixed onto Immersive Vehicles' {@code TileEntityPole} (by
 * {@code mixin.mts.IvPolePowerMixin}): whether the pole block's grid node
 * currently lets its lamps light. Pushed every tick, on both sides, by the
 * node living on the pole's own block entity ({@code IvTileNodeMixin}), and read
 * by the lamp gates in {@link IvPoleLights}. Defaults to unpowered, so a pole
 * whose node hasn't reported yet stays dark rather than flashing on.
 *
 * <p>Lives outside the mixin package so plain code can reference it.</p>
 */
public interface IvPowerState {

    boolean cio$ivPowered();

    void cio$setIvPowered(boolean powered);
}
