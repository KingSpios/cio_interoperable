package com.cio.createinteroperable.voice;

import com.cio.createinteroperable.CreateInteroperable;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.ServerPlayer;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import de.maxhenkel.voicechat.api.packets.MicrophonePacket;
import net.minecraft.server.level.ServerLevel;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Carries player voice down an answered Telephone call. While a call is up, anyone talking
 * within the capture radius of either phone is heard, spatially, from the phone at the
 * other end (see {@link VoiceCallTracker} for how "on a call" reaches this thread).
 *
 * <p>Each (far phone, speaker) pair gets its own {@link LocationalAudioChannel} and the
 * speaker's Opus frames are forwarded to it untouched — no decode or re-encode. The speaker's
 * end-of-speech marker (an empty frame) is forwarded too, which is what tells listening
 * clients to close that voice stream. Audio sent through a plugin channel does not raise
 * {@link MicrophonePacketEvent} again, so two phones within earshot of each other cannot loop.</p>
 *
 * <p>This is the only class in the mod that touches Simple Voice Chat's API. Simple Voice
 * Chat finds it by the {@link ForgeVoicechatPlugin} annotation and instantiates it only
 * when it is installed; nothing else may reference it.</p>
 */
@ForgeVoicechatPlugin
public class CioVoicechatPlugin implements VoicechatPlugin {

    /** A channel not used for this long is dropped (the speaker left, or the call ended). */
    private static final long CHANNEL_IDLE_MS = 30_000L;
    private static final int SWEEP_EVERY = 512;

    /** {@code lastUsed} is a one-element array so it can be bumped without replacing the map entry. */
    private record Chan(LocationalAudioChannel channel, long[] lastUsed) {
    }

    private static final int FRAME_SAMPLES = 960;
    private static final long NOISE_SYNC_MS = 500L;
    /** Peak amplitude (of 32767) at a line-noise level of 1.0; the 0.25 default lands around -53 dBFS. */
    private static final float NOISE_PEAK = 280f;
    private static final float SAMPLE_RATE = 48_000f;
    /** The North American dial tone: two steady sines a major sixth apart. */
    private static final double DIAL_TONE_LOW_HZ = 350.0;
    private static final double DIAL_TONE_HIGH_HZ = 440.0;
    /** How loud each dial-tone sine is next to the hiss's peak. */
    private static final float DIAL_TONE_GAIN = 0.6f;

    /**
     * The quiet hiss one phone plays while it is on an answered call: a steady stream of
     * generated frames into its own channel, separate from any speech (so voice can stay an
     * untouched Opus passthrough). Frames are pulled by Simple Voice Chat's audio player
     * thread; returning {@code null} from {@link #frame} ends the stream.
     */
    private static final class Noise {
        volatile boolean active = true;
        volatile float level;
        private final Random random = new Random();
        private float smoothed;
        private double lowPhase;
        private double highPhase;

        Noise(float level) {
            this.level = level;
        }

        short[] frame() {
            if (!active) {
                return null;
            }
            float peak = level * NOISE_PEAK;
            short[] out = new short[FRAME_SAMPLES];
            for (int i = 0; i < FRAME_SAMPLES; i++) {
                // White noise, lightly smoothed so it hisses rather than rasps.
                smoothed = smoothed * 0.5f + (random.nextFloat() * 2f - 1f) * 0.5f;
                // A faint, unbroken dial tone underneath, as if the line were left open.
                float tone = (float) (Math.sin(lowPhase) + Math.sin(highPhase)) * DIAL_TONE_GAIN * peak;
                lowPhase += 2.0 * Math.PI * DIAL_TONE_LOW_HZ / SAMPLE_RATE;
                highPhase += 2.0 * Math.PI * DIAL_TONE_HIGH_HZ / SAMPLE_RATE;
                float sample = smoothed * peak + tone;
                // The odd soft crackle, roughly one a second.
                if (random.nextInt(48_000) == 0) {
                    sample += (random.nextBoolean() ? 1f : -1f) * peak * 2.5f;
                }
                out[i] = (short) Math.max(-32768f, Math.min(32767f, sample));
            }
            return out;
        }
    }

    private final Map<UUID, Chan> channels = new ConcurrentHashMap<>();
    private final Map<String, Noise> noises = new ConcurrentHashMap<>();
    private final AtomicInteger sweepCounter = new AtomicInteger();
    private volatile VoicechatServerApi serverApi;
    private ScheduledExecutorService noiseScheduler;

    @Override
    public String getPluginId() {
        return CreateInteroperable.ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        // Also called with the client API on a client; only the server one is of use here.
        if (api instanceof VoicechatServerApi server) {
            serverApi = server;
            if (noiseScheduler == null) {
                noiseScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "CIO phone line noise");
                    thread.setDaemon(true);
                    return thread;
                });
                noiseScheduler.scheduleWithFixedDelay(this::syncNoise, NOISE_SYNC_MS, NOISE_SYNC_MS, TimeUnit.MILLISECONDS);
            }
        }
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophone);
        registration.registerEvent(VoicechatServerStoppedEvent.class, event -> {
            channels.clear();
            noises.values().forEach(noise -> noise.active = false);
            noises.clear();
        });
    }

    /**
     * Starts a hiss stream at every phone that is on an answered call and stops it at every
     * phone that no longer is, following {@link VoiceCallTracker}'s snapshot. Runs on its own
     * thread twice a second; a failure must not cancel the schedule, so it is caught here.
     */
    private void syncNoise() {
        try {
            VoicechatServerApi api = serverApi;
            if (api == null) {
                return;
            }
            Map<String, VoiceLink> wanted = new HashMap<>();
            for (VoiceLink link : VoiceCallTracker.links()) {
                if (link.noiseLevel() > 0f) {
                    wanted.put(link.destinationKey(), link);
                }
            }
            noises.entrySet().removeIf(entry -> {
                if (wanted.containsKey(entry.getKey())) {
                    return false;
                }
                entry.getValue().active = false;
                return true;
            });
            for (Map.Entry<String, VoiceLink> entry : wanted.entrySet()) {
                Noise existing = noises.get(entry.getKey());
                if (existing != null) {
                    existing.level = entry.getValue().noiseLevel();
                } else {
                    startNoise(api, entry.getKey(), entry.getValue());
                }
            }
        } catch (Throwable t) {
            CreateInteroperable.LOGGER.warn("Telephone line noise sync failed", t);
        }
    }

    private void startNoise(VoicechatServerApi api, String key, VoiceLink link) {
        UUID id = UUID.nameUUIDFromBytes(("cio-phone-noise|" + key).getBytes(StandardCharsets.UTF_8));
        LocationalAudioChannel channel = api.createLocationalAudioChannel(id, api.fromServerLevel(link.level()),
                api.createPosition(link.toX(), link.toY(), link.toZ()));
        if (channel == null) {
            return; // the voice chat server isn't running
        }
        channel.setDistance(link.playbackRange());
        OpusEncoder encoder = api.createEncoder();
        Noise noise = new Noise(link.noiseLevel());
        AudioPlayer player = api.createAudioPlayer(channel, encoder, noise::frame);
        player.setOnStopped(encoder::close);
        noises.put(key, noise);
        player.startPlaying();
    }

    private void onMicrophone(MicrophonePacketEvent event) {
        List<VoiceLink> links = VoiceCallTracker.links();
        VoicechatServerApi api = serverApi;
        if (links.isEmpty() || api == null) {
            return;
        }
        VoicechatConnection sender = event.getSenderConnection();
        if (sender == null) {
            return;
        }
        ServerPlayer player = sender.getPlayer();
        if (!(player.getServerLevel().getServerLevel() instanceof ServerLevel level)) {
            return;
        }
        Position at = player.getPosition();
        MicrophonePacket packet = event.getPacket();
        // Simple Voice Chat's own whisper carries half as far; a phone does the same.
        double reach = packet.isWhispering() ? 0.5 : 1.0;

        for (VoiceLink link : links) {
            if (link.dimension() != level.dimension()) {
                continue;
            }
            double dx = at.getX() - link.fromX();
            double dy = at.getY() - link.fromY();
            double dz = at.getZ() - link.fromZ();
            double radius = link.captureRadius() * reach;
            if (dx * dx + dy * dy + dz * dz > radius * radius) {
                continue;
            }
            LocationalAudioChannel channel = channelFor(api, level, link, player.getUuid());
            if (channel != null) {
                channel.setDistance(link.playbackRange());
                channel.send(packet);
            }
        }
        if (sweepCounter.incrementAndGet() % SWEEP_EVERY == 0) {
            long cutoff = System.currentTimeMillis() - CHANNEL_IDLE_MS;
            channels.values().removeIf(chan -> chan.lastUsed()[0] < cutoff);
        }
    }

    private LocationalAudioChannel channelFor(VoicechatServerApi api, ServerLevel level, VoiceLink link, UUID speaker) {
        UUID id = UUID.nameUUIDFromBytes(("cio-phone|" + link.destinationKey() + "|" + speaker)
                .getBytes(StandardCharsets.UTF_8));
        Chan chan = channels.get(id);
        if (chan == null) {
            LocationalAudioChannel created = api.createLocationalAudioChannel(id, api.fromServerLevel(level),
                    api.createPosition(link.toX(), link.toY(), link.toZ()));
            if (created == null) {
                return null; // the voice chat server isn't running
            }
            chan = channels.computeIfAbsent(id, k -> new Chan(created, new long[1]));
        }
        chan.lastUsed()[0] = System.currentTimeMillis();
        return chan.channel();
    }
}
