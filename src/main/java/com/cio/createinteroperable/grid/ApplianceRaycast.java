package com.cio.createinteroperable.grid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Common (both-dist) ray cast of a player's look ray against every loaded
 * {@link ApplianceNode}'s link cube &mdash; the no-item analogue of Crayfish's
 * {@code WrenchItem.performNodeRaycast}. Each box is inflated 1/16 like Crayfish,
 * and the first cell hit along the ray wins.
 */
public final class ApplianceRaycast {

    private ApplianceRaycast() {
    }

    @Nullable
    public static BlockPos nodeUnderCrosshair(Player player, float partialTick, double reach) {
        Level level = player.level();
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 look = player.getViewVector(partialTick);
        Vec3 end = eye.add(look.x * reach, look.y * reach, look.z * reach);
        BlockPos hit = BlockGetter.traverseBlocks(eye, end, eye, (start, pos) -> {
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof ApplianceNode node)) {
                return null;
            }
            AABB box = node.positionedApplianceNodeBox().inflate(1.0 / 16.0);
            return box.clip(start, end).isPresent() ? pos.immutable() : null;
        }, start -> null);
        return hit;
    }
}
