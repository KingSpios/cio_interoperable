package com.cio.createinteroperable.deb;

import com.cio.createinteroperable.CIOBlockEntities;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.DirectionalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;

/**
 * Tier-4 ("Industrial") Power Kit &mdash; the {@code deb_rectifier_tier_4}
 * model. A full substation: a three-tap intake (120&nbsp;V / 240&nbsp;V /
 * 1&nbsp;kV, Create slider), a high-voltage pass-through, a regulated
 * 120&nbsp;V feed and the 12&nbsp;V feed, five viewer plates and heat-sink
 * plating. Eight terminals; the intake and HV nubs are double-wide
 * (2&times;2&nbsp;px) &mdash; heavier connectors. All the behaviour lives in
 * {@link PowerKitTier4BlockEntity} / {@link PowerKitTier3BlockEntity}.
 */
public class PowerKitTier4Block extends DebRectifierBlock {

    private static final double NUB_EXPAND = 0.02;

    // 8 terminals — order MUST match PowerKitTier3BlockEntity.buildCircuit.
    // Model is internally consistent (higher-X nub = positive within each pair).
    //   0/1 intake +/-     (highv_positive_intake / highv_negative_intake, bottom, 2x2)
    //   2/3 HV Feed +/-    (highv_positive_outlet / highv_negative_outlet, top, 2x2, bleeds y17-18)
    //   4/5 120 V Feed +/- (lv_positive_120v_out / lv_negative_120v_out, top corners z4-5)
    //   6/7 12 V Feed +/-  (lv_positive_12v_out / lv_negative_12v_out, top corners z1-2)
    private static final TerminalBoundingBox[] TIER4_TERMINALS = {
            new TerminalBoundingBox(Component.literal("Intake +"), 9, 0, 2, 11, 1, 4, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.RED),
            new TerminalBoundingBox(Component.literal("Intake −"), 5, 0, 2, 7, 1, 4, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.BLUE),
            new TerminalBoundingBox(Component.literal("HV Feed +"), 9, 17, 2, 11, 18, 4, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.RED),
            new TerminalBoundingBox(Component.literal("HV Feed −"), 5, 17, 2, 7, 18, 4, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.BLUE),
            new TerminalBoundingBox(Component.literal("120V Feed +"), 14, 17, 4, 15, 18, 5, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.RED),
            new TerminalBoundingBox(Component.literal("120V Feed −"), 1, 17, 4, 2, 18, 5, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.BLUE),
            new TerminalBoundingBox(Component.literal("12V Feed +"), 14, 17, 1, 15, 18, 2, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.RED),
            new TerminalBoundingBox(Component.literal("12V Feed −"), 1, 17, 1, 2, 18, 2, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.BLUE),
    };

    /**
     * Outline / collision, NORTH-authored. The {@code top_plate} (y16&ndash;17)
     * and the top feed nubs (y17&ndash;18) bleed above the cell, so the shape
     * clamps the top plate to y15&ndash;16 and gives the nubs no box of their
     * own &mdash; that clamped top edge plus {@code NUB_EXPAND} on the terminal
     * {@code check()} covers clicks near the nubs.
     */
    // The real shape lives in the protocol-neutral PowerKitGeometry — see the
    // comment on DebRectifierBlock.SHAPE for why the CEE-wired twin must never
    // touch a static member of this (PG-rooted) class directly.
    static final VoxelShape TIER4_SHAPE = PowerKitGeometry.TIER4_SHAPE;

    public PowerKitTier4Block(Properties properties) {
        super(properties);
        // Overwrite the tier-2 terminal collection the super constructor set.
        setTerminalCollection(DirectionalElectricBlock.directionalNorthTerminals(this, TIER4_TERMINALS, TIER4_SHAPE, TIER4_SHAPE));
    }

    @Override
    public BlockEntityType<? extends DebRectifierBlockEntity> getBlockEntityType() {
        return CIOBlockEntities.DEB_RECTIFIER_TIER4.get();
    }
}
