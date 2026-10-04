package com.cio.createinteroperable.mixin.cee;

import com.cio.createinteroperable.compat.CeeDeviceTemperature;
import com.cio.createinteroperable.compat.ColdSweatCompat;
import com.cio.createinteroperable.compat.ColdSweatWorldTemp;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Makes Create: Electro Energetics' component overheating respond to Cold
 * Sweat's temperature AT THE DEVICE'S COORDINATE, the same way Power Grid
 * components already do through {@code ThermalBehaviourMixin} (which points
 * PG's cooling baseline at Cold Sweat's position temperature).
 * <p>
 * CEE has no ambient: a device's {@code temp} is heat units that decay to 0,
 * and {@code ElectricalDevice#handleTemp(level, pos, devices, temp, warn,
 * burn)} — the one place every device's warning particles / burnout is
 * decided — compares that number to fixed limits. It is static and is handed
 * the position, so this shifts the compared {@code temp} by how far Cold
 * Sweat's temperature at {@code pos} is from {@link
 * CeeDeviceTemperature#REFERENCE_AMBIENT_C}: a device in a hot room is that
 * much closer to warning/burning, one in a cold room has extra headroom. The
 * device's stored heat is never modified, so its own simulation is untouched.
 * Equivalent to PG's model (absolute overheat limit, ambient + rise), and
 * consistent with the Thermometer readout ({@code ambient + heat/300}).
 * <p>
 * Inert without Cold Sweat.
 */
@Mixin(targets = "com.george_vi.electroenergetics.foundation.device.ElectricalDevice")
public interface ElectricalDeviceHeatMixin {
    @ModifyVariable(method = "handleTemp", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static float createinteroperable$ambientShift(float temp, Level level, BlockPos pos) {
        if (!ColdSweatCompat.present()) {
            return temp;
        }
        double ambientC = ColdSweatWorldTemp.getWorldTemperatureC(level, pos);
        return temp + (float) ((ambientC - CeeDeviceTemperature.REFERENCE_AMBIENT_C)
                * CeeDeviceTemperature.HEAT_PER_DEGREE);
    }
}
