package com.cio.createinteroperable.grid.client;

import com.cio.createinteroperable.CreateInteroperable;
import com.cio.createinteroperable.grid.ApplianceGrid;
import com.cio.createinteroperable.grid.ApplianceGridTags;
import com.cio.createinteroperable.grid.ApplianceNode;
import com.cio.createinteroperable.grid.ApplianceRaycast;
import com.cio.createinteroperable.grid.CrayfishCompat;
import com.cio.createinteroperable.grid.GridAffinity;
import com.cio.createinteroperable.grid.GridConnection;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Draws the native appliance grid in-world: the grey node cubes and their wires
 * for every {@link ApplianceNode} in range whose grid matches the connector in
 * the player's hand, plus the yellow in-progress link from the armed node to the
 * crosshair. The no-Crayfish analogue of MrCrayfish's
 * {@code ElectricBlockEntityRenderer.drawNodeAndConnections} + {@code LinkHandler
 * .render}; a no-op with Refurbished Furniture installed (its own renderer runs).
 */
@EventBusSubscriber(modid = CreateInteroperable.ID, value = Dist.CLIENT)
public final class ApplianceGridClientEvents {

    private static final int GREY = 0xFFB0B0B0;
    private static final int YELLOW = 0xFFFFE04D;
    private static final int WIRE = 0xFF2A2A2A;
    private static final double RENDER_RANGE = 48.0;

    private ApplianceGridClientEvents() {
    }

    @SubscribeEvent
    static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || CrayfishCompat.present()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        GridAffinity held = ApplianceGridTags.heldConnectorGrid(player);
        if (held == null) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        BlockPos armed = ApplianceLinkClient.armedFrom();
        BlockPos crosshair = mc.hitResult instanceof BlockHitResult bhr ? bhr.getBlockPos() : null;

        for (ApplianceNode node : ApplianceGrid.get(mc.level).liveNodes()) {
            BlockPos pos = node.appliancePos();
            if (pos.getCenter().distanceToSqr(cam) > RENDER_RANGE * RENDER_RANGE) {
                continue;
            }
            if (!ApplianceGridTags.componentGrid(mc.level, node).matches(held)) {
                continue;
            }
            AABB box = node.applianceNodeBox().move(pos);
            boolean hot = pos.equals(armed) || pos.equals(crosshair);
            drawBox(pose, lines, box, hot ? YELLOW : GREY);
            Vec3 from = box.getCenter();
            for (GridConnection conn : node.applianceConnections()) {
                BlockPos other = conn.otherPos(node);
                if (other == null) {
                    continue;
                }
                AABB otherBox = mc.level.getBlockEntity(other) instanceof ApplianceNode on
                        ? on.applianceNodeBox().move(other)
                        : ApplianceNode.APPLIANCE_NODE_BOX.move(other);
                drawLine(pose.last(), lines, from, otherBox.getCenter(), WIRE);
            }
        }

        // --- in-progress link: armed node -> hovered node or crosshair ---
        if (armed != null && mc.level.getBlockEntity(armed) instanceof ApplianceNode fromNode) {
            Vec3 start = fromNode.applianceNodeBox().move(armed).getCenter();
            BlockPos hovered = ApplianceRaycast.nodeUnderCrosshair(player,
                    event.getPartialTick().getGameTimeDeltaPartialTick(true), 5.0);
            Vec3 end;
            if (hovered != null && !hovered.equals(armed)
                    && mc.level.getBlockEntity(hovered) instanceof ApplianceNode toNode) {
                end = toNode.applianceNodeBox().move(hovered).getCenter();
            } else {
                Vec3 eye = player.getEyePosition(event.getPartialTick().getGameTimeDeltaPartialTick(true));
                end = eye.add(player.getViewVector(1.0f).scale(Math.min(5.0, start.subtract(eye).length() + 2.0)));
            }
            drawLine(pose.last(), lines, start, end, YELLOW);
        }

        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }

    private static void drawBox(PoseStack pose, VertexConsumer lines, AABB b, int argb) {
        LevelRenderer.renderLineBox(pose, lines, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ,
                ((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f, ((argb >>> 24) & 0xFF) / 255f);
    }

    /** Dead-straight line, no sag. */
    private static void drawLine(PoseStack.Pose p, VertexConsumer c, Vec3 a, Vec3 b, int argb) {
        Matrix4f m = p.pose();
        float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, bl = (argb & 0xFF) / 255f, al = ((argb >>> 24) & 0xFF) / 255f;
        float nx = (float) (b.x - a.x), ny = (float) (b.y - a.y), nz = (float) (b.z - a.z);
        float len = Math.max(1.0e-4f, (float) Math.sqrt(nx * nx + ny * ny + nz * nz));
        nx /= len; ny /= len; nz /= len;
        c.addVertex(m, (float) a.x, (float) a.y, (float) a.z).setColor(r, g, bl, al).setNormal(p, nx, ny, nz);
        c.addVertex(m, (float) b.x, (float) b.y, (float) b.z).setColor(r, g, bl, al).setNormal(p, nx, ny, nz);
    }
}
