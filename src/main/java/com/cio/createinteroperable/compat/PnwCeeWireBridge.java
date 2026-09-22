package com.cio.createinteroperable.compat;

import com.cio.createinteroperable.CreateInteroperable;
import com.george_vi.electroenergetics.CEEWireTypes;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Experimental electrical backend for PnW's existing wire graph.
 *
 * <p>PnW remains the owner of placement, rendering, and collision. Each of
 * its conductive logical edges is represented by one CEE copper connection
 * between node {@code 0} at its endpoint blocks. CEE therefore supplies the
 * resistance/temperature simulation; if CEE removes the mirrored connection,
 * the original PnW edge is removed on the next server tick.</p>
 *
 * <p>This intentionally uses reflection: PnW is a soft dependency and is not
 * published to a Maven repository used by this project. The two mixins call
 * this class only when both optional mods are present.</p>
 */
public final class PnwCeeWireBridge {
    private static final ResourceLocation ENERGY_WIRE = ResourceLocation.fromNamespaceAndPath("pantographsandwires", "energy_wire");
    private static final ResourceLocation CATENARY_WIRE = ResourceLocation.fromNamespaceAndPath("pantographsandwires", "catenary_wire");
    private static final Map<EdgeKey, Mirror> MIRRORS = new HashMap<>();
    private static long ticks;

    private PnwCeeWireBridge() {
    }

    /** Called after PnW has added or rebuilt an edge. Arguments deliberately use Object to keep PnW optional. */
    public static void upsert(Object graph, Object edge) {
        try {
            Level level = (Level) call(graph, "getLevel");
            if (!(level instanceof ServerLevel server) || !isConductive(edge)) {
                return;
            }

            UUID id = (UUID) call(edge, "getId");
            EdgeKey key = new EdgeKey(server.dimension().location(), id);
            removeMirror(server, key, false);

            Object nodeA = call(graph, "getNode", call(edge, "getNodeAId"));
            Object nodeB = call(graph, "getNode", call(edge, "getNodeBId"));
            Vec3 a = vector(call(nodeA, "getPos"));
            Vec3 b = vector(call(nodeB, "getPos"));
            BlockPos posA = BlockPos.containing(a);
            BlockPos posB = BlockPos.containing(b);
            if (posA.equals(posB)) {
                return;
            }

            InfrastructureSavedData data = InfrastructureSavedData.load(server);
            InWorldNode ceeA = new InWorldNode(0, posA);
            InWorldNode ceeB = new InWorldNode(0, posB);
            ensureNode(data, ceeA, a);
            ensureNode(data, ceeB, b);

            // CEE has one physical connection per node pair. Do not overwrite
            // a player-created CEE wire that happens to use the same terminals.
            if (data.isConnected(ceeA, ceeB)) {
                CreateInteroperable.LOGGER.warn("PnW edge {} was not mirrored: CEE already has a connection between its endpoints.", id);
                return;
            }

            data.connect(ceeA, ceeB, WireData.ofLength(CEEWireTypes.COPPER.get(), a.distanceTo(b)));
            MIRRORS.put(key, new Mirror(graph, new InWorldNodeConnection(ceeA, ceeB), a));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            CreateInteroperable.LOGGER.error("Could not mirror a Pantographs & Wires edge into Electro Energetics.", exception);
        }
    }

    /** Called before PnW removes an edge through its public removal path. */
    public static void remove(Object graph, UUID id) {
        try {
            Level level = (Level) call(graph, "getLevel");
            if (level instanceof ServerLevel server) {
                removeMirror(server, new EdgeKey(server.dimension().location(), id), true);
            }
        } catch (ReflectiveOperationException exception) {
            CreateInteroperable.LOGGER.error("Could not remove the CEE mirror for a Pantographs & Wires edge.", exception);
        }
    }

    /** Reconciles loaded PnW graphs and turns CEE burnouts into PnW wire removal. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticks % 20 != 0) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            reconcile(level);
        }
    }

    private static void reconcile(ServerLevel level) {
        InfrastructureSavedData data = InfrastructureSavedData.load(level);
        for (Map.Entry<EdgeKey, Mirror> entry : Map.copyOf(MIRRORS).entrySet()) {
            if (!entry.getKey().dimension.equals(level.dimension().location())) {
                continue;
            }
            if (data.getConnectionData(entry.getValue().connection) == null) {
                // WireLifetimeModule has destroyed/replaced the CEE wire. PnW
                // owns its visual edge, so remove that edge as the visible burn.
                try {
                    UUID id = entry.getKey().id;
                    call(entry.getValue().graph, "removeEdge", id, entry.getValue().breakPosition, Optional.empty());
                } catch (ReflectiveOperationException exception) {
                    CreateInteroperable.LOGGER.error("CEE burned a PnW mirror, but the PnW edge could not be removed.", exception);
                }
                MIRRORS.remove(entry.getKey());
            }
        }

        // Covers edges restored from PnW's SavedData after a server restart.
        // It also makes this test resilient if PnW updates an edge internally.
        try {
            Class<?> manager = Class.forName("de.mrjulsen.wires.graph.WireGraphManager");
            Collection<?> graphs = (Collection<?>) manager.getMethod("getAll", Level.class).invoke(null, level);
            for (Object graph : graphs) {
                Collection<?> edges = (Collection<?>) call(graph, "getEdges");
                for (Object edge : edges) {
                    EdgeKey key = new EdgeKey(level.dimension().location(), (UUID) call(edge, "getId"));
                    if (!MIRRORS.containsKey(key)) {
                        upsert(graph, edge);
                    }
                }
            }
        } catch (ReflectiveOperationException exception) {
            CreateInteroperable.LOGGER.error("Could not reconcile Pantographs & Wires edges with CEE.", exception);
        }
    }

    private static void ensureNode(InfrastructureSavedData data, InWorldNode node, Vec3 position) {
        if (!data.hasNode(node)) {
            Vec3 local = position.subtract(node.sourcePos().getX(), node.sourcePos().getY(), node.sourcePos().getZ());
            data.createNode(node, local, position);
        }
    }

    private static void removeMirror(ServerLevel level, EdgeKey key, boolean removeCeeConnection) {
        Mirror mirror = MIRRORS.remove(key);
        if (mirror != null && removeCeeConnection) {
            InfrastructureSavedData.load(level).removeConnection(mirror.connection);
        }
    }

    private static boolean isConductive(Object edge) throws ReflectiveOperationException {
        Object type = call(edge, "getType");
        Object id = call(type, "getRegistryId");
        return ENERGY_WIRE.equals(id) || CATENARY_WIRE.equals(id);
    }

    private static Vec3 vector(Object vector) throws ReflectiveOperationException {
        double x = ((Number) call(vector, "x")).doubleValue();
        double y = ((Number) call(vector, "y")).doubleValue();
        double z = ((Number) call(vector, "z")).doubleValue();
        return new Vec3(x, y, z);
    }

    private static Object call(Object target, String method, Object... arguments) throws ReflectiveOperationException {
        for (Method candidate : target.getClass().getMethods()) {
            if (candidate.getName().equals(method) && candidate.getParameterCount() == arguments.length) {
                return candidate.invoke(target, arguments);
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "#" + method);
    }

    private record EdgeKey(ResourceLocation dimension, UUID id) {
    }

    private record Mirror(Object graph, InWorldNodeConnection connection, Object breakPosition) {
    }
}
