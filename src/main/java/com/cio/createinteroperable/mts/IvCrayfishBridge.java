package com.cio.createinteroperable.mts;

import com.mrcrayfish.furniture.refurbished.electricity.IElectricityNode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Crayfish-typed glue for the Immersive Vehicles pole/controller node: reads and
 * writes the Crayfish-side connection set in the small client sync tag that
 * {@code IvTileNodeMixin} gives IV's block entity (IV ships no vanilla BE sync
 * of its own, and Crayfish's wire sync goes through {@code getUpdateTag()}),
 * and drops every link of a pole that lost its last lamp. Only ever called
 * behind {@code CrayfishCompat.present()}, so it never classloads without
 * Refurbished Furniture.
 */
public final class IvCrayfishBridge {

    private IvCrayfishBridge() {
    }

    public static void writeNodeNbt(BlockEntity be, CompoundTag tag) {
        if ((Object) be instanceof IElectricityNode node) {
            node.writeNodeNbt(tag);
        }
    }

    public static void readNodeNbt(BlockEntity be, CompoundTag tag) {
        if ((Object) be instanceof IElectricityNode node) {
            node.readNodeNbt(tag);
        }
    }

    /** True if this node had links and they were all removed (both ends). */
    public static boolean removeAllConnections(BlockEntity be) {
        if ((Object) be instanceof IElectricityNode node && !node.getNodeConnections().isEmpty()) {
            node.removeAllNodeConnections();
            return true;
        }
        return false;
    }
}
