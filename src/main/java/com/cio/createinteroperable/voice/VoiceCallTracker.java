package com.cio.createinteroperable.voice;

import com.cio.createinteroperable.CIOConfig;
import com.cio.createinteroperable.telephone.PhoneLineFilter;
import com.cio.createinteroperable.telephone.SoundPickups;
import com.cio.createinteroperable.telephone.TelephoneListeners;
import com.cio.createinteroperable.telephone.TelephoneNode;
import com.cio.createinteroperable.telephone.TelephoneRegistry;
import com.cio.createinteroperable.compat.Mods;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Publishes "which phones are on an answered call, and where each one's voice comes out"
 * for the audio thread. Simple Voice Chat delivers microphone packets on its own network
 * thread, where reading live block entities or {@link TelephoneRegistry} would be a data
 * race — so instead this snapshots every answered call on the server thread every couple
 * of ticks into an immutable list behind a volatile, and the plugin only does arithmetic on
 * that. Holds no Simple Voice Chat types, and is only registered when it is installed.
 *
 * <p>Works off {@link TelephoneNode}, so all five phones (CIO's Interoperable / CPG / CEE
 * telephones and Iden's two) are covered by the one code path.</p>
 */
public final class VoiceCallTracker {

    /** Module gate, read by CIOModules (see its doc). */
    public static final String REQUIRES = Mods.Id.SIMPLE_VOICE_CHAT;

    private static final int REFRESH_TICKS = 2;

    private static volatile List<VoiceLink> links = List.of();

    private VoiceCallTracker() {
    }

    static List<VoiceLink> links() {
        return links;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % REFRESH_TICKS != 0) {
            return;
        }
        if (!CIOConfig.VOICE_RELAY.get()) {
            links = List.of();
            CallAudio.setMouths(List.of());
            return;
        }
        double radius = CIOConfig.VOICE_CAPTURE_RADIUS.get();
        float range = CIOConfig.VOICE_PLAYBACK_RANGE.get().floatValue();
        float noise = CIOConfig.VOICE_LINE_NOISE.get().floatValue();

        List<VoiceLink> next = new ArrayList<>();
        // Phones on a call, for audio played aloud near them (CallAudio.overheard): down the line, and who is on it.
        Map<BlockPos, Boolean> mouths = new LinkedHashMap<>();
        Map<BlockPos, Set<BlockPos>> partners = new HashMap<>();
        Map<BlockPos, ResourceKey<Level>> dimensions = new HashMap<>();
        for (TelephoneNode phone : TelephoneRegistry.serverPhones()) {
            if (!phone.isCallAnswered() || !(phone instanceof BlockEntity be)) {
                continue;
            }
            Level level = be.getLevel();
            BlockPos from = phone.telephonePos();
            BlockPos bridged = phone.isExternalCall() ? phone.voicePartnerPos() : null;
            TelephoneNode far = TelephoneRegistry.get(level, bridged);
            if (far != null && far.isCallAnswered() && from.equals(far.voicePartnerPos())) {
                // Two radio phones joined by TeleLink computers that both know where the
                // other is: their voices go straight across, like a wired call.
                next.add(new VoiceLink(level.dimension(), (ServerLevel) level,
                        from.getX() + 0.5, from.getY() + 0.5, from.getZ() + 0.5,
                        bridged.getX() + 0.5, bridged.getY() + 0.5, bridged.getZ() + 0.5,
                        radius, range, Math.min(1f, noise * far.lineNoiseFactor()), false, 1f, PhoneLineFilter.FULL, false));
                mouth(mouths, partners, dimensions, level, from, bridged, true);
                continue;
            }
            if (phone.isExternalCall()) {
                // The far end is a computer: the phone is both ends of its one link.
                next.add(new VoiceLink(level.dimension(), (ServerLevel) level,
                        from.getX() + 0.5, from.getY() + 0.5, from.getZ() + 0.5,
                        from.getX() + 0.5, from.getY() + 0.5, from.getZ() + 0.5,
                        radius, range, noise, true, 1f, PhoneLineFilter.FULL, false));
                mouth(mouths, partners, dimensions, level, from, bridged, false);
                continue;
            }
            BlockPos partnerPos = phone.callPartnerPos();
            TelephoneNode partner = TelephoneRegistry.get(level, partnerPos);
            // Both ends must agree the call is up; a half-torn-down call carries nothing.
            if (partner == null || !partner.isCallAnswered()) {
                continue;
            }
            // The hiss the partner hears grows with its radio reception's trouble (1 on a wire).
            next.add(new VoiceLink(level.dimension(), (ServerLevel) level,
                    from.getX() + 0.5, from.getY() + 0.5, from.getZ() + 0.5,
                    partnerPos.getX() + 0.5, partnerPos.getY() + 0.5, partnerPos.getZ() + 0.5,
                    radius, range, Math.min(1f, noise * partner.lineNoiseFactor()), false, 1f, PhoneLineFilter.FULL, false));
            mouth(mouths, partners, dimensions, level, from, partnerPos, true);
        }
        // Radio receivers: each tuned to a phone on the air hears its side of the call; each
        // also plays the radio audio it receives (a link from and to itself).
        for (TelephoneListeners.Listener listener : TelephoneListeners.listeners()) {
            ServerLevel level = event.getServer().getLevel(listener.dimension());
            if (level != null) {
                BlockPos from = listener.from(), to = listener.to();
                // A channel off: a poorer copy, and the radio hisses.
                boolean poor = listener.quality() < PhoneLineFilter.FULL;
                next.add(new VoiceLink(listener.dimension(), level, from.getX() + 0.5, from.getY() + 0.5, from.getZ() + 0.5,
                        to.getX() + 0.5, to.getY() + 0.5, to.getZ() + 0.5, radius, range, poor ? poorNoise(noise) : 0f, false,
                        listener.gain(), listener.quality(), true));
            }
        }
        for (TelephoneListeners.Speaker speaker : TelephoneListeners.speakers()) {
            ServerLevel level = event.getServer().getLevel(speaker.dimension());
            if (level != null) {
                BlockPos at = speaker.pos();
                next.add(new VoiceLink(speaker.dimension(), level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5,
                        at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 0.0, range,
                        speaker.poor() ? poorNoise(noise) : 0f, true, 1f, PhoneLineFilter.FULL, false));
            }
        }
        // Live microphones: voices around each are captured for its computer (CallAudio.MIC), nothing replayed.
        for (SoundPickups.Place pickup : SoundPickups.places()) {
            ServerLevel level = event.getServer().getLevel(pickup.dimension());
            if (level != null && SoundPickups.capturesVoice(pickup)) {
                BlockPos at = pickup.pos();
                next.add(new VoiceLink(pickup.dimension(), level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5,
                        at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, radius, range, 0f, true, 1f,
                        PhoneLineFilter.FULL, false));
            }
        }
        links = next.isEmpty() ? List.of() : List.copyOf(next);
        List<CallAudio.Mouth> nowMouths = new ArrayList<>(mouths.size());
        mouths.forEach((pos, downLine) -> nowMouths.add(new CallAudio.Mouth(dimensions.get(pos), pos, radius, downLine,
                Set.copyOf(partners.getOrDefault(pos, Set.of())))));
        CallAudio.setMouths(List.copyOf(nowMouths));
    }

    /** {@code phone} is on a call ({@code partner} the phone at the other end, when known): both ways. */
    private static void mouth(Map<BlockPos, Boolean> mouths, Map<BlockPos, Set<BlockPos>> partners,
                              Map<BlockPos, ResourceKey<Level>> dimensions, Level level, BlockPos phone,
                              @Nullable BlockPos partner, boolean downLine) {
        BlockPos at = phone.immutable();
        mouths.put(at, downLine);
        dimensions.put(at, level.dimension());
        if (partner != null) {
            partners.computeIfAbsent(at, k -> new HashSet<>()).add(partner.immutable());
            partners.computeIfAbsent(partner.immutable(), k -> new HashSet<>()).add(at);
        }
    }

    /** The hiss of a radio tuned a channel off: well above the line's own (0.25 by default gives 0.75). */
    private static float poorNoise(float noise) {
        return Math.min(1f, Math.max(0.5f, noise * 3f));
    }
}
