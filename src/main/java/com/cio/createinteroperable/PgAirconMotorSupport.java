package com.cio.createinteroperable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.patryk3211.powergrid.electricity.base.ElectricBlock;

/**
 * Isolates {@link AirconMotorAssembly}'s one Power Grid-specific step (wire
 * connections need an explicit refresh after a PG block's blockstate changes
 * underneath them) — same class-loading isolation reasoning as
 * {@code ColdSweatWorldTemp}/{@code CrayfishClient}/every other "isolated
 * glue" class in this codebase (see {@code cio-context}'s own build-
 * environment notes): {@link AirconMotorAssembly} is always loaded
 * regardless of whether Power Grid is installed (once the fan/venters are
 * backend-agnostic, {@code tryAssemble} can run in a CEE-only, Power-Grid-
 * absent world), so it must never reference {@link ElectricBlock} or
 * {@link AirconMotorBottomBlock} directly in its own bytecode — that would
 * force the JVM to resolve those Power-Grid-only types the moment
 * {@code tryAssemble} is verified/invoked, even on a run where the block
 * being paired is actually the CEE variant. Routing through this dedicated
 * class defers that resolution to the moment THIS class's own method
 * actually runs, which {@link AirconMotorAssembly} only ever calls from
 * inside a {@code PowerGridCompat.present()}-gated branch.
 */
final class PgAirconMotorSupport {
    private PgAirconMotorSupport() {
    }

    /** Refreshes wire connections at {@code pos} if (and only if) the block there is genuinely the Power Grid Aircon Motor bottom — a no-op otherwise. Caller must already have confirmed {@code PowerGridCompat.present()} before calling this at all. */
    static void refreshIfPgBottom(Level level, BlockPos pos, Block block) {
        if (block instanceof AirconMotorBottomBlock) {
            ElectricBlock.refreshConnectionEntities(level, pos);
        }
    }
}
