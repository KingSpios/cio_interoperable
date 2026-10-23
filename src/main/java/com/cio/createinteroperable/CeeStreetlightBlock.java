package com.cio.createinteroperable;

import com.cio.createinteroperable.modules.ElectroEnergeticsModule;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Map;

/**
 * "Streetlight" &mdash; a CEE-only 120 V lamp head (see
 * {@link CeeStreetlightDevice} for the electrical behaviour). Wired at the
 * left pin (node 0) and right pin (node 1) under the mount end; the mount end
 * is the model's north, so {@link #FACING} points at whatever the head hangs
 * from. The model is translucent: the brownout and on textures fade their
 * light-overflow planes with partial alpha, which cutout would snap to 0/255.
 *
 * <ul>
 *   <li>A wrench cycles {@link #STYLE} the way CEE's connectors cycle theirs:
 *   straight head, straight head with arms, and the 22.5&deg; angled head with
 *   angled arms. The arms reach a full block to the north, into the pole.</li>
 *   <li>The photocell mode slider sits on the sensor puck (see
 *   {@link CeeStreetlightBlockEntity}).</li>
 *   <li>Lit, it projects light down to {@link CeeStreetlightBlockEntity#MAX_REACH}
 *   blocks below, the way Power Grid's Factory Light does.</li>
 *   <li>A bulb burnt out by over-voltage is replaced by right-clicking the head
 *   with a CEE Bulb, which is consumed.</li>
 * </ul>
 */
public class CeeStreetlightBlock extends SimpleElectricalDeviceBlock<CeeStreetlightDevice> implements IBE<CeeStreetlightBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Style> STYLE = EnumProperty.create("style", Style.class);
    public static final EnumProperty<Bulb> BULB = EnumProperty.create("bulb", Bulb.class);
    public static final IntegerProperty LIGHT = IntegerProperty.create("light", 0, 15);
    /** CEE's Bulb item &mdash; replaces a burnt-out bulb. By id: CEE's BlockEntry needs Registrate on the compile classpath. */
    private static final ResourceLocation CEE_BULB = ResourceLocation.fromNamespaceAndPath("electroenergetics", "bulb");

    /**
     * Model variants, in wrench-cycle order: straight head ({@code streetlamp_basic}), straight with arms
     * ({@code streetlamp_straight_arms}, arms dropped 0.1 px against z-fighting) and angled with angled arms
     * ({@code streetlamp_angled_22h}).
     */
    public enum Style implements StringRepresentable {
        STRAIGHT("straight"), STRAIGHT_ARMS("straight_arms"), ANGLED("angled");

        private final String name;

        Style(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /**
     * What the bulb shows, each its own texture: dark ({@code streetlight}), brownout 100&ndash;120 V
     * ({@code streetlight_brown}), on 120&ndash;330 V ({@code streetlight_on_actual}), burnt out
     * ({@code streetlight_broken}).
     */
    public enum Bulb implements StringRepresentable {
        OFF("off"), BROWN("brown"), ON("on"), BROKEN("broken");

        private final String name;

        Bulb(String name) {
            this.name = name;
        }

        public boolean lit() {
            return this == BROWN || this == ON;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    // Model points in 1/16 voxels at FACING=NORTH on the straight head (streetlamp_basic).
    /** Wire attach points: the centres of the 1 px left_pin / right_pin cubes at the bottom of the north end. */
    private static final Vec3[] PINS = {new Vec3(7.5 / 16, 0.5 / 16, 0.5 / 16), new Vec3(8.5 / 16, 0.5 / 16, 0.5 / 16)};
    /** Slider slot: centred on the sensor puck's top face (the puck is twisted 45&deg;; the slot isn't). */
    static final Vec3 PUCK_TOP = new Vec3(8 / 16.0, 4.05 / 16, 8 / 16.0);
    /** Middle of bulb_body; where the burn-out arc plays. */
    static final Vec3 BULB_CENTER = new Vec3(8 / 16.0, 1.5 / 16, 6.6 / 16);
    /**
     * The angled head is the straight head tilted -22.5&deg; about X around this pivot (1/16 voxels, y/z):
     * the earlier angled model's "shift (0, +1.465, -0.623), then tilt about (8, 2.588, 5.75)" folded into one
     * rotation, so every streetlamp_angled_22h element keeps streetlamp_basic's own from/to.
     */
    private static final double TILT_PIVOT_Y = 0.28907 / 16, TILT_PIVOT_Z = 2.37949 / 16;
    private static final double TILT = Math.toRadians(-22.5);

    private static final Map<Direction, VoxelShape> FLAT_SHAPES = ShapeRotation.forHorizontalFacing(
            new ShapeRotation.Box(5, 0, 0, 11, 3, 12),            // body, bulb, frame and pins (glass hangs below the block)
            new ShapeRotation.Box(6.6, 3, 6.6, 9.4, 4, 9.4));     // sensor puck, 45 deg twist
    private static final Map<Direction, VoxelShape> ANGLED_SHAPES = ShapeRotation.forHorizontalFacing(
            new ShapeRotation.Box(5, 0, 0, 11, 3.5, 3),           // tilted head, stepped along its length
            new ShapeRotation.Box(5, 0, 3, 11, 5.8, 6),
            new ShapeRotation.Box(5, 0.4, 6, 11, 6.3, 9),
            new ShapeRotation.Box(5, 1.6, 9, 11, 6.5, 12.5));

    public CeeStreetlightBlock(Properties properties) {
        super(properties.lightLevel(state -> state.getValue(LIGHT)));
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(STYLE, Style.STRAIGHT)
                .setValue(BULB, Bulb.OFF)
                .setValue(LIGHT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, STYLE, BULB, LIGHT);
    }

    /** Hung on the side of a block: the mount (north) end faces it. Placed on a top/bottom face: the mount faces away from the player. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        Direction facing = clicked.getAxis().isHorizontal() ? clicked.getOpposite() : context.getHorizontalDirection();
        return defaultBlockState().setValue(FACING, facing);
    }

    /** Like CEE's connectors: the wrench cycles the model style rather than rotating. Sneak-wrench still picks it up. */
    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        return originalState.cycle(STYLE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(STYLE) == Style.ANGLED ? ANGLED_SHAPES : FLAT_SHAPES).get(state.getValue(FACING));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            CeeStreetlightBlockEntity.clearProjection(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** A device created for an already burnt-out head (e.g. after a world edit) starts burnt out, like CEE's broken bulb. */
    @Override
    public CompoundTag getDefaultDeviceData(Level level, BlockPos pos, BlockState state) {
        CompoundTag tag = new CompoundTag();
        if (state.getValue(BULB) == Bulb.BROKEN) {
            tag.putBoolean("Broken", true);
        }
        return tag;
    }

    /** Replacing a burnt-out bulb: a CEE Bulb, consumed. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (state.getValue(BULB) != Bulb.BROKEN || !BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(CEE_BULB)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level instanceof ServerLevel sl) {
            CeeStreetlightDevice device = DevicesSavedData.load(sl).getDevice(pos, CeeStreetlightDevice.class);
            if (device != null) {
                device.repair();
            }
            level.setBlockAndUpdate(pos, state.setValue(BULB, Bulb.OFF).setValue(LIGHT, 0));
            AllSoundEvents.WRENCH_ROTATE.playOnServer(level, pos);
            if (!player.isCreative()) {
                stack.shrink(1);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("block.createinteroperable.cee_streetlight.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("block.createinteroperable.cee_streetlight.tooltip.rating").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("block.createinteroperable.cee_streetlight.tooltip.wrench").withStyle(ChatFormatting.DARK_GRAY));
    }

    // --- Geometry shared with the block entity ---------------------

    /** Maps a default-frame (FACING=NORTH, straight head) point onto this state's style and facing. */
    static Vec3 orient(BlockState state, Vec3 point) {
        if (state.getValue(STYLE) == Style.ANGLED) {
            point = tilt(point);
        }
        return CeeTelephoneBlock.rotateY(point, CeeTelephoneBlock.angleFor(state));
    }

    /** The same X rotation Minecraft applies to the angled model's elements (y' = y cos - z sin, z' = y sin + z cos). */
    private static Vec3 tilt(Vec3 p) {
        double dy = p.y - TILT_PIVOT_Y, dz = p.z - TILT_PIVOT_Z;
        double cos = Math.cos(TILT), sin = Math.sin(TILT);
        return new Vec3(p.x, TILT_PIVOT_Y + dy * cos - dz * sin, TILT_PIVOT_Z + dy * sin + dz * cos);
    }

    // --- CEE device wiring -----------------------------------------

    @Override
    public SimulatedDeviceType<CeeStreetlightDevice> getDevice() {
        return CIODevices.STREETLIGHT.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return Map.of(0, getNodePosition(level, pos, state, 0), 1, getNodePosition(level, pos, state, 1));
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        if (id != 0 && id != 1) {
            return null;
        }
        return orient(state, PINS[id]);
    }

    @Override
    public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int id) {
        return Component.translatable(id == 0
                ? "createinteroperable.streetlight.node.left"
                : "createinteroperable.streetlight.node.right");
    }

    /** 1 px pins sitting side by side: a 1 px pick box each, so neither swallows its neighbour. */
    @Override
    public float getNodeSize(Level level, BlockPos pos, BlockState state, int id) {
        return 0.0625F;
    }

    // --- Create IBE ---------------------------------------------

    @Override
    public Class<CeeStreetlightBlockEntity> getBlockEntityClass() {
        return CeeStreetlightBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CeeStreetlightBlockEntity> getBlockEntityType() {
        return ElectroEnergeticsModule.CEE_STREETLIGHT_BE.get();
    }
}
