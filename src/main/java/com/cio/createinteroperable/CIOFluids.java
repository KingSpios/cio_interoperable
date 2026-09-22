package com.cio.createinteroperable;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * "Steam" — the fluid the Boiler Outlet produces (from a Create Fluid
 * Tank/Boiler's real activeHeat/waterSupply, see SteamOutletBlockEntity) and
 * the Brass Heater consumes. Registered as a genuine NeoForge Fluid purely so
 * Create's pipe network (which is fluid-agnostic — it just looks up the
 * plain IFluidHandler capability on neighboring blocks, confirmed by reading
 * FluidPropagator/PumpBlockEntity) can carry it, and so a wrenched
 * (transparent) pipe renders it with its own texture/tint.
 * <p>
 * Deliberately has NO placeable world form (no {@code .block(...)} on the
 * BaseFlowingFluid.Properties below). Confirmed via BaseFlowingFluid's real
 * source: with no block supplier, {@code createLegacyBlock} falls back to
 * {@code Blocks.AIR}. This is exactly the "vented to atmosphere" behavior we
 * want for an open/dangling pipe end (see OpenEndedPipe#provideFluidToSpace,
 * which returns true immediately without placing anything when
 * {@code FluidHelper.hasBlockState(fluid)} is false) — a leaking steam pipe
 * just silently discards the fluid instead of leaving a floating gas block
 * in the world, with zero extra code required on our side.
 */
public class CIOFluids {
    private static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, CreateInteroperable.ID);
    private static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, CreateInteroperable.ID);

    public static final DeferredHolder<FluidType, FluidType> STEAM_TYPE = FLUID_TYPES.register("steam",
            () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("fluid.createinteroperable.steam")
                    .lightLevel(0)
                    // Negative, not the earlier "1": FluidType#isLighterThanAir()
                    // (vanilla NeoForge, not a CIO override) is defined as
                    // getDensity() < 0 — the real, load-bearing signal any
                    // density-aware mod uses to tell a gas from a liquid (see
                    // Create: Pipes n Physics' own PipeProbe#isGas/SettlingRun
                    // #lighterThanAir, both calling this exact method). A
                    // positive density, however small, reads as an ordinary
                    // (if extremely light) LIQUID — steam then got modelled
                    // with liquid surface/suction physics instead of rising
                    // like a gas. The magnitude past the sign doesn't matter
                    // to PnP (its buoyant lift is deliberately
                    // density-independent — see its TankMassFormulas), so -1
                    // is the plain "this is a gas" marker, same convention
                    // other gas fluids use.
                    .density(-1)
                    // Directly controls real Pipes n Physics throughput, not
                    // just a cosmetic number: confirmed by reading its real
                    // engine source (de.devin.pipesnphysics.engine.FluidPass,
                    // the per-fluid pass constructor) — every pipe run's
                    // conductance is computed as
                    // {@code PIPE_CONDUCTANCE_CONFIG * (1000.0 / effectiveViscosity)},
                    // where 1000 is vanilla water's own viscosity (so water
                    // flows at exactly the configured baseline, and anything
                    // thinner flows faster than that in direct proportion —
                    // NeoForge's own FluidType#getViscosity is the only input,
                    // no PnP-specific API needed). 50 gives steam 20x water's
                    // conductance (vs. the previous 200's already-generous 5x)
                    // — real steam is far less viscous than any liquid, and a
                    // reported real playtest bug ("pipes hold 250mb, deplete
                    // at 1mb/tick, not enough to feed the Radiators") needed
                    // active throughput headroom, not just a "thin" cosmetic
                    // tag (PnP's own goggle already called 200 "thin", the
                    // same tier as 50 — this only changes the real number).
                    .viscosity(50)
                    .temperature(373)
                    .canConvertToSource(false)
                    .rarity(Rarity.COMMON)));

    // Cross-referenced via CIOFluids.<FIELD>::get (fully qualified) rather
    // than a bare simple name — STEAM_STILL's own properties need to name
    // STEAM_FLOWING, which is declared further down this same class, and a
    // bare forward reference to it is an illegal-forward-reference compile
    // error (JLS 8.3.3) even though it's runtime-safe (the lambda isn't
    // invoked until the registry event fires). Same fix already established
    // elsewhere in this project for DeferredHolder self/forward-references.
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> STEAM_STILL = FLUIDS.register("steam",
            () -> new BaseFlowingFluid.Source(new BaseFlowingFluid.Properties(
                    CIOFluids.STEAM_TYPE::get, CIOFluids.STEAM_STILL::get, CIOFluids.STEAM_FLOWING::get)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> STEAM_FLOWING = FLUIDS.register("steam_flowing",
            () -> new BaseFlowingFluid.Flowing(new BaseFlowingFluid.Properties(
                    CIOFluids.STEAM_TYPE::get, CIOFluids.STEAM_STILL::get, CIOFluids.STEAM_FLOWING::get)));

    // --- "hot_air" / "cold_air" — the Aircon's two carried gases. Same
    // pattern as steam above: density -1 (gas), no block/bucket form (falls
    // back to Blocks.AIR — an open aircon pipe just vents instead of leaving
    // a floating gas block), canConvertToSource(false).

    /**
     * Viscosity matched to {@link #STEAM_TYPE}'s own (50), not the original
     * 20 — a real report of rendering/flow glitches specifically on hot_air
     * (not cold_air, not steam) in Create: Pipes n Physics pipes, with the
     * user's own explicit instruction that steam — the one gas in this mod
     * proven stable through this whole feature's testing — should be the
     * behavioral model for hot_air. 20 made hot_air notably thinner (faster,
     * more volatile flow) than steam's already-thin 50; bringing it in line
     * removes that as a variable. Density (-1, same sign as steam — a
     * rising gas) was already identical, so viscosity was the one real
     * structural difference between the two.
     */
    public static final DeferredHolder<FluidType, FluidType> HOT_AIR_TYPE = FLUID_TYPES.register("hot_air",
            () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("fluid.createinteroperable.hot_air")
                    .lightLevel(0)
                    .density(-1)
                    .viscosity(50)
                    .temperature(340)
                    .canConvertToSource(false)
                    .rarity(Rarity.COMMON)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> HOT_AIR_STILL = FLUIDS.register("hot_air",
            () -> new BaseFlowingFluid.Source(new BaseFlowingFluid.Properties(
                    CIOFluids.HOT_AIR_TYPE::get, CIOFluids.HOT_AIR_STILL::get, CIOFluids.HOT_AIR_FLOWING::get)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> HOT_AIR_FLOWING = FLUIDS.register("hot_air_flowing",
            () -> new BaseFlowingFluid.Flowing(new BaseFlowingFluid.Properties(
                    CIOFluids.HOT_AIR_TYPE::get, CIOFluids.HOT_AIR_STILL::get, CIOFluids.HOT_AIR_FLOWING::get)));

    // Deliberately POSITIVE (unlike every other gas above) — density's sign
    // is the actual, load-bearing "gas vs. liquid" signal a density-aware
    // mod reads (FluidType#isLighterThanAir() = getDensity() < 0; see
    // HOT_AIR_TYPE/STEAM_TYPE's own doc for the confirmation that Pipes n
    // Physics' own PipeProbe#isGas/SettlingRun#lighterThanAir call this exact
    // method). Cold air is realistically DENSER than ambient air, not
    // lighter — flipping the sign makes PnP's settling simulation treat it
    // like a heavy fluid that sinks toward the bottom of an open network
    // instead of rising, i.e. it genuinely "wants to fall" when PnP is
    // installed. Magnitude (2) is still small/arbitrary, same as every other
    // fluid here — only the sign matters to PnP's buoyancy handling. With no
    // PnP installed this is inert (nothing else in vanilla/NeoForge cares
    // about a blockless custom fluid's density sign the way PnP's settling
    // logic does), matching "if we support Pipes and Physics."
    public static final DeferredHolder<FluidType, FluidType> COLD_AIR_TYPE = FLUID_TYPES.register("cold_air",
            () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("fluid.createinteroperable.cold_air")
                    .lightLevel(0)
                    .density(2)
                    .viscosity(60)
                    .temperature(250)
                    .canConvertToSource(false)
                    .rarity(Rarity.COMMON)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> COLD_AIR_STILL = FLUIDS.register("cold_air",
            () -> new BaseFlowingFluid.Source(new BaseFlowingFluid.Properties(
                    CIOFluids.COLD_AIR_TYPE::get, CIOFluids.COLD_AIR_STILL::get, CIOFluids.COLD_AIR_FLOWING::get)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> COLD_AIR_FLOWING = FLUIDS.register("cold_air_flowing",
            () -> new BaseFlowingFluid.Flowing(new BaseFlowingFluid.Properties(
                    CIOFluids.COLD_AIR_TYPE::get, CIOFluids.COLD_AIR_STILL::get, CIOFluids.COLD_AIR_FLOWING::get)));

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
    }
}
