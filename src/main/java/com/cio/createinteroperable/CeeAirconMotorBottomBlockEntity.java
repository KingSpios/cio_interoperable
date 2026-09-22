package com.cio.createinteroperable;

import com.cio.createinteroperable.compat.ColdSweatCompat;
import com.cio.createinteroperable.compat.ColdSweatWorldTemp;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Electro Energetics twin of {@link AirconMotorBottomBlockEntity} — same
 * dynamic-resistance load, same Off/Low/Mid/Max slider, same flat-rate gas
 * production/hot_air-return/water-output mechanics, same overvoltage smoke/
 * explode thresholds, ported near-verbatim (matching this codebase's own
 * {@code CeeDebRectifierBlockEntity}/{@code DebRectifierBlockEntity}
 * precedent for why CPG/CEE pairs duplicate rather than share this kind of
 * logic — see that class's own doc) rather than extending the PG class
 * directly, since this one extends Create's {@link SmartBlockEntity} (no
 * Power Grid type anywhere in its hierarchy) and drives its single load
 * through {@link AirconCeeDevice} instead of a PG {@code SwitchedWire}.
 * <p>
 * Implements {@link AirconMotorBottomInfo} — the exact same interface the PG
 * variant implements — so {@link AirconMotorTopBlockEntity} (the fan) and
 * {@link AirconVenterBlockEntity} (the venters) work identically on top
 * of/nearby either variant with zero duplication of their own.
 */
public class CeeAirconMotorBottomBlockEntity extends SmartBlockEntity
        implements IHaveGoggleInformation, AirconMotorBottomInfo {
    private static final float OPTIMAL_VOLTS = 120f;
    /** Same wire-safety reasoning as {@code AirconMotorBottomBlockEntity#MIN_RESISTANCE_OHMS} — CEE has its own real cable thermal model with its own numbers, but this project has no decompiled facts about it yet (unlike Power Grid's, confirmed by decompiling the real jar); reusing the same 50 A worst-case-current target here is a deliberately conservative placeholder pending that same real analysis on the CEE side. */
    private static final float MIN_RESISTANCE_OHMS = OPTIMAL_VOLTS / 50f;

    private static final int OFF_INDEX = 0;
    private static final float[] SETTING_RESISTANCE = {24f, 24f, 12f, 6f};
    private static final float[] SETTING_DROP_C = {0f, 6f, 12f, 24f};
    private static final String[] SETTING_LABELS = {
            "Off", "Low " + '❄', "Mid " + '❄' + '❄', "Max " + '❄' + '❄' + '❄'
    };
    private static final float[] SETTING_HUM_FRACTION = {0f, 0.25f, 0.5f, 1f};

    private static final float AMBIENT_NEUTRAL_C = 20f;
    private static final float WATTAGE_INCREASE_PER_DEGREE_ABOVE_NEUTRAL = 0.03f;
    private static final float WATTAGE_INCREASE_PER_COOLED_BLOCK = 0.002f;
    private static final int VENTER_SCAN_INTERVAL_TICKS = 20;
    private static final int VENTER_SCAN_CHUNK_RADIUS = 2;

    private static final int BASE_COLD_AIR_RATE = 250;
    private static final int MAX_HOT_AIR_CONSUMPTION = 250;
    private static final int HOT_AIR_PER_WATER = 4;
    private static final int GAS_TANK_CAPACITY_MB = 250;
    private static final int WATER_TANK_CAPACITY_MB = 250;
    private static final int HOT_AIR_SUPPLY_GRACE_TICKS = 40;

    private long lastHotAirFillTick = Long.MIN_VALUE / 2;

    private final FluidTank coldAirTank = new FluidTank(GAS_TANK_CAPACITY_MB) {
        @Override
        public boolean isFluidValid(FluidStack stack) {
            return stack.getFluid() == CIOFluids.COLD_AIR_STILL.get();
        }
    };
    private final FluidTank hotAirIntakeTank = new FluidTank(GAS_TANK_CAPACITY_MB) {
        @Override
        public boolean isFluidValid(FluidStack stack) {
            return stack.getFluid() == CIOFluids.HOT_AIR_STILL.get();
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            int filled = super.fill(resource, action);
            if (filled > 0 && action.execute() && level != null) {
                lastHotAirFillTick = level.getGameTime();
            }
            return filled;
        }
    };
    private final FluidTank waterTank = new FluidTank(WATER_TANK_CAPACITY_MB) {
        @Override
        public boolean isFluidValid(FluidStack stack) {
            return stack.getFluid() == Fluids.WATER;
        }
    };

    @Override
    public boolean isReceivingHotAirSupply() {
        return level != null && level.getGameTime() - lastHotAirFillTick <= HOT_AIR_SUPPLY_GRACE_TICKS;
    }

    private static final float EFFICIENCY_SMOOTHING = 0.08f;

    /** Looked up lazily by position, same "no stored reference survives a reload" pattern {@code CeeRedstoneSwitchBlockEntity}/{@code CeeDebRectifierBlockEntity} already use in this codebase. */
    private AirconCeeDevice ceeDevice;
    private ScrollValueBehaviour powerSetting;
    private float voltage = 0f;
    private boolean reversed = false;
    private int cachedCooledRoomSize = 0;
    private long cooledRoomSizeComputedAtTick = -VENTER_SCAN_INTERVAL_TICKS;
    private float efficiency = 0f;
    private float smoothedPerformance = 0f;
    private float smoothedHumIntensity = 0f;

    public CeeAirconMotorBottomBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        powerSetting = new PowerSettingScrollValueBehaviour(
                Component.translatable("createinteroperable.aircon_power_setting"), this, new PowerSettingSlot())
                .between(0, 3)
                .withFormatter(i -> SETTING_LABELS[Mth.clamp(i, 0, 3)]);
        behaviours.add(powerSetting);
    }

    private static class PowerSettingScrollValueBehaviour extends ScrollValueBehaviour {
        PowerSettingScrollValueBehaviour(Component label, SmartBlockEntity be, ValueBoxTransform slot) {
            super(label, be, slot);
        }

        @Override
        public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
            return new ValueSettingsBoard(label, max, 1, ImmutableList.of(Component.literal("Cooling")),
                    new ValueSettingsFormatter(vs -> Component.literal(SETTING_LABELS[Mth.clamp(vs.value(), 0, 3)])));
        }
    }

    private int getPowerSettingIndex() {
        return powerSetting == null ? SETTING_RESISTANCE.length - 1 : Mth.clamp(powerSetting.getValue(), 0, 3);
    }

    private boolean isOff() {
        return getPowerSettingIndex() == OFF_INDEX;
    }

    @Override
    public boolean isPoweredOn() {
        return !isOff();
    }

    @Override
    public float getSettingDropC() {
        return SETTING_DROP_C[getPowerSettingIndex()];
    }

    @Override
    public float getSettingIntensityFraction() {
        return SETTING_HUM_FRACTION[getPowerSettingIndex()];
    }

    private float currentResistance() {
        float base = SETTING_RESISTANCE[getPowerSettingIndex()];
        float ambientC = ambientTemperatureC();
        float hotExcess = Math.max(0f, ambientC - AMBIENT_NEUTRAL_C);
        float tempFactor = 1f + hotExcess * WATTAGE_INCREASE_PER_DEGREE_ABOVE_NEUTRAL;
        float roomFactor = 1f + totalCooledRoomSize() * WATTAGE_INCREASE_PER_COOLED_BLOCK;
        return Math.max(base / (tempFactor * roomFactor), MIN_RESISTANCE_OHMS);
    }

    private int totalCooledRoomSize() {
        if (level == null) {
            return 0;
        }
        long gameTime = level.getGameTime();
        if (gameTime - cooledRoomSizeComputedAtTick < VENTER_SCAN_INTERVAL_TICKS) {
            return cachedCooledRoomSize;
        }
        cooledRoomSizeComputedAtTick = gameTime;
        int total = 0;
        ChunkPos centerChunk = new ChunkPos(worldPosition);
        for (int x = -VENTER_SCAN_CHUNK_RADIUS; x <= VENTER_SCAN_CHUNK_RADIUS; x++) {
            for (int z = -VENTER_SCAN_CHUNK_RADIUS; z <= VENTER_SCAN_CHUNK_RADIUS; z++) {
                int chunkX = centerChunk.x + x;
                int chunkZ = centerChunk.z + z;
                if (!level.hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                ChunkAccess chunk = level.getChunk(chunkX, chunkZ);
                for (BlockPos bePos : chunk.getBlockEntitiesPos()) {
                    if (chunk.getBlockEntity(bePos) instanceof AirconVenterBlockEntity venter) {
                        total += venter.getCooledRoomSize();
                    }
                }
            }
        }
        cachedCooledRoomSize = total;
        return total;
    }

    private float ambientTemperatureC() {
        if (level == null) {
            return AMBIENT_NEUTRAL_C;
        }
        if (ColdSweatCompat.present()) {
            return (float) ColdSweatWorldTemp.getWorldTemperatureC(level, worldPosition);
        }
        return BrassHeaterBlockEntity.ambientTemperatureC(level, worldPosition);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) {
            return;
        }
        if (level.isClientSide) {
            // No PG ElectricBlockEntity hook to call this for us here (this
            // class extends SmartBlockEntity directly) — same
            // "tickAudio() from the client branch of tick()" pattern
            // CeeRedstoneSwitchBlockEntity already uses in this codebase.
            tickAudio();
        } else {
            serverTick();
        }
    }

    /**
     * The CEE analogue of {@code AirconMotorBottomBlockEntity#electricalTick}
     * — same math throughout, just pushing/reading through
     * {@link AirconCeeDevice} (looked up lazily by position, same pattern
     * {@code CeeRedstoneSwitchBlockEntity} uses) instead of a PG
     * {@code SwitchedWire}/terminal pair.
     */
    private void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (ceeDevice == null || !ceeDevice.isValid()) {
            ceeDevice = DevicesSavedData.load(serverLevel).getDevice(worldPosition, AirconCeeDevice.class);
        }
        boolean off = isOff();
        if (ceeDevice != null) {
            ceeDevice.configure(!off, currentResistance());
            double potentialDifference = ceeDevice.getIntakePotentialDifference();
            voltage = (float) Math.abs(potentialDifference);
            reversed = potentialDifference < 0;
        } else {
            voltage = 0f;
            reversed = false;
        }
        efficiency = off ? 0f : Mth.clamp(voltage / OPTIMAL_VOLTS, 0f, 1f);
        smoothedPerformance += (efficiency - smoothedPerformance) * EFFICIENCY_SMOOTHING;
        float humTarget = efficiency * SETTING_HUM_FRACTION[getPowerSettingIndex()];
        smoothedHumIntensity += (humTarget - smoothedHumIntensity) * EFFICIENCY_SMOOTHING;

        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof CeeAirconMotorBottomBlock)) {
            return;
        }

        if (tickOvervoltage(serverLevel)) {
            return;
        }

        int coldProduced = Math.round(BASE_COLD_AIR_RATE * efficiency);
        if (coldProduced > 0) {
            coldAirTank.fill(new FluidStack(CIOFluids.COLD_AIR_STILL.get(), coldProduced), IFluidHandler.FluidAction.EXECUTE);
        }

        int desiredConsume = Math.round(MAX_HOT_AIR_CONSUMPTION * efficiency);
        if (desiredConsume > 0) {
            FluidStack drained = hotAirIntakeTank.drain(desiredConsume, IFluidHandler.FluidAction.EXECUTE);
            if (!drained.isEmpty()) {
                int waterProduced = drained.getAmount() / HOT_AIR_PER_WATER;
                if (waterProduced > 0) {
                    waterTank.fill(new FluidStack(Fluids.WATER, waterProduced), IFluidHandler.FluidAction.EXECUTE);
                }
            }
        }

        if ((level.getGameTime() & 3) == 0) {
            notifyUpdate();
        }
    }

    private static final float SMOKE_THRESHOLD_VOLTS = 149f;
    private static final float EXPLODE_THRESHOLD_VOLTS = 160f;

    private boolean tickOvervoltage(ServerLevel serverLevel) {
        if (voltage >= EXPLODE_THRESHOLD_VOLTS) {
            explode(serverLevel);
            return true;
        }
        if (voltage >= SMOKE_THRESHOLD_VOLTS) {
            tickSmoke(serverLevel);
        }
        return false;
    }

    private void tickSmoke(ServerLevel serverLevel) {
        float dangerFraction = Mth.clamp(
                (voltage - SMOKE_THRESHOLD_VOLTS) / (EXPLODE_THRESHOLD_VOLTS - SMOKE_THRESHOLD_VOLTS), 0f, 1f);
        int interval = Math.max(2, Math.round(24 * (1f - dangerFraction)) + 2);
        if ((serverLevel.getGameTime() + worldPosition.hashCode()) % interval != 0) {
            return;
        }
        int count = 1 + Math.round(dangerFraction * 3);
        for (int i = 0; i < count; i++) {
            double x = worldPosition.getX() + 0.5 + (serverLevel.random.nextDouble() - 0.5) * 0.6;
            double y = worldPosition.getY() + 1.0;
            double z = worldPosition.getZ() + 0.5 + (serverLevel.random.nextDouble() - 0.5) * 0.6;
            double riseSpeed = 0.02 + dangerFraction * 0.03;
            serverLevel.sendParticles(ParticleTypes.SMOKE, x, y, z, 0, 0.0, riseSpeed, 0.0, 1.0);
        }
    }

    private void explode(ServerLevel serverLevel) {
        BlockPos bottomPos = worldPosition.immutable();
        BlockPos topPos = bottomPos.above();
        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                bottomPos.getX() + 0.5, bottomPos.getY() + 1.0, bottomPos.getZ() + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
        // Quartered along with every other aircon_motor sound (see
        // #tickAudio's own comment) — "way too loud" per direct feedback.
        serverLevel.playSound(null, bottomPos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS,
                4.0f * 0.25f, 0.9f + serverLevel.random.nextFloat() * 0.2f);
        serverLevel.removeBlock(bottomPos, false);
        if (serverLevel.getBlockState(topPos).getBlock() instanceof AirconMotorTopBlock) {
            serverLevel.removeBlock(topPos, false);
        }
    }

    /** Same hum call shape as the PG variant — but through {@code SoundScapes} is a PG-utility class ({@code org.patryk3211.powergrid.utility.sound.SoundScapes}), which this CEE-hierarchy class must never reference directly (see this codebase's established CPG/CEE isolation rule). Plays the vanilla-equivalent block sound directly instead. */
    public void tickAudio() {
        if (level == null || !level.isClientSide || smoothedHumIntensity <= 0.01f) {
            return;
        }
        float pitch = 0.65f + 0.35f * smoothedHumIntensity;
        // Quartered per direct feedback that the aircon_motor's sounds were
        // "way too loud" — same 0.25x applied to the PG variant, the top
        // half's own hum, and both electrical variants' failure explosion.
        float volume = (0.2f + 0.8f * smoothedHumIntensity) * 0.25f;
        level.playLocalSound(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
                SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, volume, pitch, false);
    }

    public float getEfficiency() {
        return efficiency;
    }

    @Override
    public float getSmoothedPerformance() {
        return smoothedPerformance;
    }

    public float getVoltage() {
        return voltage;
    }

    public boolean isReversed() {
        return reversed;
    }

    @Nullable
    public IFluidHandler getFluidHandler(@Nullable Direction side) {
        if (side == null) {
            return null;
        }
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof CeeAirconMotorBottomBlock)) {
            return null;
        }
        if (side == Direction.DOWN) {
            return waterTank;
        }
        Direction facing = state.getValue(CeeAirconMotorBottomBlock.FACING);
        Direction coldOut = rotate(reversed ? Direction.EAST : Direction.WEST, facing);
        if (side == coldOut) {
            return coldAirTank;
        }
        Direction hotIn = rotate(reversed ? Direction.WEST : Direction.EAST, facing);
        if (side == hotIn) {
            return hotAirIntakeTank;
        }
        return null;
    }

    /** Same rotation convention as {@code AirconMotorBottomBlock#rotate} — duplicated locally since that method is package-visible to the PG class only in spirit (both live in this same root package, but this keeps the CEE class self-contained, matching the rest of this file's "no cross-references into the PG class" discipline). */
    private static Direction rotate(Direction base, Direction facing) {
        Direction d = base;
        int steps = switch (facing) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        for (int i = 0; i < steps; i++) {
            d = d.getClockWise();
        }
        return d;
    }

    private static int angleDegrees(Direction facing) {
        return switch (facing) {
            case NORTH -> 0;
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }

    private static Vec3 rotateY(Vec3 base, int angle) {
        double dx = base.x - 0.5, dz = base.z - 0.5;
        return switch (angle) {
            case 90 -> new Vec3(0.5 - dz, base.y, 0.5 + dx);
            case 180 -> new Vec3(0.5 - dx, base.y, 0.5 - dz);
            case 270 -> new Vec3(0.5 + dz, base.y, 0.5 - dx);
            default -> base;
        };
    }

    private static final Vec3 POWER_SLOT_BASE = VecHelper.voxelSpace(8, 8, 15.1);

    private static class PowerSettingSlot extends ValueBoxTransform {
        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            if (!(state.getBlock() instanceof CeeAirconMotorBottomBlock)) {
                return POWER_SLOT_BASE;
            }
            return rotateY(POWER_SLOT_BASE, angleDegrees(state.getValue(CeeAirconMotorBottomBlock.FACING)));
        }

        @Override
        public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
            int angle = state.getBlock() instanceof CeeAirconMotorBottomBlock
                    ? angleDegrees(state.getValue(CeeAirconMotorBottomBlock.FACING)) : 0;
            TransformStack.of(ms).rotateYDegrees(180 + angle);
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.text("AC Unit Motor (bottom, CEE)")
                .style(ChatFormatting.WHITE)
                .forGoggles(tooltip);

        BlockState state = getBlockState();
        boolean assembled = state.getBlock() instanceof CeeAirconMotorBottomBlock
                && state.getValue(CeeAirconMotorBottomBlock.ASSEMBLED);
        CreateLang.text("Assembled: ")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.text(assembled ? "Yes" : "No")
                        .style(assembled ? ChatFormatting.GREEN : ChatFormatting.RED))
                .forGoggles(tooltip, 1);

        boolean overvoltage = voltage >= SMOKE_THRESHOLD_VOLTS;
        CreateLang.text("Voltage: ")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.number(Math.round(voltage))
                        .text(" V" + (reversed ? " (reversed polarity)" : ""))
                        .style(overvoltage ? ChatFormatting.RED : ChatFormatting.AQUA))
                .forGoggles(tooltip, 1);

        boolean off = isOff();
        CreateLang.text("Setting: ")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.text(SETTING_LABELS[getPowerSettingIndex()])
                        .text(off ? " (power cut)" : " (venter cooling ceiling: " + Math.round(getSettingDropC()) + "°C)")
                        .style(off ? ChatFormatting.DARK_GRAY : ChatFormatting.BLUE))
                .forGoggles(tooltip, 1);

        float resistance = currentResistance();
        float watts = off || resistance <= 0f ? 0f : voltage * voltage / resistance;
        CreateLang.text("Resistance: ")
                .style(ChatFormatting.GRAY)
                .add(off ? CreateLang.text("Disconnected").style(ChatFormatting.DARK_GRAY)
                        : CreateLang.number(Math.round(resistance * 10) / 10f).text(" Ω").style(ChatFormatting.GOLD))
                .forGoggles(tooltip, 1);

        CreateLang.text("Power Draw: ")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.number(Math.round(watts)).text(" W").style(ChatFormatting.GOLD))
                .forGoggles(tooltip, 1);

        CreateLang.text("Efficiency: ")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.number(Math.round(efficiency * 100)).text("%").style(ChatFormatting.GREEN))
                .forGoggles(tooltip, 1);

        CreateLang.text("Cold Air Out: ")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.number(Math.round(BASE_COLD_AIR_RATE * efficiency))
                        .text(" / " + BASE_COLD_AIR_RATE + " mB/t")
                        .style(ChatFormatting.AQUA))
                .forGoggles(tooltip, 1);
        CreateLang.builder()
                .add(CreateLang.number(coldAirTank.getFluidAmount())
                        .text(" / " + coldAirTank.getCapacity() + " mB buffered")
                        .style(ChatFormatting.DARK_GRAY))
                .forGoggles(tooltip, 1);

        boolean receivingHotAir = isReceivingHotAirSupply();
        CreateLang.text("Hot Air In: ")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.number(Math.round(MAX_HOT_AIR_CONSUMPTION * efficiency))
                        .text(" / " + MAX_HOT_AIR_CONSUMPTION + " mB/t max")
                        .style(ChatFormatting.GOLD))
                .forGoggles(tooltip, 1);
        CreateLang.builder()
                .add(CreateLang.text(receivingHotAir ? "Pipe delivering" : "Nothing recently received")
                        .style(receivingHotAir ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY))
                .forGoggles(tooltip, 1);

        int waterPercent = waterTank.getCapacity() > 0
                ? Math.round(waterTank.getFluidAmount() / (float) waterTank.getCapacity() * 100) : 0;
        CreateLang.text("Water Buffer: ")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.number(waterTank.getFluidAmount())
                        .text(" / " + waterTank.getCapacity() + " mB (" + waterPercent + "%)")
                        .style(ChatFormatting.BLUE))
                .forGoggles(tooltip, 1);

        if (overvoltage) {
            CreateLang.text("Warning: ")
                    .style(ChatFormatting.RED)
                    .add(CreateLang.text("Overvoltage — smoking").style(ChatFormatting.RED))
                    .forGoggles(tooltip, 1);
        }

        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("ColdAirTank", coldAirTank.writeToNBT(registries, new CompoundTag()));
        tag.put("HotAirIntakeTank", hotAirIntakeTank.writeToNBT(registries, new CompoundTag()));
        tag.put("WaterTank", waterTank.writeToNBT(registries, new CompoundTag()));
        tag.putFloat("Voltage", voltage);
        tag.putBoolean("Reversed", reversed);
        tag.putFloat("Efficiency", efficiency);
        tag.putFloat("SmoothedPerformance", smoothedPerformance);
        tag.putFloat("SmoothedHumIntensity", smoothedHumIntensity);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        coldAirTank.readFromNBT(registries, tag.getCompound("ColdAirTank"));
        hotAirIntakeTank.readFromNBT(registries, tag.getCompound("HotAirIntakeTank"));
        waterTank.readFromNBT(registries, tag.getCompound("WaterTank"));
        voltage = tag.getFloat("Voltage");
        reversed = tag.getBoolean("Reversed");
        efficiency = tag.getFloat("Efficiency");
        smoothedPerformance = tag.getFloat("SmoothedPerformance");
        smoothedHumIntensity = tag.getFloat("SmoothedHumIntensity");
    }
}
