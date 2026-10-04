package com.cio.createinteroperable.mixin.vista;

import com.cio.createinteroperable.grid.ApplianceNode;
import com.mrcrayfish.furniture.refurbished.electricity.Connection;
import com.mrcrayfish.furniture.refurbished.electricity.IModuleNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

/**
 * Crayfish adapter for Vista's TV: bolts {@code IModuleNode} on top of
 * {@link VistaTvNodeMixin} (the real implementation) so Crayfish's ticker,
 * wrench linking, wire renderer and "no power" overlay work, forwarding power
 * state straight through to the {@link ApplianceNode} half. Applied only when
 * Refurbished Furniture is installed ({@code CrayfishMixinPlugin}).
 *
 * <p>{@link #imn$moduleTick} is overridden (rather than left to {@code
 * IModuleNode}'s default) so {@link VistaTvNodeMixin#reconcileAppliancePower()}
 * &mdash; the DEB-feed-plus-redstone-kill-switch verdict, flooded onto the
 * whole connected wall &mdash; runs under the Crayfish backend too, the same
 * shape as {@code CrnDisplayCrayfishMixin}.</p>
 *
 * <p><b>Every {@code cioCf$node()} call is null-checked</b> (a real crash,
 * fixed 2026-09-12: {@code TVBlockEntity cannot be cast to ApplianceNode} —
 * {@code VistaTvNodeMixin}, a <em>separate</em> {@code required: false} mixin
 * config, silently failed to merge onto an existing world's TV while this
 * config's mixin merged fine, so the blind cast in {@code cioCf$node()} threw
 * mid-tick from Crayfish's own {@code ElectricityTicker} with no player even
 * involved). Whatever the reason the two configs' mixins can disagree on a
 * given class instance, this adapter must never assume the other one
 * succeeded — degrade to "no CIO power info for this TV" instead of crashing
 * the whole level tick.</p>
 *
 * <p><b>Vista 5.5.x ships its own {@code IModuleNode} on the same class</b>
 * ({@code CompatRefurbishedFurnitureSelfTvBlockEntityMixin}), gated on Vista's
 * {@code television.use_furniture_electricity} config: with that off (the
 * default), its {@code getNodeMaximumConnections()} returns 0, so a TV read
 * 0/0 links and could never be wired. Two mixins adding the same method
 * resolve by priority (an equal-priority later one is skipped), hence the
 * raised {@code priority} here, and every {@code IModuleNode} method Vista
 * defines is overridden below so none of Vista's copies survive.</p>
 */
@Mixin(targets = "net.mehvahdjukaar.vista.common.tv.TVBlockEntity", priority = 1100)
@Implements(@Interface(iface = IModuleNode.class, prefix = "imn$"))
public abstract class VistaTvBlockEntityMixin {

    @Unique private final Set<Connection> cioCf$conns = new HashSet<>();
    @Unique private final Set<BlockPos> cioCf$sources = new HashSet<>();

    @Unique
    private BlockEntity cioCf$be() {
        return (BlockEntity) (Object) this;
    }

    @Unique
    @Nullable
    private ApplianceNode cioCf$node() {
        return (Object) this instanceof ApplianceNode node ? node : null;
    }

    @Unique
    private IModuleNode cioCf$imn() {
        return (IModuleNode) (Object) this;
    }

    public BlockPos imn$getNodePosition() { return cioCf$be().getBlockPos(); }
    public Level imn$getNodeLevel() { return cioCf$be().getLevel(); }
    public BlockEntity imn$getNodeOwner() { return cioCf$be(); }
    public Set<Connection> imn$getNodeConnections() { return this.cioCf$conns; }
    public Set<BlockPos> imn$getPowerSources() { return this.cioCf$sources; }

    public void imn$setNodeReceivingPower(boolean state) {
        ApplianceNode node = cioCf$node();
        if (node != null) {
            node.setApplianceReceivingPower(state);
        }
    }

    public boolean imn$isNodeReceivingPower() {
        ApplianceNode node = cioCf$node();
        return node != null && node.applianceReceivingPower();
    }

    public boolean imn$isNodePowered() {
        ApplianceNode node = cioCf$node();
        return node != null && node.appliancePowered();
    }

    /**
     * Share the {@link ApplianceNode} half's box &mdash; pushed proud of the
     * screen so the wrench-link raycast and the look-at "Missing power" label
     * both clear the TV's own opaque panel, instead of Crayfish's own default
     * (buried at cell centre, unreachable). Same fix as {@code
     * CrnDisplayCrayfishMixin#imn$getNodeInteractBox}.
     */
    public AABB imn$getNodeInteractBox() {
        ApplianceNode node = cioCf$node();
        return node != null ? node.applianceNodeBox() : ApplianceNode.APPLIANCE_NODE_BOX;
    }

    /**
     * Crayfish's {@code NodeIndicatorOverlay} shows the look-at "Missing
     * power" label whenever the targeted node's {@code
     * isNodeInPowerableNetwork()} is false, and the {@code IElectricityNode}
     * default reads {@code getPowerSources()} &mdash; filled whenever a wire
     * physically reaches this node, regardless of whether that rail is
     * actually live right now (brownout, tripped board, or the redstone kill
     * switch). Report the real combined verdict instead, same fix as {@code
     * CrnDisplayCrayfishMixin#imn$isNodeInPowerableNetwork} &mdash; a wired TV
     * that's off for any of those reasons should still show "Missing power",
     * not silently pass the check because a wire happens to reach it.
     */
    public boolean imn$isNodeInPowerableNetwork() {
        ApplianceNode node = cioCf$node();
        return !this.cioCf$sources.isEmpty() || (node != null && node.appliancePowered());
    }

    /** CIO's own link budget; Vista's copy returns 0 while its own electricity option is off. */
    public int imn$getNodeMaximumConnections() {
        ApplianceNode node = cioCf$node();
        return node != null ? node.applianceConnectionLimit() : 0;
    }

    /** Vista's copy is a no-op while its own electricity option is off; fold into CIO's verdict instead. */
    public void imn$updateNodePoweredState() {
        ApplianceNode node = cioCf$node();
        if (node != null) {
            node.reconcileAppliancePower();
        }
    }

    public void imn$setNodePowered(boolean powered) {
        ApplianceNode node = cioCf$node();
        if (node != null) {
            node.setAppliancePowered(powered);
        }
    }

    /** Crayfish's {@code ElectricityTicker} calls this every tick for every module node. */
    public void imn$moduleTick(Level level) {
        if (level.isClientSide) {
            return;
        }
        ApplianceNode node = cioCf$node();
        if (node == null) {
            return;
        }
        node.reconcileAppliancePower();
        node.setApplianceReceivingPower(false);
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void cioCf$saveNode(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        cioCf$imn().writeNodeNbt(tag);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void cioCf$loadNode(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        cioCf$imn().readNodeNbt(tag);
    }
}
