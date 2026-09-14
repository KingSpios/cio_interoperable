package com.cio.createinteroperable.mixin.coldsweat;

import com.cio.createinteroperable.CreateInteroperable;
import com.cio.createinteroperable.RadiatorValveNorthBlockEntity;
import com.mojang.datafixers.util.Pair;
import com.momosoftworks.coldsweat.util.world.WorldHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes Cold Sweat's own real {@code WorldHelper.getInsulationAt} — the
 * lookup every position-based ambient-temperature query
 * ({@code getRoughTemperatureAt}/{@code getTemperatureAt}, and through them a
 * real Cold Sweat Thermometer, and now PG's own {@code ThermalBehaviour} via
 * {@code ThermalBehaviourMixin}) uses to fold in "am I standing somewhere a
 * Hearth is heating/cooling" — also recognize our own Steam Hearth's room,
 * not just Cold Sweat's native {@code HearthBlockEntity}.
 * <p>
 * Confirmed by reading the real source: {@code getInsulationAt} scans nearby
 * chunks' block entities with a hardcoded
 * {@code if (be instanceof HearthBlockEntity hearth && hearth.getPathLookup().containsKey(pos))}
 * check — a literal class check, not an interface or tag, so there was no
 * non-mixin way for a third-party heat source to participate at all. That's
 * exactly why our own Steam Hearth's room-wide warmth (a per-entity
 * {@code WarmthTempModifier} applied directly in
 * {@code RadiatorValveNorthBlockEntity#tickHearthEffect}) was invisible to
 * anything that isn't "a specific LivingEntity known to be inside the room" —
 * a Cold Sweat Thermometer, or PG's own Thermometer/Coil (once
 * {@code ThermalBehaviourMixin} started routing their ambient baseline
 * through this same real Cold Sweat calculation), would read the room's raw,
 * un-warmed temperature even while a player standing right there was
 * genuinely being warmed.
 * <p>
 * Fixed by re-running the exact same "scan nearby chunks' block entities"
 * technique Cold Sweat's own method already uses, looking for
 * {@link RadiatorValveNorthBlockEntity} instead, and folding its
 * {@link RadiatorValveNorthBlockEntity#hearthStrengthAt} into the heating
 * side of the returned pair (cooling is untouched — a Steam Hearth never
 * cools). This is intentionally the SAME {@code chunkRadius} the original
 * call already searches with, so it costs one extra {@code instanceof} check
 * per block entity Cold Sweat was already visiting, not a second independent
 * scan of a different area.
 */
@Mixin(WorldHelper.class)
public abstract class WorldHelperMixin {
    @Inject(
            method = "getInsulationAt(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;I)Lcom/mojang/datafixers/util/Pair;",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void createinteroperable$steamHearthInsulation(Level level, BlockPos pos, int chunkRadius,
                                                                    CallbackInfoReturnable<Pair<Integer, Integer>> cir) {
        if (!createinteroperable$logged) {
            createinteroperable$logged = true;
            // Same reasoning as ThermalBehaviourMixin's own one-time log —
            // this line existing in the log at all is proof the mixin
            // actually applied, regardless of whether a Steam Hearth happens
            // to be nearby on this particular call.
            CreateInteroperable.LOGGER.info(
                    "[Create: Interoperable] Cold Sweat WorldHelper#getInsulationAt mixin active (Steam Hearth room now visible to position-based queries)");
        }
        int extraHeating = 0;
        ChunkPos centerChunk = new ChunkPos(pos);
        for (int x = -chunkRadius; x <= chunkRadius && extraHeating < 3; x++) {
            for (int z = -chunkRadius; z <= chunkRadius && extraHeating < 3; z++) {
                int chunkX = centerChunk.x + x;
                int chunkZ = centerChunk.z + z;
                if (!level.hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                ChunkAccess chunk = level.getChunk(chunkX, chunkZ);
                for (BlockPos bePos : chunk.getBlockEntitiesPos()) {
                    if (chunk.getBlockEntity(bePos) instanceof RadiatorValveNorthBlockEntity hearth) {
                        extraHeating = Math.max(extraHeating, hearth.hearthStrengthAt(pos));
                    }
                }
            }
        }
        if (extraHeating <= 0) {
            return;
        }
        Pair<Integer, Integer> original = cir.getReturnValue();
        cir.setReturnValue(Pair.of(original.getFirst(), Math.max(original.getSecond(), extraHeating)));
    }

    @Unique
    private static boolean createinteroperable$logged = false;
}
