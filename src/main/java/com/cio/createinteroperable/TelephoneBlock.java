package com.cio.createinteroperable;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.LevelTickAccess;
import org.patryk3211.powergrid.electricity.base.ElectricBlock;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.electricity.base.terminals.BlockStateTerminalCollection;
import org.patryk3211.powergrid.electricity.info.ElectricPropertiesUtils;
import org.patryk3211.powergrid.electricity.info.IHaveElectricProperties;
import org.patryk3211.powergrid.electricity.info.Resistance;
import org.patryk3211.powergrid.electricity.info.Voltage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A wall-mounted, single 1x1 Power Grid device. Terminal/interaction
 * placement is derived directly from the real named cubes in
 * models/block/telephone.json:
 * <ul>
 *     <li>TAP/LISTENER -&gt; two small diamond nubs both literally named
 *     {@code pulse_sender_tap} in the model (a Blockbench rename that missed
 *     one of them) — disambiguated here by Y position (top=TAP, bottom=
 *     LISTENER), not by name.</li>
 *     <li>{@code power_positive}/{@code power_negative} -&gt; POSITIVE/NEGATIVE terminals</li>
 *     <li>{@code outlet_positive}/{@code outlet_negative} -&gt; a breaker pair
 *     onto the same positive/negative rail, closed only on the receiving end
 *     of an answered, ongoing call (see TelephoneBlockEntity#buildCircuit /
 *     #updateCallBreakers)</li>
 *     <li>{@code auto_lever} -&gt; Auto-Answer toggle (a ScrollValueBehaviour, see
 *     TelephoneBlockEntity#addBehaviours)</li>
 *     <li>{@code phone_part_handle}/{@code phone_part} (the handset assembly)
 *     -&gt; send-pulse/hang-up/answer, handled here directly in
 *     {@link #useWithoutItem}. {@code hook} is decorative only (a static
 *     cradle support) and gets no hitbox.</li>
 *     <li>{@code dial_ring} -&gt; opens the dial-out screen. Protrudes forward
 *     of body_box's own front face, so it needs its own MODEL_BOXES entry
 *     for the outline shape to even reach it.</li>
 * </ul>
 * The remaining 2 interactions (own Area Code/Number, label) have no
 * dedicated model geometry and attach to whole-region/whole-face checks
 * instead: the label screen on the top face (a plain hitFace check), and the
 * own Area Code/Number screen on back_plate specifically (its own hitbox) —
 * opened via TelephoneNumberScreen, a pair of Create ScrollInput widgets,
 * since in-world ScrollValueBehaviours placed on body_box's faces kept
 * landing inside overlapping model geometry (phone_part/hook) and were
 * unreliable to click.
 * <p>
 * FACING follows the same convention as BrassHeaterBlock/SteamOutletBlock's
 * blockstate ("north"=0, "east"=90, "south"=180, "west"=270 model rotation) —
 * it's the direction the phone's front (dial_ring) points, matching a
 * furnace-style FACING, not the direction it's viewed from.
 */
public class TelephoneBlock extends ElectricBlock
        implements IBE<TelephoneBlockEntity>, IHaveElectricProperties, ElectricalDeviceBlock<TelephoneDevice> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /**
     * getShape() previously returned a flat Shapes.block() (a full 16x16x16
     * cube) — since the actual model only occupies a fraction of that space,
     * vanilla's raytrace was resolving every click against the outer cube's
     * own surface and never reaching the real geometry at all, making the
     * small power_positive/power_negative nubs (and everything else)
     * unreachable. Same root cause InteroperableSmallBlock already hit once
     * (see that class's own extensive doc on the subject) — the fix here is
     * simpler than that one needed, since none of telephone.json's elements
     * are a nub barely poking out of a much larger competing surface (the
     * closest case, power_positive/negative, sit fully separated above
     * body_box's own top edge with nothing else occupying that space, so a
     * plain union of the model's real boxes is enough — no carve-and-pad
     * needed).
     */
    private static AABB box16(double x1, double y1, double z1, double x2, double y2, double z2) {
        return new AABB(x1 / 16, y1 / 16, z1 / 16, x2 / 16, y2 / 16, z2 / 16);
    }

    /**
     * One box per element in telephone.json. TAP and LISTENER used to be
     * part of one continuous flush-panel strip with back_plate; the model
     * has since split them into separate small diamond-style nubs (matching
     * power_positive/negative's own look) at z12.6-13.6, no longer touching
     * back_plate's own z13.9-15.9 range at all — both json elements are
     * still literally named "pulse_sender_tap" (a Blockbench rename that
     * missed the bottom one), disambiguated here purely by Y position
     * (top=TAP, bottom=LISTENER), matching this block's own code-side
     * terminal assignment rather than the model's own names.
     */
    private static final AABB[] MODEL_BOXES = new AABB[] {
            box16(7, 2, 13.9, 9, 14, 15.9),        // back_plate
            box16(5, 2, 10, 11, 14, 14),            // body_box
            box16(5.5, 13.9, 12.5, 6.5, 14.9, 13.5), // power_positive
            box16(9.5, 13.9, 12.6, 10.5, 14.9, 13.6), // power_negative
            box16(7.5, 13.9, 12.6, 8.5, 14.9, 13.6), // pulse_sender_tap (TAP, top)
            box16(7.5, 0.9, 12.6, 8.5, 1.9, 13.6),   // pulse_sender_tap (LISTENER, bottom)
            box16(3, 7, 11, 5, 12, 13),     // phone_part_handle + phone_part (handset)
            box16(11, 9, 12, 12, 12, 13),   // auto_lever
            box16(4, 10, 11, 5, 11, 13),    // hook
            box16(6, 1, 12, 7, 2, 13),      // outlet_positive
            box16(9, 1, 12, 10, 2, 13),     // outlet_negative
            box16(6, 3, 15, 10, 4, 16),      // unnamed decorative frame piece
            box16(9, 3, 14, 10, 4, 15),      // unnamed decorative frame piece
            box16(6, 3, 14, 7, 4, 15),       // unnamed decorative frame piece
            box16(6, 11, 14, 7, 12, 15),     // unnamed decorative frame piece
            box16(9, 11, 14, 10, 12, 15),    // unnamed decorative frame piece
            box16(6, 11, 15, 10, 12, 16),    // unnamed decorative frame piece
            box16(6.5, 3, 9, 9.5, 6, 10),    // dial_ring — protrudes forward of body_box's own front face
    };

    private static AABB rotateAABB(AABB box, int angle) {
        Vec3 p1 = rotateY(new Vec3(box.minX, 0, box.minZ), angle);
        Vec3 p2 = rotateY(new Vec3(box.maxX, 0, box.maxZ), angle);
        return new AABB(Math.min(p1.x, p2.x), box.minY, Math.min(p1.z, p2.z),
                Math.max(p1.x, p2.x), box.maxY, Math.max(p1.z, p2.z));
    }

    private static VoxelShape buildShape(int angle) {
        VoxelShape shape = Shapes.empty();
        for (AABB box : MODEL_BOXES) {
            shape = Shapes.or(shape, Shapes.create(rotateAABB(box, angle)));
        }
        return shape;
    }

    /** Index i -> angle i*90. rotateY (below) must be defined before this runs. */
    private static final VoxelShape[] ROTATED_SHAPES =
            { buildShape(0), buildShape(90), buildShape(180), buildShape(270) };

    private static VoxelShape shapeFor(BlockState state) {
        return ROTATED_SHAPES[angleFor(state) / 90];
    }

    /** Combined bounding region of the 3 phone_part cubes (the handset). */
    private static final TerminalBoundingBox HANDSET_HITBOX =
            new TerminalBoundingBox(IDecoratedTerminal.CONNECTOR, 3, 7, 11, 5, 12, 13);

    /**
     * A hair of {@code check()} tolerance beyond each 1&times;1 model nub —
     * matches {@code DebRectifierBlock}'s own {@code NUB_EXPAND}. These boxes
     * used to expand by a full {@code 1} (one whole pixel each side, tripling
     * every nub to 3&times;3&times;3) on the mistaken belief that this matched
     * {@code InteroperableSmallBlock}'s {@code CHECK_MARGIN} precedent — that
     * block's nubs are a different, larger, cell-bleeding shape with the
     * margin baked into raw coordinates, not this constructor's own
     * {@code expand} param, so the precedent didn't actually transfer. The
     * real effect for these small nubs was POSITIVE/TAP/NEGATIVE's expanded
     * boxes overlapping each other — a real, confirmed cause of wires and
     * clicks landing on the wrong terminal.
     */
    private static final double NUB_EXPAND = 0.02;

    /**
     * back_plate's own current footprint — right-clicking this opens the
     * Area Code/Number screen. No longer needs to exclude TAP/LISTENER (they
     * moved off the flush panel to their own diamond nubs at z12.6-13.6,
     * spatially disjoint from back_plate's z13.9-15.9 now).
     */
    private static final TerminalBoundingBox BACK_PLATE_HITBOX =
            new TerminalBoundingBox(IDecoratedTerminal.CONNECTOR, 7, 2, 13.9, 9, 14, 15.9, NUB_EXPAND);

    private static final TerminalBoundingBox POSITIVE_BASE =
            new TerminalBoundingBox(IDecoratedTerminal.POSITIVE, 5.5, 13.9, 12.5, 6.5, 14.9, 13.5, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.RED);
    private static final TerminalBoundingBox NEGATIVE_BASE =
            new TerminalBoundingBox(IDecoratedTerminal.NEGATIVE, 9.5, 13.9, 12.6, 10.5, 14.9, 13.6, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.BLUE);
    /** The top "pulse_sender_tap" nub (both json elements share that name now — see MODEL_BOXES comment). */
    private static final TerminalBoundingBox TAP_BASE =
            new TerminalBoundingBox(IDecoratedTerminal.TAP, 7.5, 13.9, 12.6, 8.5, 14.9, 13.6, NUB_EXPAND);
    /**
     * The bottom "pulse_sender_tap" nub — this is the LISTENER terminal. No
     * longer a voltage source — a breaker that only completes an externally-
     * wired circuit while the receiving end of a call is answered (see
     * TelephoneBlockEntity#updateCallBreakers).
     */
    private static final TerminalBoundingBox LISTENER_BASE =
            new TerminalBoundingBox(Component.literal("Call Breaker"), 7.5, 0.9, 12.6, 8.5, 1.9, 13.6, NUB_EXPAND);

    /**
     * The model's bottom {@code outlet_positive}/{@code outlet_negative} nubs
     * — real terminals on the same electrical rail as {@link #POSITIVE_BASE}/
     * {@link #NEGATIVE_BASE} (see TelephoneBlockEntity#buildCircuit's breaker
     * connection), live only while the receiving end of a call is answered
     * and ongoing — not merely while the phone is powered. Never nulled out
     * by WIRE_LOCK (same reasoning as TAP_BASE/LISTENER_BASE) — they sit at
     * their own bottom spot, entirely separate from the shared positive/
     * negative nubs WIRE_LOCK actually arbitrates.
     */
    private static final TerminalBoundingBox OUTLET_POSITIVE_BASE =
            new TerminalBoundingBox(Component.literal("Call Feed +"), 6, 1, 12, 7, 2, 13, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.RED);
    private static final TerminalBoundingBox OUTLET_NEGATIVE_BASE =
            new TerminalBoundingBox(Component.literal("Call Feed −"), 9, 1, 12, 10, 2, 13, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.BLUE);

    /**
     * CEE node ids 0/1 share the exact same physical nubs as PG terminal
     * indices 0/1 (positive/negative) — center points of POSITIVE_BASE/
     * NEGATIVE_BASE above, normalized to 0-1 voxel space (CEE gives no
     * TerminalBoundingBox-style helper, just plain Vec3s — same pattern
     * InteroperableSmallBlock's own CEE_NODE_0/1_BASE already uses). Only
     * one of the two protocols can actually use them at a time — see
     * WIRE_LOCK.
     */
    private static final Vec3 CEE_POSITIVE_BASE = new Vec3(6.0 / 16, 14.4 / 16, 13.0 / 16);
    private static final Vec3 CEE_NEGATIVE_BASE = new Vec3(10.0 / 16, 14.4 / 16, 13.1 / 16);
    /**
     * Node id 2 — the pulse/tap line, at the model's own "pulse_sender_tap_above"
     * cube's center (x7.5-8.5, y13.9-14.9, z12.6-13.6, matching TAP_BASE's own
     * coordinates exactly). Sitting right between CEE_POSITIVE_BASE (x6) and
     * CEE_NEGATIVE_BASE (x10), those two nodes' own proximity/search radius in
     * CEE's own closest-node resolution swallowed this spot when it wasn't its
     * own distinct node — real report: "the tap node for CEE gets hidden by
     * the two positive/negative nodes' bounding boxes." Unlike positive/
     * negative, this is NOT gated by WIRE_LOCK (see #isNodeAccessible) — the
     * pulse line isn't a power concept, same reason PG's own tap terminal
     * (index 2) is never nulled out by terminalsFor regardless of WIRE_LOCK.
     */
    private static final Vec3 CEE_TAP_BASE = new Vec3(8.0 / 16, 14.4 / 16, 13.1 / 16);

    /** dial_ring — right-click opens the dial-out screen (see TelephoneBlockEntity#onFrontUsed / #onDialRingUsed). */
    private static final TerminalBoundingBox DIAL_RING_HITBOX =
            new TerminalBoundingBox(IDecoratedTerminal.CONNECTOR, 6.5, 3, 9, 9.5, 6, 10, NUB_EXPAND);

    /**
     * Real texture swap, not a tint: each of these 14 DyeColor values has a
     * matching hand-painted texture, one small "parent": block/telephone,
     * overridden-textures model file per color (telephone_<color>.json — see
     * blockstates/telephone.json). Only LIGHT_BLUE and LIGHT_GRAY have no
     * matching texture yet and are deliberately left out of this property's
     * allowed values — TelephoneBlock#useItemOn checks
     * getPossibleValues().contains(...) before ever touching the block, so
     * dyeing with one of those is a silent no-op, exactly as requested ("if
     * the texture matching the dye does not exist, then nothing happens").
     */
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class,
            DyeColor.BLUE, DyeColor.WHITE, DyeColor.GRAY, DyeColor.RED, DyeColor.CYAN, DyeColor.GREEN,
            DyeColor.YELLOW, DyeColor.BLACK, DyeColor.PINK, DyeColor.PURPLE, DyeColor.ORANGE,
            DyeColor.BROWN, DyeColor.LIME, DyeColor.MAGENTA);

    /**
     * Which protocol currently owns the positive/negative nubs — at most one
     * at a time. TelephoneBlockEntity#updateWireLock polls both sides' real
     * connection state every electrical tick and writes the result here;
     * this state, not any block-entity data, is what actually gates new
     * connections (PG has no live "reject a connection attempt" hook — only
     * a null TerminalBoundingBox, which is baked per-BlockState — see
     * #terminalsFor; CEE's own #isNodeAccessible below is a real, instant
     * reject hook). NOT listed in blockstates/telephone.json — omitted
     * properties already match any value there (same trick already proven
     * for POWER), so this needs zero model-file changes.
     */
    public enum WireLock implements StringRepresentable {
        NONE, PG, CEE;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }
    public static final EnumProperty<WireLock> WIRE_LOCK = EnumProperty.create("wire_lock", WireLock.class);

    public TelephoneBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH)
                .setValue(COLOR, DyeColor.BLUE).setValue(WIRE_LOCK, WireLock.NONE)
                .setValue(CIOProperties.CALL_ACTIVE, false));
        setTerminalCollection(BlockStateTerminalCollection.builder(this)
                .forAllStates(TelephoneBlock::terminalsFor)
                .build());
    }

    public Direction getFacing(BlockState state) {
        return state.getValue(FACING);
    }

    /** North=0, East=90, South=180, West=270 — same convention as the blockstate's own "y" rotation. */
    static int angleFor(BlockState state) {
        return switch (state.getValue(FACING)) {
            case NORTH -> 0;
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }

    /**
     * Rotates a plain offset vector (0-1 normalized voxel space) about the
     * block's own horizontal center, same clockwise-from-above convention as
     * the blockstate's own "y" rotation — for the 5 ScrollValueBehaviour
     * slots, which aren't TerminalBoundingBoxes and so don't get
     * rotateAroundY(angle) for free (mirrors InteroperableSmallBlock's own
     * identically-purposed rotateY helper).
     */
    static Vec3 rotateY(Vec3 base, int angle) {
        double dx = base.x - 0.5, dz = base.z - 0.5;
        return switch (angle) {
            case 90 -> new Vec3(0.5 - dz, base.y, 0.5 + dx);
            case 180 -> new Vec3(0.5 - dx, base.y, 0.5 - dz);
            case 270 -> new Vec3(0.5 + dz, base.y, 0.5 - dx);
            default -> base;
        };
    }

    /**
     * A null entry here is a real, first-class "no terminal at this index"
     * to BlockStateTerminalCollection (confirmed via its own source: get()
     * returns null straight through, each()/the shape mapper both already
     * special-case it) — PG's own terminalIndexAt/onWire then treat that
     * index as simply not present, no error, no message, same as clicking
     * empty space. That's the ONLY lever PG exposes to reject a connection
     * (no live "canConnect" hook exists), so positive/negative null out
     * here whenever CEE currently holds WIRE_LOCK.
     */
    private static TerminalBoundingBox[] terminalsFor(BlockState state) {
        int angle = angleFor(state);
        boolean ceeOwnsPower = state.getValue(WIRE_LOCK) == WireLock.CEE;
        return new TerminalBoundingBox[] {
                ceeOwnsPower ? null : POSITIVE_BASE.rotateAroundY(angle),
                ceeOwnsPower ? null : NEGATIVE_BASE.rotateAroundY(angle),
                TAP_BASE.rotateAroundY(angle),
                LISTENER_BASE.rotateAroundY(angle),
                OUTLET_POSITIVE_BASE.rotateAroundY(angle),
                OUTLET_NEGATIVE_BASE.rotateAroundY(angle)
        };
    }

    static TerminalBoundingBox handsetHitboxFor(BlockState state) {
        return HANDSET_HITBOX.rotateAroundY(angleFor(state));
    }

    static TerminalBoundingBox backPlateHitboxFor(BlockState state) {
        return BACK_PLATE_HITBOX.rotateAroundY(angleFor(state));
    }

    static TerminalBoundingBox dialRingHitboxFor(BlockState state) {
        return DIAL_RING_HITBOX.rotateAroundY(angleFor(state));
    }

    /**
     * Real, confirmed root cause of "CEE wiring doesn't work here, only PG
     * does": BACK_PLATE_HITBOX's own expand=1 margin (needed for ITS OWN
     * exclusive-max-bound click reliability, see its doc) pushes its
     * effective z-range down to ~12.9, overlapping the positive/negative/tap
     * nubs (z12.5-13.6) enough that a click meant for a terminal instead
     * lands inside back_plate's box too. useWithoutItem below checks
     * back_plate BEFORE any item-level wire logic ever runs, and returns
     * SUCCESS unconditionally there (regardless of held item) — silently
     * consuming the click before it can reach a wire item's own useOn().
     * <p>
     * This only actually breaks CEE, not PG, because of an interaction-
     * dispatch asymmetry: PG's WireItem resolves connections via
     * {@code InteractionEvent.RIGHT_CLICK_BLOCK} (PowerGrid.java), an
     * EARLY global event that fires before vanilla's block-level
     * useItemOn/useWithoutItem dispatch even starts — confirmed via
     * PowerGrid.java's own event registration. CEE's WireSpoolItem instead
     * overrides the ordinary, LATE-stage {@code Item#useOn(UseOnContext)}
     * (confirmed via javap) — which vanilla only ever reaches if BOTH
     * useItemOn and useWithoutItem passed without consuming the click. So
     * PG's wire clicks never touch this code path at all, while CEE's
     * always do.
     * <p>
     * Fix: exclude the terminal nubs' own (already expand=1) click regions
     * from back_plate's — a click that would resolve to a real terminal
     * should never fall to back_plate instead, regardless of protocol.
     */
    private static boolean onPowerOrTapTerminal(BlockState state, Vec3 local) {
        int angle = angleFor(state);
        return POSITIVE_BASE.rotateAroundY(angle).check(local)
                || NEGATIVE_BASE.rotateAroundY(angle).check(local)
                || TAP_BASE.rotateAroundY(angle).check(local)
                || OUTLET_POSITIVE_BASE.rotateAroundY(angle).check(local)
                || OUTLET_NEGATIVE_BASE.rotateAroundY(angle).check(local);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, COLOR, WIRE_LOCK, CIOProperties.CALL_ACTIVE);
    }

    // --- Redstone output while on an answered call (see TelephoneRedstone) ---

    @Override
    protected boolean isSignalSource(BlockState state) {
        return TelephoneRedstone.isActive(state);
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return TelephoneRedstone.weakSignal(state);
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return TelephoneRedstone.directSignal(state, direction, FACING);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        TelephoneRedstone.onRemoved(level, pos, state, newState, movedByPiston, FACING);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirrorIn) {
        return state.rotate(mirrorIn.getRotation(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof TelephoneBlockEntity be)) {
            return InteractionResult.PASS;
        }

        Vec3 local = hitResult.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        if (handsetHitboxFor(state).check(local)) {
            // Same call site pattern as Create's own DeskBellBlock#useWithoutItem:
            // .play(...) (not .playAt, which calls Level#playLocalSound — a
            // client-only no-op when invoked from server code, which is why
            // routing this through TelephoneBlockEntity's server-only
            // hangUp()/answer() never actually played anything) runs
            // identically on both sides, so both the clicking player's own
            // client and the server (broadcasting to everyone else) fire it.
            AllSoundEvents.DESK_BELL_USE.play(level, player, pos);
            // Self-dial / busy-dial: about to attempt dial() while idle.
            // dial()'s own checks (target==this, target.busy via the
            // filtered findByNumber) already no-op the actual call
            // harmlessly either way, so this is purely the player feedback —
            // same dual-side .play() pattern as the bell above, plus a
            // client-only particle burst (no sync needed, this branch
            // already runs identically on both sides). Busy additionally
            // gets an actionbar message — sound+smoke alone doesn't say
            // WHY the call didn't go through, unlike self-dial where that's
            // usually obvious to the player already.
            if (!be.isBusy()) {
                if (be.isDialingOwnNumber()) {
                    AllSoundEvents.DENY.play(level, player, pos);
                    if (level.isClientSide) {
                        spawnDenySmoke(level, pos);
                    }
                } else if (be.isDialingBusyNumber()) {
                    AllSoundEvents.DENY.play(level, player, pos);
                    if (level.isClientSide) {
                        spawnDenySmoke(level, pos);
                    } else {
                        player.displayClientMessage(
                                Component.literal("That number is busy.").withStyle(ChatFormatting.RED), true);
                    }
                }
            }
            return be.onHandsetUsed(player);
        }
        if (backPlateHitboxFor(state).check(local) && !onPowerOrTapTerminal(state, local)) {
            return be.onBackPlateUsed(player);
        }
        // dial_ring replaces the old "click anywhere on the front face"
        // trigger — the front face otherwise no longer does anything on its
        // own, only the dial itself opens the dial-out screen now.
        if (dialRingHitboxFor(state).check(local)) {
            return be.onFrontUsed(player);
        }

        Direction hitFace = hitResult.getDirection();
        if (hitFace == Direction.UP) {
            return be.onTopUsed(player);
        }
        return InteractionResult.PASS;
    }

    /** Slow, sparse white smoke — the visual half of the self-dial deny feedback. */
    private static void spawnDenySmoke(Level level, BlockPos pos) {
        for (int i = 0; i < 6; i++) {
            double x = pos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.4;
            double y = pos.getY() + 0.4 + level.random.nextDouble() * 0.3;
            double z = pos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.4;
            level.addParticle(ParticleTypes.WHITE_SMOKE, x, y, z, 0, 0.01, 0);
        }
    }

    /**
     * Same visual as spawnDenySmoke, but for deny feedback with no direct
     * client-dispatched interaction to piggyback on (a rejected in-world
     * Area Code scroll, or a rejected Number-screen submission) — those run
     * from server-only contexts (a behaviour callback, a packet handler),
     * so level.addParticle (a client-only no-op server-side, same class of
     * bug already hit once for sounds in this project) won't do anything.
     * ServerLevel#sendParticles is the real broadcast-to-nearby-clients
     * equivalent.
     */
    static void spawnDenySmokeServer(ServerLevel level, BlockPos pos) {
        level.sendParticles(ParticleTypes.WHITE_SMOKE,
                pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5,
                6, 0.2, 0.15, 0.2, 0.01);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (hand != InteractionHand.MAIN_HAND) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // Dyeing — any right-click position on the block, matching
        // LightFixtureBlock's own dye branch (no hitbox restriction there
        // either). A real blockstate change (COLOR), not a tint: swaps to a
        // different model/texture entirely, and gets vanilla's own normal
        // client sync/re-render for free (no render-refresh hack needed,
        // unlike the earlier tint-based approach). Dyes with no matching
        // texture aren't in COLOR's allowed values at all, so they fall
        // through untouched — "nothing happens", as intended.
        if (stack.getItem() instanceof DyeItem dyeItem) {
            DyeColor newColor = dyeItem.getDyeColor();
            if (!COLOR.getPossibleValues().contains(newColor)) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(COLOR, newColor), Block.UPDATE_ALL);
                if (!player.isCreative()) {
                    stack.shrink(1);
                }
            }
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * Same building blocks PG's own devices use (Voltage.rated/Resistance.series
     * — see e.g. HeaterBlock#appendProperties) for a matching "hold shift for
     * more info" tooltip. 12V matches POWERED_THRESHOLD's own real target
     * (see TelephoneBlockEntity); the coil's series resistance is disclosed
     * automatically "if any" since Resistance.series no-ops at 0 on its own.
     */
    @Override
    public void appendProperties(ItemStack stack, Player player, List<Component> tooltip) {
        Voltage.rated(12f, player, tooltip);
        Resistance.series(TelephoneBlockEntity.COIL_RESISTANCE, player, tooltip);
    }

    /**
     * Plain BlockItem#appendHoverText (confirmed via bytecode) forwards
     * straight into Block#appendHoverText, so overriding it here is enough
     * to reach the item tooltip — no custom Item subclass needed. Only ever
     * invoked client-side (tooltip rendering), same as WireItem's own
     * identical Minecraft.getInstance().player use for this exact purpose.
     */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        ElectricPropertiesUtils.modify(this, stack, Minecraft.getInstance().player, flag, tooltip);
    }

    @Override
    public Class<TelephoneBlockEntity> getBlockEntityClass() {
        return TelephoneBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends TelephoneBlockEntity> getBlockEntityType() {
        return CIOBlockEntities.TELEPHONE.get();
    }

    @Override
    public SimulatedDeviceType<TelephoneDevice> getDevice() {
        return CIODevices.TELEPHONE.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        int angle = angleFor(state);
        return Map.of(
                0, rotateY(CEE_POSITIVE_BASE, angle),
                1, rotateY(CEE_NEGATIVE_BASE, angle),
                2, rotateY(CEE_TAP_BASE, angle));
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        int angle = angleFor(state);
        return switch (id) {
            case 0 -> rotateY(CEE_POSITIVE_BASE, angle);
            case 1 -> rotateY(CEE_NEGATIVE_BASE, angle);
            case 2 -> rotateY(CEE_TAP_BASE, angle);
            default -> null;
        };
    }

    /** Mirror image of terminalsFor's own null-out — CEE's real, synchronous reject hook (default true; see InWorldNode#closestNode, which drops a node from candidate resolution entirely when this returns false). */
    @Override
    public boolean isNodeAccessible(Level level, BlockPos pos, BlockState state, int id) {
        if (id != 0 && id != 1) {
            return true;
        }
        return state.getValue(WIRE_LOCK) != WireLock.PG;
    }

    // Copied from InteroperableSmallBlock (itself copied from CEE's own
    // SimpleElectricalDeviceBlock, which this can't extend since it already
    // extends PG's ElectricBlock) — CEE only learns a block's declared CEE
    // nodes exist via InfrastructureSavedData#registerOrUpdateNodes, called
    // from a scheduled block tick, not automatically on placement.

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        LevelTickAccess<Block> blockTicks = level.getBlockTicks();
        if (!blockTicks.hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        List<Integer> nodes = new ArrayList<>(getNodePositions(level, pos, state).keySet());
        InfrastructureSavedData sd = InfrastructureSavedData.load(level);
        sd.registerOrUpdateNodes(pos, nodes);
        super.tick(state, level, pos, random);
    }
}
