package com.cio.createinteroperable.streetlight.cee;

import com.cio.createinteroperable.CIODevices;
import com.cio.createinteroperable.modules.ElectroEnergeticsModule;
import com.cio.createinteroperable.streetlight.Streetlight;
import com.cio.createinteroperable.streetlight.StreetlightCore;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Map;

/**
 * The Electro Energetics Streetlight: the shared lamp head ({@link Streetlight})
 * wired at the left pin (node 0) and right pin (node 1) under the mount end
 * (see {@link CeeStreetlightDevice} for the electrical side). The model is
 * translucent: the brownout and on textures fade their light-overflow planes
 * with partial alpha, which cutout would snap to 0/255.
 *
 * <ul>
 *   <li>A wrench cycles {@link Streetlight#STYLE} the way CEE's connectors
 *   cycle theirs.</li>
 *   <li>The photocell mode slider sits on the sensor puck (see {@link StreetlightCore}).</li>
 *   <li>A bulb burnt out by over-voltage is replaced by right-clicking the head
 *   with a CEE Bulb, which is consumed.</li>
 * </ul>
 */
public class CeeStreetlightBlock extends SimpleElectricalDeviceBlock<CeeStreetlightDevice> implements IBE<CeeStreetlightBlockEntity> {
    /** CEE's Bulb item &mdash; replaces a burnt-out bulb. By id: CEE's BlockEntry needs Registrate on the compile classpath. */
    private static final ResourceLocation CEE_BULB = ResourceLocation.fromNamespaceAndPath("electroenergetics", "bulb");

    public CeeStreetlightBlock(Properties properties) {
        super(properties.lightLevel(state -> state.getValue(Streetlight.LIGHT)));
        registerDefaultState(Streetlight.defaults(defaultBlockState()));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(Streetlight.FACING, Streetlight.STYLE, Streetlight.BULB, Streetlight.LIGHT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return Streetlight.placement(defaultBlockState(), context);
    }

    /** Like CEE's connectors: the wrench cycles the model style rather than rotating. Sneak-wrench still picks it up. */
    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        return originalState.cycle(Streetlight.STYLE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Streetlight.shape(state);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(Streetlight.FACING, rotation.rotate(state.getValue(Streetlight.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(Streetlight.FACING, mirror.mirror(state.getValue(Streetlight.FACING)));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            StreetlightCore.clearProjection(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** A device created for an already burnt-out head (e.g. after a world edit) starts burnt out, like CEE's broken bulb. */
    @Override
    public CompoundTag getDefaultDeviceData(Level level, BlockPos pos, BlockState state) {
        CompoundTag tag = new CompoundTag();
        if (state.getValue(Streetlight.BULB) == Streetlight.Bulb.BROKEN) {
            tag.putBoolean("Broken", true);
        }
        return tag;
    }

    /** Replacing a burnt-out bulb: a CEE Bulb, consumed. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (state.getValue(Streetlight.BULB) != Streetlight.Bulb.BROKEN || !BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(CEE_BULB)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level instanceof ServerLevel sl) {
            CeeStreetlightDevice device = DevicesSavedData.load(sl).getDevice(pos, CeeStreetlightDevice.class);
            if (device != null) {
                device.repair();
            }
            level.setBlockAndUpdate(pos, state.setValue(Streetlight.BULB, Streetlight.Bulb.OFF).setValue(Streetlight.LIGHT, 0));
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
        return Streetlight.orient(state, Streetlight.PINS[id]);
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
