package com.cio.createinteroperable.mixin;

import com.cio.createinteroperable.SteamOutletBlock;
import com.simibubi.create.content.fluids.tank.BoilerData;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes Create's real Fluid Tank/Boiler recognize our Steam Outlet as an
 * "attached engine," exactly like a real Steam Engine or Steam Whistle
 * (BoilerData#evaluate itself hardcodes {@code AllBlocks.STEAM_ENGINE.has(...)}
 * / {@code AllBlocks.STEAM_WHISTLE.has(...)} checks with no registry or tag
 * to extend — confirmed by reading the real source — so there is no
 * non-Mixin way to participate in the same counter).
 * <p>
 * Deliberately reuses the SAME counter Steam Engines use
 * ({@code attachedEngines}), not a separate one like Whistles get
 * ({@code attachedWhistles}) — Whistles activate the boiler for free without
 * diluting anything (BoilerData#getEngineEfficiency only divides by
 * attachedEngines), but a Steam Outlet is explicitly meant to compete for
 * the same finite heat/water budget real Steam Engines draw from. Sharing
 * the counter means:
 * <ul>
 *     <li>{@code isActive()} (attachedEngines > 0) becomes true from Outlets
 *     alone — the tank "forms" as a boiler with no Steam Engine present.</li>
 *     <li>{@code getEngineEfficiency(boilerSize)}'s division by
 *     attachedEngines automatically dilutes every real Steam Engine's share
 *     when an Outlet is added, and vice versa — the exact "compromise SU if
 *     Engines do get attached" behavior, achieved with zero extra math.</li>
 * </ul>
 * evaluate() resets attachedEngines to 0 and recounts from scratch every time
 * it runs (triggered by neighbor block changes), so this can't be done by
 * writing the field once from SteamOutletBlockEntity — it would just get
 * wiped by the next real evaluate() call. Injecting at the TAIL of the same
 * method, after Create's own recount, is the only way our addition survives.
 */
@Mixin(BoilerData.class)
public abstract class BoilerDataMixin {
    @Shadow
    public int attachedEngines;

    @Inject(
            method = "evaluate(Lcom/simibubi/create/content/fluids/tank/FluidTankBlockEntity;)Z",
            at = @At("RETURN"),
            cancellable = true
    )
    private void createinteroperable$countSteamOutlets(FluidTankBlockEntity controller,
                                                        CallbackInfoReturnable<Boolean> cir) {
        Level level = controller.getLevel();
        if (level == null) {
            return;
        }

        BlockPos controllerPos = controller.getBlockPos();
        FluidTankBlockEntityAccessor accessor = (FluidTankBlockEntityAccessor) controller;
        int width = accessor.createinteroperable$getWidth();
        int height = accessor.createinteroperable$getHeight();

        int outlets = 0;
        for (int yOffset = 0; yOffset < height; yOffset++) {
            for (int xOffset = 0; xOffset < width; xOffset++) {
                for (int zOffset = 0; zOffset < width; zOffset++) {
                    BlockPos pos = controllerPos.offset(xOffset, yOffset, zOffset);
                    BlockState tankState = level.getBlockState(pos);
                    if (!FluidTankBlock.isTank(tankState)) {
                        continue;
                    }
                    // Steam Outlets only ever sit directly on top of a tank
                    // cell now (see SteamOutletBlock#canSurvive) — no FACING
                    // check needed, since a SteamOutletBlock found straight
                    // up from a tank cell can only exist there because it's
                    // surviving on THIS tank cell. Interior tank cells never
                    // match here: their "up" neighbor is another tank block,
                    // not open air an Outlet could occupy.
                    BlockPos abovePos = pos.above();
                    BlockState aboveState = level.getBlockState(abovePos);
                    if (aboveState.getBlock() instanceof SteamOutletBlock) {
                        outlets++;
                    }
                }
            }
        }

        if (outlets > 0) {
            this.attachedEngines += outlets;
            cir.setReturnValue(true);
        }
    }
}
