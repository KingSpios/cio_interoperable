package com.cio.createinteroperable.deb;

import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/** Shared goggle-overlay body for both Redstone Switch BlockEntities. */
final class RedstoneSwitchGoggles {
    private RedstoneSwitchGoggles() {}

    static void append(List<Component> tooltip, String gk, MutableComponent blockName,
                       boolean closed, boolean overridden,
                       float throughputWatts, float acrossVolts,
                       boolean faulted, int faultTicks, float faultProgress, float temperatureC) {
        CreateLang.builder().add(blockName.withStyle(ChatFormatting.WHITE)).forGoggles(tooltip);

        CreateLang.builder()
                .add(Component.translatable(gk + "state",
                        Component.translatable(gk + (closed ? "closed" : "open"))
                                .withStyle(closed ? ChatFormatting.GREEN : ChatFormatting.RED)))
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);

        if (overridden) {
            CreateLang.builder()
                    .add(Component.translatable(gk + "override").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC))
                    .forGoggles(tooltip, 1);
        }

        int pct = RedstoneSwitchStats.RATED_WATTS <= 0 ? 0
                : Math.round(throughputWatts / (float) RedstoneSwitchStats.RATED_WATTS * 100f);
        ChatFormatting loadColor = faulted ? ChatFormatting.RED
                : pct >= Math.round(RedstoneSwitchStats.HAZE_FRACTION * 100f) ? ChatFormatting.GOLD : ChatFormatting.GREEN;
        CreateLang.builder()
                .add(Component.translatable(gk + "throughput",
                        Component.literal(Math.round(throughputWatts) + " W").withStyle(loadColor),
                        Component.literal((int) Math.round(RedstoneSwitchStats.RATED_WATTS) + " W").withStyle(ChatFormatting.GRAY)))
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);

        CreateLang.builder()
                .add(Component.translatable(gk + "voltage",
                        Component.literal(Double.toString(Math.round(acrossVolts * 10f) / 10.0)).withStyle(ChatFormatting.AQUA)))
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);

        if (faultTicks > 0) {
            CreateLang.builder()
                    .add(Component.translatable(gk + "fault",
                            Component.literal(Math.round(faultProgress * 100f) + "%").withStyle(ChatFormatting.RED)))
                    .style(ChatFormatting.GRAY)
                    .forGoggles(tooltip, 1);
        }

        double over = RedstoneSwitchStats.OVERHEAT_CELSIUS;
        ChatFormatting tempColor = temperatureC >= over * 0.8 ? ChatFormatting.RED
                : temperatureC >= over * 0.55 ? ChatFormatting.GOLD : ChatFormatting.GREEN;
        CreateLang.builder()
                .add(Component.translatable(gk + "temperature",
                        Component.literal(Math.round(temperatureC) + "°").withStyle(tempColor),
                        Component.literal((int) Math.round(over) + "°").withStyle(ChatFormatting.GRAY)))
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);
    }
}
