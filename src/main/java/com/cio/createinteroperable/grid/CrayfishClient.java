package com.cio.createinteroperable.grid;

import com.cio.createinteroperable.letsdo.BeachpartyRadioRenderer;
import com.cio.createinteroperable.urbandecor.UrbanColdRenderer;
import com.cio.createinteroperable.urbandecor.UrbanMachineLampRenderer;
import com.cio.createinteroperable.urbandecor.UrbanSpeakerRenderer;
import com.cio.createinteroperable.urbandecor.UrbanWashingMachineBlockEntity;
import com.cio.createinteroperable.compat.Mods;
import com.cio.createinteroperable.CIOBlockEntities;
import com.cio.createinteroperable.modules.CIOModules;
import com.cio.createinteroperable.modules.IdenDecorModule;
import com.cio.createinteroperable.modules.ImmersiveVehiclesModule;
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
 * {@link Mods#REFURBISHED_FURNITURE} check, so this class never classloads when
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
        if (CIOModules.isEnabled(IdenDecorModule.REQUIRES)) {
            event.registerBlockEntityRenderer((BlockEntityType) IdenDecorModule.ELECTRIC_SWITCH_BE.get(),
                    (BlockEntityRendererProvider) ElectricBlockEntityRenderer::new);
        }
        // The Urban Decor washing machine node (a type with no blocks without Urban Decor).
        // With its indicator lamps (UrbanMachineLampRenderer) in the same renderer: a type has one.
        event.registerBlockEntityRenderer((BlockEntityType) CIOBlockEntities.URBAN_WASHER.get(),
                (BlockEntityRendererProvider) context -> new UrbanMachineNodeRenderer());
        // ...and its dryer and dishwasher, the desk fan, and (filtered: Urban Decor's smoker block entity is
        // also the microwave's, which is never a node) the oven.
        event.registerBlockEntityRenderer((BlockEntityType) CIOBlockEntities.URBAN_DRYER.get(),
                (BlockEntityRendererProvider) context -> new UrbanMachineNodeRenderer());
        event.registerBlockEntityRenderer((BlockEntityType) CIOBlockEntities.URBAN_DISHWASHER.get(),
                (BlockEntityRendererProvider) context -> new UrbanMachineNodeRenderer());
        event.registerBlockEntityRenderer((BlockEntityType) CIOBlockEntities.URBAN_FAN.get(),
                (BlockEntityRendererProvider) ElectricBlockEntityRenderer::new);
        registerFilteredNodeRenderer(event, "urban_decor:smoker_decor");
        registerFilteredNodeRenderer(event, "urban_decor:furnace_decor"); // the microwave (and the stove, which is no node)
        // Urban Decor's fridge and freezer (the storage type its cupboards share): node, plus the light inside.
        BlockEntityType<?> storage = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(ResourceLocation.parse("urban_decor:storage_decor"));
        if (storage != null) {
            event.registerBlockEntityRenderer((BlockEntityType) storage,
                    (BlockEntityRendererProvider) context -> new UrbanColdNodeRenderer());
        }
        if (CIOModules.isEnabled(ImmersiveVehiclesModule.REQUIRES)) {
            event.registerBlockEntityRenderer((BlockEntityType) ImmersiveVehiclesModule.MTS_AA_POWER_NODE_BE.get(),
                    (BlockEntityRendererProvider) ElectricBlockEntityRenderer::new);
        }
        // Immersive Vehicles' shared tile type: only poles / Signal Controllers are
        // nodes (IvTileNodeMixin), so draw just those — never a road or pump.
        registerFilteredNodeRenderer(event, "mts:builder_base");
        // The radio's own CIO parts (antenna pin, lit visor) in the same renderer: a type has one.
        BlockEntityType<?> radio = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(ResourceLocation.parse("beachparty:radio"));
        if (radio != null) {
            event.registerBlockEntityRenderer((BlockEntityType) radio, (BlockEntityRendererProvider) context -> new RadioRenderer());
        }
        registerForeignNodeRenderer(event, "beachparty:mini_fridge");
        // Urban Decor's speaker: its node, and its radio pin in the same renderer.
        BlockEntityType<?> speaker = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(ResourceLocation.parse("urban_decor:speaker"));
        if (speaker != null) {
            event.registerBlockEntityRenderer((BlockEntityType) speaker, (BlockEntityRendererProvider) context -> new UrbanSpeakerNodeRenderer());
        }
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

    /** Crayfish's node box + wires, and {@link BeachpartyRadioRenderer}'s parts, on Beachparty's radio. */
    private static final class RadioRenderer implements BlockEntityRenderer<BlockEntity> {
        @Override
        public void render(BlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            BeachpartyRadioRenderer.draw(be, pose, buffers, light);
            drawNodeOverlayIfNode(be);
        }
    }

    /** Crayfish's node box + wires, and {@link UrbanSpeakerRenderer}'s pin, on Urban Decor's speaker. */
    private static final class UrbanSpeakerNodeRenderer implements BlockEntityRenderer<BlockEntity> {
        @Override
        public void render(BlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            UrbanSpeakerRenderer.draw(be, pose, buffers, light);
            drawNodeOverlayIfNode(be);
        }
    }

    /** Crayfish's node box + wires, and the indicator lamps, on the Urban Decor washer / dryer / dishwasher. */
    private static final class UrbanMachineNodeRenderer implements BlockEntityRenderer<BlockEntity> {
        @Override
        public void render(BlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            if (be instanceof UrbanWashingMachineBlockEntity machine) {
                UrbanMachineLampRenderer.draw(machine, pose, buffers);
            }
            drawNodeOverlayIfNode(be);
        }
    }

    /** Crayfish's node box + wires on a fridge or freezer (never a cupboard), and the light inside. */
    private static final class UrbanColdNodeRenderer implements BlockEntityRenderer<BlockEntity> {
        @Override
        public void render(BlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            UrbanColdRenderer.draw(be, pose, buffers);
            if (be instanceof ApplianceNode node && node.applianceConnectionLimit() > 0) {
                drawNodeOverlayIfNode(be);
            }
        }

        @Override
        public boolean shouldRenderOffScreen(BlockEntity be) {
            return true;
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
