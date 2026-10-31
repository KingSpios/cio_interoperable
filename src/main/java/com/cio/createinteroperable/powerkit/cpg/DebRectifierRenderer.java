package com.cio.createinteroperable.powerkit.cpg;

import com.cio.createinteroperable.pole.client.FenceMountRenderer;
import net.minecraft.world.phys.AABB;
import com.cio.createinteroperable.powerkit.PowerKitReadout;
import com.cio.createinteroperable.powerkit.PowerKitGeometry;
import com.cio.createinteroperable.Lang;
import com.cio.createinteroperable.CreateInteroperable;
import com.cio.createinteroperable.compat.Mods;
import com.cio.createinteroperable.grid.CrayfishClient;
import com.cio.createinteroperable.grid.Pool;
import com.cio.createinteroperable.powerkit.client.SignalNubRenderer;
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
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One renderer for every Power Kit tier (a BlockEntityType can only have one):
 * <ol>
 *   <li>delegates to Crayfish's own {@link ElectricBlockEntityRenderer#drawNodeAndConnections}
 *       so the wrench-link node box and connection lines draw like a native
 *       Crayfish electrical block;</li>
 *   <li>draws the face readouts &mdash; tier&nbsp;1 a spinning needle, tier&nbsp;2
 *       three text plates, tier&nbsp;3 five (HV, temperature, 120&nbsp;V,
 *       12&nbsp;V, usage) &mdash; only while an inlet is delivering power. Grey
 *       non-emissive label on the top row, emissive value below. Tiny-fitted-text
 *       technique from {@code TelephoneRenderer}.</li>
 * </ol>
 */
public class DebRectifierRenderer extends SafeBlockEntityRenderer<DebRectifierBlockEntity> {

    /** Tier-2 viewer plates sit at z=4.5; draw a hair in front. */
    private static final float PLATE_Z_T2 = (4.5f + 0.05f) / 16f;
    /** Tier-3 viewer plates sit at z=7.5. */
    private static final float PLATE_Z_T3 = (7.5f + 0.05f) / 16f;

    private static final int LABEL_GREY = 0xFFB8B8B8;
    private static final int VALUE_AMBER = 0xFFFFC048;
    private static final int VALUE_RED = 0xFFFF5555;
    /**
     * Height budget (model px) for a viewer's value. {@code drawFace} fits a whole
     * 9-row font line into its budget; the glyphs fill 8 of those rows (7 plus a
     * descender), so 9/8 makes the tallest glyph exactly 1 px. More digits only
     * shrink it further (the 2 px width budget).
     */
    private static final float VALUE_HEIGHT_PX = 9f / 8f;

    // --- tier-1 needle gauge --------------------------------------------
    /**
     * Just the {@code pointer_facing_up} element of the tier-1 model, exported
     * to its own sub-model. Rests pointing straight up (= 0 W). Flywheel bakes
     * and reloads this automatically once the asset exists.
     */
    public static final PartialModel POINTER =
            PartialModel.of(CreateInteroperable.rl("block/power_kit/pointer"));

    /**
     * No-op touch point. Call this from client renderer registration (which
     * runs before Flywheel bakes its partial-model set) so this class's static
     * initializer &mdash; and therefore {@link #POINTER}'s {@link PartialModel#of}
     * registration &mdash; happens in time.
     */
    public static void init() {
        SignalNubRenderer.init();
    }

    /** Pin centre from {@code clock_pin} [7.8,6.8,3.5]&ndash;[8.3,7.3,4.5], the pivot the pointer spins about. */
    private static final float PIN_X = 8.05f / 16f;
    private static final float PIN_Y = 7.05f / 16f;
    private static final float PIN_Z = 4.0f / 16f;

    /** Full-scale sweep: 0 W &rarr; 0&deg; (up); worst rail at its soft cap &rarr; 370&deg; clockwise. */
    private static final float FULL_SCALE_DEGREES = 370f;

    public DebRectifierRenderer(BlockEntityRendererProvider.Context context) {
        super();
    }

    @Override
    protected void renderSafe(DebRectifierBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        // The kit's back plate against a fence post: the pole's bracket.
        FenceMountRenderer.render(be, PowerKitGeometry.facing(be.getBlockState()), ms, buffer);
        // Crayfish's own node box + connection lines. The native grid draws its
        // cubes/wires from ApplianceGridClientEvents instead, so nothing here.
        if (Mods.REFURBISHED_FURNITURE.present()) {
            CrayfishClient.drawDebNodeOverlay(be);
        }

        BlockState state = be.getBlockState();

        // The signal nub (ComputerCraft CEE Addon installed) shows whether or not the kit is powered.
        if (state.getBlock() instanceof DebRectifierBlock kit) {
            ms.pushPose();
            applyFacing(ms, state);
            SignalNubRenderer.render(state, kit.signalNubBox(), ms, buffer, light);
            ms.popPose();
        }

        if (!be.isPowered()) {
            return;
        }

        ms.pushPose();
        applyFacing(ms, state);

        if (be.usesNeedleGauge()) {
            renderGauge(be, partialTicks, ms, buffer, light);
        } else if (be.usesTier3Viewers() && be instanceof PowerKitTier3BlockEntity t3) {
            // Substation (tier 3/4): the BE owns the per-model plate map.
            float z = t3.viewerPlateZ();
            for (PowerKitTier3BlockEntity.ViewerSpec s : t3.viewerSpecs()) {
                drawViewer(ms, buffer, light, z, s.label(), s.value(), s.fault(), s.cx(), s.baseY());
            }
        } else {
            float z = PLATE_Z_T2;
            drawViewer(ms, buffer, light, z, "12v", railPercent(be.getRailLoad(Pool.LV)), be.isRailFaulted(Pool.LV), 11f, 4f);
            drawViewer(ms, buffer, light, z, "120v", railPercent(be.getRailLoad(Pool.MV)), be.isRailFaulted(Pool.MV), 5f, 4f);
            String usage = be.isSwitchedOff() ? Lang.component("common.off_caps").getString()
                    : Integer.toString(Math.round(be.getTotalUsageWatts()));
            drawViewer(ms, buffer, light, z, Lang.component("powerkit.usage").getString(), usage, be.isSwitchedOff(), 8f, 8f);
        }

        ms.popPose();
    }

    private void renderGauge(DebRectifierBlockEntity be, float partialTicks, PoseStack ms,
                             MultiBufferSource buffer, int light) {
        float fraction = Mth.clamp(Mth.lerp(partialTicks, be.gaugePrev, be.gauge), 0f, 2f);
        float radians = (float) Math.toRadians(-fraction * FULL_SCALE_DEGREES); // -ve = clockwise looking at +Z

        CachedBuffers.partial(POINTER, be.getBlockState())
                .translate(PIN_X, PIN_Y, PIN_Z)
                .rotateZ(radians)
                .translate(-PIN_X, -PIN_Y, -PIN_Z)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }

    private static String railPercent(float load) {
        return PowerKitReadout.percent(load) + "%";
    }

    /**
     * @param plateZ the plate's own front-face Z (block units), tier-dependent
     * @param cx     viewer centre X in model pixels
     * @param baseY  viewer's bottom Y in model pixels (plate is 3 tall)
     */
    private void drawViewer(PoseStack ms, MultiBufferSource buffer, int worldLight, float plateZ,
                            String label, String value, boolean fault, float cx, float baseY) {
        drawFace(ms, buffer, label, cx / 16f, (baseY + 2.5f) / 16f, plateZ, 2f, 1f, LABEL_GREY, worldLight);
        drawFace(ms, buffer, value, cx / 16f, (baseY + 1.5f) / 16f, plateZ, 2f, VALUE_HEIGHT_PX,
                fault ? VALUE_RED : VALUE_AMBER, LightTexture.FULL_BRIGHT);
    }

    /** Auto-fit text onto the +Z viewer face within a model-pixel budget. */
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
        // Eyeball nudge: 0.1 model-px right + 0.1 down (readable face points +Z).
        ms.translate(localX + 0.1f / 16f, localY - 0.1f / 16f, plateZ);
        ms.mulPose(Axis.YP.rotationDegrees(180));
        ms.scale(-scale, -scale, scale);
        ms.translate(-stringWidth / 2f, -font.lineHeight / 2f, 0);
        font.drawInBatch(text, 0, 0, color, false, ms.last().pose(), buffer,
                Font.DisplayMode.NORMAL, 0, light);
        ms.popPose();
    }

    /** Rotate the render to match the blockstate JSON's per-facing model rotation, about block centre. */
    private static void applyFacing(PoseStack ms, BlockState state) {
        // Blockstate JSON order: x first, then y (so y is pushed first). JSON angles turn clockwise, Axis turns counter-clockwise.
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(Axis.YP.rotationDegrees(-PowerKitGeometry.modelY(state)));
        ms.mulPose(Axis.XP.rotationDegrees(-PowerKitGeometry.modelX(state)));
        ms.translate(-0.5, -0.5, -0.5);
    }

    @Override
    public AABB getRenderBoundingBox(DebRectifierBlockEntity be) {
        return FenceMountRenderer.renderBox(be);
    }

    @Override
    public int getViewDistance() {
        return FenceMountRenderer.VIEW_DISTANCE;
    }
}
