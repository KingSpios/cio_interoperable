package com.cio.createinteroperable.deb;

import com.cio.createinteroperable.CIOBlockEntities;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.DirectionalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;

/**
 * Tier-1 ("Improvised") Power Kit &mdash; the same block as
 * {@link DebRectifierBlock} but with the {@code deb_rectifier_tier_1} model: a
 * <b>120&nbsp;V intake</b> pair (bottom), a regulated <b>12&nbsp;V Power
 * Feed</b> pair (top), no 120&nbsp;V pool and no 120&nbsp;V Power Feed, and a
 * physical needle gauge instead of the flat text viewers. Terminal geometry
 * and outline are re-authored here from the tier-1 model's own element
 * coordinates; everything else is inherited.
 */
public class PowerKitTier1Block extends DebRectifierBlock {

    private static final double NUB_EXPAND = 0.02;

    // 4 terminals: [120V in +, 120V in -, 12V Feed +, 12V Feed -] — matches
    // DebRectifierBlockEntity.buildCircuit's tier-1 (no-MV) layout.
    // Coordinates track deb_rectifier_tier_1.json (whole model raised +1 Y so
    // nothing bleeds below the cell); only the labels/roles changed in the
    // single-intake rewire — the bottom pair is now the 120 V grid feed.
    private static final TerminalBoundingBox[] TIER1_TERMINALS = {
            new TerminalBoundingBox(Component.literal("120V +"), 9, 0, 2, 10, 1, 3, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.RED),
            new TerminalBoundingBox(Component.literal("120V −"), 6, 0, 2, 7, 1, 3, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.BLUE),
            new TerminalBoundingBox(Component.literal("12V Feed +"), 9, 13, 2, 10, 14, 3, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.RED),
            new TerminalBoundingBox(Component.literal("12V Feed −"), 6, 13, 2, 7, 14, 3, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.BLUE),
    };

    // The real shape lives in the protocol-neutral PowerKitGeometry — see the
    // comment on DebRectifierBlock.SHAPE for why the CEE-wired twin must never
    // touch a static member of this (PG-rooted) class directly.
    static final VoxelShape TIER1_SHAPE = PowerKitGeometry.TIER1_SHAPE;

    public PowerKitTier1Block(Properties properties) {
        super(properties);
        // Overwrite the tier-2 terminal collection the super constructor set.
        setTerminalCollection(DirectionalElectricBlock.directionalNorthTerminals(this, TIER1_TERMINALS, TIER1_SHAPE, TIER1_SHAPE));
    }

    @Override
    public BlockEntityType<? extends DebRectifierBlockEntity> getBlockEntityType() {
        return CIOBlockEntities.DEB_RECTIFIER_TIER1.get();
    }
}
