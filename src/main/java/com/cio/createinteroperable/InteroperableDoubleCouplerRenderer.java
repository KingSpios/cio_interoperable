package com.cio.createinteroperable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws the Double Coupler's four gauge needles — {@code pointer_lw/le/rw/re},
 * split out of {@code interoperable_double_coupler.json} into
 * {@code block/double_coupler/pointer_*.json}. Each pivots about its pin around
 * world X by {@link InteroperableDoubleCouplerBlockEntity#getPointerAngle()}
 * (south-positive: +45 = CPG&rarr;CEE, -45 = CEE&rarr;CPG, 0 = OFF). All four take
 * the same signed angle so the whole block reads one way. While power is actually
 * crossing the needles buzz a few degrees around ~46 &mdash; a gauge pinned at its
 * endstop &mdash; more agitated the more current is flowing.
 */
public class InteroperableDoubleCouplerRenderer extends SafeBlockEntityRenderer<InteroperableDoubleCouplerBlockEntity> {

    public static final PartialModel POINTER_LW =
            PartialModel.of(CreateInteroperable.rl("block/double_coupler/pointer_lw"));
    public static final PartialModel POINTER_LE =
            PartialModel.of(CreateInteroperable.rl("block/double_coupler/pointer_le"));
    public static final PartialModel POINTER_RW =
            PartialModel.of(CreateInteroperable.rl("block/double_coupler/pointer_rw"));
    public static final PartialModel POINTER_RE =
            PartialModel.of(CreateInteroperable.rl("block/double_coupler/pointer_re"));

    public static void init() { }

    /** Pin centres from each pointer element's own rotation origin (model units / 16). */
    private static final float[] PIN_LW = { -0.5f / 16f, 3f / 16f, 8f / 16f };
    private static final float[] PIN_LE = { 8.5f / 16f, 3f / 16f, 8f / 16f };
    private static final float[] PIN_RW = { 7.5f / 16f, 3f / 16f, 8f / 16f };
    private static final float[] PIN_RE = { 16.5f / 16f, 3f / 16f, 8f / 16f };

    public InteroperableDoubleCouplerRenderer(BlockEntityRendererProvider.Context context) {
        super();
    }

    @Override
    protected void renderSafe(InteroperableDoubleCouplerBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        BlockState state = be.getBlockState();

        float angle = Mth.lerp(partialTicks, be.getPointerAnglePrev(), be.getPointerAngle());
        if (be.isTransferring() && be.getLevel() != null && Math.abs(angle) > 1f) {
            // Needle pinned at its endstop, buzzing: a slow sway + a fast tremor
            // that gets more agitated the more current is flowing. Centre ~46
            // (just past the +/-45 rest), swinging a few degrees either way.
            float t = be.getLevel().getGameTime() + partialTicks;
            float load = Mth.clamp(be.getLastTransferCurrent() / 5f, 0f, 1f);
            float buzz = 1.6f * Mth.sin(t * 0.40f)
                       + (1.0f + load) * Mth.sin(t * 1.90f + 2f);
            angle = Math.copySign(Mth.clamp(46f + buzz, 43f, 49f), angle);
        }
        float rad = (float) Math.toRadians(angle);

        int facingAngle = InteroperableSmallBlock.angleFor(state);

        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(Axis.YP.rotationDegrees(-facingAngle));
        ms.translate(-0.5, -0.5, -0.5);

        drawNeedle(POINTER_LW, state, ms, buffer, light, PIN_LW, rad);
        drawNeedle(POINTER_LE, state, ms, buffer, light, PIN_LE, rad);
        drawNeedle(POINTER_RW, state, ms, buffer, light, PIN_RW, rad);
        drawNeedle(POINTER_RE, state, ms, buffer, light, PIN_RE, rad);

        ms.popPose();
    }

    private static void drawNeedle(PartialModel model, BlockState state, PoseStack ms, MultiBufferSource buffer,
                                   int light, float[] pin, float radians) {
        CachedBuffers.partial(model, state)
                .translate(pin[0], pin[1], pin[2])
                .rotateX(radians)
                .translate(-pin[0], -pin[1], -pin[2])
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }
}
