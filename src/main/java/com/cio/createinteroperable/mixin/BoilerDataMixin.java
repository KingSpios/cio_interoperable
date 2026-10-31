package com.cio.createinteroperable.mixin;

import com.cio.createinteroperable.Lang;
import com.cio.createinteroperable.thermal.RemoteEngineLedger;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import com.cio.createinteroperable.thermal.SteamOutletBlock;
import com.simibubi.create.content.fluids.tank.BoilerData;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * <p>
 * Also carries the {@link RemoteEngineLedger}: Steam Engines mounted on a
 * Steam-Outlet-fed pipe (see {@code thermal.SteamEngineNetwork}) are passed on
 * to the boiler by the Steam Outlet — each one drawing from this boiler is
 * added to the real {@code attachedEngines}, kept current every server tick
 * ({@link #createinteroperable$appliedRemote} is how many are in it). That is
 * the only thing every reader sees: Create's own engine sharing and boiler
 * goggles, and other mods that rework them — Create Aeronautics rebuilds the
 * boiler tooltip ("Kinetic Stress Used", "via N engines") from the raw field,
 * which is why per-method goggle hooks never showed up in game (0.1.151).
 * A pipe engine split across several boilers (or throttled by its outlet's
 * valve) draws only part of an engine, so {@code getEngineEfficiency}'s result
 * is corrected from whole engines to that fractional load.
 */
@Mixin(BoilerData.class)
public abstract class BoilerDataMixin implements RemoteEngineLedger {
    /** Create's private {@code BoilerData.passiveEngineEfficiency} (a constant, so not shadowable). */
    @Unique
    private static final float createinteroperable$PASSIVE_ENGINE_EFFICIENCY = 1 / 8f;

    @Shadow
    public int attachedEngines;

    @Shadow
    public int activeHeat;

    @Shadow
    public abstract boolean isPassive(int boilerSize);

    @Shadow
    public abstract int getMaxHeatLevelForWaterSupply();

    @Shadow
    public abstract int getMaxHeatLevelForBoilerSize(int boilerSize);

    @Unique
    private final Map<BlockPos, Float> createinteroperable$remoteShares = new HashMap<>();

    @Unique
    private final Map<BlockPos, Long> createinteroperable$remoteSeen = new HashMap<>();

    /** Client side only: the server's totals (see FluidTankBoilerSyncMixin); always 0 on the server. */
    @Unique
    private float createinteroperable$syncedLoad;

    @Unique
    private int createinteroperable$syncedCount;

    /** How many pipe-fed engines are currently counted in {@code attachedEngines}. Saved and synced with the boiler. */
    @Unique
    private int createinteroperable$appliedRemote;

    /** Game time as last seen by the boiler's own tick or an engine's refresh. */
    @Unique
    private long createinteroperable$now;

    @Override
    public void createinteroperable$putRemoteShare(BlockPos enginePos, float share, long gameTime) {
        BlockPos key = enginePos.immutable();
        createinteroperable$remoteShares.put(key, share);
        createinteroperable$remoteSeen.put(key, gameTime);
        createinteroperable$now = Math.max(createinteroperable$now, gameTime);
    }

    @Override
    public void createinteroperable$setSyncedRemote(float load, int count) {
        createinteroperable$syncedLoad = load;
        createinteroperable$syncedCount = count;
    }

    @Override
    public float createinteroperable$remoteLoad() {
        createinteroperable$prune();
        float load = createinteroperable$syncedLoad;
        for (float share : createinteroperable$remoteShares.values()) {
            load += share;
        }
        return load;
    }

    @Override
    public int createinteroperable$remoteEngineCount() {
        createinteroperable$prune();
        return createinteroperable$remoteShares.size() + createinteroperable$syncedCount;
    }

    @Override
    public float createinteroperable$effectiveEngines() {
        return attachedEngines - createinteroperable$appliedRemote + createinteroperable$remoteLoad();
    }

    @Unique
    private void createinteroperable$prune() {
        if (createinteroperable$remoteSeen.isEmpty()) {
            return;
        }
        long now = createinteroperable$now;
        createinteroperable$remoteSeen.values().removeIf(seen -> now - seen > EXPIRY_TICKS);
        createinteroperable$remoteShares.keySet().retainAll(createinteroperable$remoteSeen.keySet());
    }

    @Inject(method = "tick(Lcom/simibubi/create/content/fluids/tank/FluidTankBlockEntity;)V", at = @At("HEAD"))
    private void createinteroperable$keepClock(FluidTankBlockEntity controller, CallbackInfo ci) {
        Level level = controller.getLevel();
        if (level == null) {
            return;
        }
        createinteroperable$now = Math.max(createinteroperable$now, level.getGameTime());
        if (level.isClientSide) {
            return;
        }
        // The Steam Outlet passes its pipe-fed engines on to the boiler: keep them
        // in the real engine count, and send the change to clients.
        int count = createinteroperable$remoteEngineCount();
        if (count != createinteroperable$appliedRemote) {
            attachedEngines += count - createinteroperable$appliedRemote;
            createinteroperable$appliedRemote = count;
            controller.notifyUpdate();
        }
    }

    @Inject(method = "write", require = 1, at = @At("RETURN"))
    private void createinteroperable$writePipeEngines(CallbackInfoReturnable<CompoundTag> cir) {
        cir.getReturnValue().putInt("CioPipeEngines", createinteroperable$appliedRemote);
    }

    @Inject(method = "read", require = 1, at = @At("TAIL"))
    private void createinteroperable$readPipeEngines(CompoundTag nbt, int boilerSize, CallbackInfo ci) {
        createinteroperable$appliedRemote = nbt.getInt("CioPipeEngines");
    }

    /** An explicit line under Create's boiler readout for the pipe-fed engines it counts. */
    @Inject(method = "addToGoggleTooltip", require = 1, at = @At("RETURN"))
    private void createinteroperable$pipeEngineLine(List<Component> tooltip, boolean isPlayerSneaking, int boilerSize,
                                                    CallbackInfoReturnable<Boolean> cir) {
        int count = createinteroperable$remoteEngineCount();
        if (!cir.getReturnValueZ() || count == 0) {
            return;
        }
        Lang.translate("thermal.pipe_steam_engines")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.number(count).style(ChatFormatting.GOLD))
                .forGoggles(tooltip, 1);
    }

    /**
     * Create (and any mod adding its own engines to the count, e.g. Aeronautics'
     * vents) computes efficiency from whole engines, pipe-fed ones included.
     * Correct that to their real fractional load: recover the engine count the
     * result was divided by, swap the whole pipe engines for their load.
     */
    @ModifyReturnValue(method = "getEngineEfficiency(I)F", require = 1, at = @At("RETURN"))
    private float createinteroperable$fractionalPipeEngines(float efficiency, @Local(argsOnly = true) int boilerSize) {
        int whole = createinteroperable$appliedRemote;
        if (whole == 0 || efficiency <= 0) {
            return efficiency;
        }
        float load = createinteroperable$remoteLoad();
        if (isPassive(boilerSize)) {
            float trueEngines = createinteroperable$PASSIVE_ENGINE_EFFICIENCY / efficiency - whole + load;
            return trueEngines > 0 ? createinteroperable$PASSIVE_ENGINE_EFFICIENCY / trueEngines : efficiency;
        }
        if (efficiency >= 1) {
            return efficiency;
        }
        int actualHeat = Math.min(activeHeat, Math.min(getMaxHeatLevelForWaterSupply(), getMaxHeatLevelForBoilerSize(boilerSize)));
        if (actualHeat <= 0) {
            return efficiency;
        }
        float trueEngines = actualHeat / efficiency - whole + load;
        return trueEngines <= actualHeat ? 1f : actualHeat / trueEngines;
    }

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

        // Create just recounted from scratch, so no pipe engines are in it any more.
        int pipeEngines = createinteroperable$remoteEngineCount();
        this.attachedEngines += outlets + pipeEngines;
        createinteroperable$appliedRemote = pipeEngines;
        if (outlets > 0 || pipeEngines > 0) {
            cir.setReturnValue(true);
        }
    }
}
