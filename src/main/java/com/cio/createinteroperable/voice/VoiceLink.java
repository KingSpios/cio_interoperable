package com.cio.createinteroperable.voice;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * One direction of an answered call: speech picked up around {@code from} is played out
 * of {@code to}. A call between phones A and B is two links (A to B, and B to A). Immutable
 * and free of any Simple Voice Chat type — built on the server thread by {@link VoiceCallTracker}
 * and read from Simple Voice Chat's own threads by {@link CioVoicechatPlugin}.
 *
 * <p>{@code noiseLevel} (0..1) is the loudness of the line hiss the {@code to} phone plays
 * for the length of the call.</p>
 */
record VoiceLink(ResourceKey<Level> dimension, ServerLevel level, double fromX, double fromY, double fromZ,
                 double toX, double toY, double toZ, double captureRadius, float playbackRange, float noiseLevel) {

    /** Stable per-destination key, so each far phone gets its own audio channel per speaker. */
    String destinationKey() {
        return dimension.location() + "|" + Math.floor(toX) + "|" + Math.floor(toY) + "|" + Math.floor(toZ);
    }
}
