package com.cio.createinteroperable.deb;

import com.cio.createinteroperable.CreateInteroperable;
import com.cio.createinteroperable.grid.CrayfishClient;
import com.cio.createinteroperable.grid.CrayfishCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * CEE-only twin of {@link DebRectifierRenderer}. Same face-plate/needle-gauge
 * drawing, but reads the CEE blocks' own vanilla {@link BlockStateProperties#FACING}
 * (6-way) instead of Power Grid's {@code DirectionalElectricBlock.FACING} —
 * this class must never reference a Power Grid type at all.
 */
public class CeeDebRectifierRenderer extends SafeBlockEntityRenderer<CeeDebRectifierBlockEntity> {

    private static final float PLATE_Z_T2 = (4.5f + 0.05f) / 16f;

    private static final int LABEL_GREY = 0xFFB8B8B8;
    private static final int VALUE_AMBER = 0xFFFFC048;
    private static final int VALUE_RED = 0xFFFF5555;

    public static final PartialModel POINTER =
            PartialModel.of(CreateInteroperable.rl("block/power_kit/pointer"));

    public static void init() { }

    private static final float PIN_X = 8.05f / 16f;
    private static final float PIN_Y = 7.05f / 16f;
    private static final float PIN_Z = 4.0f / 16f;

    private static final float FULL_SCALE_DEGREES = 370f;

    public CeeDebRectifierRenderer(BlockEntityRendererProvider.Context context) {
        super();
    }

    @Override
    protected void renderSafe(CeeDebRectifierBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        // CeeDebRectifierBlockEntity gains Crayfish's IElectricityNode/ISourceNode
        // at runtime via CeeDebSourceNodeMixin (mirroring DebSourceNodeMixin on
        // the PG-rooted family) — same wrench-link node box + connection lines
        // as the CPG-wired Power Kit.
        if (CrayfishCompat.present()) {
            CrayfishClient.drawDebNodeOverlay(be);
        }

        if (!be.isPowered()) {
            return;
        }

        BlockState state = be.getBlockState();
        Direction facing = state.hasProperty(BlockStateProperties.FACING)
                ? state.getValue(BlockStateProperties.FACING) : Direction.NORTH;

        ms.pushPose();
        applyFacing(ms, facing);

        if (be.usesNeedleGauge()) {
            renderGauge(be, partialTicks, ms, buffer, light);
        } else if (be.usesTier3Viewers() && be instanceof CeePowerKitTier3BlockEntity t3) {
            float z = t3.viewerPlateZ();
            for (CeePowerKitTier3BlockEntity.ViewerSpec s : t3.viewerSpecs()) {
                drawViewer(ms, buffer, light, z, s.label(), s.value(), s.fault(), s.cx(), s.baseY());
            }
        } else {
            float z = PLATE_Z_T2;
            drawViewer(ms, buffer, light, z, "12v", railPercent(be.getRailLoad(Pool.LV)), be.isRailFaulted(Pool.LV), 11f, 4f);
            drawViewer(ms, buffer, light, z, "120v", railPercent(be.getRailLoad(Pool.MV)), be.isRailFaulted(Pool.MV), 5f, 4f);
            drawViewer(ms, buffer, light, z, "USAGE", Integer.toString(Math.round(be.getTotalUsageWatts())), false, 8f, 8f);
        }

        ms.popPose();
    }

    private void renderGauge(CeeDebRectifierBlockEntity be, float partialTicks, PoseStack ms,
                             MultiBufferSource buffer, int light) {
        float fraction = Mth.clamp(Mth.lerp(partialTicks, be.gaugePrev, be.gauge), 0f, 2f);
        float radians = (float) Math.toRadians(-fraction * FULL_SCALE_DEGREES);

        CachedBuffers.partial(POINTER, be.getBlockState())
                .translate(PIN_X, PIN_Y, PIN_Z)
                .rotateZ(radians)
                .translate(-PIN_X, -PIN_Y, -PIN_Z)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }

    private static String railPercent(float load) {
        return Math.round(load * 100f) + "%";
    }

    private void drawViewer(PoseStack ms, MultiBufferSource buffer, int worldLight, float plateZ,
                            String label, String value, boolean fault, float cx, float baseY) {
        drawFace(ms, buffer, label, cx / 16f, (baseY + 2.5f) / 16f, plateZ, 2f, 1f, LABEL_GREY, worldLight);
        drawFace(ms, buffer, value, cx / 16f, (baseY + 1.5f) / 16f, plateZ, 2f, 2f,
                fault ? VALUE_RED : VALUE_AMBER, LightTexture.FULL_BRIGHT);
    }

    private static void drawFace(PoseStack ms, MultiBufferSource buffer, String text,
                                 float localX, float localY, float plateZ, float maxWidthPx, float maxHeightPx,
                                 int color, int light) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int stringWidth = Math.max(1, font.width(text));

        float widthScale = (maxWidthPx / 16f) / stringWidth;
        float heightScale = (maxHeightPx / 16f) / font.lineHeight;
        float scale = Math.min(widthScale, heightScale);

        ms.pushPose();
        ms.translate(localX + 0.1f / 16f, localY - 0.1f / 16f, plateZ);
        ms.mulPose(Axis.YP.rotationDegrees(180));
        ms.scale(-scale, -scale, scale);
        ms.translate(-stringWidth / 2f, -font.lineHeight / 2f, 0);
        font.drawInBatch(text, 0, 0, color, false, ms.last().pose(), buffer,
                Font.DisplayMode.NORMAL, 0, light);
        ms.popPose();
    }

    private static void applyFacing(PoseStack ms, Direction facing) {
        ms.translate(0.5, 0.5, 0.5);
        switch (facing) {
            case NORTH -> { }
            case SOUTH -> ms.mulPose(Axis.YP.rotationDegrees(180));
            case EAST -> ms.mulPose(Axis.YP.rotationDegrees(-90));
            case WEST -> ms.mulPose(Axis.YP.rotationDegrees(90));
            case DOWN -> ms.mulPose(Axis.XP.rotationDegrees(-90));
            case UP -> ms.mulPose(Axis.XP.rotationDegrees(90));
        }
        ms.translate(-0.5, -0.5, -0.5);
    }
}
