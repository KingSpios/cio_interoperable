package com.cio.createinteroperable.iden;

import com.cio.createinteroperable.CIOProperties;
import com.cio.createinteroperable.TelephoneNumbers;
import com.cio.createinteroperable.TelephoneRedstone;
import com.cio.createinteroperable.compat.ElectroEnergeticsCompat;
import com.cio.createinteroperable.compat.PowerGridCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Block-side behaviour shared by the three CIO stand-ins for Iden's Decor's
 * telephone ({@link IdenPgTelephoneBlock}, {@link IdenInteropTelephoneBlock},
 * {@link IdenCeeTelephoneBlock}) &mdash; they have different parent classes
 * (Power Grid's {@code ElectricBlock} vs. Electro Energetics'
 * {@code SimpleElectricalDeviceBlock}) and can't share a base, so each
 * delegates here. Deliberately free of any Power Grid or Electro Energetics
 * type, so it loads on every install.
 *
 * <p><b>Blockstate</b>: Iden's own {@code facing}/{@code phone}/{@code powered}
 * (same names and values, so Iden's blockstate file and models render it
 * unchanged), plus CIO's {@link CIOProperties#CALL_ACTIVE} for the call's
 * redstone output (unlisted in Iden's variants, which is fine &mdash; variant
 * keys only constrain the properties they name). {@code phone} = handset on
 * the cradle; {@code powered} = redstone <em>input</em>, as on Iden's block.</p>
 *
 * <p><b>Geometry</b> is authored facing NORTH, where Iden's model puts the flat
 * diagonal dial plate at z&nbsp;3.5, i.e. the dial faces {@code FACING} (toward
 * whoever placed it). The two tap nubs sit on the opposite side, the back of
 * the base (z&nbsp;12&ndash;13, y&nbsp;1&ndash;2): CPG left, CEE right.</p>
 */
public final class IdenPhoneBlocks {

    private IdenPhoneBlocks() {
    }

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Iden's {@code phone}: the handset is on the cradle. */
    public static final BooleanProperty PHONE = BooleanProperty.create("phone");
    /** Iden's {@code powered}: a redstone signal is reaching the phone (input, not output). */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    /** CEE node id of the tap &mdash; the same id CIO's own telephones walk to ({@code ceeTapReaches}). */
    public static final int CEE_TAP_NODE = 2;

    /** Tap nubs, NORTH reference (1/16 units). Also the rendered nubs' model coordinates. */
    public static final AABB CPG_TAP_BOX = box16(5.5, 1, 12, 6.5, 2, 13);
    public static final AABB CEE_TAP_BOX = box16(9.5, 1, 12, 10.5, 2, 13);
    private static final Vec3 CEE_TAP_POINT = CEE_TAP_BOX.getCenter();

    /** Appliance-grid node cube: above the handset, which covers the cell centre. Rotation-invariant. */
    static final AABB NODE_BOX = box16(6, 9, 6, 10, 13, 10);

    // Iden's own shapes (TelephoneBlock#getShape, NORTH case), plus the tap nubs.
    private static final AABB[] BODY_BOXES = { box16(5, 2, 7, 11, 5, 11), box16(4, 0, 4, 12, 3, 12) };
    private static final AABB HANDSET_ON_CRADLE = box16(2, 5, 7.5, 14, 8, 10.5);
    private static final AABB EMPTY_CRADLE = box16(6, 5, 8, 10, 7, 10);

    /** [angle / 90][phone ? 1 : 0]. */
    private static final VoxelShape[][] SHAPES = buildShapes();

    private static AABB box16(double x1, double y1, double z1, double x2, double y2, double z2) {
        return new AABB(x1 / 16, y1 / 16, z1 / 16, x2 / 16, y2 / 16, z2 / 16);
    }

    private static VoxelShape[][] buildShapes() {
        VoxelShape[][] out = new VoxelShape[4][2];
        for (int i = 0; i < 4; i++) {
            for (int phone = 0; phone < 2; phone++) {
                VoxelShape shape = Shapes.create(rotate(phone == 1 ? HANDSET_ON_CRADLE : EMPTY_CRADLE, i * 90));
                for (AABB box : BODY_BOXES) {
                    shape = Shapes.or(shape, Shapes.create(rotate(box, i * 90)));
                }
                if (PowerGridCompat.present()) {
                    shape = Shapes.or(shape, Shapes.create(rotate(CPG_TAP_BOX, i * 90)));
                }
                if (ElectroEnergeticsCompat.present()) {
                    shape = Shapes.or(shape, Shapes.create(rotate(CEE_TAP_BOX, i * 90)));
                }
                out[i][phone] = shape;
            }
        }
        return out;
    }

    // --- geometry --------------------------------------------------------

    /** North=0, East=90, South=180, West=270 &mdash; the blockstate's own model "y". */
    public static int angleFor(BlockState state) {
        return TelephoneNumbers.angleFor(state);
    }

    /** Same rotation as a blockstate's model "y" (and CIO's own telephones' {@code rotateY}). */
    public static Vec3 rotate(Vec3 base, int angle) {
        double dx = base.x - 0.5, dz = base.z - 0.5;
        return switch (angle) {
            case 90 -> new Vec3(0.5 - dz, base.y, 0.5 + dx);
            case 180 -> new Vec3(0.5 - dx, base.y, 0.5 - dz);
            case 270 -> new Vec3(0.5 + dz, base.y, 0.5 - dx);
            default -> base;
        };
    }

    public static AABB rotate(AABB box, int angle) {
        Vec3 a = rotate(new Vec3(box.minX, box.minY, box.minZ), angle);
        Vec3 b = rotate(new Vec3(box.maxX, box.maxY, box.maxZ), angle);
        return new AABB(Math.min(a.x, b.x), box.minY, Math.min(a.z, b.z), Math.max(a.x, b.x), box.maxY, Math.max(a.z, b.z));
    }

    public static Vec3 ceeTapPoint(BlockState state) {
        return rotate(CEE_TAP_POINT, angleFor(state));
    }

    public static VoxelShape shape(BlockState state) {
        return SHAPES[angleFor(state) / 90][state.getValue(PHONE) ? 1 : 0];
    }

    // --- blockstate ------------------------------------------------------

    public static void addProperties(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PHONE, POWERED, CIOProperties.CALL_ACTIVE);
    }

    /** Iden's defaults: facing north, handset off the cradle, no redstone in. */
    public static BlockState defaultState(BlockState any) {
        return any.setValue(FACING, Direction.NORTH).setValue(PHONE, false).setValue(POWERED, false)
                .setValue(CIOProperties.CALL_ACTIVE, false);
    }

    public static BlockState placement(BlockState defaultState, BlockPlaceContext context) {
        return defaultState.setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** Iden's strength / occlusion. */
    public static BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.of().strength(2.0f).noOcclusion();
    }

    // --- Iden's handset item + sounds, by id (CIO doesn't compile against Iden) ---

    private static final ResourceLocation HANDSET_ID = ResourceLocation.fromNamespaceAndPath("iden_decor", "telephone_item");

    public static Item handset() {
        return BuiltInRegistries.ITEM.get(HANDSET_ID);
    }

    static void playIdenSound(Level level, BlockPos pos, String name) {
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.fromNamespaceAndPath("iden_decor", name));
        if (sound != null) {
            level.playSound(null, pos, sound, SoundSource.BLOCKS, 1f, 1f);
        }
    }

    // --- interaction -----------------------------------------------------

    /**
     * Empty-handed use. Sneaking opens the settings screen; otherwise, as on
     * Iden's block, lifting the handset off the cradle hands it to the player
     * &mdash; and now also answers a ringing call or dials the configured number.
     */
    public static InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof IdenPhone phone)) {
            return InteractionResult.PASS;
        }
        IdenPhoneCore core = phone.phoneCore();
        if (player.isShiftKeyDown()) {
            if (level.isClientSide) {
                com.cio.createinteroperable.TelephoneClient.openSettings(pos, core.areaCode(), core.ownNumberText(), core.label(),
                        core.dialTarget(), core.autoAnswer(), false, false);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!state.getValue(PHONE) || !player.getMainHandItem().isEmpty() || handset() == Items.AIR) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(PHONE, false), Block.UPDATE_ALL);
            playIdenSound(level, pos, "phone_pick");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(handset()));
            core.onHandsetLifted(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Putting the handset back on the cradle; hangs up any call. */
    public static ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos) {
        Item handset = handset();
        if (handset == Items.AIR || !stack.is(handset) || state.getValue(PHONE)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(PHONE, true), Block.UPDATE_ALL);
            playIdenSound(level, pos, "phone_slam");
            stack.shrink(1);
            if (level.getBlockEntity(pos) instanceof IdenPhone phone) {
                phone.phoneCore().onHandsetReturned();
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Tracks redstone input in {@code powered}, as Iden's block did; a rising
     * edge now dials the configured number instead of just playing the bell,
     * and a falling edge withdraws that call if it hasn't been answered yet.
     * The phone's own call-time output can feed back in here (through the block
     * behind it), but that edge always lands mid-call, where it's ignored.
     */
    public static void neighborChanged(BlockState state, Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        boolean redstone = level.hasNeighborSignal(pos);
        if (redstone == state.getValue(POWERED)) {
            return;
        }
        level.setBlock(pos, state.setValue(POWERED, redstone), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof IdenPhone phone) {
            if (redstone) {
                phone.phoneCore().onRedstoneRise();
            } else {
                phone.phoneCore().onRedstoneFall();
            }
        }
    }

    /** Iden's comparator output: 5 while the handset is on the cradle. */
    public static int comparator(BlockState state) {
        return state.getValue(PHONE) ? 5 : 0;
    }

    // --- redstone output while on an answered, incoming call -------------

    public static boolean isSignalSource(BlockState state) {
        return TelephoneRedstone.isActive(state);
    }

    public static int weakSignal(BlockState state) {
        return TelephoneRedstone.weakSignal(state);
    }

    public static int directSignal(BlockState state, Direction direction) {
        return TelephoneRedstone.directSignal(state, direction, FACING);
    }

    public static void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        TelephoneRedstone.onRemoved(level, pos, state, newState, movedByPiston, FACING);
    }
}
