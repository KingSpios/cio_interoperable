package com.cio.createinteroperable;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Isolates the Power Grid-wired Aircon Motor bottom's fluid-handler
 * capability registration off {@link CIOCapabilities} — same class-loading
 * isolation reasoning as {@code PgAirconMotorSupport}/{@code CrayfishClient}/
 * every other "isolated glue" class in this codebase (see {@code cio-context}'s
 * build-environment notes), but for a DIFFERENT trigger than usual:
 * {@link CIOCapabilities} is {@code @EventBusSubscriber}, so NeoForge's
 * {@code AutomaticEventSubscriber} calls {@code Class#getDeclaredMethods()}
 * on it at mod construct time to find {@code @SubscribeEvent} handlers — and
 * that reflective enumeration resolves EVERY declared method's parameter/
 * return types, including synthetic, lambda-desugared ones, REGARDLESS of
 * whether that lambda's own body ever executes.
 * <p>
 * A lambda passed to {@code registerBlockEntity} for
 * {@code AirconMotorBottomBlockEntity} (which extends Power Grid's own
 * {@code ElectricBlockEntity}) gets that concrete type inferred as its own
 * parameter type — confirmed by decompiling the real compiled class:
 * {@code CIOCapabilities} carried a real declared method
 * {@code lambda$register$N(AirconMotorBottomBlockEntity, Direction)}, baked
 * in unconditionally, even though the registration CALL itself was already
 * behind {@code CIOBlockEntities.AIRCON_MOTOR_BOTTOM != null}. That runtime
 * guard only controls whether the lambda's invokedynamic executes — the
 * method descriptor itself is a permanent part of the class file, and
 * NeoForge's reflection resolves it the instant {@code CIOCapabilities} is
 * scanned, throwing {@code NoClassDefFoundError} for PG's
 * {@code ElectricBlockEntity} on any CEE-only (Power Grid absent) install,
 * confirmed the real cause of a reported mod-construct crash.
 * <p>
 * Routing the lambda into this separate, plain (never
 * {@code @EventBusSubscriber}, never reflectively enumerated) class defers
 * that resolution to when {@link #registerPgMotorBottom} is actually
 * CALLED — which only ever happens from behind the same null guard in
 * {@link CIOCapabilities}.
 */
final class AirconCapabilitySupport {
    private AirconCapabilitySupport() {
    }

    static void registerPgMotorBottom(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CIOBlockEntities.AIRCON_MOTOR_BOTTOM.get(),
                (be, side) -> be.getFluidHandler(side));
    }
}
