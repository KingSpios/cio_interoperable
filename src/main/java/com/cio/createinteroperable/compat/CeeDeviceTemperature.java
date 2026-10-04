package com.cio.createinteroperable.compat;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDevice;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads the heat of a Create: Electro Energetics device so Power Grid's
 * Thermometer can display it. CEE's simulated devices (transformers, voltage
 * regulators, resistors, capacitors, relays, fuses, ...) each keep their heat
 * in a plain public {@code temp} field on the server-only
 * {@link SimulatedDevice} — there is no shared interface or getter — so this
 * reads that one field reflectively, cached per device class. A device class
 * without a numeric {@code temp} field simply reads as "no temperature".
 * <p>
 * <b>Must only be referenced when Electro Energetics is present</b> (this
 * class imports its types directly); callers gate on
 * {@link ElectroEnergeticsCompat#present()}.
 */
public final class CeeDeviceTemperature {

    /** Sentinel for "no CEE device with a temperature here". */
    public static final float NONE = Float.NaN;

    /**
     * CEE's {@code temp} is abstract "heat units" (0 = cold, decays a fixed
     * ~33/tick, components warn around 30000 and burn at 40000), not degrees.
     * This converts it for display: 300 heat units per °C above ambient puts a
     * component's warning threshold ~100 °C over ambient, comparable to PG's
     * own overheat range. An invented display scale, not a CEE quantity.
     */
    public static final float HEAT_PER_DEGREE = 300f;

    /**
     * The ambient (°C) at which CEE's fixed heat limits are taken to apply —
     * Cold Sweat temperatures above/below this shift a device toward/away
     * from overheating (see the {@code ElectricalDeviceHeatMixin}).
     */
    public static final float REFERENCE_AMBIENT_C = 20f;

    /** How far a multi-block device is searched for its controller position. */
    private static final int MAX_SEARCH = 24;

    private static final Field NO_FIELD;
    private static final Map<Class<?>, Field> FIELDS = new ConcurrentHashMap<>();

    static {
        try {
            NO_FIELD = CeeDeviceTemperature.class.getDeclaredField("NONE");
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private CeeDeviceTemperature() {
    }

    /**
     * Raw CEE heat units (see {@link #HEAT_PER_DEGREE}) of the CEE device at {@code pos}, or of the device
     * owning the multi-block structure {@code pos} belongs to (the device
     * lives at one part only, e.g. a Voltage Regulator's pole); {@link #NONE}
     * if there is no such device.
     */
    public static float read(ServerLevel level, BlockPos pos) {
        DevicesSavedData devices = DevicesSavedData.load(level);
        SimulatedDevice direct = devices.getDevice(pos);
        if (direct != null) {
            return temperatureOf(direct);
        }
        Block block = level.getBlockState(pos).getBlock();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        queue.add(pos);
        seen.add(pos);
        while (!queue.isEmpty() && seen.size() < MAX_SEARCH) {
            BlockPos cur = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos next = cur.relative(dir);
                if (!seen.add(next) || !level.getBlockState(next).is(block)) {
                    continue;
                }
                SimulatedDevice device = devices.getDevice(next);
                if (device != null) {
                    return temperatureOf(device);
                }
                queue.add(next);
            }
        }
        return NONE;
    }

    private static float temperatureOf(SimulatedDevice device) {
        Field field = FIELDS.computeIfAbsent(device.getClass(), CeeDeviceTemperature::findField);
        if (field == NO_FIELD) {
            return NONE;
        }
        try {
            float value = ((Number) field.get(device)).floatValue();
            return Float.isFinite(value) ? value : NONE;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return NONE;
        }
    }

    private static Field findField(Class<?> type) {
        try {
            Field field = type.getField("temp");
            if (!Modifier.isStatic(field.getModifiers())
                    && (field.getType() == float.class || field.getType() == double.class)) {
                return field;
            }
        } catch (NoSuchFieldException | SecurityException ignored) {
            // falls through to "no temperature"
        }
        return NO_FIELD;
    }
}
