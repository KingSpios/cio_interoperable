package com.cio.createinteroperable.deb;

import com.cio.createinteroperable.CreateInteroperable;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
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
 * One renderer for both Redstone Switch variants (Power Grid
 * {@link RedstoneSwitchBlockEntity} and Electro Energetics
 * {@link CeeRedstoneSwitchBlockEntity}) &mdash; generic over any
 * {@link SmartBlockEntity} that also reports {@link RedstoneSwitchDisplay},
 * since all it draws is two text readouts and the sliding contact, neither of
 * which touches either solver.
 *
 * <p>The contact ({@code switch} partial) is translated in the model's own
 * local &minus;Y <em>before</em> the facing rotation, so it always slides
 * toward the bottom (voltage) viewer &mdash; down a wall, along a floor,
 * across a ceiling &mdash; never blindly along world Y.</p>
 */
public class RedstoneSwitchRenderer<T extends SmartBlockEntity & RedstoneSwitchDisplay>
        extends SafeBlockEntityRenderer<T> {

    /** Both viewer plates' front face sits at z=4.5; draw text a hair in front. */
    private static final float PLATE_Z = (4.5f + 0.05f) / 16f;
    /** Contact travel: 2 model px toward the bottom viewer. */
    private static final float SLIDE_PX = 2f;

    private static final int LABEL_GREY = 0xFFB8B8B8;
    private static final int VALUE_AMBER = 0xFFFFC048;
    private static final int VALUE_RED = 0xFFFF5555;

    /** The {@code switch_off} element of {@code redstone_switch.json}, split into its own sub-model. */
    public static final PartialModel CONTACT =
            PartialModel.of(CreateInteroperable.rl("block/redstone_switch/switch"));

    /** No-op touch point, called from renderer registration so this class's {@code <clinit>} runs before Flywheel bakes. */
    public static void init() {
    }

    public RedstoneSwitchRenderer(BlockEntityRendererProvider.Context context) {
        super();
    }

    @Override
    protected void renderSafe(T be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        BlockState state = be.getBlockState();
        Direction facing = state.hasProperty(BlockStateProperties.FACING)
                ? state.getValue(BlockStateProperties.FACING) : Direction.NORTH;

        ms.pushPose();
        applyFacing(ms, facing);

        float slide = Mth.clamp(Mth.lerp(partialTicks, be.switchSlidePrev(), be.switchSlide()), 0f, 1f);
        CachedBuffers.partial(CONTACT, state)
                .translate(0, -slide * SLIDE_PX / 16f, 0)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));

        if (be.switchHasReadout()) {
            boolean fault = be.switchFaulted();
            drawViewer(ms, buffer, light, "W", Integer.toString(Math.round(be.switchThroughputWatts())), fault, 8f, 10f);
            drawViewer(ms, buffer, light, "V", fmt1(be.switchAcrossVolts()), false, 8f, 3f);
        }

        ms.popPose();
    }

    private static String fmt1(float v) {
        return Double.toString(Math.round(v * 10f) / 10.0);
    }

    /** @param cx viewer centre X (model px); @param baseY viewer bottom Y (model px), plate is 3 tall. */
    private void drawViewer(PoseStack ms, MultiBufferSource buffer, int worldLight,
                            String label, String value, boolean fault, float cx, float baseY) {
        drawFace(ms, buffer, label, cx / 16f, (baseY + 2.5f) / 16f, 2f, 1f, LABEL_GREY, worldLight);
        drawFace(ms, buffer, value, cx / 16f, (baseY + 1.5f) / 16f, 2f, 2f,
                fault ? VALUE_RED : VALUE_AMBER, LightTexture.FULL_BRIGHT);
    }

    private static void drawFace(PoseStack ms, MultiBufferSource buffer, String text,
                                 float localX, float localY, float maxWidthPx, float maxHeightPx, int color, int light) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int stringWidth = Math.max(1, font.width(text));
        float scale = Math.min((maxWidthPx / 16f) / stringWidth, (maxHeightPx / 16f) / font.lineHeight);

        ms.pushPose();
        ms.translate(localX + 0.1f / 16f, localY - 0.1f / 16f, PLATE_Z);
        ms.mulPose(Axis.YP.rotationDegrees(180));
        ms.scale(-scale, -scale, scale);
        ms.translate(-stringWidth / 2f, -font.lineHeight / 2f, 0);
        font.drawInBatch(text, 0, 0, color, false, ms.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, light);
        ms.popPose();
    }

    /** Match the blockstate JSON's per-facing model rotation, about the block centre. */
    private static void applyFacing(PoseStack ms, Direction facing) {
        ms.translate(0.5, 0.5, 0.5);
        switch (facing) {
            case NORTH -> {
            }
            case SOUTH -> ms.mulPose(Axis.YP.rotationDegrees(180));
            case EAST -> ms.mulPose(Axis.YP.rotationDegrees(-90));
            case WEST -> ms.mulPose(Axis.YP.rotationDegrees(90));
            case DOWN -> ms.mulPose(Axis.XP.rotationDegrees(-90));
            case UP -> ms.mulPose(Axis.XP.rotationDegrees(90));
        }
        ms.translate(-0.5, -0.5, -0.5);
    }
}
