package com.cio.createinteroperable.voice;

import com.cio.createinteroperable.CIOConfig;
import com.cio.createinteroperable.TelephoneNode;
import com.cio.createinteroperable.TelephoneRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

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
            return;
        }
        double radius = CIOConfig.VOICE_CAPTURE_RADIUS.get();
        float range = CIOConfig.VOICE_PLAYBACK_RANGE.get().floatValue();
        float noise = CIOConfig.VOICE_LINE_NOISE.get().floatValue();

        List<VoiceLink> next = new ArrayList<>();
        for (TelephoneNode phone : TelephoneRegistry.serverPhones()) {
            if (!phone.isCallAnswered() || !(phone instanceof BlockEntity be)) {
                continue;
            }
            Level level = be.getLevel();
            BlockPos partnerPos = phone.callPartnerPos();
            TelephoneNode partner = TelephoneRegistry.get(level, partnerPos);
            // Both ends must agree the call is up; a half-torn-down call carries nothing.
            if (partner == null || !partner.isCallAnswered()) {
                continue;
            }
            BlockPos from = phone.telephonePos();
            next.add(new VoiceLink(level.dimension(), (ServerLevel) level,
                    from.getX() + 0.5, from.getY() + 0.5, from.getZ() + 0.5,
                    partnerPos.getX() + 0.5, partnerPos.getY() + 0.5, partnerPos.getZ() + 0.5,
                    radius, range, noise));
        }
        links = next.isEmpty() ? List.of() : List.copyOf(next);
    }
}
