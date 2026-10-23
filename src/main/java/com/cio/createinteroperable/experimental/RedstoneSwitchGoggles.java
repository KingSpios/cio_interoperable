package com.cio.createinteroperable.experimental;

import com.cio.createinteroperable.Lang;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/** Shared goggle-overlay body for both Redstone Switch BlockEntities. */
public final class RedstoneSwitchGoggles {
    private RedstoneSwitchGoggles() {}

    public static void append(List<Component> tooltip, String gk, MutableComponent blockName,
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
                        Lang.component("unit.watts_value", Math.round(throughputWatts)).withStyle(loadColor),
                        Lang.component("unit.watts_value", (int) Math.round(RedstoneSwitchStats.RATED_WATTS)).withStyle(ChatFormatting.GRAY)))
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
                            Lang.component("unit.percent_value", Math.round(faultProgress * 100f)).withStyle(ChatFormatting.RED)))
                    .style(ChatFormatting.GRAY)
                    .forGoggles(tooltip, 1);
        }

        double over = RedstoneSwitchStats.OVERHEAT_CELSIUS;
        ChatFormatting tempColor = temperatureC >= over * 0.8 ? ChatFormatting.RED
                : temperatureC >= over * 0.55 ? ChatFormatting.GOLD : ChatFormatting.GREEN;
        CreateLang.builder()
                .add(Component.translatable(gk + "temperature",
                        Lang.component("unit.degrees_value", Math.round(temperatureC)).withStyle(tempColor),
                        Lang.component("unit.degrees_value", (int) Math.round(over)).withStyle(ChatFormatting.GRAY)))
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);
    }
}
