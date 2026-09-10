package com.cio.createinteroperable.deb;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tier-1 ("Improvised") Power Kit BlockEntity — 12&nbsp;V rail only (no
 * 120&nbsp;V), and a physical needle gauge instead of the flat text viewers.
 * All behaviour lives in {@link DebRectifierBlockEntity}; this only flips the
 * two tier hooks.
 */
public class PowerKitTier1BlockEntity extends DebRectifierBlockEntity {

    public PowerKitTier1BlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
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
