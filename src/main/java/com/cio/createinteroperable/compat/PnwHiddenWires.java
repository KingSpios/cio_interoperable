package com.cio.createinteroperable.compat;

import com.george_vi.electroenergetics.client.WireRenderer;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import net.createmod.catnip.data.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side filter that keeps the bridge's mirrored copies of PnW wires out
 * of CEE's renderer. Whether an end is a PnW node is read from the world, and
 * CEE sends a joining player its wires before the chunks holding their ends
 * arrive, so undecided wires are parked here and settled once both ends load.
 */
public final class PnwHiddenWires {
    private static final Map<InWorldNodeConnection, WireData> PENDING = new HashMap<>();
    /** Hidden taut (contact) wires: not drawn, but CEE's pantograph arm still has to find them. */
    private static final Map<InWorldNodeConnection, WireData> CONTACT = new LinkedHashMap<>();
    private static List<Pair<InWorldNodeConnection, WireData>> combined;
    private static List<?> combinedBase;
    private static int combinedBaseSize = -1;
    private static boolean releasing;
    private static int ticks;

    private PnwHiddenWires() {
    }

    private enum Verdict { HIDE, SHOW, UNKNOWN }

    /** Called from {@code WireRenderer.addConnection}; true cancels the add. */
    public static boolean intercept(InWorldNodeConnection connection, WireData data) {
        if (releasing) {
            return false;
        }
        return switch (verdict(Minecraft.getInstance().level, connection)) {
            case HIDE -> {
                PENDING.remove(connection);
                hide(connection, data);
                yield true;
            }
            case SHOW -> {
                PENDING.remove(connection);
                yield false;
            }
            case UNKNOWN -> {
                PENDING.put(connection, data);
                yield true;
            }
        };
    }

    public static void forget(InWorldNodeConnection connection) {
        PENDING.remove(connection);
        if (CONTACT.remove(connection) != null) {
            combined = null;
        }
    }

    public static void clear() {
        PENDING.clear();
        CONTACT.clear();
        combined = null;
    }

    private static void hide(InWorldNodeConnection connection, WireData data) {
        if (data.wireType().getSag() == 0) {
            CONTACT.put(connection, data);
            combined = null;
        }
    }

    /**
     * CEE's visible wires plus the hidden contact wires, for the pantograph
     * arm's wire search. Rebuilt only when either side changes.
     */
    public static List<Pair<InWorldNodeConnection, WireData>> withContactWires(List<Pair<InWorldNodeConnection, WireData>> visible) {
        if (CONTACT.isEmpty()) {
            return visible;
        }
        if (combined == null || combinedBase != visible || combinedBaseSize != visible.size()) {
            List<Pair<InWorldNodeConnection, WireData>> list = new ArrayList<>(visible.size() + CONTACT.size());
            list.addAll(visible);
            CONTACT.forEach((connection, data) -> list.add(Pair.of(connection, data)));
            combined = list;
            combinedBase = visible;
            combinedBaseSize = visible.size();
        }
        return combined;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (PENDING.isEmpty() || ++ticks % 5 != 0) {
            return;
        }
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            PENDING.clear();
            return;
        }
        for (Map.Entry<InWorldNodeConnection, WireData> entry : Map.copyOf(PENDING).entrySet()) {
            Verdict verdict = verdict(level, entry.getKey());
            if (verdict == Verdict.UNKNOWN) {
                continue;
            }
            PENDING.remove(entry.getKey());
            if (verdict == Verdict.HIDE) {
                hide(entry.getKey(), entry.getValue());
            } else {
                releasing = true;
                try {
                    WireRenderer.addConnection(entry.getKey(), entry.getValue());
                } finally {
                    releasing = false;
                }
            }
        }
    }

    private static Verdict verdict(Level level, InWorldNodeConnection connection) {
        if (level == null) {
            return Verdict.UNKNOWN;
        }
        Verdict a = end(level, connection.node1());
        Verdict b = end(level, connection.node2());
        if (a == Verdict.SHOW || b == Verdict.SHOW) {
            return Verdict.SHOW; // touches a native CEE node: always drawn
        }
        return a == Verdict.HIDE && b == Verdict.HIDE ? Verdict.HIDE : Verdict.UNKNOWN;
    }

    /** HIDE = a PnW node, SHOW = a native CEE node, UNKNOWN = its chunk has not arrived yet. */
    private static Verdict end(Level level, InWorldNode node) {
        if (node.id() >= PnwCeeNodes.DERIVED_ID_BASE) {
            return Verdict.HIDE;
        }
        if (!PnwCeeNodes.isChunkPresent(level, node.sourcePos())) {
            return Verdict.UNKNOWN;
        }
        return PnwCeeNodes.isConnector(level.getBlockState(node.sourcePos()).getBlock()) ? Verdict.HIDE : Verdict.SHOW;
    }
}
