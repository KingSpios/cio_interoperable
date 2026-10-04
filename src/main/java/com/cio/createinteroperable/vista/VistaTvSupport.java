package com.cio.createinteroperable.vista;

import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
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

    private static volatile java.lang.reflect.Method findMaster;
    private static volatile java.lang.reflect.Method onNeighborChanged;
    private static volatile boolean speakerHookMissing;

    private VistaTvSupport() {
    }

    /**
     * The non-redstone half of Vista's {@code TVBlock#neighborChanged}: hand
     * the change to the wall's master {@code TVBlockEntity#onNeighborChanged},
     * which notices a speaker placed or removed next to the TV. CIO cancels
     * that whole method to take over the TV's {@code powered} state, so this
     * part is replayed here. Reflective (no compile dependency on Vista); a
     * no-op on Vista builds without either method.
     */
    public static void forwardSpeakerCheck(Object tvBlock, Level level, BlockPos pos, BlockState state, BlockPos neighborPos) {
        if (speakerHookMissing) {
            return;
        }
        try {
            java.lang.reflect.Method find = findMaster;
            if (find == null) {
                find = tvBlock.getClass().getMethod("findMasterBlockEntity", LevelAccessor.class, BlockPos.class, BlockState.class);
                findMaster = find;
            }
            Object master = find.invoke(tvBlock, level, pos, state);
            if (master == null) {
                return;
            }
            java.lang.reflect.Method changed = onNeighborChanged;
            if (changed == null) {
                changed = master.getClass().getMethod("onNeighborChanged", BlockPos.class);
                onNeighborChanged = changed;
            }
            changed.invoke(master, neighborPos);
        } catch (NoSuchMethodException e) {
            speakerHookMissing = true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Speaker detection is cosmetic; never let it break the TV.
        }
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
