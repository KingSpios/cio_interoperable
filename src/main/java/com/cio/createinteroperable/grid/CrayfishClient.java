package com.cio.createinteroperable.grid;

import com.cio.createinteroperable.CIOBlockEntities;
import com.mrcrayfish.furniture.refurbished.client.renderer.blockentity.ElectricBlockEntityRenderer;
import com.mrcrayfish.furniture.refurbished.electricity.IElectricityNode;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client-side Crayfish glue, kept out of {@code CIOClient} and
 * {@code DebRectifierRenderer} so neither has a hard {@code com.mrcrayfish.*}
 * reference. Every entry point is called only from behind a
 * {@link CrayfishCompat#present()} check, so this class never classloads when
 * Refurbished Furniture is absent.
 *
 * <p>Raw casts throughout: the CIO block entities gain Crayfish's
 * {@code IElectricityNode} at runtime via the node adapter mixins, not at
 * compile time.</p>
 */
public final class CrayfishClient {

    private CrayfishClient() {
    }

    /**
     * Attach Crayfish's own {@code ElectricBlockEntityRenderer} (yellow node box
     * + connection lines) to the CIO Let's Do lamp type and to Beachparty's
     * radio / mini-fridge block entity types, so a CIO appliance node draws just
     * like a native Crayfish one.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void registerApplianceNodeRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer((BlockEntityType) CIOBlockEntities.LETSDO_LAMP.get(),
                (BlockEntityRendererProvider) ElectricBlockEntityRenderer::new);
        if (CIOBlockEntities.ELECTRIC_SWITCH != null) {
            event.registerBlockEntityRenderer((BlockEntityType) CIOBlockEntities.ELECTRIC_SWITCH.get(),
                    (BlockEntityRendererProvider) ElectricBlockEntityRenderer::new);
        }
        if (CIOBlockEntities.MTS_AA_POWER_NODE != null) {
            event.registerBlockEntityRenderer((BlockEntityType) CIOBlockEntities.MTS_AA_POWER_NODE.get(),
                    (BlockEntityRendererProvider) ElectricBlockEntityRenderer::new);
        }
        // Immersive Vehicles' shared tile type: only poles / Signal Controllers are
        // nodes (IvTileNodeMixin), so draw just those — never a road or pump.
        registerFilteredNodeRenderer(event, "mts:builder_base");
        registerForeignNodeRenderer(event, "beachparty:radio");
        registerForeignNodeRenderer(event, "beachparty:mini_fridge");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerForeignNodeRenderer(EntityRenderersEvent.RegisterRenderers event, String beTypeId) {
        BlockEntityType<?> type = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(ResourceLocation.parse(beTypeId));
        if (type != null) {
            event.registerBlockEntityRenderer((BlockEntityType) type,
                    (BlockEntityRendererProvider) ElectricBlockEntityRenderer::new);
        }
    }

    /**
     * Crayfish's node box + wires on a foreign block entity type that is only
     * sometimes a node (IV's one tile type behind poles, roads, pumps...): drawn
     * only for instances that currently have link slots. For a type with no
     * renderer of its own.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerFilteredNodeRenderer(EntityRenderersEvent.RegisterRenderers event, String beTypeId) {
        BlockEntityType<?> type = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(ResourceLocation.parse(beTypeId));
        if (type != null) {
            event.registerBlockEntityRenderer((BlockEntityType) type,
                    (BlockEntityRendererProvider) context -> new FilteredNodeRenderer());
        }
    }

    private static final class FilteredNodeRenderer implements BlockEntityRenderer<BlockEntity> {
        @Override
        public void render(BlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            if (be instanceof ApplianceNode node && node.applianceConnectionLimit() > 0) {
                drawNodeOverlayIfNode(be);
            }
        }
    }

    /**
     * As {@link #drawDebNodeOverlay}, for a foreign block entity whose own
     * renderer displaces Crayfish's: a no-op unless its node adapter mixin
     * actually merged (checked, not cast blindly).
     */
    public static void drawNodeOverlayIfNode(BlockEntity be) {
        if ((Object) be instanceof IElectricityNode node) {
            ElectricBlockEntityRenderer.drawNodeAndConnections(node);
        }
    }

    /** Draw the DEB / Power Kit's Crayfish wrench-link node box + connection lines. */
    public static void drawDebNodeOverlay(BlockEntity be) {
        ElectricBlockEntityRenderer.drawNodeAndConnections((IElectricityNode) (Object) be);
    }
}
