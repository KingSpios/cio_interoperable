package com.cio.createinteroperable;

import com.cio.createinteroperable.compat.ColdSweatCompat;
import com.cio.createinteroperable.compat.ColdSweatWarmthEffect;
import com.simibubi.create.api.effect.OpenPipeEffectHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Makes our steam fluid leak the same white campfire-style smoke the Steam
 * Outlet itself uses (see SteamOutletBlockEntity#tickLeakParticles) when it
 * vents out of a Create pipe network's own open/dangling end — a pipe that's
 * actively carrying steam but whose far end has no receiving block/handler
 * (see Create's real {@code OpenEndedPipe}: an unconnected end just drops
 * whatever isn't a real placeable fluid block, which is exactly what our
 * steam fluid is — see CIOFluids' own doc on why it has no block form).
 * <p>
 * Uses Create's own public, non-mixin extension point for precisely this —
 * {@link OpenPipeEffectHandler} (confirmed by reading Create's real
 * {@code OpenEndedPipe.OpenEndFluidHandler#fill}, which looks up
 * {@code OpenPipeEffectHandler.REGISTRY.get(resource.getFluid())} and calls
 * {@code apply(level, aoe, fluid)} whenever that fluid is pushed into an open
 * end) — the same mechanism Create itself uses for water extinguishing fire,
 * milk clearing potion effects, etc. (see its own {@code AllOpenPipeEffectHandlers}).
 * No mixin needed, and Create is already a hard dependency of this mod.
 * <p>
 * Registered during {@link FMLCommonSetupEvent}, matching the exact timing
 * Create's own {@code AllOpenPipeEffectHandlers.registerDefaults()} uses
 * (inside {@code event.enqueueWork}, after registries have settled) — see
 * Create's {@code Create.init}.
 */
public class CIOOpenPipeEffects {
    /** Same cadence/density/rise-speed as the Steam Outlet's own leak — see SteamOutletBlockEntity#tickLeakParticles. */
    private static final int INTERVAL_TICKS = 8;
    private static final double RISE_SPEED = 0.025;

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(CIOOpenPipeEffects::commonSetup);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> OpenPipeEffectHandler.REGISTRY.register(CIOFluids.STEAM_STILL.get(), CIOOpenPipeEffects::onSteamVented));
    }

    /**
     * @param area the small area Create itself computed just past the open pipe
     *             end (see {@code OpenEndedPipe}'s constructor) — used directly
     *             rather than re-deriving the pipe's own facing/position.
     */
    /** Matches SteamOutletBlockEntity's own leak-warmth numbers — see its class doc for why. */
    private static final int WARMTH_AMPLIFIER = 1;
    private static final int WARMTH_DURATION_TICKS = 20;
    private static final double WARMTH_RADIUS = 2.0;

    private static void onSteamVented(Level level, AABB area, FluidStack fluid) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockPos anchor = BlockPos.containing(area.getCenter());
        if ((serverLevel.getGameTime() + anchor.hashCode()) % INTERVAL_TICKS != 0) {
            return;
        }
        // Same bubble-instead-of-smoke swap as SteamOutletBlockEntity's own
        // leak — a dangling pipe end submerged in water bubbles, it doesn't smoke.
        boolean underwater = serverLevel.getFluidState(anchor).is(FluidTags.WATER);
        int count = 2 + serverLevel.random.nextInt(3);
        for (int i = 0; i < count; i++) {
            double x = area.getCenter().x + (serverLevel.random.nextDouble() - 0.5) * 0.4;
            double y = area.getCenter().y;
            double z = area.getCenter().z + (serverLevel.random.nextDouble() - 0.5) * 0.4;
            serverLevel.sendParticles(underwater ? ParticleTypes.BUBBLE : CIOParticles.RADIATOR_SMOKE.get(),
                    x, y, z, 0, 0.0, RISE_SPEED, 0.0, 1.0);
        }
        if (ColdSweatCompat.present()) {
            AABB warmArea = new AABB(anchor).inflate(WARMTH_RADIUS);
            for (LivingEntity entity : serverLevel.getEntitiesOfClass(LivingEntity.class, warmArea)) {
                ColdSweatWarmthEffect.apply(entity, WARMTH_AMPLIFIER, WARMTH_DURATION_TICKS);
            }
        }
    }
}
