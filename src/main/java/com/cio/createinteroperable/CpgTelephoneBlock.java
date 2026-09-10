package com.cio.createinteroperable;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import org.patryk3211.powergrid.electricity.base.ElectricBlock;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.electricity.base.terminals.BlockStateTerminalCollection;
import org.patryk3211.powergrid.electricity.info.ElectricPropertiesUtils;
import org.patryk3211.powergrid.electricity.info.IHaveElectricProperties;
import org.patryk3211.powergrid.electricity.info.Resistance;
import org.patryk3211.powergrid.electricity.info.Voltage;

import java.util.List;

/**
 * "CPG Telephone" — the Power-Grid-only twin of the Interoperable Telephone.
 * Same model and the same positive/negative/tap/listener terminal layout
 * (see the Interoperable {@link TelephoneBlock}'s own doc for the model-
 * derived geometry), but no CEE node system and no WIRE_LOCK arbitration at
 * all — there's only ever one protocol here, so the terminals are always
 * live. PG-only: extends PG's {@link ElectricBlock} but does not implement
 * Electro Energetics' {@code ElectricalDeviceBlock}.
 */
public class CpgTelephoneBlock extends ElectricBlock implements IBE<CpgTelephoneBlockEntity>, IHaveElectricProperties {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static AABB box16(double x1, double y1, double z1, double x2, double y2, double z2) {
        return new AABB(x1 / 16, y1 / 16, z1 / 16, x2 / 16, y2 / 16, z2 / 16);
    }

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
            box16(6, 1, 12, 7, 2, 13),      // outlet_positive (Power Feed +, pass-through to POSITIVE_BASE)
            box16(9, 1, 12, 10, 2, 13),     // outlet_negative (Power Feed -, pass-through to NEGATIVE_BASE)
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

    private static final VoxelShape[] ROTATED_SHAPES =
            { buildShape(0), buildShape(90), buildShape(180), buildShape(270) };

    private static VoxelShape shapeFor(BlockState state) {
        return ROTATED_SHAPES[angleFor(state) / 90];
    }

    /**
     * A hair of {@code check()} tolerance beyond each 1&times;1 model nub —
     * see {@code DebRectifierBlock}'s own {@code NUB_EXPAND} for why this needs
     * to be small. The {@code expand} param here used to be a full {@code 1}
     * (one whole pixel each side), tripling every nub's actual hit volume to
     * 3&times;3&times;3 and making POSITIVE/TAP/NEGATIVE's expanded boxes
     * overlap each other — a real, confirmed cause of wires landing on the
     * wrong terminal.
     */
    private static final double NUB_EXPAND = 0.02;

    private static final TerminalBoundingBox HANDSET_HITBOX =
            new TerminalBoundingBox(IDecoratedTerminal.CONNECTOR, 3, 7, 11, 5, 12, 13);

    private static final TerminalBoundingBox BACK_PLATE_HITBOX =
            new TerminalBoundingBox(IDecoratedTerminal.CONNECTOR, 7, 2, 13.9, 9, 14, 15.9, NUB_EXPAND);

    private static final TerminalBoundingBox POSITIVE_BASE =
            new TerminalBoundingBox(IDecoratedTerminal.POSITIVE, 5.5, 13.9, 12.5, 6.5, 14.9, 13.5, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.RED);
    private static final TerminalBoundingBox NEGATIVE_BASE =
            new TerminalBoundingBox(IDecoratedTerminal.NEGATIVE, 9.5, 13.9, 12.6, 10.5, 14.9, 13.6, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.BLUE);
    private static final TerminalBoundingBox TAP_BASE =
            new TerminalBoundingBox(IDecoratedTerminal.TAP, 7.5, 13.9, 12.6, 8.5, 14.9, 13.6, NUB_EXPAND);
    /** No longer a voltage source — a breaker that only completes an externally-wired circuit while a call is answered. */
    private static final TerminalBoundingBox LISTENER_BASE =
            new TerminalBoundingBox(Component.literal("Call Breaker"), 7.5, 0.9, 12.6, 8.5, 1.9, 13.6, NUB_EXPAND);

    /**
     * The model's bottom {@code outlet_positive}/{@code outlet_negative} nubs
     * — previously decorative-only (no {@link TerminalBoundingBox} at all, so
     * nothing could ever be wired there). Now real terminals on the same
     * electrical rail as {@link #POSITIVE_BASE}/{@link #NEGATIVE_BASE} (see
     * {@code CpgTelephoneBlockEntity#buildCircuit}'s breaker connection), live
     * only while a call is answered and ongoing — not merely while the phone
     * is powered.
     */
    private static final TerminalBoundingBox OUTLET_POSITIVE_BASE =
            new TerminalBoundingBox(Component.literal("Call Feed +"), 6, 1, 12, 7, 2, 13, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.RED);
    private static final TerminalBoundingBox OUTLET_NEGATIVE_BASE =
            new TerminalBoundingBox(Component.literal("Call Feed −"), 9, 1, 12, 10, 2, 13, NUB_EXPAND)
                    .withColor(IDecoratedTerminal.BLUE);

    private static final TerminalBoundingBox DIAL_RING_HITBOX =
            new TerminalBoundingBox(IDecoratedTerminal.CONNECTOR, 6.5, 3, 9, 9.5, 6, 10, NUB_EXPAND);

    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class,
            DyeColor.BLUE, DyeColor.WHITE, DyeColor.GRAY, DyeColor.RED, DyeColor.CYAN, DyeColor.GREEN,
            DyeColor.YELLOW, DyeColor.BLACK, DyeColor.PINK, DyeColor.PURPLE, DyeColor.ORANGE,
            DyeColor.BROWN, DyeColor.LIME, DyeColor.MAGENTA);

    public CpgTelephoneBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(COLOR, DyeColor.BLUE)
                .setValue(CIOProperties.CALL_ACTIVE, false));
        setTerminalCollection(BlockStateTerminalCollection.builder(this)
                .forAllStates(CpgTelephoneBlock::terminalsFor)
                .build());
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

    static Vec3 rotateY(Vec3 base, int angle) {
        double dx = base.x - 0.5, dz = base.z - 0.5;
        return switch (angle) {
            case 90 -> new Vec3(0.5 - dz, base.y, 0.5 + dx);
            case 180 -> new Vec3(0.5 - dx, base.y, 0.5 - dz);
            case 270 -> new Vec3(0.5 + dz, base.y, 0.5 - dx);
            default -> base;
        };
    }

    private static TerminalBoundingBox[] terminalsFor(BlockState state) {
        int angle = angleFor(state);
        return new TerminalBoundingBox[] {
                POSITIVE_BASE.rotateAroundY(angle),
                NEGATIVE_BASE.rotateAroundY(angle),
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
        builder.add(FACING, COLOR, CIOProperties.CALL_ACTIVE);
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
        if (!(level.getBlockEntity(pos) instanceof CpgTelephoneBlockEntity be)) {
            return InteractionResult.PASS;
        }

        Vec3 local = hitResult.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        if (handsetHitboxFor(state).check(local)) {
            AllSoundEvents.DESK_BELL_USE.play(level, player, pos);
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
                                Component.literal("That number is busy.").withStyle(net.minecraft.ChatFormatting.RED), true);
                    }
                }
            }
            return be.onHandsetUsed(player);
        }
        if (backPlateHitboxFor(state).check(local) && !onPowerOrTapTerminal(state, local)) {
            return be.onBackPlateUsed(player);
        }
        if (dialRingHitboxFor(state).check(local)) {
            return be.onFrontUsed(player);
        }

        Direction hitFace = hitResult.getDirection();
        if (hitFace == Direction.UP) {
            return be.onTopUsed(player);
        }
        return InteractionResult.PASS;
    }

    private static void spawnDenySmoke(Level level, BlockPos pos) {
        for (int i = 0; i < 6; i++) {
            double x = pos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.4;
            double y = pos.getY() + 0.4 + level.random.nextDouble() * 0.3;
            double z = pos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.4;
            level.addParticle(ParticleTypes.WHITE_SMOKE, x, y, z, 0, 0.01, 0);
        }
    }

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

    @Override
    public void appendProperties(ItemStack stack, Player player, List<Component> tooltip) {
        Voltage.rated(12f, player, tooltip);
        Resistance.series(CpgTelephoneBlockEntity.COIL_RESISTANCE, player, tooltip);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        ElectricPropertiesUtils.modify(this, stack, Minecraft.getInstance().player, flag, tooltip);
    }

    @Override
    public Class<CpgTelephoneBlockEntity> getBlockEntityClass() {
        return CpgTelephoneBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CpgTelephoneBlockEntity> getBlockEntityType() {
        return CIOBlockEntities.CPG_TELEPHONE.get();
    }
}
