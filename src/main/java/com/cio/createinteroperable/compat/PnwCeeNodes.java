package com.cio.createinteroperable.compat;

import com.cio.createinteroperable.CreateInteroperable;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Side-independent facts about the CEE nodes that stand in for Pantographs &
 * Wires blocks. Safe to load on the client: it touches no server-only state.
 *
 * <p>PnW is reached by reflection because it is not on the compile classpath.</p>
 */
public final class PnwCeeNodes {
    /** CEE node ids at or above this belong to PnW mast/generic nodes; below it, to connector blocks. */
    public static final int DERIVED_ID_BASE = 1_000_000;
    /** PnW's {@code AbstractCantileverBlock.MAX_CANTILEVERS}. */
    public static final int MAX_CANTILEVERS = 3;
    /** Per-point key PnW's catenary wire item stores the chosen sub-cantilever under. */
    public static final String CANTILEVER_INDEX = "CantileverIndex";

    private static final double PIXEL = 1.0 / 16.0;
    private static Class<?> connectorInterface;
    private static Class<?> hangingInsulator;
    private static Class<?> insulator;
    private static Class<?> cantilever;
    private static Constructor<?> customData;
    private static Method getConnectorData;
    private static boolean warned;
    private static Method getSubCantileverSettings;

    private PnwCeeNodes() {
    }

    /** True for any block PnW lets wires attach to as a connector. */
    public static boolean isConnector(Object block) {
        connectorInterface = load(connectorInterface, "de.mrjulsen.wires.block.IWireConnector");
        return connectorInterface != null && connectorInterface.isInstance(block);
    }

    public static boolean isCantilever(Object block) {
        cantilever = load(cantilever, "de.mrjulsen.paw.block.abstractions.AbstractCantileverBlock");
        return cantilever != null && cantilever.isInstance(block);
    }

    /**
     * Every CEE node a PnW connector block exposes, as block-local positions.
     * A cantilever exposes one per sub-cantilever (keyed by its index) at the
     * tip of that cantilever's contact wire; every other connector exposes node
     * {@code 0} at {@link #nodeLocal}.
     */
    public static Map<Integer, Vec3> nodePositions(Level level, BlockPos pos, BlockState state) {
        Map<Integer, Vec3> contact = isCantilever(state.getBlock()) ? cantileverContactPoints(level, pos, state) : null;
        return contact != null ? contact : Map.of(0, nodeLocal(state));
    }

    /**
     * Where a plain connector's CEE node (and its hitbox) sits inside the block.
     * PnW attaches wires near one end of an insulator, not at its centre, so the
     * node is nudged towards that end: 5.5 px down for hanging insulators and
     * 3 px up for the standing one.
     */
    public static Vec3 nodeLocal(BlockState state) {
        double y = 0.5;
        Object block = state.getBlock();
        hangingInsulator = load(hangingInsulator, "de.mrjulsen.paw.block.AbstractPlaceableHangingInsulatorBlock");
        insulator = load(insulator, "de.mrjulsen.paw.block.InsulatorBlock");
        if (hangingInsulator != null && hangingInsulator.isInstance(block)) {
            y -= 5.5 * PIXEL;
        } else if (insulator != null && insulator.isInstance(block) && state.hasProperty(BlockStateProperties.HALF)) {
            y += state.getValue(BlockStateProperties.HALF) == Half.TOP ? 3 * PIXEL : -5.5 * PIXEL;
        }
        return new Vec3(0.5, y, 0.5);
    }

    /**
     * Asks PnW itself where each sub-cantilever's contact wire attaches, the
     * same call its catenary wire uses to build the rendered wire, so the node
     * follows every width/height/registration-arm setting exactly. Null if the
     * block entity is missing or PnW's shape has changed.
     */
    private static Map<Integer, Vec3> cantileverContactPoints(Level level, BlockPos pos, BlockState state) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return null;
        }
        try {
            if (getConnectorData == null) {
                Class<?> customDataClass = Class.forName("de.mrjulsen.wires.item.CustomData");
                customData = customDataClass.getConstructor(CompoundTag.class);
                getConnectorData = Class.forName("de.mrjulsen.wires.block.IWireConnector")
                        .getMethod("getConnectorData", Level.class, BlockPos.class, customDataClass, int.class);
            }
            if (getSubCantileverSettings == null || !getSubCantileverSettings.getDeclaringClass().isInstance(be)) {
                getSubCantileverSettings = be.getClass().getMethod("getSubCantileverSettings");
            }
            int count = Math.min(Math.max(((List<?>) getSubCantileverSettings.invoke(be)).size(), 1), MAX_CANTILEVERS);
            Map<Integer, Vec3> points = new TreeMap<>();
            for (int i = 0; i < count; i++) {
                Object provider = getConnectorData.invoke(state.getBlock(), level, pos, customData.newInstance(pointData(i)), 0);
                // getAttachOffset() lives on BasicConnectorDataProvider (the cantilever's
                // provider extends it), not on the abstract ConnectorDataProvider.
                Vector3d offset = (Vector3d) provider.getClass().getMethod("getAttachOffset").invoke(provider);
                points.put(i, new Vec3(offset.x, offset.y, offset.z));
            }
            return points;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (!warned) {
                warned = true;
                CreateInteroperable.LOGGER.warn("Could not read PnW cantilever contact points; their CEE nodes fall back to the block centre.", exception);
            }
            return null;
        }
    }

    /** A PnW {@code CustomData} tag selecting sub-cantilever {@code index} for point 0. */
    private static CompoundTag pointData(int index) {
        CompoundTag point = new CompoundTag();
        point.putInt(CANTILEVER_INDEX, index);
        CompoundTag points = new CompoundTag();
        points.put("0", point);
        CompoundTag nbt = new CompoundTag();
        nbt.put("CustomPointData", points);
        return nbt;
    }

    /**
     * Whether a CEE node is one the PnW bridge owns, judged only from world
     * state so the client needs no extra sync. An unloaded position is treated
     * as native, so a wire is never hidden on a guess.
     */
    /**
     * Whether the chunk holding {@code pos} is really present. Do not use
     * {@code level.hasChunkAt(pos)} for this on the client: vanilla's
     * {@code ClientLevel.hasChunk} is hard-coded to {@code true}, so a chunk
     * that has not arrived yet reads as loaded and full of air. The chunk
     * source asks the client chunk cache itself, which answers honestly (and is
     * equally correct on a server level).
     */
    public static boolean isChunkPresent(Level level, BlockPos pos) {
        return level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    public static boolean isPnwNode(Level level, InWorldNode node) {
        if (node.id() >= DERIVED_ID_BASE) {
            return true;
        }
        return isChunkPresent(level, node.sourcePos()) && isConnector(level.getBlockState(node.sourcePos()).getBlock());
    }

    private static Class<?> load(Class<?> cached, String name) {
        if (cached != null) {
            return cached;
        }
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException exception) {
            return null;
        }
    }
}
