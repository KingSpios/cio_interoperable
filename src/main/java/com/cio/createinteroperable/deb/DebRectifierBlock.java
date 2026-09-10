package com.cio.createinteroperable.deb;

import com.cio.createinteroperable.CIOBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.DirectionalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;

import java.util.List;

/**
 * "Power Kit" (tier 2) &mdash; the Power Grid-fed replacement for Crayfish's
 * Electricity Generator. Wall/floor/ceiling mountable (PG's
 * {@link DirectionalElectricBlock}, 6-way {@code FACING}, back plate against
 * the mounting surface). It carries two things at once:
 *
 * <ul>
 *   <li><b>Six PG wire terminals</b> (the model's nubs): one <b>120&nbsp;V
 *       intake</b> pair (bottom-front centre &mdash; the single grid feed),
 *       plus two <b>Power Feed</b> output pairs up top &mdash; a 120&nbsp;V
 *       pass-through and a regulated 12&nbsp;V step-down &mdash; for driving
 *       CPG's own fixtures downstream. Feed draw is metered and counted
 *       against the matching pool's cap.</li>
 *   <li><b>A Crayfish electricity <em>source</em> node</b> (handled by
 *       {@link DebRectifierBlockEntity}, which implements Crayfish's
 *       {@code ISourceNode}): appliances are wrench-linked to it exactly like
 *       they were to the generator.</li>
 * </ul>
 *
 * Terminal boxes and the outline shape are authored here in the NORTH
 * reference orientation, matching {@code models/block/deb_rectifier_tier_2.json}
 * as the artist built it (back plate at z=0). PG's
 * {@code directionalNorthTerminals(...)} rotates both for the other five
 * facings; the blockstate JSON applies the matching model rotation.
 */
public class DebRectifierBlock extends DirectionalElectricBlock implements IBE<DebRectifierBlockEntity> {

    /**
     * A hair of {@code check()} tolerance ({@literal ~}0.02&nbsp;px) beyond
     * each 1&times;1 model nub. Enough to defeat {@code TerminalBoundingBox}'s
     * exclusive max-bound (a dead-on click on a nub's outward face lands
     * exactly on the boundary otherwise) while the rendered highlight still
     * reads as exactly the nub's own size.
     */
    private static final double NUB_EXPAND = 0.02;

    // --- PG wire terminals, NORTH-authored, 1/16 units, one per model nub -
    // Index order MUST match DebRectifierBlockEntity.buildCircuit:
    //   0/1 = 120 V intake +/-        (mv_positive_120v / mv_negative_120v)
    //   2/3 = 120 V Power Feed +/-    (lv_positive_120v_out / lv_negative_120v_out)
    //   4/5 = 12 V Power Feed +/-     (lv_positive_12v_out / lv_negative_12v_out)
    private static final TerminalBoundingBox[] TERMINALS = {
            new TerminalBoundingBox(Component.literal("120V +"), 9, 1, 0, 10, 2, 1, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.RED),
            new TerminalBoundingBox(Component.literal("120V −"), 6, 1, 0, 7, 2, 1, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.BLUE),
            new TerminalBoundingBox(Component.literal("120V Feed +"), 6, 15, 1, 7, 16, 2, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.RED),
            new TerminalBoundingBox(Component.literal("120V Feed −"), 4, 15, 1, 5, 16, 2, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.BLUE),
            new TerminalBoundingBox(Component.literal("12V Feed +"), 9, 15, 1, 10, 16, 2, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.RED),
            new TerminalBoundingBox(Component.literal("12V Feed −"), 11, 15, 1, 12, 16, 2, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.BLUE),
    };

    /**
     * Outline / pick / collision shape, NORTH-authored, from the model's own
     * elements. PG unions every {@link TerminalBoundingBox#getShape()} on top
     * of this, so the nub footprints only need a coarse strip here.
     */
    // The real shape lives in the protocol-neutral PowerKitGeometry, not here —
    // CeeDebRectifierBlock needs this same outline but must never touch a
    // static member of this class, since this class extends PG's
    // DirectionalElectricBlock and doing so would force-resolve PG on a
    // CEE-only install (this was a real crash, not a theoretical one).
    static final VoxelShape SHAPE = PowerKitGeometry.TIER2_SHAPE;

    /** {@link #SHAPE} rotated for all six facings &mdash; used for collision so entities catch on the model, not a full cube. */
    private static final VoxelShaper SHAPE_SHAPER =
            VoxelShaper.forDirectional(SHAPE, Direction.NORTH).withVerticalShapes(SHAPE);

    public DebRectifierBlock(Properties properties) {
        super(properties);
        setTerminalCollection(DirectionalElectricBlock.directionalNorthTerminals(this, TERMINALS, SHAPE, SHAPE));
    }

    /**
     * Collision follows the model's own silhouette (thin wall plate + rail +
     * nub strips), not a full block &mdash; {@code ElectricBlock} only shapes
     * {@code getShape}/{@code getOutlineShape}, leaving collision to vanilla's
     * full-cube default.
     */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE_SHAPER.get(state.getValue(FACING));
    }

    /**
     * The board's orientation is fixed by the surface it was placed against,
     * so the wrench must not rotate it in place &mdash; {@code ElectricBlock}'s
     * default {@code onWrenched} would cycle {@code FACING} through all six
     * directions and detach it from its mount. Sneak-wrench (pick the block
     * up) is left alone.
     */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }

    /**
     * Second tooltip line, shared by every tier (tier 1 and the future
     * tiers 3&ndash;4 all extend this block). Plain {@code BlockItem}'s
     * {@code appendHoverText} forwards straight here, so this reaches the item
     * tooltip with no custom Item subclass &mdash; same technique as
     * {@code TelephoneBlock}.
     */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("block.createinteroperable.power_kit.serves")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    @Override
    public Class<DebRectifierBlockEntity> getBlockEntityClass() {
        return DebRectifierBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends DebRectifierBlockEntity> getBlockEntityType() {
        return CIOBlockEntities.DEB_RECTIFIER.get();
    }
}
