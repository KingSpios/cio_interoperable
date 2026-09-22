package com.cio.createinteroperable;

import com.cio.createinteroperable.compat.PowerGridCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Wrench-assembly for the 2-tall Aircon Motor: place {@link AirconMotorTopBlock}
 * directly on either {@link AirconMotorBottomBlock} (Power Grid-wired) or the
 * CEE-wired {@code CeeAirconMotorBottomBlock}, then wrench either half —
 * matches this project's own "wrench any half" convention
 * ({@code InteroperableCoreBlock}, {@code RadiatorAssembly}). Unlike
 * {@code InteroperableCoreBlock}'s pairing (which converts placeholder
 * blocks into a different type), both halves here are already the correct
 * block: assembly only flips {@link CIOProperties#AIRCON_ASSEMBLED} on each,
 * so top/bottom stay findable afterward by a fixed 1-block relative offset
 * (see {@code AirconMotorTopBlockEntity#getPerformance}) — no cross-link
 * position needs to be stored, unlike the Multi Radiator's variable-length
 * run.
 * <p>
 * <b>Always loaded regardless of which (if either) of Power Grid/Electro
 * Energetics is installed</b> — the top half registers unconditionally (see
 * {@code CIOBlocks#AIRCON_MOTOR_TOP}), so this class must never reference
 * either mod's own types directly in its own bytecode, even behind a runtime
 * {@code present()} check in the SAME method (this project's own established
 * rule — see {@code cio-context}'s build-environment notes on client-dist
 * classloading, which applies identically to mod-presence gating): the two
 * bottom variants are recognized only via the zero-dependency
 * {@link AirconMotorBottomMarker} interface, and the one genuinely Power
 * Grid-specific step (wire-connection refresh) is routed through
 * {@link PgAirconMotorSupport}, called only from behind
 * {@link PowerGridCompat#present()}.
 */
public final class AirconMotorAssembly {
    private AirconMotorAssembly() {
    }

    /** @return whether {@code pos} (a just-wrenched top or bottom half) formed a valid pair. No world changes on failure. */
    public static boolean tryAssemble(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockPos bottomPos;
        BlockPos topPos;
        if (state.getBlock() instanceof AirconMotorBottomMarker) {
            bottomPos = pos;
            topPos = pos.above();
        } else if (state.getBlock() instanceof AirconMotorTopBlock) {
            topPos = pos;
            bottomPos = pos.below();
        } else {
            return false;
        }

        BlockState bottomState = level.getBlockState(bottomPos);
        BlockState topState = level.getBlockState(topPos);
        if (!(bottomState.getBlock() instanceof AirconMotorBottomMarker)
                || !(topState.getBlock() instanceof AirconMotorTopBlock)) {
            return false;
        }
        // BlockStateProperties.HORIZONTAL_FACING — a plain vanilla property,
        // safe to read off either bottom variant's state with no type-
        // specific casting, since both declare it under that exact name.
        if (bottomState.getValue(BlockStateProperties.HORIZONTAL_FACING)
                != topState.getValue(BlockStateProperties.HORIZONTAL_FACING)) {
            return false;
        }

        if (!level.isClientSide) {
            level.setBlockAndUpdate(bottomPos, bottomState.setValue(CIOProperties.AIRCON_ASSEMBLED, true));
            level.setBlockAndUpdate(topPos, topState.setValue(CIOProperties.AIRCON_ASSEMBLED, true));
            if (PowerGridCompat.present()) {
                Block bottomBlock = bottomState.getBlock();
                PgAirconMotorSupport.refreshIfPgBottom(level, bottomPos, bottomBlock);
            }
        }
        return true;
    }
}
