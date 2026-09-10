package com.cio.createinteroperable;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * Only ever called from TelephoneBlockEntity's onFrontUsed/onTopUsed, both
 * already guarded by {@code level.isClientSide} — kept as a tiny separate
 * helper (rather than opening screens inline in the BlockEntity) purely so
 * TelephoneBlockEntity itself doesn't need a direct static import of
 * Minecraft.getInstance() sprinkled through its server-shared logic.
 */
public class TelephoneClient {
    public static void openDialScreen(BlockPos pos, String currentTarget) {
        Minecraft.getInstance().setScreen(new TelephoneDialScreen(pos, currentTarget));
    }

    public static void openLabelScreen(BlockPos pos, String currentLabel) {
        Minecraft.getInstance().setScreen(new TelephoneLabelScreen(pos, currentLabel));
    }

    public static void openNumberScreen(BlockPos pos, int areaCode, String number) {
        Minecraft.getInstance().setScreen(new TelephoneNumberScreen(pos, areaCode, number));
    }
}
