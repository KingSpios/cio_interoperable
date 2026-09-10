package com.cio.createinteroperable.deb;

import com.cio.createinteroperable.CIOBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * "CEE Industrial Power Kit" (tier 4) &mdash; CEE-wired twin of
 * {@link PowerKitTier4Block}. Same three-tap substation as its CPG counterpart;
 * eight CEE nodes at the tier-4 model's (wider) nub spots, ids matching
 * {@link PowerKitTier3BlockEntity#buildCircuit}.
 */
public class CeePowerKitTier4Block extends CeeDebRectifierBlock {

    private static final Map<Integer, Vec3> NODES = nodeMap(new double[][] {
            {10.0, 0.5, 3.0}, {6.0, 0.5, 3.0},
            {10.0, 17.5, 3.0}, {6.0, 17.5, 3.0},
            {14.5, 17.5, 4.5}, {1.5, 17.5, 4.5},
            {14.5, 17.5, 1.5}, {1.5, 17.5, 1.5},
    });

    public CeePowerKitTier4Block(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape outlineNorth() {
        return PowerKitGeometry.TIER4_SHAPE;
    }

    @Override
    protected Map<Integer, Vec3> nodesNorth() {
        return NODES;
    }

    @Override
    public BlockEntityType<? extends CeeDebRectifierBlockEntity> getBlockEntityType() {
        return CIOBlockEntities.CEE_DEB_RECTIFIER_TIER4.get();
    }
}
