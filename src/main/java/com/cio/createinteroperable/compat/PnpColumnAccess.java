package com.cio.createinteroperable.compat;

/**
 * Duck interface mixed into Pipes n Physics' {@code BoundaryColumn} (see
 * {@code mixin.pnpengine.BoundaryColumnMixin}) so CIO can flip a column's private
 * give-from-any-level permission. Lives outside
 * the mixin package because a mixin package may hold nothing but mixins.
 */
public interface PnpColumnAccess {
    /** Let this column give at any fill level, like a machine port. */
    void cio$giveFromAnyLevel();
}
