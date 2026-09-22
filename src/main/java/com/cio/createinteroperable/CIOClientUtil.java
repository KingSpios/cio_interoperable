package com.cio.createinteroperable;

import net.minecraft.client.gui.screens.Screen;

/**
 * Tiny client-only wrapper for small client-singleton reads (currently just
 * {@code Screen.hasShiftDown()}) needed from common classes' tooltip code —
 * same reasoning as {@link TelephoneClient}: a common class (Block/BlockEntity/
 * Item, loaded on both sides) calling a client-only static directly is a real
 * dedicated-server crash risk (confirmed: TelephoneBlock/CpgTelephoneBlock's
 * own {@code Minecraft.getInstance().player} call, which passed a
 * client-only-typed value where a common-typed parameter was expected, made
 * NeoForge's RuntimeDistCleaner refuse to load the whole block class during
 * registration — "Attempted to load class .../LocalPlayer for invalid dist
 * DEDICATED_SERVER"). A plain {@code boolean}-returning call like
 * {@code Screen.hasShiftDown()} used only in an if-condition doesn't hit that
 * specific failure mode (no reference-type assignability check is needed at
 * class-verify time for a primitive), but that safety margin is an unwritten
 * detail of how javac/the JVM verifier happen to behave today, not a
 * documented guarantee — so it's routed through this class instead of left
 * inline, exactly like every other client-singleton read in this project.
 */
public class CIOClientUtil {
    public static boolean hasShiftDown() {
        return Screen.hasShiftDown();
    }
}
