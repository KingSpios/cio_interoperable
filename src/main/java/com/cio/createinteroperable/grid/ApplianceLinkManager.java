package com.cio.createinteroperable.grid;

import com.cio.createinteroperable.CreateInteroperable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Server-side node-to-node linking for the native appliance grid &mdash; a port
 * of MrCrayfish's {@code LinkManager}, minus the wrench item (an empty main hand
 * is the gate; the client raycasts the node cubes and fires
 * {@code MsgApplianceNodeInteract}).
 *
 * <p>First interact on a node arms a link from it; the second forms a reciprocal
 * {@link GridConnection}. Crouching on the second click re-arms from the new node
 * (chain-linking). Two sources never link to each other. The arm is dropped if
 * the player fills their hand or logs out.</p>
 */
public final class ApplianceLinkManager {

    /** Max link length (blocks, centre-to-centre). Matches CIO's default network reach. */
    public static final double MAX_LINK_LENGTH = 48.0;

    private static final Map<MinecraftServer, ApplianceLinkManager> INSTANCES = new WeakHashMap<>();

    private final Map<UUID, BlockPos> armedFrom = new HashMap<>();

    private ApplianceLinkManager() {
    }

    public static ApplianceLinkManager get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, s -> new ApplianceLinkManager());
    }

    public boolean isArmed(Player player) {
        return this.armedFrom.containsKey(player.getUUID());
    }

    /** The node the player is currently linking from, or {@code null}. */
    public BlockPos armedNode(Player player) {
        return this.armedFrom.get(player.getUUID());
    }

    public void onNodeInteract(Level level, Player player, ApplianceNode node) {
        if (node.applianceConnectionLimitReached()) {
            return;
        }
        if (arm(node.appliancePos(), level, player)) {
            return;
        }

        BlockPos fromPos = this.armedFrom.get(player.getUUID());
        ApplianceNode other = fromPos != null && level.getBlockEntity(fromPos) instanceof ApplianceNode n ? n : null;
        if (other == null || other == node) {
            return;
        }
        if (other.isApplianceSource() && node.isApplianceSource()) {
            return;
        }
        double distance = other.appliancePos().getCenter().distanceTo(node.appliancePos().getCenter());
        if (distance > MAX_LINK_LENGTH) {
            this.armedFrom.remove(player.getUUID());
            notify(player, null);
            return;
        }

        // The CPG and CEE grids must not intersect through a shared appliance:
        // reject a link that would join two components each already holding a
        // Domestic Electrical Board of a different grid.
        GridAffinity ga = ApplianceGridTags.componentGrid(level, other);
        GridAffinity gb = ApplianceGridTags.componentGrid(level, node);
        if (ga != GridAffinity.NONE && gb != GridAffinity.NONE && ga != gb) {
            this.armedFrom.remove(player.getUUID());
            level.playSound(null, node.appliancePos(), SoundEvents.NOTE_BLOCK_BASS.value(),
                    SoundSource.BLOCKS, 0.7f, 0.6f);
            notify(player, null);
            return;
        }

        this.armedFrom.remove(player.getUUID());
        other.connectApplianceTo(node);

        if (player.isCrouching() && arm(node.appliancePos(), level, player)) {
            return;
        }
        level.playSound(null, node.appliancePos(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7f, 1.4f);
        notify(player, null);
    }

    /** Clear every link on a node (empty-hand sneak-attack on its cube). */
    public void onNodeDelete(Level level, Player player, ApplianceNode node) {
        if (node.applianceConnections().isEmpty()) {
            return;
        }
        node.removeAllApplianceConnections();
        level.playSound(null, node.appliancePos(), SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.7f, 0.9f);
        this.armedFrom.remove(player.getUUID());
        notify(player, null);
    }

    private boolean arm(BlockPos pos, Level level, Player player) {
        if (this.armedFrom.containsKey(player.getUUID())) {
            return false;
        }
        this.armedFrom.put(player.getUUID(), pos.immutable());
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.7f, 1.6f);
        notify(player, pos);
        return true;
    }

    public void onPlayerTick(Player player) {
        if (!this.armedFrom.containsKey(player.getUUID())) {
            return;
        }
        // Drop the pending link if the player is no longer holding a connector
        // (mirrors Crayfish cancelling when the wrench leaves the hand).
        if (ApplianceGridTags.heldConnectorGrid(player) == null) {
            this.armedFrom.remove(player.getUUID());
            notify(player, null);
        }
    }

    public void onPlayerLoggedOut(Player player) {
        this.armedFrom.remove(player.getUUID());
    }

    private static void notify(Player player, BlockPos from) {
        if (player instanceof ServerPlayer sp) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
                    new com.cio.createinteroperable.MsgApplianceLinkState(java.util.Optional.ofNullable(from)));
        }
        CreateInteroperable.LOGGER.debug("[grid] link state for {} -> {}", player.getGameProfile().getName(), from);
    }
}
