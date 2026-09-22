package com.cio.createinteroperable;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * A single custom particle type — {@link #RADIATOR_SMOKE} — used instead of
 * vanilla's {@code ParticleTypes.WHITE_SMOKE} for the Multi Radiator's
 * campfire-style puffs (see RadiatorValveNorthBlockEntity#tickSmokeParticles).
 * <p>
 * {@code WHITE_SMOKE} was tried first and rejected: it's backed by
 * {@code WhiteSmokeParticle} (a {@code BaseAshSmokeParticle}), which uses a
 * small ash sprite and an 8-tick base lifetime — genuinely tiny next to real
 * campfire smoke, confirmed by reading its real decompiled source. Vanilla's
 * actual campfire smoke ({@code ParticleTypes.CAMPFIRE_COSMETIC_SMOKE}, via
 * {@code CampfireSmokeParticle}) is the right size/behavior (scale 3, ~80-130
 * tick life, slow upward drift) but doesn't tint its sprite at all — the grey,
 * sooty look is baked directly into vanilla's {@code big_smoke_0..11.png}
 * frames, confirmed by extracting and inspecting them, so it can't be
 * recolored via {@code setColor}/rCol-gCol-bCol (a multiply-based tint can
 * only ever darken a texture, never lighten it past its own baked pixels).
 * <p>
 * So this reuses vanilla's exact same 12-frame growing-puff animation
 * ({@code particles/radiator_smoke.json} lists the same shapes as
 * {@code campfire_cosy_smoke.json}) with every frame's RGB lifted toward
 * white (alpha/shape untouched) — see the one-off {@code Recolor.java} tool
 * used to generate {@code textures/particle/radiator_smoke_0..11.png} from
 * vanilla's own {@code big_smoke_0..11.png}. The actual particle class,
 * {@link RadiatorSmokeParticle}, is otherwise a straight clone of
 * {@code CampfireSmokeParticle}'s physics/lifetime/render type.
 */
public class CIOParticles {
    private static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, CreateInteroperable.ID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RADIATOR_SMOKE =
            PARTICLE_TYPES.register("radiator_smoke", () -> new SimpleParticleType(false));

    public static void register(IEventBus modEventBus) {
        PARTICLE_TYPES.register(modEventBus);
    }
}
