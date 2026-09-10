package com.cio.createinteroperable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Shared by all three Telephone variants (Interoperable, CPG-only, CEE-only)
 * — generic over any {@link BlockEntity} that also implements
 * {@link TelephoneNode}, since the info-plate readout this draws (Area Code,
 * own number, label) is pure text formatting with no protocol-specific
 * dependency at all. The bulb_mount feature this used to also render has been
 * removed from every variant (including the Interoperable one).
 */
public class TelephoneRenderer<T extends BlockEntity & TelephoneNode> extends SafeBlockEntityRenderer<T> {
    /** A hair in front of z=9.9 (info_plate_top/bottom's own north face) so the text doesn't z-fight with the plate. */
    private static final float PLATE_Z = (9.9f - 0.05f) / 16f;
    private static final float PLATE_CENTER_X = 8f / 16f;

    private static final int GREY_BLACK = 0xFF303030;
    private static final int LIGHT_GREY = 0xFFC6C6C6;
    private static final int WHITE_ISH = 0xFFEDEDED;

    public TelephoneRenderer(BlockEntityRendererProvider.Context context) {
        super();
    }

    @Override
    protected void renderSafe(T be, float partialTicks, PoseStack matrices, MultiBufferSource consumer, int light, int overlay) {
        int angle = TelephoneNumbers.angleFor(be.getBlockState());
        renderInfoText(be, angle, matrices, consumer, light);
    }

    private void renderInfoText(TelephoneNode be, int angle, PoseStack matrices, MultiBufferSource consumer, int light) {
        matrices.pushPose();
        // Same yaw as the model itself, so the text stays glued to
        // info_plate_top/bottom regardless of which wall the phone faces.
        matrices.translate(0.5, 0, 0.5);
        matrices.mulPose(Axis.YP.rotationDegrees(-angle));
        matrices.translate(-0.5, 0, -0.5);

        // Only glow (ignore actual world lighting) while the phone is
        // powered — unpowered, the text just uses the block's real ambient
        // light like any other surface.
        int textLight = be.isPowered() ? LightTexture.FULL_BRIGHT : light;

        // info_plate_top: x6-10,y11-13 -> a 4x2 pixel plate, area code fills it exactly.
        String areaCode = String.format("%03d", be.getAreaCode());
        drawFittedText(matrices, consumer, areaCode, PLATE_CENTER_X, 12f / 16f, 4f, 2f, GREY_BLACK, textLight);

        // info_plate_bottom: x6-10,y7-10 -> 4x3. Own number (no area code —
        // that's already shown on info_plate_top) on one line (y9-10),
        // label on the last pixel (y7-8). y8-9 is unused now.
        drawFittedText(matrices, consumer, be.getOwnNumberText(), PLATE_CENTER_X, 9.5f / 16f, 4f, 1f, LIGHT_GREY, textLight);
        drawFittedText(matrices, consumer, be.getLabel(), PLATE_CENTER_X, 7.5f / 16f, 4f, 1f, WHITE_ISH, textLight);

        matrices.popPose();
    }

    /**
     * Auto-fit technique lifted directly from Create's own
     * ValueBox.TextValueBox#renderContents (confirmed by reading its real
     * source) — the only place in Create's codebase that scales a
     * Font#drawInBatch call to fit a fixed pixel budget rather than a fixed
     * font size. maxWidthPx/maxHeightPx are in the model's own 16-units-
     * per-block voxel scale (so 4 means "4 of the model's own pixels").
     */
    private static void drawFittedText(PoseStack matrices, MultiBufferSource buffer, String text,
                                        float localX, float localY, float maxWidthPx, float maxHeightPx, int color, int light) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int stringWidth = Math.max(1, font.width(text));

        matrices.pushPose();
        // Eyeball nudge: 0.1 model-px right + 0.1 down. This plate's readable
        // face points north, so "right" (from a viewer looking south at it) is
        // model -X; "down" is -Y. Flip a sign here if it drifts the wrong way.
        matrices.translate(localX - 0.1f / 16f, localY - 0.1f / 16f, PLATE_Z);
        // Flip to face north (toward the room) and avoid mirrored text —
        // same negative-scale trick Create's own ValueBox render() uses for
        // exactly this reason. Untested against a live view angle; if the
        // text reads backwards or upside down in-game, flip the sign here.
        float widthScale = (maxWidthPx / 16f) / stringWidth;
        float heightScale = (maxHeightPx / 16f) / font.lineHeight;
        float scale = Math.min(widthScale, heightScale);
        matrices.scale(-scale, -scale, scale);
        matrices.translate(-stringWidth / 2f, -font.lineHeight / 2f, 0);

        font.drawInBatch(text, 0, 0, color, false, matrices.last().pose(), buffer,
                Font.DisplayMode.NORMAL, 0, light);
        matrices.popPose();
    }
}
