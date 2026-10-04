package com.cio.createinteroperable.iden;

import com.cio.createinteroperable.CreateInteroperable;
import com.cio.createinteroperable.compat.ElectroEnergeticsCompat;
import com.cio.createinteroperable.compat.PowerGridCompat;
import com.cio.createinteroperable.grid.CrayfishClient;
import com.cio.createinteroperable.grid.CrayfishCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * Draws what Iden's Decor's own telephone model doesn't have: the tap nubs on
 * the back of the base (CPG with Power Grid installed, CEE with Electro
 * Energetics), rotated with the phone. Also stands in for Crayfish's
 * {@code ElectricBlockEntityRenderer} (this renderer displaces it) so the
 * appliance-grid node and its wires still draw.
 */
public class IdenTelephoneRenderer<T extends SmartBlockEntity & IdenPhone> extends SafeBlockEntityRenderer<T> {

    public static final PartialModel CPG_TAP = PartialModel.of(CreateInteroperable.rl("block/iden_telephone_cpg_tap"));
    public static final PartialModel CEE_TAP = PartialModel.of(CreateInteroperable.rl("block/iden_telephone_cee_tap"));

    /** Classloads the partials above before models bake; called from {@code CIOClient}. */
    public static void init() {
    }

    public IdenTelephoneRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(T be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        if (CrayfishCompat.present()) {
            CrayfishClient.drawNodeOverlayIfNode(be);
        }
        float yaw = (float) Math.toRadians(-IdenPhoneBlocks.angleFor(be.getBlockState()));
        if (PowerGridCompat.present()) {
            renderNub(CPG_TAP, be, yaw, ms, buffer, light);
        }
        if (ElectroEnergeticsCompat.present()) {
            renderNub(CEE_TAP, be, yaw, ms, buffer, light);
        }
    }

    private static void renderNub(PartialModel model, SmartBlockEntity be, float yaw, PoseStack ms,
                                  MultiBufferSource buffer, int light) {
        CachedBuffers.partial(model, be.getBlockState())
                .translate(0.5f, 0, 0.5f)
                .rotateY(yaw)
                .translate(-0.5f, 0, -0.5f)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
    }
}
