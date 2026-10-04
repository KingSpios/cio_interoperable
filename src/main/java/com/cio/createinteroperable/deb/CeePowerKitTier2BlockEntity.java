package com.cio.createinteroperable.deb;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * CEE-only twin of {@link PowerKitTier2BlockEntity}: the tier-2 ("Domestic")
 * kit with the 120&nbsp;V / 240&nbsp;V intake slider, six terminals (no HV feed)
 * and the flat viewer bank. Everything else is inherited from
 * {@link CeePowerKitTier3BlockEntity}.
 */
public class CeePowerKitTier2BlockEntity extends CeePowerKitTier3BlockEntity {

    public CeePowerKitTier2BlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected DebTier tier() {
        return DebTier.TIER_2;
    }

    @Override
    public boolean usesTier3Viewers() {
        return false;
    }

    @Override
    protected boolean hasHvFeed() {
        return false;
    }

    /** Transformer balance — see {@link PowerKitTier2BlockEntity#softCap}: the 240&nbsp;V tap's cap rates the intake, so the 120&nbsp;V pool's caps shrink by the step-down efficiency. */
    @Override
    protected double softCap(Pool pool) {
        double cap = super.softCap(pool);
        return pool == Pool.MV ? cap * mvFeedEfficiency() : cap;
    }

    @Override
    protected double hardCap(Pool pool) {
        double cap = super.hardCap(pool);
        return pool == Pool.MV ? cap * mvFeedEfficiency() : cap;
    }

    @Override
    protected Vec3 sliderSlotBase() {
        return VecHelper.voxelSpace(8, 2.5, 4.6);
    }
}
