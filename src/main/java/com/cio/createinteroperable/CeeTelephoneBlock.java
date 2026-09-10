package com.cio.createinteroperable;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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

import java.util.Map;

/**
 * "CEE Telephone" — the Electro-Energetics-only twin of the Interoperable
 * Telephone. Same model, CEE nodes at the exact same physical spots the PG
 * terminals sit on the PG/Interoperable variants (positive/negative/tap), no
 * WIRE_LOCK arbitration at all — there's only ever one protocol here.
 * CEE-only: extends CEE's own {@link SimpleElectricalDeviceBlock} convenience
 * base (onPlace/tick node-registration lifecycle for free, same as
 * {@code CeeDebRectifierBlock}) rather than Power Grid's {@code ElectricBlock}.
 */
public class CeeTelephoneBlock extends SimpleElectricalDeviceBlock<TelephoneDevice> implements IBE<CeeTelephoneBlockEntity> {
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
            box16(6, 1, 12, 7, 2, 13),      // outlet_positive (decorative only on this variant)
            box16(9, 1, 12, 10, 2, 13),     // outlet_negative (decorative only on this variant)
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

    /** Handset (phone_part_handle + phone_part), block-local — same footprint as the PG/Interoperable variants. */
    private static final AABB HANDSET_HITBOX = box16(3, 7, 11, 5, 12, 13);
    /** back_plate's own current footprint — right-clicking this (outside a terminal) opens the Area Code/Number screen. */
    private static final AABB BACK_PLATE_HITBOX = box16(7, 2, 13.9, 9, 14, 15.9);
    /** dial_ring — right-click opens the dial-out screen. */
    private static final AABB DIAL_RING_HITBOX = box16(6.5, 3, 9, 9.5, 6, 10);

    /** Same physical spot as the PG/Interoperable variants' POSITIVE_BASE/NEGATIVE_BASE nubs, normalized to 0-1 voxel space. */
    private static final Vec3 CEE_POSITIVE_BASE = new Vec3(6.0 / 16, 14.4 / 16, 13.0 / 16);
    private static final Vec3 CEE_NEGATIVE_BASE = new Vec3(10.0 / 16, 14.4 / 16, 13.1 / 16);
    /** Same spot as TAP_BASE — a distinct node, not swallowed by positive/negative's own search radius. */
    private static final Vec3 CEE_TAP_BASE = new Vec3(8.0 / 16, 14.4 / 16, 13.1 / 16);
    /**
     * Ids 3/4/5 — the model's bottom outlet_positive/outlet_negative/listener
     * nubs (matching CpgTelephoneBlock's own OUTLET_POSITIVE_BASE/
     * OUTLET_NEGATIVE_BASE/LISTENER_BASE center coordinates). Previously
     * decorative-only here; now real nodes, gated on the phone's own
     * answered/call state via {@link TelephoneDevice#setAnswered}, not merely
     * on the phone being powered.
     */
    private static final Vec3 CEE_OUTLET_POSITIVE_BASE = new Vec3(6.5 / 16, 1.5 / 16, 12.5 / 16);
    private static final Vec3 CEE_OUTLET_NEGATIVE_BASE = new Vec3(9.5 / 16, 1.5 / 16, 12.5 / 16);
    private static final Vec3 CEE_LISTENER_BASE = new Vec3(8.0 / 16, 1.4 / 16, 13.1 / 16);

    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class,
            DyeColor.BLUE, DyeColor.WHITE, DyeColor.GRAY, DyeColor.RED, DyeColor.CYAN, DyeColor.GREEN,
            DyeColor.YELLOW, DyeColor.BLACK, DyeColor.PINK, DyeColor.PURPLE, DyeColor.ORANGE,
            DyeColor.BROWN, DyeColor.LIME, DyeColor.MAGENTA);

    public CeeTelephoneBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(COLOR, DyeColor.BLUE)
                .setValue(CIOProperties.CALL_ACTIVE, false));
    }

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

    private static boolean checkLocal(AABB base, int angle, Vec3 local) {
        // Same expand=1 (1/16 block) margin as the PG variants' TerminalBoundingBox
        // check boxes — a dead-on hit lands exactly on a raw box's own boundary,
        // so the check box must be strictly larger than the raytrace surface.
        AABB rotated = rotateAABB(base, angle).inflate(1.0 / 16);
        return rotated.contains(local);
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
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        TelephoneRedstone.onRemoved(level, pos, state, newState, movedByPiston, FACING);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shapeFor(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shapeFor(state);
    }

    /** Wall-mounted, fixed orientation — a plain wrench must not spin it off its mount, same as CeeDebRectifierBlock. Sneak-wrench pickup stays with SimpleElectricalDeviceBlock. */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof CeeTelephoneBlockEntity be)) {
            return InteractionResult.PASS;
        }
        int angle = angleFor(state);
        Vec3 local = hitResult.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());

        if (checkLocal(HANDSET_HITBOX, angle, local)) {
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
        if (checkLocal(BACK_PLATE_HITBOX, angle, local)) {
            return be.onBackPlateUsed(player);
        }
        if (checkLocal(DIAL_RING_HITBOX, angle, local)) {
            return be.onFrontUsed(player);
        }

        Direction hitFace = hitResult.getDirection();
        if (hitFace == Direction.UP) {
            return be.onTopUsed(player);
        }
        return InteractionResult.PASS;
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

    // --- CEE device wiring -----------------------------------------

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
                2, rotateY(CEE_TAP_BASE, angle),
                3, rotateY(CEE_OUTLET_POSITIVE_BASE, angle),
                4, rotateY(CEE_OUTLET_NEGATIVE_BASE, angle),
                5, rotateY(CEE_LISTENER_BASE, angle));
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        int angle = angleFor(state);
        return switch (id) {
            case 0 -> rotateY(CEE_POSITIVE_BASE, angle);
            case 1 -> rotateY(CEE_NEGATIVE_BASE, angle);
            case 2 -> rotateY(CEE_TAP_BASE, angle);
            case 3 -> rotateY(CEE_OUTLET_POSITIVE_BASE, angle);
            case 4 -> rotateY(CEE_OUTLET_NEGATIVE_BASE, angle);
            case 5 -> rotateY(CEE_LISTENER_BASE, angle);
            default -> null;
        };
    }

    // --- Create IBE ---------------------------------------------

    @Override
    public Class<CeeTelephoneBlockEntity> getBlockEntityClass() {
        return CeeTelephoneBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CeeTelephoneBlockEntity> getBlockEntityType() {
        return CIOBlockEntities.CEE_TELEPHONE.get();
    }
}
