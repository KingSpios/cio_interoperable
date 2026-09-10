package com.cio.createinteroperable.deb;

import com.cio.createinteroperable.CIOBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * "CEE Commercial Power Kit" (tier 3) &mdash; CEE-wired twin of
 * {@link PowerKitTier3Block}. Substation layout: eight CEE nodes at the tier-3
 * model's nub spots &mdash; {@code 0/1} intake, {@code 2/3} HV Feed,
 * {@code 4/5} 120&nbsp;V Feed, {@code 6/7} 12&nbsp;V Feed &mdash; matching
 * {@link PowerKitTier3BlockEntity#buildCircuit}. The mode slider and every
 * electrical detail live in the shared BlockEntity.
 */
public class CeePowerKitTier3Block extends CeeDebRectifierBlock {

    private static final Map<Integer, Vec3> NODES = nodeMap(new double[][] {
            {9.5, 1.5, 2.5}, {6.5, 1.5, 2.5},
            {6.5, 16.5, 1.5}, {4.5, 16.5, 1.5},
            {11.5, 16.5, 4.5}, {9.5, 16.5, 4.5},
            {11.5, 16.5, 1.5}, {9.5, 16.5, 1.5},
    });

    public CeePowerKitTier3Block(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape outlineNorth() {
        return PowerKitGeometry.TIER3_SHAPE;
    }

    @Override
    protected Map<Integer, Vec3> nodesNorth() {
        return NODES;
    }

    @Override
    public BlockEntityType<? extends CeeDebRectifierBlockEntity> getBlockEntityType() {
        return CIOBlockEntities.CEE_DEB_RECTIFIER_TIER3.get();
    }
}
