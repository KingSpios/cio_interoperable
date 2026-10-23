package com.cio.createinteroperable.compat;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Optional;

/**
 * Client-side picking of PnW cantilever contact-wire nodes. CEE finds nodes by
 * looking around the block under the crosshair, but a cantilever's contact wire
 * tip hangs up to several blocks away from the cantilever block, so it is never
 * found that way. This ray-tests those tips directly.
 */
public final class PnwNodePicker {
    /** Cantilever arm reach (6.5) plus the player's interaction range, with margin. */
    private static final int SEARCH_RADIUS = 16;
    /** Same hitbox size CEE uses for its own free-floating nodes. */
    private static final double NODE_SIZE = 0.25;

    private PnwNodePicker() {
    }

    /**
     * The cantilever node under the crosshair if it is nearer than {@code current}
     * (CEE's own pick), otherwise {@code null}.
     */
    public static InWorldNode pick(Level level, InWorldNode current) {
        Player player = Minecraft.getInstance().player;
        if (level == null || player == null) {
            return null;
        }
        Vec3 from = player.getEyePosition();
        Vec3 to = from.add(player.getViewVector(1f).scale(player.blockInteractionRange() + 1));
        double best = Double.MAX_VALUE;
        if (current != null) {
            Vec3 position = current.getPosition(level);
            if (position != null) {
                best = position.distanceTo(from);
            }
        }

        InWorldNode picked = null;
        BlockPos center = player.blockPosition();
        int minX = SectionPos.blockToSectionCoord(center.getX() - SEARCH_RADIUS);
        int maxX = SectionPos.blockToSectionCoord(center.getX() + SEARCH_RADIUS);
        int minZ = SectionPos.blockToSectionCoord(center.getZ() - SEARCH_RADIUS);
        int maxZ = SectionPos.blockToSectionCoord(center.getZ() + SEARCH_RADIUS);
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos pos = be.getBlockPos();
                    if (!PnwCeeNodes.isCantilever(be.getBlockState().getBlock()) || pos.distManhattan(center) > SEARCH_RADIUS * 3) {
                        continue;
                    }
                    for (Map.Entry<Integer, Vec3> node : PnwCeeNodes.nodePositions(level, pos, be.getBlockState()).entrySet()) {
                        Vec3 tip = Vec3.atLowerCornerOf(pos).add(node.getValue());
                        Optional<Vec3> hit = AABB.ofSize(tip, NODE_SIZE, NODE_SIZE, NODE_SIZE).clip(from, to);
                        if (hit.isPresent() && hit.get().distanceTo(from) < best) {
                            best = hit.get().distanceTo(from);
                            picked = new InWorldNode(node.getKey(), pos);
                        }
                    }
                }
            }
        }
        return picked;
    }

    /** Whether CEE's spool should treat this node like a free-floating ("detached") one. */
    public static boolean isCantileverNode(InWorldNode node) {
        Level level = Minecraft.getInstance().level;
        return level != null && node.id() < PnwCeeNodes.DERIVED_ID_BASE && PnwCeeNodes.isChunkPresent(level, node.sourcePos())
                && PnwCeeNodes.isCantilever(level.getBlockState(node.sourcePos()).getBlock());
    }
}
