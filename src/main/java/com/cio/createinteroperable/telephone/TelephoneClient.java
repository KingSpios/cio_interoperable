package com.cio.createinteroperable.telephone;

import net.minecraft.client.Minecraft;
import com.simibubi.create.AllSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Only ever called from TelephoneCore's onFrontUsed/onBackPlateUsed, both
 * already guarded by {@code level.isClientSide} — kept as a tiny separate
 * helper (rather than opening screens inline in the BlockEntity) purely so
 * TelephoneBlockEntity itself doesn't need a direct static import of
 * Minecraft.getInstance() sprinkled through its server-shared logic.
 * <p>
 * The same reasoning applies to {@link #getLocalPlayer()}: TelephoneBlock is
 * a common (both-sides-loaded) class, and a real dedicated-server crash
 * confirmed that referencing {@code Minecraft}/{@code LocalPlayer} directly
 * from its bytecode (even only inside a client-only-invoked method like
 * appendHoverText) makes the JVM verifier resolve those
 * {@code @OnlyIn(Dist.CLIENT)} classes while loading TelephoneBlock itself —
 * NeoForge's RuntimeDistCleaner then refuses the load outright
 * (RegisterEvent, "Attempted to load class .../LocalPlayer for invalid dist
 * DEDICATED_SERVER"). Routing the read through this already-existing
 * client-only helper class defers that classload to when this method is
 * actually invoked (client-side tooltip rendering only), instead of
 * triggering it merely by TelephoneBlock's own class being loaded during
 * registration.
 */
public class TelephoneClient {
    public static void openDialScreen(BlockPos pos, String currentTarget, boolean radio) {
        // Ordinary phones get the gray rotary-dial screen; radio phones keep the faceplate one.
        Minecraft.getInstance().setScreen(radio ? TelephoneSettingsScreen.dial(pos, currentTarget, true)
                : TelephoneScreen.dial(pos, currentTarget));
    }

    /** Full settings (own number, area code, label, number to call, Auto-Answer) — any telephone type. */
    public static void openSettings(BlockPos pos, int areaCode, String number, String label, String dialTarget,
                                    boolean autoAnswer, boolean pulseSupported, boolean pulse) {
        openSettings(pos, areaCode, number, label, dialTarget, autoAnswer, pulseSupported, pulse, null, false);
    }

    /**
     * As above; a radio telephone also passes its {@code tuning}, which adds the frequency
     * controls, and whether it keeps its chunk loaded.
     */
    public static void openSettings(BlockPos pos, int areaCode, String number, String label, String dialTarget,
                                    boolean autoAnswer, boolean pulseSupported, boolean pulse, @Nullable RadioTuning tuning,
                                    boolean keepLoaded) {
        Minecraft.getInstance().setScreen(tuning == null
                ? TelephoneScreen.settings(pos, areaCode, number, label, dialTarget, autoAnswer, pulseSupported, pulse)
                : TelephoneSettingsScreen.settings(pos, areaCode, number, label, dialTarget, autoAnswer,
                        pulseSupported, pulse, tuning, keepLoaded));
    }

    /**
     * From a phone's client tick: as it starts placing a call its number is dialled out on
     * the keypad ({@link TelephoneKeypad}), then the dial tone plays while the call waits.
     */
    public static void syncDialTone(BlockEntity phone, boolean placingCall, String number) {
        TelephoneKeypad.syncPlacing(phone.getBlockPos(), placingCall, number);
        if (placingCall) {
            TelephoneDialTone.keep(phone.getBlockPos());
        }
    }

    /**
     * A key typed into a dial screen's number: its keypad tone, and on an ordinary (non-radio)
     * phone a ding of Create's desk bell as well.
     */
    static void keyTone(BlockPos pos, char key, boolean deskBell) {
        TelephoneKeypad.press(pos, key);
        Minecraft mc = Minecraft.getInstance();
        if (deskBell && TelephoneKeypad.isKey(key) && mc.level != null) {
            mc.level.playLocalSound(pos, AllSoundEvents.DESK_BELL_USE.getMainEvent(), SoundSource.BLOCKS, 0.6f, 1f, false);
        }
    }

    /** Whether the phone at {@code pos} has its line in use, as last synced: the dial screens' Dial key shows ON. */
    static boolean lineInUse(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockEntity(pos) instanceof CioTelephone phone
                && phone.telephoneCore().isLineInUse();
    }

    /** From an open dial screen, every tick: the dial tone of the phone being dialled from. */
    static void keepDialTone(BlockPos pos) {
        TelephoneDialTone.keep(pos);
    }

    /** Declared return type is the plain {@code Player} ElectricPropertiesUtils.modify actually wants — see class doc. */
    public static Player getLocalPlayer() {
        return Minecraft.getInstance().player;
    }
}
