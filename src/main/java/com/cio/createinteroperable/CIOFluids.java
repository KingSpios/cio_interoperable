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
                    .density(1)
                    .viscosity(200)
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

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
    }
}
