package com.cio.createinteroperable.deb;

import com.cio.createinteroperable.CIOBlockEntities;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.DirectionalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;

/**
 * Tier-3 ("Commercial") Power Kit &mdash; the {@code deb_rectifier_tier_3}
 * model: a <b>240&nbsp;V intake</b> (Create slider drops it to 120&nbsp;V mode),
 * a high-voltage pass-through feed, a regulated 120&nbsp;V feed, the 12&nbsp;V
 * feed, five viewer plates (HV / temperature / 120&nbsp;V / 12&nbsp;V / usage)
 * and side heat-sink fins. Eight terminals, re-authored here from the model's
 * own element coordinates; the electrical model, the mode switch and the
 * thermal simulation live in {@link PowerKitTier3BlockEntity}.
 */
public class PowerKitTier3Block extends DebRectifierBlock {

    private static final double NUB_EXPAND = 0.02;

    // 8 terminals — order MUST match PowerKitTier3BlockEntity.buildCircuit.
    // The model is internally consistent: within every +/- pair, the higher-X
    // nub is positive (confirmed against every pair's own element name, not
    // assumed) — verify against the model file's element names, not just
    // position, if the model moves again.
    //   0/1 intake +/-       (mv_positive_120v / mv_negative_120v, bottom-front)
    //   2/3 HV Feed +/-      (lv_positive_240v_out / lv_negative_240v_out, top z1-2, x4-7)
    //   4/5 120 V Feed +/-   (lv_positive_120v_out / lv_negative_120v_out, top z4-5, x9-12)
    //   6/7 12 V Feed +/-    (lv_positive_12v_out / lv_negative_12v_out, top z1-2, x9-12)
    private static final TerminalBoundingBox[] TIER3_TERMINALS = {
            new TerminalBoundingBox(Component.literal("Intake +"), 9, 1, 2, 10, 2, 3, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.RED),
            new TerminalBoundingBox(Component.literal("Intake −"), 6, 1, 2, 7, 2, 3, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.BLUE),
            new TerminalBoundingBox(Component.literal("HV Feed +"), 6, 16, 1, 7, 17, 2, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.RED),
            new TerminalBoundingBox(Component.literal("HV Feed −"), 4, 16, 1, 5, 17, 2, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.BLUE),
            new TerminalBoundingBox(Component.literal("120V Feed +"), 11, 16, 4, 12, 17, 5, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.RED),
            new TerminalBoundingBox(Component.literal("120V Feed −"), 9, 16, 4, 10, 17, 5, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.BLUE),
            new TerminalBoundingBox(Component.literal("12V Feed +"), 11, 16, 1, 12, 17, 2, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.RED),
            new TerminalBoundingBox(Component.literal("12V Feed −"), 9, 16, 1, 10, 17, 2, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.BLUE),
    };

    /**
     * Outline / collision, NORTH-authored, from the tier-3 model's elements.
     * The top feed nubs (y16&ndash;17) bleed 1&nbsp;px above the cell, so they
     * get no box of their own &mdash; {@code top_rail}/{@code top_rail_forward}
     * reach y16 and give the raytrace a surface at the nub bases, which
     * {@code NUB_EXPAND} on the terminal {@code check()} covers.
     */
    // The real shape lives in the protocol-neutral PowerKitGeometry — see the
    // comment on DebRectifierBlock.SHAPE for why the CEE-wired twin must never
    // touch a static member of this (PG-rooted) class directly.
    static final VoxelShape TIER3_SHAPE = PowerKitGeometry.TIER3_SHAPE;

    public PowerKitTier3Block(Properties properties) {
        super(properties);
        // Overwrite the tier-2 terminal collection the super constructor set.
        setTerminalCollection(DirectionalElectricBlock.directionalNorthTerminals(this, TIER3_TERMINALS, TIER3_SHAPE, TIER3_SHAPE));
    }

    @Override
    public BlockEntityType<? extends DebRectifierBlockEntity> getBlockEntityType() {
        return CIOBlockEntities.DEB_RECTIFIER_TIER3.get();
    }

    // The slider-slot rotation helpers (angleFor/rotateY) moved to the
    // protocol-neutral PowerKitGeometry — both this class's own BlockEntity
    // and the CEE-wired twins call PowerKitGeometry directly now.
}
