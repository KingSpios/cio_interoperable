package com.cio.createinteroperable;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

/**
 * Only ever called from TelephoneBlockEntity's onFrontUsed/onTopUsed, both
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
    public static void openDialScreen(BlockPos pos, String currentTarget) {
        Minecraft.getInstance().setScreen(TelephoneSettingsScreen.dial(pos, currentTarget));
    }

    public static void openLabelScreen(BlockPos pos, String currentLabel) {
        Minecraft.getInstance().setScreen(new TelephoneLabelScreen(pos, currentLabel));
    }

    /** Full settings (own number, area code, label, number to call, Auto-Answer) — any telephone type. */
    public static void openSettings(BlockPos pos, int areaCode, String number, String label, String dialTarget,
                                    boolean autoAnswer, boolean pulseSupported, boolean pulse) {
        Minecraft.getInstance().setScreen(TelephoneSettingsScreen.settings(pos, areaCode, number, label, dialTarget, autoAnswer,
                pulseSupported, pulse));
    }

    /** Declared return type is the plain {@code Player} ElectricPropertiesUtils.modify actually wants — see class doc. */
    public static Player getLocalPlayer() {
        return Minecraft.getInstance().player;
    }
}
