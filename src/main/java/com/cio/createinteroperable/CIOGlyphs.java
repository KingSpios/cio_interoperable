package com.cio.createinteroperable;

/**
 * Special characters + spacing for slider labels. Escaped rather than typed
 * literally so the source file's own encoding can never mangle them. Value
 * boxes shrink text to fit their width, so labels stay short: one symbol, one
 * space, then the word/number.
 */
public final class CIOGlyphs {
    private CIOGlyphs() {}

    public static final String BOLT = "⚡";       // lightning bolt
    public static final String SNOW = "❄";       // snowflake
    public static final String ARROW = "→";      // right arrow
    public static final String ARROW_LEFT = "←"; // left arrow
    public static final String CROSS = "✖";      // heavy X
    public static final String CHECK = "✔";      // heavy check

    /** Lightning bolt, a space, then the tap label (e.g. 240 V). */
    public static String volts(String plainLabel) {
        return BOLT + " " + plainLabel;
    }

    /** CPG is always written on the left; the arrow shows which way power flows: "CPG -> CEE". */
    public static String cpgToCee() {
        return "CPG " + ARROW + " CEE";
    }

    /** Same word order as {@link #cpgToCee()}, arrow reversed: "CPG <- CEE". */
    public static String ceeToCpg() {
        return "CPG " + ARROW_LEFT + " CEE";
    }

    /** {@code count} snowflakes, a space, then the word. */
    public static String snow(int count, String word) {
        return SNOW.repeat(count) + " " + word;
    }

    /** Heavy X, a space, then the word. */
    public static String off(String word) {
        return CROSS + " " + word;
    }

    /** Check + ON, or X + OFF. */
    public static String onOff(boolean on) {
        return on ? CHECK + " ON" : CROSS + " OFF";
    }
}
