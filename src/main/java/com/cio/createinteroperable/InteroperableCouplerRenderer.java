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
 * Draws the Interoperable Coupler's two gauge needles ({@code pointer_east} /
 * {@code pointer_west}, split out of {@code interoperable_coupler.json} into
 * {@code block/coupler/pointer_*.json}). Each pivots about its pin
 * ({@code cpg_east_pin} / {@code cee_west_pin}) around the world X axis by
 * {@link InteroperableCouplerBlockEntity#getPointerAngle()} — south-positive, so
 * +45&deg; = CPG&rarr;CEE (default), -45&deg; = CEE&rarr;CPG, 0&deg; = OFF. Both
 * needles take the same signed angle, so they always read the same way.
 *
 * While power is actually crossing, a small sine wobble makes the needle
 * magnitude drift ~44&ndash;48&deg; ("magnetic influence").
 */
public class InteroperableCouplerRenderer extends SafeBlockEntityRenderer<InteroperableCouplerBlockEntity> {

    public static final PartialModel POINTER_EAST =
            PartialModel.of(CreateInteroperable.rl("block/coupler/pointer_east"));
    public static final PartialModel POINTER_WEST =
            PartialModel.of(CreateInteroperable.rl("block/coupler/pointer_west"));

    /** Touch point so this class's static init (and the PartialModel.of calls) runs before Flywheel bakes. */
    public static void init() { }

    /** Pin centres, from the pointer elements' own rotation origins in the model. */
    private static final float PIN_E_X = 12.5f / 16f, PIN_E_Y = 3f / 16f, PIN_E_Z = 8f / 16f;
    private static final float PIN_W_X = 3.5f / 16f, PIN_W_Y = 3f / 16f, PIN_W_Z = 8f / 16f;

    /** Flip if the west needle ever renders mirrored relative to the east one. */
    private static final float WEST_SIGN = 1f;

    public InteroperableCouplerRenderer(BlockEntityRendererProvider.Context context) {
        super();
    }

    @Override
    protected void renderSafe(InteroperableCouplerBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        BlockState state = be.getBlockState();

        float angle = Mth.lerp(partialTicks, be.getPointerAnglePrev(), be.getPointerAngle());
        if (be.isTransferring() && be.getLevel() != null && Math.abs(angle) > 1f) {
            float t = (be.getLevel().getGameTime() + partialTicks) * 0.5f;
            angle = Math.copySign(46f + Mth.sin(t) * 2f, angle);
        }

        int facingAngle = InteroperableSmallBlock.angleFor(state);

        ms.pushPose();
        // Match the blockstate's per-facing "y" rotation (clockwise from top).
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(Axis.YP.rotationDegrees(-facingAngle));
        ms.translate(-0.5, -0.5, -0.5);

        float rad = (float) Math.toRadians(angle);
        drawNeedle(POINTER_EAST, state, ms, buffer, light, PIN_E_X, PIN_E_Y, PIN_E_Z, rad);
        drawNeedle(POINTER_WEST, state, ms, buffer, light, PIN_W_X, PIN_W_Y, PIN_W_Z, rad * WEST_SIGN);

        ms.popPose();
    }

    private static void drawNeedle(PartialModel model, BlockState state, PoseStack ms, MultiBufferSource buffer,
                                   int light, float px, float py, float pz, float radians) {
        CachedBuffers.partial(model, state)
                .translate(px, py, pz)
                .rotateX(radians)
                .translate(-px, -py, -pz)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }
}
