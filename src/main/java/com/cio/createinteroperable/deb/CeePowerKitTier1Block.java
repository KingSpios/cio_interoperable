package com.cio.createinteroperable.deb;

import com.cio.createinteroperable.CIOBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * "CEE Improvised Power Kit" (tier 1) &mdash; CEE-wired twin of
 * {@link PowerKitTier1Block}. 12&nbsp;V rail only: four CEE nodes
 * ({@code 0/1} intake, {@code 2/3} 12&nbsp;V Power Feed) at the tier-1 model's
 * own nub spots.
 */
public class CeePowerKitTier1Block extends CeeDebRectifierBlock {

    private static final Map<Integer, Vec3> NODES = nodeMap(new double[][] {
            {9.5, 0.5, 2.5}, {6.5, 0.5, 2.5},
            {9.5, 13.5, 2.5}, {6.5, 13.5, 2.5},
    });

    public CeePowerKitTier1Block(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape outlineNorth() {
        return PowerKitGeometry.TIER1_SHAPE;
    }

    @Override
    protected Map<Integer, Vec3> nodesNorth() {
        return NODES;
    }

    @Override
    public BlockEntityType<? extends CeeDebRectifierBlockEntity> getBlockEntityType() {
        return CIOBlockEntities.CEE_DEB_RECTIFIER_TIER1.get();
    }
}
