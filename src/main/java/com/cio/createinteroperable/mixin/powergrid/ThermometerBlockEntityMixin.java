package com.cio.createinteroperable.mixin.powergrid;

import com.cio.createinteroperable.compat.CeeDeviceTemperature;
import com.cio.createinteroperable.compat.ElectroEnergeticsCompat;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.patryk3211.powergrid.electricity.base.ThermalBehaviour;
import org.patryk3211.powergrid.equipment.thermometer.ThermometerBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets Power Grid's Thermometer read Create: Electro Energetics devices
 * (transformers, voltage regulators, resistors, ...).
 * <p>
 * PG's {@code ThermometerBlockEntity#temperature()} only understands a
 * {@link ThermalBehaviour} on the block it faces; CEE devices have none —
 * their heat is a plain {@code temp} field on a server-only simulated device
 * (see {@link CeeDeviceTemperature}). So when there is no ThermalBehaviour,
 * the server reads the CEE device instead. The client can't see server-side
 * devices, and the Thermometer's needle/goggle tooltip run client-side, so the
 * server-read value is mirrored to the client through this block entity's own
 * sync ({@code write}/{@code read} with {@code clientPacket}), re-sent
 * whenever it moves by a degree.
 * <p>
 * String-targeted: PG is a soft dependency. The CEE helper is only ever
 * touched behind {@link ElectroEnergeticsCompat#present()}.
 */
@Mixin(targets = "org.patryk3211.powergrid.equipment.thermometer.ThermometerBlockEntity")
public abstract class ThermometerBlockEntityMixin {
    @Unique
    private static final String createinteroperable$KEY = "CioCeeTemperature";

    /** Last CEE reading (server: live; client: last synced), NaN = none. */
    @Unique
    private float createinteroperable$ceeTemp = Float.NaN;

    /** Value last pushed to clients (server only). */
    @Unique
    private float createinteroperable$sentTemp = Float.NaN;

    @Inject(method = "temperature()F", at = @At("HEAD"), cancellable = true)
    private void createinteroperable$ceeTemperature(CallbackInfoReturnable<Float> cir) {
        BlockEntity self = (BlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null) {
            return;
        }
        if (level instanceof ServerLevel serverLevel && ElectroEnergeticsCompat.present()) {
            Direction facing = self.getBlockState().getValue(ThermometerBlock.FACING);
            BlockPos target = self.getBlockPos().relative(facing);
            float reading = Float.NaN;
            if (BlockEntityBehaviour.get(level, target, ThermalBehaviour.TYPE) == null) {
                float heat = CeeDeviceTemperature.read(serverLevel, target);
                if (!Float.isNaN(heat)) {
                    reading = ThermalBehaviour.getAmbientTemperature(level, target)
                            + heat / CeeDeviceTemperature.HEAT_PER_DEGREE;
                }
            }
            createinteroperable$ceeTemp = reading;
            boolean changed = Float.isNaN(reading) != Float.isNaN(createinteroperable$sentTemp)
                    || Math.abs(reading - createinteroperable$sentTemp) >= 1.0f;
            if (changed) {
                createinteroperable$sentTemp = reading;
                ((SmartBlockEntity) (Object) this).sendData();
            }
        }
        if (!Float.isNaN(createinteroperable$ceeTemp)) {
            cir.setReturnValue(createinteroperable$ceeTemp);
        }
    }

    @Inject(method = "write(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;Z)V",
            at = @At("TAIL"))
    private void createinteroperable$writeCeeTemp(CompoundTag tag, HolderLookup.Provider registries,
                                                  boolean clientPacket, CallbackInfo ci) {
        if (clientPacket && !Float.isNaN(createinteroperable$ceeTemp)) {
            tag.putFloat(createinteroperable$KEY, createinteroperable$ceeTemp);
        }
    }

    @Inject(method = "read(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;Z)V",
            at = @At("TAIL"))
    private void createinteroperable$readCeeTemp(CompoundTag tag, HolderLookup.Provider registries,
                                                 boolean clientPacket, CallbackInfo ci) {
        if (clientPacket) {
            createinteroperable$ceeTemp = tag.contains(createinteroperable$KEY)
                    ? tag.getFloat(createinteroperable$KEY) : Float.NaN;
        }
    }
}
