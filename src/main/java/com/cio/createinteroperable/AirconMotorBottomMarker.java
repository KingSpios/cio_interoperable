package com.cio.createinteroperable;

/**
 * A plain, zero-dependency marker implemented by both
 * {@link AirconMotorBottomBlock} (Power Grid-wired) and
 * {@code CeeAirconMotorBottomBlock} (Electro Energetics-wired) — lets
 * {@link AirconMotorAssembly} (always loaded, regardless of which — if
 * either — electrical mod is installed) recognize either bottom variant via
 * a single {@code instanceof} check, without ever referencing either
 * concrete Block class (and therefore either mod's own types) directly.
 * <p>
 * Deliberately has no methods: this is purely a type-level "is a valid
 * Aircon Motor bottom half" tag. Anything variant-specific (Power Grid's own
 * {@code ElectricBlock.refreshConnectionEntities} call after assembly) is
 * routed through a separately mod-gated helper instead — see
 * {@link PgAirconMotorSupport}.
 */
public interface AirconMotorBottomMarker {
}
