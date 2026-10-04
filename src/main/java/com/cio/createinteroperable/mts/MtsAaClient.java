package com.cio.createinteroperable.mts;

import com.cio.createinteroperable.grid.CrayfishCompat;
import mcinterface1211.WrapperWorld;
import minecrafttransportsimulator.baseclasses.EntityInteractResult;
import minecrafttransportsimulator.baseclasses.Point3D;
import minecrafttransportsimulator.entities.components.AEntityE_Interactable;
import minecrafttransportsimulator.entities.instances.APart;
import minecrafttransportsimulator.entities.instances.EntityPlacedPart;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Client-only: the look-at "Missing power" label for the Immersive Vehicles AA
 * Spotlight. Crayfish's own label only ever fires on a vanilla block hit, and
 * IV's plate and Spotlight are entities that vanilla picking never lands on, so
 * this draws the same label (same box, same icon when Refurbished Furniture is
 * installed) whenever the crosshair is on a ground-placed AA Base Plate &mdash;
 * or anything mounted on it &mdash; that carries a Spotlight and has no power.
 *
 * <p>Registered on the game bus only on a physical client with Immersive
 * Vehicles installed ({@code CreateInteroperable}).</p>
 */
public final class MtsAaClient {

    private static final ResourceLocation CRAYFISH_ICONS =
            ResourceLocation.fromNamespaceAndPath(CrayfishCompat.MOD_ID, "textures/gui/icons.png");

    private static boolean showLabel;
    private static int ticks;

    private MtsAaClient() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (++ticks % 2 != 0) {
            return;
        }
        showLabel = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null) {
            return;
        }
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getViewVector(1.0F);
        double reach = mc.player.blockInteractionRange();
        Point3D start = new Point3D(eye.x, eye.y, eye.z);
        Point3D end = new Point3D(eye.x + look.x * reach, eye.y + look.y * reach, eye.z + look.z * reach);
        EntityInteractResult hit = WrapperWorld.getWrapperFor(mc.level).getMultipartEntityIntersect(start, end);
        if (hit == null) {
            return;
        }
        // A solid block in front of the plate wins, like any look-at label.
        HitResult vanilla = mc.hitResult;
        if (vanilla != null && vanilla.getType() == HitResult.Type.BLOCK
                && vanilla.getLocation().distanceToSqr(eye) < new Vec3(hit.position.x, hit.position.y, hit.position.z).distanceToSqr(eye)) {
            return;
        }
        APart base = groundBaseUnder(hit.entity);
        if (base == null || base.parts.stream().noneMatch(MtsAaSearchlights::isSpotlight)) {
            return;
        }
        showLabel = !MtsAaSearchlights.isBasePowered(base);
    }

    /** The ground-placed plate the clicked entity is (or is mounted on), walking up the part chain. */
    @Nullable
    private static APart groundBaseUnder(AEntityE_Interactable<?> entity) {
        if (entity instanceof EntityPlacedPart placed) {
            entity = placed.currentPart;
        }
        for (int depth = 0; entity instanceof APart part && depth < 4; depth++) {
            if (MtsAaSearchlights.isGroundBase(part)) {
                return part;
            }
            entity = part.partOn;
        }
        return null;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!showLabel || mc.options.hideGui || mc.screen != null) {
            return;
        }
        drawLabel(mc, event.getGuiGraphics(), Component.translatable(MtsAaSearchlights.NO_POWER_KEY));
    }

    /** Crayfish's {@code NodeIndicatorOverlay} label, drawn identically. */
    private static void drawLabel(Minecraft mc, GuiGraphics graphics, Component label) {
        boolean icon = CrayfishCompat.present();
        int padding = 3;
        int iconSize = icon ? 10 : 0;
        int iconGap = icon ? padding : 0;
        int messageWidth = mc.font.width(label);
        int contentWidth = padding + iconSize + iconGap + messageWidth + padding;
        int contentHeight = padding + mc.font.lineHeight + padding;
        int contentStart = (graphics.guiWidth() - contentWidth) / 2;
        int contentTop = (graphics.guiHeight() - contentHeight) / 2 + 50;

        graphics.fill(contentStart, contentTop + 1, contentStart + 1, contentTop + contentHeight - 1, 0x77000000);
        graphics.fill(contentStart + 1, contentTop, contentStart + contentWidth - 1, contentTop + contentHeight, 0x77000000);
        graphics.fill(contentStart + contentWidth - 1, contentTop + 1, contentStart + contentWidth, contentTop + contentHeight - 1, 0x77000000);

        if (icon) {
            graphics.blit(CRAYFISH_ICONS, contentStart + padding, contentTop + padding, 20, 20, iconSize, iconSize, 64, 64);
        }
        graphics.drawString(mc.font, label, contentStart + padding + iconSize + iconGap, contentTop + padding + 1, 0xFFFFFFFF);
    }
}
