package com.cio.createinteroperable.voice;

import com.cio.createinteroperable.CIOConfig;
import com.cio.createinteroperable.CreateInteroperable;
import com.cio.createinteroperable.telephone.PhoneLineFilter;
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
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import de.maxhenkel.voicechat.api.packets.MicrophonePacket;
import net.minecraft.core.BlockPos;
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
 * <p>Each (far phone, speaker) pair gets its own {@link LocationalAudioChannel}. With
 * {@code telephoneVoice.compressAudio} on (the default) the speaker's voice is decoded, put
 * through the line's {@link PhoneLineFilter} and encoded again, once per speaker, so it comes
 * out of the far phone band-limited and coarse like a real phone line; the speaker's
 * end-of-speech marker (an empty frame) becomes a {@code flush()}, which is what tells
 * listening clients to close that voice stream. With it off the Opus frames are forwarded
 * untouched, end-of-speech marker included. Audio sent through a plugin channel does not raise
 * {@link MicrophonePacketEvent} again, so two phones within earshot of each other cannot loop.</p>
 *
 * <p>Computers (the ComputerCraft CEE Addon integration) share the calls through
 * {@link CallAudio}: while one listens to a phone, the voices relayed to it (its earpiece)
 * and the voices it picks up (its mouthpiece) are decoded into that phone's buffers, once
 * per talker; audio a computer sends down a phone's line is streamed out of the far phone,
 * and audio it plays at a phone out of that phone, each on a channel of its own. A phone on
 * an external call (the far end is a computer) has one capture-only link to itself.</p>
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
    /**
     * How loud each dial-tone sine is next to the hiss's peak: only a trace once a call
     * is answered (the full tone plays while dialling and waiting, see TelephoneDialTone).
     */
    private static final float DIAL_TONE_GAIN = 0.15f;

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

    /**
     * Computer audio ({@link CallAudio#SENT} or {@link CallAudio#PLAYED}) streamed out of a
     * phone like a caller's voice. Pulls queued frames; a short silence pads gaps, and after
     * {@link #STREAM_IDLE_FRAMES} empty frames the stream ends (restarted on the next sync if
     * more arrives). Sent audio is also handed to the far phone's own computers
     * ({@code echoTo}), so two computers can talk over a call.
     */
    private static final class Stream {
        volatile boolean active = true;
        private final CallAudio.Queues queue;
        private final String phone;
        private final String echoTo;
        private final PhoneLineFilter filter;
        private int idleFrames;

        Stream(CallAudio.Queues queue, String phone, String echoTo, boolean compressed) {
            this.queue = queue;
            this.phone = phone;
            this.echoTo = echoTo;
            this.filter = compressed ? new PhoneLineFilter(SAMPLE_RATE, 1) : null;
        }

        short[] frame() {
            if (!active) {
                return null;
            }
            short[] frame = queue.nextFrame(phone);
            if (frame == null) {
                return ++idleFrames > STREAM_IDLE_FRAMES ? null : new short[FRAME_SAMPLES];
            }
            idleFrames = 0;
            if (filter != null) {
                frame = frame.clone();
                filter.process(frame);
            }
            if (echoTo != null && CallAudio.EARPIECE.wanted(echoTo)) {
                CallAudio.EARPIECE.hear(echoTo, COMPUTER_TALKER, frame);
            }
            return frame;
        }
    }

    /**
     * One Opus decoder per talker, used while a computer listens to a phone that talker
     * reaches or the line is compressed; and, for the compressed line, that talker's filter
     * and encoder (made on first use).
     */
    private static final class Decoder {
        final OpusDecoder decoder;
        final long[] lastUsed = new long[1];
        OpusEncoder encoder;
        PhoneLineFilter filter;

        Decoder(OpusDecoder decoder) {
            this.decoder = decoder;
        }

        OpusDecoder decoder() {
            return decoder;
        }

        long[] lastUsed() {
            return lastUsed;
        }

        void close() {
            decoder.close();
            if (encoder != null) {
                encoder.close();
            }
        }
    }

    /** About half a second of padding before an idle computer stream ends. */
    private static final int STREAM_IDLE_FRAMES = 25;
    /** A talker's decoder starts afresh after a gap this long (frames in between went undecoded). */
    private static final long DECODER_GAP_MS = 200L;
    /** Stands in as the talker for computer audio heard by another computer. */
    private static final UUID COMPUTER_TALKER = UUID.nameUUIDFromBytes("cio-computer".getBytes(StandardCharsets.UTF_8));

    private final Map<UUID, Chan> channels = new ConcurrentHashMap<>();
    private final Map<String, Noise> noises = new ConcurrentHashMap<>();
    private final Map<String, Stream> streams = new ConcurrentHashMap<>();
    private final Map<UUID, Decoder> decoders = new ConcurrentHashMap<>();
    /** One encoder per (talker, quieter listener): see {@link #scaledEncode}. */
    private record ScaledEncoder(OpusEncoder encoder, long[] lastUsed, PhoneLineFilter[] filter) {
    }

    private final Map<String, ScaledEncoder> scaledEncoders = new ConcurrentHashMap<>();
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
            streams.values().forEach(stream -> stream.active = false);
            streams.clear();
            decoders.values().forEach(Decoder::close);
            decoders.clear();
            scaledEncoders.values().forEach(e -> e.encoder().close());
            scaledEncoders.clear();
            CallAudio.clear();
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
            syncStreams(api);
        } catch (Throwable t) {
            CreateInteroperable.LOGGER.warn("Telephone line noise sync failed", t);
        }
    }

    /**
     * Streams computer audio ({@link CallAudio}) for every phone on an answered call: what it
     * {@linkplain CallAudio#SENT sent} out of the far phone, what it
     * {@linkplain CallAudio#PLAYED plays} out of the phone itself. Drops what a phone still had
     * queued once it is no longer on a call.
     */
    private void syncStreams(VoicechatServerApi api) {
        Map<String, VoiceLink> bySource = new HashMap<>();
        Map<String, VoiceLink> byDestination = new HashMap<>();
        for (VoiceLink link : VoiceCallTracker.links()) {
            if (!link.external()) {
                bySource.put(link.sourceKey(), link);
            }
            byDestination.put(link.destinationKey(), link);
        }
        streams.entrySet().removeIf(entry -> {
            Stream stream = entry.getValue();
            Map<String, VoiceLink> calls = stream.queue == CallAudio.SENT ? bySource : byDestination;
            if (calls.containsKey(stream.phone)) {
                return false;
            }
            stream.active = false;
            return true;
        });
        syncQueue(api, CallAudio.SENT, bySource, true);
        syncQueue(api, CallAudio.PLAYED, byDestination, false);
    }

    private void syncQueue(VoicechatServerApi api, CallAudio.Queues queue, Map<String, VoiceLink> calls, boolean sent) {
        for (String phone : List.copyOf(queue.phones())) {
            VoiceLink link = calls.get(phone);
            if (link == null) {
                queue.drop(phone);
            } else if (!streams.containsKey(streamKey(queue, phone)) && queue.waiting(phone) > 0) {
                startStream(api, queue, phone, link, sent ? link.destinationKey() : null);
            }
        }
    }

    private static String streamKey(CallAudio.Queues queue, String phone) {
        return (queue == CallAudio.SENT ? "sent|" : "played|") + phone;
    }

    /** Plays {@code queue}'s audio for {@code phone} out of {@code link}'s {@code to} phone. */
    private void startStream(VoicechatServerApi api, CallAudio.Queues queue, String phone, VoiceLink link, String echoTo) {
        String key = streamKey(queue, phone);
        UUID id = UUID.nameUUIDFromBytes(("cio-phone-computer|" + key).getBytes(StandardCharsets.UTF_8));
        LocationalAudioChannel channel = api.createLocationalAudioChannel(id, api.fromServerLevel(link.level()),
                api.createPosition(link.toX(), link.toY(), link.toZ()));
        if (channel == null) {
            return; // the voice chat server isn't running
        }
        channel.setDistance(link.playbackRange());
        OpusEncoder encoder = api.createEncoder();
        Stream stream = new Stream(queue, phone, echoTo, CIOConfig.LINE_COMPRESSION.get());
        AudioPlayer player = api.createAudioPlayer(channel, encoder, stream::frame);
        player.setOnStopped(() -> {
            encoder.close();
            streams.remove(key, stream);
        });
        streams.put(key, stream);
        player.startPlaying();
    }

    /** {@code packet} decoded with {@code talker}'s own decoder; null for the empty end-of-speech frame. */
    private short[] decode(VoicechatServerApi api, UUID talker, MicrophonePacket packet) {
        Decoder decoder = decoders.computeIfAbsent(talker, k -> new Decoder(api.createDecoder()));
        long now = System.currentTimeMillis();
        if (now - decoder.lastUsed()[0] > DECODER_GAP_MS) {
            decoder.decoder().resetState();
            decoder.filter = null;
        }
        decoder.lastUsed()[0] = now;
        byte[] opus = packet.getOpusEncodedData();
        if (opus == null || opus.length == 0) {
            // End of speech: the next frame starts a new stream.
            decoder.decoder().resetState();
            decoder.filter = null;
            return null;
        }
        return decoder.decoder().decode(opus);
    }

    /**
     * {@code pcm} (a talker's decoded frame) as heard down the line: filtered with that
     * talker's own {@link PhoneLineFilter} and encoded again; the filtered frame goes in
     * {@code filteredOut[0]}. {@code pcm} itself is left as it was.
     */
    private byte[] lineEncode(VoicechatServerApi api, UUID talker, short[] pcm, short[][] filteredOut) {
        Decoder decoder = decoders.get(talker);
        if (decoder.encoder == null) {
            decoder.encoder = api.createEncoder();
        }
        if (decoder.filter == null) {
            decoder.filter = new PhoneLineFilter(SAMPLE_RATE, 1);
            decoder.encoder.resetState();
        }
        short[] filtered = pcm.clone();
        decoder.filter.process(filtered);
        filteredOut[0] = filtered;
        return decoder.encoder.encode(filtered);
    }

    /**
     * {@code pcm} at {@code gain} and {@code quality}, encoded for one listener
     * ({@code destination}): its own encoder (and, below full quality, its own extra
     * {@link PhoneLineFilter}) per talker, as both keep state from frame to frame.
     */
    private byte[] scaledEncode(VoicechatServerApi api, UUID talker, String destination, short[] pcm, float gain,
                                float quality) {
        ScaledEncoder scaled = scaledEncoders.computeIfAbsent(talker + "|" + destination,
                k -> new ScaledEncoder(api.createEncoder(), new long[1], new PhoneLineFilter[1]));
        scaled.lastUsed()[0] = System.currentTimeMillis();
        short[] out = new short[pcm.length];
        for (int i = 0; i < pcm.length; i++) {
            out[i] = (short) (pcm[i] * gain);
        }
        if (quality < PhoneLineFilter.FULL) {
            if (scaled.filter()[0] == null) {
                scaled.filter()[0] = new PhoneLineFilter(SAMPLE_RATE, 1, quality);
            }
            scaled.filter()[0].process(out);
        }
        return scaled.encoder().encode(out);
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
        short[] pcm = null;
        boolean decoded = false;
        boolean compressed = CIOConfig.LINE_COMPRESSION.get();
        // The compressed line: this talker's frame filtered and encoded again, made once per packet.
        byte[] lineOpus = null;
        short[][] linePcm = new short[1][];
        boolean encoded = false;
        // Links that play quieter (a radio's volume) need their own encoding of this frame.
        Map<String, byte[]> scaled = null;

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
            boolean earpiece = !link.external() && CallAudio.EARPIECE.wanted(link.destinationKey());
            boolean mic = CallAudio.MIC.wanted(link.sourceKey());
            // A radio's volume, or a channel-off copy: this listener gets its own encoding.
            boolean quieter = !link.external() && (link.gain() < 0.999f || link.quality() < PhoneLineFilter.FULL);
            // A radio playing this call aloud: phones on other calls beside it overhear it.
            BlockPos caller = link.speaker() ? BlockPos.containing(link.fromX(), link.fromY(), link.fromZ()) : null;
            boolean overheard = caller != null
                    && CallAudio.overheardNear(link.dimension(), link.toX(), link.toY(), link.toZ(), caller);
            if (!decoded && (earpiece || mic || quieter || overheard || (compressed && !link.external()))) {
                pcm = decode(api, player.getUuid(), packet);
                decoded = true;
            }
            if (compressed && !encoded && !link.external() && pcm != null) {
                lineOpus = lineEncode(api, player.getUuid(), pcm, linePcm);
                encoded = true;
            }
            if (!link.external()) {
                LocationalAudioChannel channel = channelFor(api, level, link, player.getUuid());
                if (channel != null) {
                    channel.setDistance(link.playbackRange());
                    if (pcm == null && (compressed || quieter)) {
                        channel.flush(); // end of speech
                    } else if (quieter) {
                        if (scaled == null) {
                            scaled = new HashMap<>();
                        }
                        short[] source = linePcm[0] != null ? linePcm[0] : pcm;
                        channel.send(scaled.computeIfAbsent(link.destinationKey(),
                                k -> scaledEncode(api, player.getUuid(), k, source, link.gain(), link.quality())));
                    } else if (!compressed) {
                        channel.send(packet);
                    } else {
                        channel.send(lineOpus);
                    }
                }
            }
            // Computers listening: the far phone's earpiece (what comes out of it), and this phone's mouthpiece.
            if (earpiece || mic) {
                if (pcm != null) {
                    if (earpiece) {
                        CallAudio.EARPIECE.hear(link.destinationKey(), player.getUuid(),
                                linePcm[0] != null ? linePcm[0] : pcm);
                    }
                    if (mic) {
                        CallAudio.MIC.hear(link.sourceKey(), player.getUuid(), pcm);
                    }
                }
            }
            if (overheard && pcm != null) {
                short[] played = pcm;
                if (link.gain() < 0.999f) {
                    played = pcm.clone();
                    for (int i = 0; i < played.length; i++) {
                        played[i] = (short) (played[i] * link.gain());
                    }
                }
                CallAudio.overheard(link.dimension(), link.toX(), link.toY(), link.toZ(), caller, player.getUuid(), played);
            }
        }
        if (sweepCounter.incrementAndGet() % SWEEP_EVERY == 0) {
            long cutoff = System.currentTimeMillis() - CHANNEL_IDLE_MS;
            channels.values().removeIf(chan -> chan.lastUsed()[0] < cutoff);
            decoders.values().removeIf(d -> {
                if (d.lastUsed()[0] >= cutoff) {
                    return false;
                }
                d.close();
                return true;
            });
            scaledEncoders.values().removeIf(e -> {
                if (e.lastUsed()[0] >= cutoff) {
                    return false;
                }
                e.encoder().close();
                return true;
            });
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
