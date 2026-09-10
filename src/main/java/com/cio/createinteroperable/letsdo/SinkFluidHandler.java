package com.cio.createinteroperable.letsdo;

import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/**
 * The fluid endpoint a pipe sees under a Let's Do kitchen sink, backed by a
 * {@link SinkBlockEntity}.
 *
 * <p>Fill only. Incoming fluid goes into the block entity's internal buffer (one
 * fluid at a time, up to {@link SinkBlockEntity#bufferCapacity()}); it does NOT
 * touch the sink's visible {@code filled} state — the player does that from the
 * faucet (see {@link SinkInteractionHandler}). Any fluid is accepted.
 */
final class SinkFluidHandler implements IFluidHandler {
    private final SinkBlockEntity be;

    SinkFluidHandler(SinkBlockEntity be) {
        this.be = be;
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return be.getBuffer().copy();
    }

    @Override
    public int getTankCapacity(int tank) {
        return be.bufferCapacity();
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return be.roomFor(stack) > 0 || (be.isSupplied() && be.getBuffer().getFluid() == stack.getFluid());
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) {
            return 0;
        }
        Level level = be.getLevel();
        if (level == null || level.isClientSide) {
            return 0;
        }
        int room = be.roomFor(resource);
        int accepted = Math.min(resource.getAmount(), room);
        if (accepted <= 0) {
            return 0;
        }
        if (action.execute()) {
            be.addToBuffer(resource.getFluid(), accepted);
            be.markDelivery();
        }
        return accepted;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return FluidStack.EMPTY;
    }
}
