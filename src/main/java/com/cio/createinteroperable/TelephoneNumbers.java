package com.cio.createinteroperable;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Pure number-formatting and geometry helpers shared by all three Telephone
 * variants (Interoperable, CPG-only, CEE-only), the shared {@link TelephoneRenderer},
 * and the client-side dial/number screens. Deliberately NOT on any of the
 * Block/BlockEntity classes — {@code TelephoneBlock} (the Interoperable
 * variant) extends Power Grid's {@code ElectricBlock} and implements its
 * {@code IHaveElectricProperties}, so a static helper referenced from
 * shared/generic code must never live there: a real crash log showed
 * {@code TelephoneRenderer} calling {@code TelephoneBlock.angleFor(...)} for a
 * CEE-only telephone forcing resolution of that PG interface and throwing
 * {@code NoClassDefFoundError} on a PG-absent install.
 */
public final class TelephoneNumbers {
    private TelephoneNumbers() {
    }

    /**
     * North=0, East=90, South=180, West=270 — same convention as the
     * blockstate's own "y" rotation. All three Telephone variants key their
     * facing off the identical {@link BlockStateProperties#HORIZONTAL_FACING}
     * property, so this one copy is correct for all of them.
     */
    public static int angleFor(BlockState state) {
        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return 0;
        }
        return switch (state.getValue(BlockStateProperties.HORIZONTAL_FACING)) {
            case NORTH -> 0;
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }

    public static String formatNumber(int areaCode, String numberText) {
        return String.format("%03d-%s", areaCode, numberText);
    }

    /**
     * Digits, #, and * only, up to 6 characters (truncated if longer) — no
     * zero-padding, so a phone's own number can genuinely be shorter than 6
     * digits. Never trust a client-controlled string verbatim.
     */
    public static String sanitizeNumberText(String raw) {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < raw.length() && sb.length() < 6; i++) {
            char c = raw.charAt(i);
            if (Character.isDigit(c) || c == '#' || c == '*') {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
