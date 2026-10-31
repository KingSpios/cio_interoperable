package com.cio.createinteroperable.telephone;

import com.cio.createinteroperable.Lang;
import com.cio.createinteroperable.pole.client.FenceMountRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

/**
 * The visors of CIO's telephones (Interoperable, CPG, CEE; the Radio Telephone's renderer extends
 * this one: its plates sit further forward, behind visor lids, so the plate depth is a constructor
 * argument). Two plates, both light, so the inks are dark:
 * <ul>
 *   <li><b>info_plate_bottom</b> (x6-10, y7-10, three 1 px rows): the network the phone is on
 *   (the label of the interconnect on its wire, else a local line), its number as
 *   {@code (AAA)-NNNNNN}, and its name in colour.</li>
 *   <li><b>info_plate_top</b> (x6-10, y11-13, lit only): "Free" across both rows; on a call the
 *   status ("Incoming call", "Calling", "In call") above the far end, whose number and label
 *   take turns every {@link #ALTERNATE_TICKS}. The Radio Telephone puts its radio readout on the
 *   top row instead and the status below ({@link #drawStatus}).</li>
 * </ul>
 * Every row keeps {@link #PAD} inside the plate's edges, the Power Kit viewers' standard.
 */
public class TelephoneRenderer<T extends BlockEntity & TelephoneNode> extends SafeBlockEntityRenderer<T> {
    protected static final float PLATE_CENTER_X = 8f / 16f;
    /** The plates: the identity plate's three rows (bottom y of each), the status plate's two. */
    protected static final float NETWORK_ROW = 9f, NUMBER_ROW = 8f, NAME_ROW = 7f, STATUS_TOP_ROW = 12f, STATUS_BOTTOM_ROW = 11f;

    /** Dark inks on the light plates (about #dadada): each 4.5:1 or better. */
    protected static final int NETWORK_INK = 0xFF4A4D55;
    protected static final int NUMBER_INK = 0xFF1E2026;
    protected static final int NAME_INK = 0xFF24408E;
    protected static final int FREE_INK = 0xFF1F7A45;
    protected static final int INCOMING_INK = 0xFFA8321E;
    protected static final int CALLING_INK = 0xFF8A5A10;
    /**
     * Text keeps this far (model px) inside a plate's edges, as the Power Kit viewers keep theirs:
     * a plate's text budget is its size less the inset on each side that is a plate edge.
     */
    protected static final float PAD = 0.25f;
    /** The font's 9-row line holds 8 rows of glyph (7 + a descender): a glyph height x 9/8 is its line budget. */
    protected static final float LINE_PER_GLYPH = 9f / 8f;
    /** The far end's number and label take turns this long each. */
    protected static final int ALTERNATE_TICKS = 40;

    /** The plates' readable (north) face, nudged a hair forward so the text doesn't z-fight. */
    private final float plateZ;

    public TelephoneRenderer(BlockEntityRendererProvider.Context context) {
        // info_plate_top/bottom's own north face is at z=9.9 on the wired phones.
        this(context, 9.9f);
    }

    protected TelephoneRenderer(BlockEntityRendererProvider.Context context, float plateFrontPx) {
        super();
        this.plateZ = (plateFrontPx - 0.05f) / 16f;
    }

    @Override
    protected void renderSafe(T be, float partialTicks, PoseStack matrices, MultiBufferSource consumer, int light, int overlay) {
        BlockState state = be.getBlockState();
        // The phone's back (its south at facing north) against a fence post: the pole's bracket.
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            FenceMountRenderer.render(be, state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite(), matrices, consumer);
        }
        int angle = TelephoneNumbers.angleFor(state);
        renderInfoText(be, angle, matrices, consumer, light);
    }

    @Override
    public AABB getRenderBoundingBox(T be) {
        return FenceMountRenderer.renderBox(be);
    }

    @Override
    public int getViewDistance() {
        return FenceMountRenderer.VIEW_DISTANCE;
    }

    private void renderInfoText(T be, int angle, PoseStack matrices, MultiBufferSource consumer, int light) {
        matrices.pushPose();
        // Same yaw as the model itself, so the text stays glued to
        // info_plate_top/bottom regardless of which wall the phone faces.
        turnWithModel(matrices, angle);

        // Lit (powered): full bright, the status plate on. Unpowered, the identity plate is
        // just print under the block's own light, the status plate blank.
        boolean lit = be.isPowered();
        int textLight = lit ? LightTexture.FULL_BRIGHT : light;
        TelephoneCore core = be instanceof CioTelephone phone ? phone.telephoneCore() : null;

        String network = core != null ? core.networkName() : "";
        drawRow(matrices, consumer, network.isEmpty() ? Lang.component("telephone.visor.local").getString() : network,
                NETWORK_ROW, 1, NETWORK_INK, textLight);
        drawRow(matrices, consumer, visorNumber(be.getAreaCode(), be.getOwnNumberText()), NUMBER_ROW, 0, NUMBER_INK, textLight);
        drawRow(matrices, consumer, be.getLabel(), NAME_ROW, -1, NAME_INK, textLight);

        if (lit && core != null) {
            drawStatus(be, core, matrices, consumer);
        }
        matrices.popPose();
    }

    /** The status plate: "Free" across it, or the call's status over its far end. */
    protected void drawStatus(T be, TelephoneCore core, PoseStack matrices, MultiBufferSource consumer) {
        TelephoneCore.VisorStatus status = core.visorStatus();
        if (status == TelephoneCore.VisorStatus.FREE) {
            drawFittedText(matrices, consumer, Lang.component("telephone.visor.free").getString(), PLATE_CENTER_X,
                    (STATUS_BOTTOM_ROW + 1f) / 16f, 4f - 2 * PAD, (2f - 2 * PAD) * LINE_PER_GLYPH, FREE_INK, LightTexture.FULL_BRIGHT);
            return;
        }
        drawRow(matrices, consumer, Lang.component("telephone.visor." + status.key()).getString(), STATUS_TOP_ROW, 1,
                statusInk(status), LightTexture.FULL_BRIGHT);
        drawRow(matrices, consumer, farEnd(be, core), STATUS_BOTTOM_ROW, -1, NUMBER_INK, LightTexture.FULL_BRIGHT);
    }

    protected static int statusInk(TelephoneCore.VisorStatus status) {
        return switch (status) {
            case FREE, IN_CALL -> FREE_INK;
            case INCOMING -> INCOMING_INK;
            case CALLING -> CALLING_INK;
        };
    }

    /** The call's far end: its number, taking turns with its label every {@link #ALTERNATE_TICKS} when it has one. */
    protected static String farEnd(BlockEntity be, TelephoneCore core) {
        String number = visorNumber(core.visorPartnerNumber());
        String label = core.visorPartnerLabel();
        if (label.isEmpty() || be.getLevel() == null) {
            return number;
        }
        return number.isEmpty() || (be.getLevel().getGameTime() / ALTERNATE_TICKS) % 2 == 1 ? label : number;
    }

    /** {@code (AAA)-NNNNNN}; just {@code (AAA)} with no number set. */
    protected static String visorNumber(int areaCode, String number) {
        String area = "(" + String.format("%03d", areaCode) + ")";
        return number.isEmpty() ? area : area + "-" + number;
    }

    /** A dialled or partner number, {@code AAA-NNNNNN}, as the visor writes numbers. */
    protected static String visorNumber(String formatted) {
        int dash = formatted.indexOf('-');
        return dash > 0 ? "(" + formatted.substring(0, dash) + ")" + formatted.substring(dash) : formatted;
    }

    /**
     * One 1 px row of a plate, its bottom at {@code rowBottomPx}: {@code edge} 1 for the plate's
     * top row (inset {@link #PAD} from the top), -1 for its bottom row (inset from the bottom),
     * 0 for a middle one; always inset from both sides. Every row's glyphs are the same height.
     */
    protected void drawRow(PoseStack matrices, MultiBufferSource buffer, String text, float rowBottomPx, int edge,
                           int color, int light) {
        float centre = rowBottomPx + 0.5f - edge * PAD / 2;
        drawFittedText(matrices, buffer, text, PLATE_CENTER_X, centre / 16f, 4f - 2 * PAD, (1f - PAD) * LINE_PER_GLYPH, color, light);
    }

    /** The blockstate's yaw for {@code angle} (see {@link TelephoneNumbers#angleFor}), about the block centre. */
    protected static void turnWithModel(PoseStack matrices, int angle) {
        matrices.translate(0.5, 0, 0.5);
        matrices.mulPose(Axis.YP.rotationDegrees(-angle));
        matrices.translate(-0.5, 0, -0.5);
    }

    /**
     * Fitted plate text ({@link VisorText}). maxWidthPx/maxHeightPx are in the model's own
     * 16-units-per-block voxel scale (so 4 means "4 of the model's own pixels"); an eyeball
     * nudge of 0.1 model-px right + 0.1 down centres the glyphs on the plate.
     */
    protected void drawFittedText(PoseStack matrices, MultiBufferSource buffer, String text,
                                  float localX, float localY, float maxWidthPx, float maxHeightPx, int color, int light) {
        VisorText.draw(matrices, buffer, text, localX, localY, plateZ, maxWidthPx, maxHeightPx, 0.1f, color, light);
    }
}
