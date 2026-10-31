package com.cio.createinteroperable.powerkit.cpg;

import com.cio.createinteroperable.powerkit.PowerKitPaint;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import com.cio.createinteroperable.modules.PowerGridModule;
import com.cio.createinteroperable.powerkit.PowerKitGeometry;
import com.cio.createinteroperable.powerkit.PowerKitLabels;
import com.cio.createinteroperable.powerkit.PowerKitSignalNub;
import org.jetbrains.annotations.Nullable;
import com.cio.createinteroperable.powerkit.PowerKitSwitch;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.DirectionalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.electricity.base.terminals.BlockStateTerminalCollection;

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
 * as the artist built it (back plate at z=0). {@link #setKitTerminals} turns
 * both for every other mount (six facings, plus {@link PowerKitGeometry#TOP}
 * on a floor or ceiling); the blockstate JSON applies the matching model rotation.
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
    //   4/5 = 12 V Power Feed +/-     (lv_positive_12v_out x11-12 / lv_negative_12v_out x9-10)
    // Unlike the other pairs, the 12 V pair's positive is the HIGHER-X nub —
    // check the model's element names, not the neighbouring pair's pattern.
    // The intake label carries no voltage: it follows the tap slider, and PG
    // names a terminal from the blockstate alone (see PowerKitLabels).
    private static final TerminalBoundingBox[] TERMINALS = {
            new TerminalBoundingBox(PowerKitLabels.intake(null, true), 9, 1, 0, 10, 2, 1, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.RED),
            new TerminalBoundingBox(PowerKitLabels.intake(null, false), 6, 1, 0, 7, 2, 1, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.BLUE),
            new TerminalBoundingBox(PowerKitLabels.outlet("120V", true), 6, 15, 1, 7, 16, 2, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.RED),
            new TerminalBoundingBox(PowerKitLabels.outlet("120V", false), 4, 15, 1, 5, 16, 2, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.BLUE),
            new TerminalBoundingBox(PowerKitLabels.outlet("12V", true), 11, 15, 1, 12, 16, 2, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.RED),
            new TerminalBoundingBox(PowerKitLabels.outlet("12V", false), 9, 15, 1, 10, 16, 2, NUB_EXPAND)
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

    /** This tier's outline turned for every mount &mdash; also the collision shape, so entities catch on the model, not a full cube. */
    private PowerKitGeometry.OrientedShape outline;

    public DebRectifierBlock(Properties properties) {
        super(properties);
        setKitTerminals(TERMINALS, SHAPE);
        if (masterSwitch().present()) {
            registerDefaultState(defaultBlockState().setValue(PowerKitSwitch.ENABLED, true));
        }
    }

    /**
     * Install this tier's terminals and outline, both turned by
     * {@link PowerKitGeometry#orient}'s rotation &mdash; the blockstate JSON's own
     * &mdash; so they sit on the model on every mount. PG's
     * {@code directionalNorthTerminals} can't be used: its outline (Create's
     * {@code VoxelShaper}) lands on the far side of the block on a floor or
     * ceiling, and it knows nothing of {@link PowerKitGeometry#TOP}.
     */
    protected final void setKitTerminals(TerminalBoundingBox[] terminals, VoxelShape northShape) {
        outline = new PowerKitGeometry.OrientedShape(northShape);
        setTerminalCollection(BlockStateTerminalCollection.builder(this)
                .forAllStates(state -> BlockStateTerminalCollection.each(terminals, t -> {
                    int x = PowerKitGeometry.modelX(state);
                    return (x != 0 ? t.rotateAroundX(x) : t).rotateAroundY(PowerKitGeometry.modelY(state));
                }))
                .withShapeMapper(outline::get)
                .build());
    }

    /**
     * The master switch this tier's model carries: tier 2's slide switch.
     * Tier subclasses override. Must return a constant &mdash; it is read from
     * {@link #createBlockStateDefinition}, i.e. during the super constructor.
     */
    public PowerKitSwitch.Kind masterSwitch() {
        return PowerKitSwitch.Kind.SLIDE_SWITCH;
    }

    /**
     * Whether a dye repaints this tier's body box ({@link PowerKitPaint}): the
     * Domestic and Commercial kits. Tier subclasses override. Must return a
     * constant &mdash; it is read from {@link #createBlockStateDefinition},
     * i.e. during the super constructor.
     */
    public boolean dyeable() {
        return true;
    }

    /**
     * This tier's signal nub box ({@link PowerKitSignalNub}), NORTH-authored px, or
     * null for none. Tier subclasses override. The nub exists only with the
     * ComputerCraft CEE Addon installed ({@link PowerKitSignalNub#present()}).
     */
    @Nullable
    public double[] signalNubBox() {
        return PowerKitSignalNub.TIER2_BOX;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PowerKitGeometry.TOP);
        if (masterSwitch().present()) {
            builder.add(PowerKitSwitch.ENABLED);
        }
        if (dyeable()) {
            builder.add(PowerKitPaint.COLOR);
        }
    }

    /** A dye repaints a dyeable kit; anything else goes to Power Grid (wires and the like). */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        ItemInteractionResult dyed = PowerKitPaint.tryDye(stack, state, level, pos, player);
        return dyed != null ? dyed : super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    /** A right-click on the master switch flips it; anywhere else falls through to Power Grid. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        InteractionResult result = PowerKitSwitch.tryToggle(masterSwitch(), state, level, pos, hit, null);
        return result != InteractionResult.PASS ? result : super.useWithoutItem(state, level, pos, player, hit);
    }

    /**
     * Collision follows the model's own silhouette (thin wall plate + rail +
     * nub strips), not a full block &mdash; {@code ElectricBlock} only shapes
     * {@code getShape}/{@code getOutlineShape}, leaving collision to vanilla's
     * full-cube default.
     */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return outline.get(state);
    }

    /** Back plate against the clicked surface, not PG's look-direction pick; see {@link PowerKitGeometry#placed}. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return PowerKitGeometry.placed(defaultBlockState(), context);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return PowerKitGeometry.rotate(state, rotation);
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
        return PowerGridModule.DEB_RECTIFIER_BE.get();
    }
}
