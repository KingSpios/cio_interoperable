package com.cio.createinteroperable.vista;

import com.cio.createinteroperable.grid.ApplianceNode;
import com.cio.createinteroperable.grid.CrayfishCompat;
import com.cio.createinteroperable.grid.GridConnection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Map;

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

    /**
     * Vista's {@code ConnectionType} (the TV's {@code connection} blockstate) by
     * serialized name, as the bit mask of directions that tile is joined toward:
     * 1 = up, 2 = down, 4 = the facing's clockwise side (Vista's "left"),
     * 8 = its counter-clockwise side. Copied from Vista 5.5.3's enum.
     */
    private static final Map<String, Integer> CONNECTION_MASKS = Map.ofEntries(
            Map.entry("single", 0), Map.entry("center", 15), Map.entry("top", 14), Map.entry("bottom", 13),
            Map.entry("left", 11), Map.entry("right", 7), Map.entry("top_left", 10), Map.entry("top_right", 6),
            Map.entry("bottom_left", 9), Map.entry("bottom_right", 5), Map.entry("h_left", 8), Map.entry("h_right", 4),
            Map.entry("h_middle", 12), Map.entry("v_bottom", 1), Map.entry("v_top", 2), Map.entry("v_middle", 3));

    /** Longest walk {@link #findMasterPos} takes; Vista caps a wall side at 24. */
    private static final int MAX_WALK = 64;

    private VistaTvSupport() {
    }

    /**
     * Where the TV wall containing {@code pos} keeps its one real block entity:
     * its bottom-left tile, found the way Vista's own {@code
     * findMasterBlockEntity} walks (down while joined downward, then toward the
     * facing's clockwise side while joined that way), but from the blockstates
     * alone, since Vista's version returns whatever block entity sits at
     * {@code pos} first, stale or not. Null if the walk leaves the wall.
     */
    @Nullable
    public static BlockPos findMasterPos(Level level, BlockPos pos, BlockState state) {
        Property<?> connection = state.getBlock().getStateDefinition().getProperty("connection");
        if (connection == null || !state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return null;
        }
        Block block = state.getBlock();
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        Direction left = facing.getClockWise();
        BlockPos current = pos;
        BlockState currentState = state;
        for (int i = 0; i < MAX_WALK && (mask(currentState, connection) & 2) != 0; i++) {
            current = current.below();
            currentState = level.getBlockState(current);
            if (!sameWall(currentState, block, facing)) {
                return null;
            }
        }
        for (int i = 0; i < MAX_WALK && (mask(currentState, connection) & 4) != 0; i++) {
            current = current.relative(left);
            currentState = level.getBlockState(current);
            if (!sameWall(currentState, block, facing)) {
                return null;
            }
        }
        return current;
    }

    private static boolean sameWall(BlockState state, Block block, Direction facing) {
        return state.is(block) && state.hasProperty(HorizontalDirectionalBlock.FACING)
                && state.getValue(HorizontalDirectionalBlock.FACING) == facing;
    }

    private static int mask(BlockState state, Property<?> connection) {
        Comparable<?> value = state.getValue(connection);
        if (value instanceof StringRepresentable sr) {
            Integer mask = CONNECTION_MASKS.get(sr.getSerializedName());
            if (mask != null) {
                return mask;
            }
        }
        return 0;
    }

    /**
     * Retire a stale TV block entity. Vista's walls keep exactly one block
     * entity, on the bottom-left tile (Moonlight's {@code IOptionalEntityBlock}
     * makes every other tile's state report no block entity), but vanilla only
     * removes a block entity when the <em>block</em> changes, not its state. So
     * every tile that was once a single TV, or an earlier bottom-left corner,
     * keeps its old {@code TVBlockEntity} until its chunk reloads. Each one
     * carries its own CIO node, which showed "Missing power" on the wall's
     * other tiles, could be wired instead of the real one, and judged power
     * and the redstone kill switch for its own tile only. Server side, its
     * links move to the wall's real master first; then it is removed, on both
     * sides, exactly as a chunk reload would.
     */
    public static void retireGhost(Level level, BlockPos pos, BlockState state, BlockEntity ghost) {
        if (!level.isClientSide) {
            BlockPos masterPos = findMasterPos(level, pos, state);
            BlockEntity master = masterPos != null && !masterPos.equals(pos) ? level.getBlockEntity(masterPos) : null;
            if (CrayfishCompat.present()) {
                VistaCrayfishLinks.moveLinks(ghost, master);
            } else if (ghost instanceof ApplianceNode from) {
                ApplianceNode to = master instanceof ApplianceNode node ? node : null;
                for (GridConnection conn : new ArrayList<>(from.applianceConnections())) {
                    ApplianceNode other = conn.otherNode(level, from);
                    from.disconnectAppliance(conn);
                    if (other != null && to != null && other != to && !to.isConnectedToAppliance(other)
                            && !to.applianceConnectionLimitReached()) {
                        to.connectApplianceTo(other);
                    }
                }
            }
        }
        level.removeBlockEntity(pos);
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
