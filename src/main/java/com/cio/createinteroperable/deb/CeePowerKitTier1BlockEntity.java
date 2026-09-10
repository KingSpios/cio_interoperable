package com.cio.createinteroperable.deb;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * "CEE Improvised Power Kit" (tier 1) BlockEntity — CEE-only twin of
 * {@link PowerKitTier1BlockEntity}. All behaviour lives in
 * {@link CeeDebRectifierBlockEntity}; this only flips the two tier hooks.
 */
public class CeePowerKitTier1BlockEntity extends CeeDebRectifierBlockEntity {

    public CeePowerKitTier1BlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected DebTier tier() {
        return DebTier.TIER_1;
    }

    @Override
    public boolean usesNeedleGauge() {
        return true;
    }
}
