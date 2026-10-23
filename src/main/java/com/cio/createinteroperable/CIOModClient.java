package com.cio.createinteroperable;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-only entrypoint: gives Create: Interoperable a Config button in the
 * mod list. The screen is NeoForge's generated one; its option names and
 * tooltips come from the {@code createinteroperable.configuration.*} keys in
 * the lang file (set on each option in {@link CIOConfig}), so it translates
 * like everything else.
 */
@Mod(value = CreateInteroperable.ID, dist = Dist.CLIENT)
public final class CIOModClient {
    public CIOModClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
