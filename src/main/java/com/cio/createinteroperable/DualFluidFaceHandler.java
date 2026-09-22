package com.cio.createinteroperable;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * Exposes two independent internal {@link FluidTank}s as ONE {@link IFluidHandler}
 * face — {@link AirconVenterBlockEntity}'s "motor-facing" side needs to both
 * accept a fill (cold_air in) and offer a drain (hot_air out) through the
 * same physical world face, which a plain single {@code FluidTank} can't do
 * (it only ever models one fluid instance). Tank index 0 is fill-oriented,
 * index 1 is drain-oriented — {@link #fill} always targets index 0,
 * {@link #drain} always targets index 1, matching the two real tanks passed
 * to the constructor. Draining index 0 or filling index 1 is still allowed
 * (harmless, e.g. an external pump also topping up the hot_air side) since
 * both are still real {@code FluidTank}s underneath.
 */
public class DualFluidFaceHandler implements IFluidHandler {
    private final FluidTank fillTarget;
    private final FluidTank drainSource;

    public DualFluidFaceHandler(FluidTank fillTarget, FluidTank drainSource) {
        this.fillTarget = fillTarget;
        this.drainSource = drainSource;
    }

    @Override
    public int getTanks() {
        return 2;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return tank == 0 ? fillTarget.getFluid() : drainSource.getFluid();
    }

    @Override
    public int getTankCapacity(int tank) {
        return tank == 0 ? fillTarget.getCapacity() : drainSource.getCapacity();
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return tank == 0 ? fillTarget.isFluidValid(stack) : drainSource.isFluidValid(stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (fillTarget.isFluidValid(resource)) {
            return fillTarget.fill(resource, action);
        }
        if (drainSource.isFluidValid(resource)) {
            return drainSource.fill(resource, action);
        }
        return 0;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (!drainSource.getFluid().isEmpty() && drainSource.getFluid().getFluid() == resource.getFluid()) {
            return drainSource.drain(resource.getAmount(), action);
        }
        if (!fillTarget.getFluid().isEmpty() && fillTarget.getFluid().getFluid() == resource.getFluid()) {
            return fillTarget.drain(resource.getAmount(), action);
        }
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        FluidStack fromDrainSource = drainSource.drain(maxDrain, action);
        if (!fromDrainSource.isEmpty()) {
            return fromDrainSource;
        }
        return fillTarget.drain(maxDrain, action);
    }
}
