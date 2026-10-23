package com.cio.createinteroperable;

import com.george_vi.electroenergetics.content.ElectricHumSoundInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Client-only half of {@link CeeCompactTransformerBlockEntity}: the electric
 * hum, exactly as CEE's Transformer plays it. Kept out of the block entity so
 * that common class never references a client-only type (see
 * {@link CIOClientUtil}); only ever reached through
 * {@code CatnipServices.PLATFORM.executeOnClientOnly}.
 */
public final class CeeCompactTransformerClient {
    private CeeCompactTransformerClient() {
    }

    static void tickAudio(CeeCompactTransformerBlockEntity be) {
        double power = be.getPower();
        if (power <= 100.0) {
            return;
        }
        if (!(be.soundInstance instanceof ElectricHumSoundInstance sound) || sound.isStopped()) {
            ElectricHumSoundInstance sound = new ElectricHumSoundInstance(be.getBlockPos());
            be.soundInstance = sound;
            Minecraft.getInstance().getSoundManager().play(sound);
        } else {
            sound.setVolume((float) Mth.clamp(power / 800000.0, 0.02, 0.25));
            sound.keepAlive();
        }
    }
}
