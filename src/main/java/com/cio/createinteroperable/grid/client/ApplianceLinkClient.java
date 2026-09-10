package com.cio.createinteroperable.grid.client;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side link-arm state for the native appliance grid &mdash; the no-item
 * analogue of MrCrayfish's {@code LinkHandler}. The gate is holding a connector
 * (see {@link com.cio.createinteroperable.grid.ApplianceGridTags}); the raycast
 * lives in {@link com.cio.createinteroperable.grid.ApplianceRaycast} and the
 * interaction wiring in {@code ApplianceGridEvents}.
 */
public final class ApplianceLinkClient {

    @Nullable
    private static BlockPos armedFrom;

    private ApplianceLinkClient() {
    }

    /** Mirrored from the server via {@code MsgApplianceLinkState}. */
    public static void setArmedFrom(@Nullable BlockPos pos) {
        armedFrom = pos;
    }

    @Nullable
    public static BlockPos armedFrom() {
        return armedFrom;
    }

    public static boolean isArmed() {
        return armedFrom != null;
    }
}
