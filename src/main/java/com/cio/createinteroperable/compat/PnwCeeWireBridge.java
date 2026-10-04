package com.cio.createinteroperable.compat;

import com.cio.createinteroperable.CreateInteroperable;
import com.george_vi.electroenergetics.CEEWireTypes;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.WireType;
import com.george_vi.electroenergetics.simulation.infrastructure.InWorldNodeData;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Experimental electrical backend for PnW's existing wire graph.
 *
 * <p>PnW remains the owner of placement, rendering, and collision. Each of
 * its conductive logical edges is represented by one CEE connection between
 * the CEE nodes of its two endpoints. CEE therefore supplies the
 * resistance/temperature simulation; if CEE burns out the mirrored
 * connection, the original PnW edge is removed on the next monitor pass.</p>
 *
 * <p><b>Node identity.</b> A PnW connector block owns exactly one graph node.
 * An insulator maps it to CEE node {@code 0}; a cantilever maps each edge end
 * to the node of the sub-cantilever it uses (id = cantilever index), placed at
 * that cantilever's contact wire tip. Mast and generic block nodes can share a block
 * with other junctions, so they get a CEE node id derived deterministically
 * from the PnW node UUID; that survives restarts without any stored mapping.
 * Mid-span catenary nodes have no fixed block and are not mirrored.</p>
 *
 * <p><b>Ownership.</b> CEE persists its connections but this bridge's state is
 * in memory. After a restart (or a PnW {@code updateEdge}) an existing CEE
 * connection with the exact wire type and length we would create is adopted as
 * ours; anything else between the same two nodes is a player's wire and is
 * left alone.</p>
 *
 * <p>This intentionally uses reflection: PnW is a soft dependency and is not
 * published to a Maven repository used by this project. The two mixins call
 * this class only when both optional mods are present.</p>
 */
public final class PnwCeeWireBridge {
    private static final ResourceLocation ENERGY_WIRE = ResourceLocation.fromNamespaceAndPath("pantographsandwires", "energy_wire");
    private static final ResourceLocation CATENARY_WIRE = ResourceLocation.fromNamespaceAndPath("pantographsandwires", "catenary_wire");
    private static final double LENGTH_EPSILON = 0.01;
    private static final int MONITOR_INTERVAL = 10;
    private static final int REPAIR_INTERVAL = 600;
    private static final double NOTIFY_RANGE_SQR = 48 * 48;

    private static final Map<EdgeKey, Mirror> MIRRORS = new HashMap<>();
    /** Reverse index so two PnW edges between the same nodes cannot both claim one CEE connection. */
    private static final Map<ConnectionKey, EdgeKey> OWNERS = new HashMap<>();
    /** Connector nodes that outlived their last PnW edge because a player wired a CEE cable to them. */
    private static final Set<Watched> WATCHED = new HashSet<>();
    private static final Set<ResourceLocation> SWEPT = new HashSet<>();
    private static final Set<String> WARNED = new HashSet<>();
    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();
        private static long ticks;

    private PnwCeeWireBridge() {
    }

    /** Called after PnW has added or rebuilt an edge. Arguments deliberately use Object to keep PnW optional. */
    public static void upsert(Object graph, Object edge) {
        mirror(graph, edge, true);
    }

    /** Called before PnW removes an edge through its public removal path. */
    public static void remove(Object graph, UUID id) {
        try {
            Level level = (Level) call(graph, "getLevel");
            if (level instanceof ServerLevel server) {
                removeMirror(server, new EdgeKey(server.dimension().location(), id));
            }
        } catch (ReflectiveOperationException exception) {
            CreateInteroperable.LOGGER.error("Could not remove the CEE mirror for a Pantographs & Wires edge.", exception);
        }
    }

    /** Detects CEE burnouts every second and repairs drift against the PnW graphs occasionally. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticks % MONITOR_INTERVAL != 0) {
            return;
        }
        boolean repair = ticks % REPAIR_INTERVAL == 0;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            monitor(level);
            boolean first = SWEPT.add(level.dimension().location());
            if (repair || first) {
                int edges = repair(level);
                if (first && edges > 0) {
                    ResourceLocation dimension = level.dimension().location();
                    long mirrored = MIRRORS.keySet().stream().filter(key -> key.dimension.equals(dimension)).count();
                    CreateInteroperable.LOGGER.info("PnW -> CEE bridge: {} of {} Pantographs & Wires edge(s) in {} mirrored into Electro Energetics.", mirrored, edges, dimension);
                }
            }
        }
    }

    /**
     * All bridge state is static, so it must not outlive its server: in
     * single-player the next world opened in the same game session would
     * otherwise inherit this world's mirrors.
     */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        MIRRORS.clear();
        OWNERS.clear();
        WATCHED.clear();
        SWEPT.clear();
        ticks = 0;
    }

    private static void mirror(Object graph, Object edge, boolean notify) {
        try {
            Level level = (Level) call(graph, "getLevel");
            WireType type = wireTypeFor(edge);
            if (!(level instanceof ServerLevel server) || type == null) {
                return;
            }

            UUID id = (UUID) call(edge, "getId");
            EdgeKey key = new EdgeKey(server.dimension().location(), id);
            Endpoint a = endpoint(server, graph, edge, 0);
            Endpoint b = endpoint(server, graph, edge, 1);
            if (a == null || b == null || a.node.equals(b.node)) {
                return;
            }

            InWorldNodeConnection connection = new InWorldNodeConnection(a.node, b.node);
            Mirror existing = MIRRORS.get(key);
            if (existing != null) {
                if (existing.connection.equals(connection) && existing.type == type
                        && Math.abs(existing.length - a.anchor.distanceTo(b.anchor)) < LENGTH_EPSILON) {
                    existing.graph = graph; // unchanged edge: PnW just refreshed it
                    // Still move its nodes if they were saved at an older position.
                    InfrastructureSavedData data = InfrastructureSavedData.load(server);
                    ensureNode(server, data, a);
                    ensureNode(server, data, b);
                    return;
                }
                removeMirror(server, key); // endpoints or wire type changed
            }

            InfrastructureSavedData data = InfrastructureSavedData.load(server);
            double length = a.anchor.distanceTo(b.anchor);
            WireData current = data.isConnected(a.node, b.node) ? data.getConnectionData(connection) : null;
            if (current != null && isLegacyMirror(current, type, length)
                    && !OWNERS.containsKey(new ConnectionKey(server.dimension().location(), connection))) {
                // Built before contact wires had their own taut type: swap it in place.
                data.removeConnectionNoDrops(connection);
                current = null;
            }
            if (current != null) {
                boolean ours = current.wireType() == type && Math.abs(current.length - length) < LENGTH_EPSILON;
                if (!ours || OWNERS.containsKey(new ConnectionKey(server.dimension().location(), connection))) {
                    // CEE has one physical connection per node pair.
                    CreateInteroperable.LOGGER.warn("PnW edge {} was not mirrored: CEE already has a different connection between its endpoints.", id);
                    if (notify) {
                        notifyNear(server, a.position);
                    }
                    return;
                }
            } else {
                ensureNode(server, data, a);
                ensureNode(server, data, b);
                data.connect(a.node, b.node, WireData.ofLength(type, length));
            }
            track(key, new Mirror(graph, connection, a.raw, type, length));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            CreateInteroperable.LOGGER.error("Could not mirror a Pantographs & Wires edge into Electro Energetics.", exception);
        }
    }

    private static void monitor(ServerLevel level) {
        InfrastructureSavedData data = InfrastructureSavedData.load(level);
        ResourceLocation dimension = level.dimension().location();
        for (Map.Entry<EdgeKey, Mirror> entry : Map.copyOf(MIRRORS).entrySet()) {
            if (!entry.getKey().dimension.equals(dimension)) {
                continue;
            }
            Mirror mirror = entry.getValue();
            // PnW does not route every removal (a broken connector, a chunk cleanup)
            // through removeEdge, so its graph is checked directly as well.
            if (pnwEdgeGone(level, mirror, entry.getKey())) {
                removeMirror(level, entry.getKey());
                continue;
            }
            WireData current = data.getConnectionData(mirror.connection);
            // WireLifetimeModule either deletes an overheated wire or swaps it
            // for its burnt replacement type; both mean the PnW edge is gone.
            if (current == null || current.wireType() != mirror.type) {
                try {
                    call(mirror.graph, "removeEdge", entry.getKey().id, mirror.breakPosition, Optional.empty());
                } catch (ReflectiveOperationException exception) {
                    CreateInteroperable.LOGGER.error("CEE burned a PnW mirror, but the PnW edge could not be removed.", exception);
                }
                // The PnW hook normally clears this; cover the case where it did not run.
                if (MIRRORS.containsKey(entry.getKey())) {
                    removeMirror(level, entry.getKey());
                }
            }
        }

        for (Watched watched : Set.copyOf(WATCHED)) {
            if (!watched.dimension().equals(dimension)) {
                continue;
            }
            InWorldNode node = watched.node();
            if (!data.hasNode(node) || data.getConnections(node).isEmpty()) {
                WATCHED.remove(watched);
            } else if (level.isLoaded(node.sourcePos()) && !PnwCeeNodes.isConnector(level.getBlockState(node.sourcePos()).getBlock())) {
                destroyNode(data, node);
                WATCHED.remove(watched);
            }
        }
    }

    private static boolean pnwEdgeGone(ServerLevel level, Mirror mirror, EdgeKey key) {
        try {
            if (call(mirror.graph, "getEdge", key.id) == null) {
                return true;
            }
        } catch (ReflectiveOperationException exception) {
            return false; // cannot tell; the hook and burnout paths still work
        }
        for (InWorldNode node : List.of(mirror.connection.node1(), mirror.connection.node2())) {
            if (isConnectorNode(node) && level.isLoaded(node.sourcePos()) && !PnwCeeNodes.isConnector(level.getBlockState(node.sourcePos()).getBlock())) {
                return true;
            }
        }
        return false;
    }

    /** Covers edges restored from PnW's SavedData after a restart, or updated without going through the hook. */
    /** Returns how many PnW edges the level has (conductive or not), for the startup log. */
    private static int repair(ServerLevel level) {
        int edges = 0;
        try {
            Class<?> manager = Class.forName("de.mrjulsen.wires.graph.WireGraphManager");
            Collection<?> graphs = (Collection<?>) manager.getMethod("getAll", Level.class).invoke(null, level);
            for (Object graph : graphs) {
                for (Object edge : (Collection<?>) call(graph, "getEdges")) {
                    edges++;
                    // Existing mirrors are revisited too, so nodes saved at a stale
                    // position (older build, re-adjusted cantilever) get moved.
                    mirror(graph, edge, false);
                }
            }
        } catch (ReflectiveOperationException exception) {
            CreateInteroperable.LOGGER.error("Could not reconcile Pantographs & Wires edges with CEE.", exception);
        }
        return edges;
    }

    private static void track(EdgeKey key, Mirror mirror) {
        MIRRORS.put(key, mirror);
        OWNERS.put(new ConnectionKey(key.dimension, mirror.connection), key);
    }

    private static void removeMirror(ServerLevel level, EdgeKey key) {
        Mirror mirror = MIRRORS.remove(key);
        if (mirror == null) {
            return;
        }
        OWNERS.remove(new ConnectionKey(key.dimension, mirror.connection));
        InfrastructureSavedData data = InfrastructureSavedData.load(level);
        if (data.getConnectionData(mirror.connection) != null) {
            data.removeConnection(mirror.connection);
        }
        prune(level, data, mirror.connection.node1());
        prune(level, data, mirror.connection.node2());
    }

    /**
     * Removes a CEE node once nothing is connected to it. A connector node that
     * still carries a player's CEE wire is kept and watched, and destroyed
     * (with its wires) if the connector block itself has been broken.
     */
    private static void prune(ServerLevel level, InfrastructureSavedData data, InWorldNode node) {
        if (!data.hasNode(node)) {
            return;
        }
        if (data.getConnections(node).isEmpty()) {
            data.removeNode(node);
        } else if (isConnectorNode(node)) {
            if (level.isLoaded(node.sourcePos()) && !PnwCeeNodes.isConnector(level.getBlockState(node.sourcePos()).getBlock())) {
                destroyNode(data, node);
            } else {
                WATCHED.add(new Watched(level.dimension().location(), node));
            }
        }
    }

    private static void destroyNode(InfrastructureSavedData data, InWorldNode node) {
        for (InWorldNodeConnection connection : List.copyOf(data.getConnections(node))) {
            data.removeAndDropConnection(connection);
        }
        data.removeNode(node);
    }

    private static void ensureNode(ServerLevel level, InfrastructureSavedData data, Endpoint endpoint) {
        InWorldNode node = endpoint.node;
        BlockPos source = node.sourcePos();
        if (isConnectorNode(node) && level.isLoaded(source)) {
            // Let CEE place (or move, after a cantilever is re-adjusted) the block's
            // nodes from the connector mixin, so node and hitbox always agree. Only
            // when no existing node would be dropped: CEE removes those with item drops.
            List<Integer> ids = List.copyOf(PnwCeeNodes.nodePositions(level, source, level.getBlockState(source)).keySet());
            boolean superset = ids.contains(node.id());
            for (InWorldNodeData existing : data.getNodesAt(source)) {
                superset &= ids.contains(existing.node.id());
            }
            if (superset) {
                data.registerOrUpdateNodes(source, ids);
                return;
            }
        }
        if (!data.hasNode(node)) {
            Vec3 local = endpoint.position.subtract(source.getX(), source.getY(), source.getZ());
            data.createNode(node, local, endpoint.position);
        }
    }

    /** Connector blocks own the low ids (0 for insulators, the sub-cantilever index for cantilevers). */
    private static boolean isConnectorNode(InWorldNode node) {
        return node.id() < PnwCeeNodes.DERIVED_ID_BASE;
    }

    /** Resolves a PnW graph node to its CEE node, or {@code null} for node kinds that have no fixed block. */
    /** {@code point} is 0 for the edge's node A and 1 for node B, matching PnW's per-point custom data. */
    private static Endpoint endpoint(ServerLevel level, Object graph, Object edge, int point) throws ReflectiveOperationException {
        Object node = call(graph, "getNode", call(edge, point == 0 ? "getNodeAId" : "getNodeBId"));
        if (node == null) {
            return null;
        }
        Object raw = call(node, "getPos");
        Vec3 position = vector(raw);
        BlockPos block = BlockPos.containing(position);
        String kind = call(node, "getData").getClass().getSimpleName();
        switch (kind) {
            case "BlockConnectorNodeData": {
                Object wiring = call(edge, "getWireConnectionData");
                Object connector = call(wiring, point == 0 ? "connectorA" : "connectorB");
                if (connector.getClass().getSimpleName().equals("CantileverConnectorDataProvider")) {
                    // The contact (bottom) wire's own tip as PnW stored it on the edge:
                    // exact for every cantilever setting, and needs no loaded chunk.
                    Vector3d offset = (Vector3d) call(connector, "getAttachOffset");
                    Vec3 tip = Vec3.atLowerCornerOf(block).add(offset.x, offset.y, offset.z);
                    return new Endpoint(new InWorldNode(cantileverIndex(level, wiring, point, block), block), tip, raw, tip);
                }
                // One node per connector block; its stored position is the block corner.
                // The node sits where the connector mixin puts its hitbox; wire length
                // (and so resistance) is measured between block centres so it does not
                // depend on that cosmetic offset or on the chunk being loaded.
                Vec3 anchor = Vec3.atCenterOf(block);
                Vec3 nodePos = level.isLoaded(block) ? Vec3.atLowerCornerOf(block).add(PnwCeeNodes.nodeLocal(level.getBlockState(block))) : anchor;
                return new Endpoint(new InWorldNode(0, block), nodePos, raw, anchor);
            }
            case "MastNodeData":
            case "LatticeMastNodeData":
            case "GenericBlockNodeData":
                return new Endpoint(new InWorldNode(derivedId((UUID) call(node, "getId")), block), position, raw, position);
            default:
                if (WARNED.add(kind)) {
                    CreateInteroperable.LOGGER.warn("PnW node kind {} has no fixed block; wires attached to it are not mirrored into CEE.", kind);
                }
                return null;
        }
    }

    /** The sub-cantilever an edge end uses, clamped the way PnW clamps it. */
    private static int cantileverIndex(ServerLevel level, Object wiring, int point, BlockPos block) throws ReflectiveOperationException {
        CompoundTag pointData = (CompoundTag) call(call(wiring, "customData"), "getCustomDataForPoint", point);
        int index = Math.clamp(pointData.getInt(PnwCeeNodes.CANTILEVER_INDEX), 0, PnwCeeNodes.MAX_CANTILEVERS - 1);
        if (level.isLoaded(block)) {
            index = Math.min(index, PnwCeeNodes.nodePositions(level, block, level.getBlockState(block)).size() - 1);
        }
        return index;
    }

    /** Stable across restarts; at or above {@link PnwCeeNodes#DERIVED_ID_BASE} so it never meets a native CEE id or 0. */
    private static int derivedId(UUID id) {
        return PnwCeeNodes.DERIVED_ID_BASE + Math.floorMod(id.hashCode(), 1 << 29);
    }

    /**
     * The CEE wire type whose stats stand in for each PnW wire: the feeder is a
     * heavily insulated cable, the overhead contact wire is bare copper (whose
     * 1500 V rating matches a railway catenary).
     */
    /** A contact-wire mirror saved by 0.1.67-0.1.72, which used sagging CEE copper. */
    private static boolean isLegacyMirror(WireData current, WireType type, double length) {
        return type == PnwCeeWireTypes.CONTACT_WIRE.get() && current.wireType() == CEEWireTypes.COPPER.get()
                && Math.abs(current.length - length) < LENGTH_EPSILON;
    }

    private static WireType wireTypeFor(Object edge) throws ReflectiveOperationException {
        Object id = call(call(edge, "getType"), "getRegistryId");
        if (ENERGY_WIRE.equals(id)) {
            return CEEWireTypes.HEAVILY_INSULATED.get();
        }
        if (CATENARY_WIRE.equals(id)) {
            return PnwCeeWireTypes.CONTACT_WIRE.get();
        }
        return null;
    }

    private static void notifyNear(ServerLevel level, Vec3 at) {
        Component message = Component.translatable("message.createinteroperable.pnw_wire_not_mirrored");
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(at) <= NOTIFY_RANGE_SQR) {
                player.displayClientMessage(message, true);
            }
        }
    }

    private static Vec3 vector(Object vector) throws ReflectiveOperationException {
        double x = ((Number) call(vector, "x")).doubleValue();
        double y = ((Number) call(vector, "y")).doubleValue();
        double z = ((Number) call(vector, "z")).doubleValue();
        return new Vec3(x, y, z);
    }

    private static Object call(Object target, String method, Object... arguments) throws ReflectiveOperationException {
        StringBuilder cacheKey = new StringBuilder(target.getClass().getName()).append('#').append(method);
        for (Object argument : arguments) {
            cacheKey.append('/').append(argument == null ? "null" : argument.getClass().getName());
        }
        Method cached = METHODS.get(cacheKey.toString());
        if (cached == null) {
            for (Method candidate : target.getClass().getMethods()) {
                if (candidate.getName().equals(method) && accepts(candidate, arguments)) {
                    cached = candidate;
                    METHODS.put(cacheKey.toString(), candidate);
                    break;
                }
            }
        }
        if (cached == null) {
            throw new NoSuchMethodException(target.getClass().getName() + "#" + method);
        }
        return cached.invoke(target, arguments);
    }

    /** Overloads such as {@code getEdge(UUID)} / {@code getEdge(WireEdgeHash)} share a name and arity. */
    private static boolean accepts(Method candidate, Object[] arguments) {
        Class<?>[] parameters = candidate.getParameterTypes();
        if (parameters.length != arguments.length) {
            return false;
        }
        for (int i = 0; i < parameters.length; i++) {
            Class<?> parameter = parameters[i].isPrimitive() ? MethodType.methodType(parameters[i]).wrap().returnType() : parameters[i];
            if (arguments[i] != null && !parameter.isInstance(arguments[i])) {
                return false;
            }
        }
        return true;
    }

    private record EdgeKey(ResourceLocation dimension, UUID id) {
    }

    private record ConnectionKey(ResourceLocation dimension, InWorldNodeConnection connection) {
    }

    /** {@code raw} is PnW's own position vector, kept opaque so it can be handed back to PnW. */
    private record Watched(ResourceLocation dimension, InWorldNode node) {
    }

    private record Endpoint(InWorldNode node, Vec3 position, Object raw, Vec3 anchor) {
    }

    private static final class Mirror {
        Object graph;
        final InWorldNodeConnection connection;
        final Object breakPosition;
        final WireType type;
        final double length;

        Mirror(Object graph, InWorldNodeConnection connection, Object breakPosition, WireType type, double length) {
            this.graph = graph;
            this.connection = connection;
            this.breakPosition = breakPosition;
            this.type = type;
            this.length = length;
        }
    }
}
