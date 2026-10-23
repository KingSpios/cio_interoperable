package com.cio.createinteroperable;

import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Player-facing text, looked up in {@code assets/createinteroperable/lang/*.json}
 * (a key {@code foo.bar} here is {@code createinteroperable.foo.bar} in the
 * file). Use these instead of {@code Component.literal} / {@code CreateLang.text}
 * for anything a player reads, so it can be translated. Plain data (a phone's
 * label, a number) stays a literal.
 */
public final class Lang {
    private Lang() {
    }

    /** A translatable component, e.g. {@code Lang.component("telephone.idle")}. */
    public static MutableComponent component(String key, Object... args) {
        return Component.translatable(CreateInteroperable.ID + "." + key, args);
    }

    /** The same as a Create {@link LangBuilder}, for goggle tooltips ({@code .style(..).forGoggles(..)}). */
    public static LangBuilder translate(String key, Object... args) {
        return new LangBuilder(CreateInteroperable.ID).translate(key, args);
    }

    /**
     * A {@link LangBuilder} holding empty text, for the "nothing" side of a
     * conditional {@code .add(cond ? Lang.translate(..) : Lang.empty())}. A bare
     * {@code new LangBuilder(..)} has no component yet and makes
     * {@code LangBuilder#add} throw "No components were added to builder".
     */
    public static LangBuilder empty() {
        return new LangBuilder(CreateInteroperable.ID).text("");
    }
}
