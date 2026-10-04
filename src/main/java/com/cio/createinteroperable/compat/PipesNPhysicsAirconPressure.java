package com.cio.createinteroperable.compat;

import com.cio.createinteroperable.AirconMotorBottomInfo;
import com.cio.createinteroperable.AirconVenterBlockEntity;
import com.cio.createinteroperable.CIOConfig;
import de.devin.pipesnphysics.PipesNPhysicsConfig;
import de.devin.pipesnphysics.engine.boundary.BoundaryColumn;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * What the Aircon Motor tells Pipes n Physics' solver so that it behaves as its own pump.
 * <p>
 * Pipes n Physics only knows pumps as inline pipe blocks, and its only pressure source is a
 * pump's head between its two flanks. The motor is a tank-style machine with two separate ports
 * (cold_air out, hot_air in), so it cannot be declared a pump: the engine would carry the hot air
 * straight through to the cold line. Instead, {@code mixin.pnpengine.FluidPassAirconMixin} adds
 * the motor's head to the branches touching those two ports, using the pump scale PnP already
 * uses (RPM * {@code pumpHeadPerRpm}): the cold port pushes out, the hot port pulls in.
 * <p>
 * Called only from mixins that are gated on Pipes n Physics being present, so this class never
 * loads without it.
 */
public final class PipesNPhysicsAirconPressure {
    private PipesNPhysicsAirconPressure() {
    }

    /**
     * Signed head this column adds to the branch leaving it: positive pushes fluid out of the
     * column (the cold port), negative pulls fluid into it (the hot port), 0 for anything else.
     */
    public static double signedHead(Level level, BoundaryColumn column) {
        Direction face = column.accessFace();
        if (face == null || !column.isFiniteReservoir()) {
            return 0;
        }
        double rpm = CIOConfig.AIRCON_PUMP_RPM.get();
        if (rpm <= 0) {
            return 0;
        }
        if (!(level.getBlockEntity(column.accessPos()) instanceof AirconMotorBottomInfo motor)) {
            return 0;
        }
        double drive = motor.getSmoothedPerformance();
        if (drive <= 0.01) {
            return 0;
        }
        double head = rpm * drive * PipesNPhysicsConfig.PUMP_HEAD_PER_RPM.get();
        if (face == motor.getColdOutSide()) {
            return head;
        }
        if (face == motor.getHotInSide()) {
            return -head;
        }
        return 0;
    }

    /**
     * Whether a port on this block is a machine port that should give at any fill level: the motor
     * and the venters are plumbed connections, and their pressure is meant to move air regardless
     * of how full a one-block tank is.
     */
    public static boolean givesFromAnyLevel(Level level, BlockPos pos, Direction face) {
        if (face == null) {
            return false;
        }
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof AirconMotorBottomInfo || be instanceof AirconVenterBlockEntity;
    }
}
