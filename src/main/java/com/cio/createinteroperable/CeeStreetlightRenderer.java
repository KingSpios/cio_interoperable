package com.cio.createinteroperable;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The Streetlight's glow: the light-overflow rays hanging under the head plus
 * a sheen over the glass and the bulb, drawn the way Power Grid draws its
 * Factory Light rays and lit bulbs &mdash; a separate model through an
 * <b>additive</b> render type at full brightness. Additive blending is what
 * makes it read as light: the glow texture is opaque, black where there is no
 * light (adds nothing) and brighter where there is more, and it brightens
 * whatever is behind it instead of tinting it like a translucent pane. A
 * static block model can't do this (chunk geometry is solid, cutout or
 * translucent only), so these parts live here, not in the block models.
 *
 * <p>Under the glow it also redraws the glass and {@code bulb_body} with the
 * lamp's own on / brown texture at the full-bright lightmap and without
 * directional shading &mdash; in effect an emissive texture. In the block
 * model those faces take world light and Minecraft's fixed shading (a
 * downward face is drawn at half brightness), so the glass looked dull even
 * with the on texture. Done here rather than with NeoForge's per-element
 * {@code neoforge_data} light because Sodium's chunk renderer does not reliably
 * honour that, and these instances run Sodium + Iris.</p>
 *
 * <p>Only drawn while the bulb is lit: warm white when on, orange while it
 * browns out, scaled by the lamp's own light level (8..15).</p>
 */
public class CeeStreetlightRenderer extends SafeBlockEntityRenderer<CeeStreetlightBlockEntity> {
    public static final PartialModel GLOW = PartialModel.of(CreateInteroperable.rl("block/streetlamp_glow"));
    public static final PartialModel GLOW_ANGLED = PartialModel.of(CreateInteroperable.rl("block/streetlamp_glow_angled"));
    public static final PartialModel LIT = PartialModel.of(CreateInteroperable.rl("block/streetlamp_lit"));
    public static final PartialModel LIT_ANGLED = PartialModel.of(CreateInteroperable.rl("block/streetlamp_lit_angled"));
    public static final PartialModel LIT_BROWN = PartialModel.of(CreateInteroperable.rl("block/streetlamp_lit_brown"));
    public static final PartialModel LIT_BROWN_ANGLED = PartialModel.of(CreateInteroperable.rl("block/streetlamp_lit_brown_angled"));

    /** Power Grid's {@code ModdedRenderLayers.ADDITIVE}, but colour-only writes so overlapping rays add up instead of hiding each other. */
    private static final RenderType ADDITIVE = RenderType.create("createinteroperable_additive", DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS, RenderType.SMALL_BUFFER_SIZE, false, true, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_TRANSLUCENT_SHADER)
                    .setTextureState(RenderStateShard.BLOCK_SHEET)
                    .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setOutputState(RenderStateShard.TRANSLUCENT_TARGET)
                    .createCompositeState(false));

    private static final int[] ON_TINT = {255, 228, 170};
    private static final int[] BROWN_TINT = {255, 130, 50};

    /** Loads this class so the PartialModels register before Flywheel bakes its set (see CIOClient). */
    public static void init() {
    }

    public CeeStreetlightRenderer(BlockEntityRendererProvider.Context context) {
        super();
    }

    @Override
    protected void renderSafe(CeeStreetlightBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        BlockState state = be.getBlockState();
        CeeStreetlightBlock.Bulb bulb = state.getValue(CeeStreetlightBlock.BULB);
        if (!bulb.lit()) {
            return;
        }
        float strength = state.getValue(CeeStreetlightBlock.LIGHT) / 15f;
        boolean on = bulb == CeeStreetlightBlock.Bulb.ON;
        boolean angled = state.getValue(CeeStreetlightBlock.STYLE) == CeeStreetlightBlock.Style.ANGLED;
        int facingDegrees = -CeeTelephoneBlock.angleFor(state); // blockstate "y": 90 turns north to east, i.e. -90 degrees about +Y

        // Full-bright glass + bulb: the on texture at full white; a browning-out bulb a little dimmer.
        int lit = on ? 255 : (int) (255 * (0.55f + 0.45f * strength));
        CachedBuffers.partial(on ? (angled ? LIT_ANGLED : LIT) : (angled ? LIT_BROWN_ANGLED : LIT_BROWN), state)
                .rotateYCenteredDegrees(facingDegrees)
                .disableDiffuse()
                .light(LightTexture.FULL_BRIGHT)
                .color(lit, lit, lit, 255)
                .renderInto(ms, buffer.getBuffer(RenderType.translucent()));

        int[] tint = on ? ON_TINT : BROWN_TINT;
        CachedBuffers.partial(angled ? GLOW_ANGLED : GLOW, state)
                .rotateYCenteredDegrees(facingDegrees)
                .disableDiffuse()
                .light(LightTexture.FULL_BRIGHT)
                .color((int) (tint[0] * strength), (int) (tint[1] * strength), (int) (tint[2] * strength), 255)
                .renderInto(ms, buffer.getBuffer(ADDITIVE));
    }

    /** The rays hang up to 8 px into the block below; keep drawing them while only that block is on screen. */
    @Override
    public AABB getRenderBoundingBox(CeeStreetlightBlockEntity be) {
        return new AABB(be.getBlockPos()).expandTowards(0, -1, 0);
    }
}
