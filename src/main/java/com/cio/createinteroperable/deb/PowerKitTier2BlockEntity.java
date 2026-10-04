package com.cio.createinteroperable.deb;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Tier-2 ("Domestic") BlockEntity. It reuses the substation machinery of
 * {@link PowerKitTier3BlockEntity} for its two-tap intake slider (120&nbsp;V
 * default / 240&nbsp;V, from {@link DebTier#TIER_2}'s substation), but keeps the
 * tier-2 model: six terminals (no HV feed pair) and the flat three-readout
 * viewer bank rather than the tier-3 plate cross.
 *
 * <p>The 120&nbsp;V Power Feed is therefore now a regulated step-down (a bare
 * 1:1 strap at the 120&nbsp;V tap) rather than the old raw pass-through, so it
 * can stay at 120&nbsp;V when the intake is on the 240&nbsp;V tap.</p>
 */
public class PowerKitTier2BlockEntity extends PowerKitTier3BlockEntity {

    public PowerKitTier2BlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
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

    /**
     * Transformer balance. Tier 2 has no HV pass-through, so on the 240&nbsp;V
     * tap everything on the 120&nbsp;V pool (the Power Feed outlets AND linked
     * appliances) sits behind the real 240&rarr;120 step-down, and the tap's
     * cap is a rating of the <em>intake</em> (~40&nbsp;A). The pool is metered
     * on the secondary, so scale its caps by the step-down efficiency: the
     * intake then hits exactly its rated wattage at the soft cap instead of
     * ~11&nbsp;% over it (10.7&nbsp;kW / 44&nbsp;A). At the 120&nbsp;V tap the
     * efficiency is 1.0 (bare strap), so nothing changes there.
     */
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

    /** Tier-2 model: body front face at z=4 (viewers stand to z=4.5); the free strip under the viewers is y 2&ndash;4. */
    @Override
    protected Vec3 sliderSlotBase() {
        return VecHelper.voxelSpace(8, 2.5, 4.6);
    }
}
