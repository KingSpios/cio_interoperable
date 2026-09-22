package com.cio.createinteroperable.vista;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Dependency-light helpers for the Vista (cameramod) TV integration: CIO
 * never compiles against Vista's classes (a soft, name-targeted-mixin-only
 * integration, same posture as every other third-party appliance here), so
 * Vista's {@code PowerState} enum (the TV's {@code powered} blockstate
 * property: {@code off}/{@code direct}/{@code indirect}) is never imported.
 *
 * <p>Every value of that enum implements vanilla's own
 * {@link StringRepresentable}, so a value can be found and set purely by its
 * serialized name — the same trick {@code LetsDoLampStates} uses for foreign
 * boolean properties, generalized to any enum-valued one.</p>
 */
public final class VistaTvSupport {

    private VistaTvSupport() {
    }

    /**
     * {@code state} with {@code property} set to whichever of its possible
     * values serializes to {@code serializedName} (e.g. {@code "direct"} or
     * {@code "off"} for Vista's {@code PowerState}). Returns {@code state}
     * unchanged if it's already set to that value, or if no matching value is
     * found (property foreign to the target, or a name that doesn't exist).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static BlockState withEnumByName(BlockState state, Property<?> property, String serializedName) {
        Comparable<?> current = state.getValue(property);
        if (current instanceof StringRepresentable sr && sr.getSerializedName().equals(serializedName)) {
            return state;
        }
        for (Comparable<?> value : property.getPossibleValues()) {
            if (value instanceof StringRepresentable sr && sr.getSerializedName().equals(serializedName)) {
                return state.setValue((Property) property, (Comparable) value);
            }
        }
        return state;
    }
}
