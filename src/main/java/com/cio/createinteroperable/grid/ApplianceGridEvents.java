package com.cio.createinteroperable.grid;

import com.cio.createinteroperable.CreateInteroperable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Ties a connector-in-hand right-click / sneak-left-click on an appliance node
 * cube to the native {@link ApplianceLinkManager} &mdash; the no-wrench-item
 * analogue of MrCrayfish's {@code NeoForgeEvents.onRightClickBlock} +
 * {@code WrenchItem}. A CPG connector ({@link ApplianceGridTags#CPG_CONNECTORS})
 * acts on CPG nodes, a CEE connector on CEE nodes; an unconnected appliance
 * accepts either. Fires on both dists; the link work runs only on the
 * {@link ServerPlayer} branch. A no-op while Refurbished Furniture is installed.
 */
@EventBusSubscriber(modid = CreateInteroperable.ID)
public final class ApplianceGridEvents {

    private static final double REACH = 5.0;

    private ApplianceGridEvents() {
    }

    /** null = don't act; otherwise the node pos to act on. */
    private static BlockPos targetNode(Player player) {
        if (CrayfishCompat.present() || player.isSpectator()) {
            return null;
        }
        if (ApplianceGridTags.heldConnectorGrid(player) == null) {
            return null;
        }
        return ApplianceRaycast.nodeUnderCrosshair(player, 1.0f, REACH);
    }

    private static boolean gridMatches(Level level, BlockPos nodePos, Player player) {
        GridAffinity held = ApplianceGridTags.heldConnectorGrid(player);
        return held != null
                && level.getBlockEntity(nodePos) instanceof ApplianceNode node
                && ApplianceGridTags.componentGrid(level, node).matches(held);
    }

    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        BlockPos nodePos = targetNode(player);
        if (nodePos == null || !gridMatches(player.level(), nodePos, player)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (player instanceof ServerPlayer sp
                && player.level().getBlockEntity(nodePos) instanceof ApplianceNode node) {
            ApplianceLinkManager.get(sp.server).onNodeInteract(player.level(), player, node);
        }
    }

    @SubscribeEvent
    static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        if (!player.isCrouching()) {
            return;
        }
        BlockPos nodePos = targetNode(player);
        if (nodePos == null || !gridMatches(player.level(), nodePos, player)) {
            return;
        }
        event.setCanceled(true);
        if (player instanceof ServerPlayer sp
                && player.level().getBlockEntity(nodePos) instanceof ApplianceNode node) {
            ApplianceLinkManager.get(sp.server).onNodeDelete(player.level(), player, node);
        }
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!CrayfishCompat.present() && event.getEntity() instanceof ServerPlayer sp) {
            ApplianceLinkManager.get(sp.server).onPlayerTick(sp);
        }
    }

    @SubscribeEvent
    static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!CrayfishCompat.present() && event.getEntity() instanceof ServerPlayer sp) {
            ApplianceLinkManager.get(sp.server).onPlayerLoggedOut(sp);
        }
    }
}
