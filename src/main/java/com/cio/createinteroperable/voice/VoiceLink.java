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
 *
 * <p>An {@code external} link belongs to a phone on an external call (its far end is a
 * computer, e.g. a radio link; see {@link CallAudio}): {@code from} and {@code to} are both
 * that phone. Speech around it is only captured for the computer ({@link CallAudio#MIC}),
 * never replayed; the phone still plays its line hiss and the computer's audio. A radio
 * receiver is an external link of its own (from and to itself), which only plays the radio
 * audio it receives.</p>
 *
 * <p>{@code gain} (0-1) scales what comes out of {@code to}: 1 for a phone, a radio
 * receiver's volume for a radio. {@code quality} is {@code PhoneLineFilter.FULL}, or less
 * for a radio a channel off the transmitter (its voice is degraded further).</p>
 *
 * <p>{@code speaker}: {@code to} is a radio playing a call it listens to, so phones near it
 * overhear that call's voices ({@link CallAudio#overheard}).</p>
 */
record VoiceLink(ResourceKey<Level> dimension, ServerLevel level, double fromX, double fromY, double fromZ,
                 double toX, double toY, double toZ, double captureRadius, float playbackRange, float noiseLevel,
                 boolean external, float gain, float quality, boolean speaker) {

    /** Stable per-destination key, so each far phone gets its own audio channel per speaker. */
    String destinationKey() {
        return CallAudio.key(dimension, toX, toY, toZ);
    }

    /** The {@code from} phone's key: whose computer-sent audio this link carries (see {@link CallAudio}). */
    String sourceKey() {
        return CallAudio.key(dimension, fromX, fromY, fromZ);
    }
}
