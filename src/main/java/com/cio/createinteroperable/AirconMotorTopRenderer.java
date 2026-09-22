package com.cio.createinteroperable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * Renders {@code blade_1}/{@code blade_2} and {@code flap1}-{@code flap6} —
 * all extracted out of the base {@code aircon_motor_top.json} into their own
 * partial model files ({@code aircon_top_blades.json},
 * {@code aircon_top_flap1.json}..{@code aircon_top_flap6.json}) so the static
 * baked model doesn't ALSO draw them (they'd otherwise double-render, once
 * static and once here) — same real technique {@code DebRectifierRenderer}
 * already uses in this project for its needle gauge (itself modeled directly
 * on Create's own {@code EncasedFanRenderer}): {@code CachedBuffers.partial(...)}
 * + translate-to-pivot / rotate / translate-back, via the generic (non-Create,
 * Flywheel-library) {@link PartialModel} API any addon can use directly —
 * confirmed by reading {@code AllPartialModels.block(...)}, which is nothing
 * more than a thin wrapper over {@code PartialModel.of(ResourceLocation)}.
 * <p>
 * The 6 flaps each get their OWN partial model file rather than sharing one
 * (unlike the blades, which share {@code aircon_top_blades.json} since both
 * blades rotate together, by the same angle, around the same shared pivot):
 * every flap wants a DIFFERENT target angle and a DIFFERENT pivot (its own Z
 * center, per {@code aircon_motor_top.json}'s original per-flap
 * {@code rotation.origin}) — combining any two into one file/one transform
 * would rotate them both around a single shared point, shifting whichever
 * one's real center doesn't sit on that point, not just tilting it in place.
 * <p>
 * Pivot for the blades is the model's own {@code reference_pin} element's
 * X/Z center (x7-9, z7-9 → 8,8) — a Y-axis rotation doesn't care about the
 * pivot's Y coordinate, so that's left at 0. Each flap instead rotates about
 * the X axis (a real vent-flap tilt, not a spin), so ITS pivot needs a real Y
 * (14, the flap's own resting height) and Z (its own per-flap center); the X
 * coordinate is irrelevant to an X-axis rotation and left at the block
 * center for clarity only. No Flywheel {@code Visual} counterpart is needed:
 * this renderer isn't superseded by one the way Create's OWN kinetic blocks
 * are (that early-return only fires for blocks Create itself has registered
 * a Visual for), so it renders unconditionally, Flywheel active or not.
 */
public class AirconMotorTopRenderer extends SafeBlockEntityRenderer<AirconMotorTopBlockEntity> {
    /** = aircon_motor_top.json's "reference_pin" element center, X/Z only. */
    private static final float PIVOT_X = 8f / 16f;
    private static final float PIVOT_Z = 8f / 16f;

    /** Every flap's own resting Y (14/16) — shared, since all 6 sit at the same height in the model. */
    private static final float FLAP_PIVOT_Y = 14f / 16f;

    /**
     * The blade partial model. No-op {@link #init()} exists purely so this
     * class's static initializer (and therefore this field's real
     * registration) runs before Flywheel bakes its partial-model set — same
     * pattern as {@code DebRectifierRenderer#POINTER}/{@code init()}, called
     * from {@code CIOClient}'s own renderer registration.
     */
    public static final PartialModel BLADES =
            PartialModel.of(CreateInteroperable.rl("block/aircon_top_blades"));

    /**
     * One partial model + pivot Z + fully-open target angle per flap, in the
     * same order the design request specified: flaps 1/2 -> 22.5°, flap 3 ->
     * 45°, flap 4 -> -45°, flaps 5/6 -> -22.5°. Z pivots copied verbatim from
     * {@code aircon_motor_top.json}'s original per-flap {@code rotation.origin}
     * before extraction (flap1=13, flap2=11, flap3=9, flap4=7, flap5=5, flap6=3).
     */
    private static final Flap[] FLAPS = {
            new Flap(PartialModel.of(CreateInteroperable.rl("block/aircon_top_flap1")), 13f / 16f, 22.5f),
            new Flap(PartialModel.of(CreateInteroperable.rl("block/aircon_top_flap2")), 11f / 16f, 22.5f),
            new Flap(PartialModel.of(CreateInteroperable.rl("block/aircon_top_flap3")), 9f / 16f, 45f),
            new Flap(PartialModel.of(CreateInteroperable.rl("block/aircon_top_flap4")), 7f / 16f, -45f),
            new Flap(PartialModel.of(CreateInteroperable.rl("block/aircon_top_flap5")), 5f / 16f, -22.5f),
            new Flap(PartialModel.of(CreateInteroperable.rl("block/aircon_top_flap6")), 3f / 16f, -22.5f),
    };

    private record Flap(PartialModel model, float pivotZ, float openDegrees) {
    }

    public static void init() {
    }

    public AirconMotorTopRenderer(BlockEntityRendererProvider.Context context) {
        super();
    }

    @Override
    protected void renderSafe(AirconMotorTopBlockEntity be, float partialTicks, PoseStack ms,
                               MultiBufferSource buffer, int light, int overlay) {
        float degrees = be.getBladeAngleDegrees(partialTicks) % 360f;
        float radians = (float) Math.toRadians(degrees);

        CachedBuffers.partial(BLADES, be.getBlockState())
                .translate(PIVOT_X, 0, PIVOT_Z)
                .rotateY(radians)
                .translate(-PIVOT_X, 0, -PIVOT_Z)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));

        // Deliberately no early-return at openFraction == 0: the flaps were
        // extracted OUT of the static baked model specifically so this
        // renderer is the only thing that ever draws them (see class doc) —
        // skipping the loop here left them fully closed AND invisible, since
        // nothing else was drawing the closed pose either. At 0, flapRadians
        // below is just 0 for every flap, which IS their correct closed
        // resting geometry — always render them, never skip.
        float openFraction = be.getFlapOpenFraction();
        for (Flap flap : FLAPS) {
            float flapRadians = (float) Math.toRadians(flap.openDegrees() * openFraction);
            CachedBuffers.partial(flap.model(), be.getBlockState())
                    .translate(PIVOT_X, FLAP_PIVOT_Y, flap.pivotZ())
                    .rotateX(flapRadians)
                    .translate(-PIVOT_X, -FLAP_PIVOT_Y, -flap.pivotZ())
                    .light(light)
                    .renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
        }
    }
}
