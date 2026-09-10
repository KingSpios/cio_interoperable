package com.cio.createinteroperable;

import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * Blockstate properties shared identically across all 6 positions of an
 * assembled Interoperable Transformer (2 tall x 3 long: PG column, core
 * column, CEE column). Defined once here — like vanilla's own shared
 * BlockStateProperties.FACING — rather than duplicated per Block class,
 * since InteroperableAssembly needs to read/write the same property
 * instances regardless of which of the 3 assembled Block classes it's
 * touching.
 */
public class CIOProperties {
    /**
     * The horizontal direction from the core column toward the PG column.
     * The CEE column is always the opposite direction. This single property
     * fully encodes orientation (4 possible horizontal rotations) since,
     * unlike Power Grid's Medium Transformer, our two ends are NOT
     * interchangeable — we can't just try "both axes" and take whichever
     * fits, we need to know specifically which end is which.
     */
    public static final DirectionProperty PG_FACING = net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;

    /**
     * false = bottom row (the functional row: real PG terminals / real CEE
     * node / core). true = top row (a non-functional cap on the PG and CEE
     * columns, a plain second core piece on the middle column) — purely
     * structural/visual, present only so the assembled shape reads as a
     * 2-tall panel like Power Grid's own Medium Transformer.
     */
    public static final BooleanProperty TOP = BooleanProperty.create("top");

    /**
     * Shared by all three Telephone blocks ({@link TelephoneBlock},
     * {@link CpgTelephoneBlock}, {@link CeeTelephoneBlock}) — true exactly
     * while that phone is on an answered, ongoing call, which is when it emits
     * a redstone signal (see {@link TelephoneRedstone}). Carried on the
     * blockstate rather than read off the BlockEntity so the signal survives
     * client sync and chunk reload with no BE lookup in the redstone hooks.
     * Deliberately NOT listed in any of the telephone blockstate JSONs — an
     * unmentioned property is a wildcard there, so it needs no model changes
     * (same trick the telephone's own {@code wire_lock} already relies on).
     */
    public static final BooleanProperty CALL_ACTIVE = BooleanProperty.create("call_active");
}
