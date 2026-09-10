package com.cio.createinteroperable.letsdo;

import com.cio.createinteroperable.CIOBlockEntities;
import com.cio.createinteroperable.CIOConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * Block entity attached (by {@code FarmAndCharmSinkBlockMixin}) to the lower half
 * of a Let's Do kitchen sink.
 *
 * <p>Holds an internal supply buffer fed by pipes ({@link SinkFluidHandler}). The
 * sink's own {@code filled} blockstate is NOT touched by pipes any more — the
 * player fills the basin by right-clicking the faucet ({@link SinkInteractionHandler}),
 * which draws {@link CIOConfig#SINK_FILL_COST_MB} out of this buffer.
 *
 * <p>Also the reason the sink is visible to Create at all: Create's pipe code
 * ignores blocks with no block entity.
 */
public class SinkBlockEntity extends BlockEntity {
    /** Fluid + amount currently buffered from the supply. One fluid at a time. */
    private FluidStack buffer = FluidStack.EMPTY;
    /** Fluid that last filled the basin (what a bucket pulls back out). */
    private Fluid basinFluid = Fluids.WATER;
    /** Game time of the last accepted pipe delivery — server side only, transient. */
    private long lastDeliveryTick = Long.MIN_VALUE;

    private SinkFluidHandler handler;

    public SinkBlockEntity(BlockPos pos, BlockState state) {
        super(CIOBlockEntities.LETSDO_SINK.get(), pos, state);
    }

    public SinkFluidHandler fluidHandler() {
        if (handler == null) {
            handler = new SinkFluidHandler(this);
        }
        return handler;
    }

    // --- buffer ----------------------------------------------------------------

    public int bufferCapacity() {
        return Math.max(1, CIOConfig.SINK_BUFFER_CAPACITY_MB.get());
    }

    public FluidStack getBuffer() {
        return buffer;
    }

    public int bufferAmount() {
        return buffer.getAmount();
    }

    public boolean isSupplied() {
        return !buffer.isEmpty();
    }

    /** Room for {@code incoming}, honouring capacity and single-fluid rule; 0 if it can't be accepted. */
    public int roomFor(FluidStack incoming) {
        if (incoming.isEmpty()) {
            return 0;
        }
        if (!buffer.isEmpty() && buffer.getFluid() != incoming.getFluid()) {
            return 0;
        }
        return Math.max(0, bufferCapacity() - buffer.getAmount());
    }

    /** Adds up to {@code amount} of {@code fluid} to the buffer; returns the amount actually stored. */
    public int addToBuffer(Fluid fluid, int amount) {
        FluidStack incoming = new FluidStack(fluid, amount);
        int room = roomFor(incoming);
        int stored = Math.min(room, amount);
        if (stored <= 0) {
            return 0;
        }
        if (buffer.isEmpty()) {
            buffer = new FluidStack(fluid, stored);
        } else {
            buffer.grow(stored);
        }
        setChanged();
        sync();
        return stored;
    }

    public void markDelivery() {
        if (level != null) {
            lastDeliveryTick = level.getGameTime();
        }
    }

    /** Whether a supply is connected right now (has buffer, delivered recently, or a pipe sits below). */
    public boolean hasLiveSupply() {
        if (isSupplied()) {
            return true;
        }
        if (level != null && level.getGameTime() - lastDeliveryTick <= 60L) {
            return true;
        }
        return pipeBelow();
    }

    private boolean pipeBelow() {
        if (level == null) {
            return false;
        }
        BlockEntity below = level.getBlockEntity(worldPosition.below());
        return below != null && below.getClass().getName().startsWith("com.simibubi.create.content.fluids");
    }

    // --- basin (the visible "filled" blockstate) ------------------------------

    /**
     * Draw one fill charge from the buffer and mark the basin filled.
     * @return true if the basin was filled (buffer had enough).
     */
    public boolean fillBasinFromBuffer() {
        if (level == null || level.isClientSide) {
            return false;
        }
        int cost = Math.max(1, CIOConfig.SINK_FILL_COST_MB.get());
        if (buffer.getAmount() < cost) {
            return false;
        }
        basinFluid = buffer.getFluid();
        buffer.shrink(cost);
        if (buffer.isEmpty()) {
            buffer = FluidStack.EMPTY;
        }
        level.setBlock(worldPosition, SinkStates.withFilled(getBlockState(), true), Block.UPDATE_ALL);
        setChanged();
        sync();
        return true;
    }

    /** Empty the basin; returns the fluid that was in it (for the bucket to receive). */
    public Fluid drainBasin() {
        Fluid was = basinFluid;
        if (level != null && !level.isClientSide) {
            level.setBlock(worldPosition, SinkStates.withFilled(getBlockState(), false), Block.UPDATE_ALL);
        }
        return was;
    }

    public Fluid basinFluid() {
        return basinFluid;
    }

    // --- sync / nbt ----------------------------------------------------------

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!buffer.isEmpty()) {
            tag.putString("BufferFluid", key(buffer.getFluid()));
            tag.putInt("BufferAmount", buffer.getAmount());
        }
        tag.putString("BasinFluid", key(basinFluid));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        int amount = tag.getInt("BufferAmount");
        Fluid bufFluid = fluid(tag.getString("BufferFluid"));
        buffer = (amount > 0 && bufFluid != Fluids.EMPTY) ? new FluidStack(bufFluid, amount) : FluidStack.EMPTY;
        Fluid basin = fluid(tag.getString("BasinFluid"));
        basinFluid = basin == Fluids.EMPTY ? Fluids.WATER : basin;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private static String key(Fluid fluid) {
        return BuiltInRegistries.FLUID.getKey(fluid).toString();
    }

    private static Fluid fluid(String id) {
        if (id == null || id.isEmpty()) {
            return Fluids.EMPTY;
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? Fluids.EMPTY : BuiltInRegistries.FLUID.get(rl);
    }

    /** Finds the lower-half sink block entity from a click on either half. */
    @Nullable
    public static SinkBlockEntity atEitherHalf(Level level, BlockPos clicked, BlockState clickedState) {
        BlockPos lower = SinkStates.isUpperHalf(clickedState) ? clicked.below() : clicked;
        return level.getBlockEntity(lower) instanceof SinkBlockEntity be ? be : null;
    }
}
