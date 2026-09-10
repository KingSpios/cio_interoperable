package com.cio.createinteroperable.mixin.crn;

import com.cio.createinteroperable.grid.ApplianceNode;
import com.mrcrayfish.furniture.refurbished.electricity.Connection;
import com.mrcrayfish.furniture.refurbished.electricity.IModuleNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
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
 * Crayfish adapter for the Advanced Display: bolts {@code IModuleNode} on top of
 * {@link CrnDisplayNodeMixin} (the real implementation) so Crayfish's ticker,
 * wrench linking, wire renderer and "no power" overlay work, forwarding power
 * state straight through to the {@link ApplianceNode} half. Applied only when
 * Refurbished Furniture is installed ({@code CrayfishMixinPlugin}).
 *
 * <p>{@link #imn$moduleTick} is overridden (rather than left to {@code
 * IModuleNode}'s default) so the board-wide power flood-fill in
 * {@link CrnDisplayNodeMixin#reconcileAppliancePower()} runs under the Crayfish
 * backend too &mdash; the same shape as {@code LetsDoLampNodeMixin}.
 * {@link #imn$isNodeInPowerableNetwork()} is likewise overridden so Crayfish's
 * look-at "Missing power" label follows that same board-wide verdict.</p>
 */
@Mixin(targets = "de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity")
@Implements(@Interface(iface = IModuleNode.class, prefix = "imn$"))
public abstract class CrnDisplayCrayfishMixin {

    @Unique private final Set<Connection> cioCf$conns = new HashSet<>();
    @Unique private final Set<BlockPos> cioCf$sources = new HashSet<>();

    @Unique
    private BlockEntity cioCf$be() {
        return (BlockEntity) (Object) this;
    }

    @Unique
    private ApplianceNode cioCf$node() {
        return (ApplianceNode) (Object) this;
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
    public void imn$setNodeReceivingPower(boolean state) { cioCf$node().setApplianceReceivingPower(state); }
    public boolean imn$isNodeReceivingPower() { return cioCf$node().applianceReceivingPower(); }
    public boolean imn$isNodePowered() { return cioCf$node().appliancePowered(); }
    public void imn$setNodePowered(boolean powered) { cioCf$node().setAppliancePowered(powered); }

    /** Share the {@link com.cio.createinteroperable.grid.ApplianceNode} half's box &mdash; pushed proud of the screen so it clears the opaque panel. */
    public AABB imn$getNodeInteractBox() { return cioCf$node().applianceNodeBox(); }

    /**
     * Crayfish's {@code NodeIndicatorOverlay} shows the look-at "Missing power"
     * label whenever the targeted node's {@code isNodeInPowerableNetwork()} is
     * false, and the {@code IElectricityNode} default reads
     * {@code getPowerSources()} &mdash; which the source only fills for nodes it
     * reaches <em>by wire</em>. On a CRN board that is just the block(s) the
     * player actually wired, so every other member of a genuinely-powered board
     * still showed "Missing power" on look-at. Report the whole board as
     * networked whenever it is powered &mdash; the board-wide verdict from
     * {@link CrnDisplayNodeMixin#reconcileAppliancePower()} &mdash; while
     * keeping the stock wire-reachability test for anything it already covers.
     */
    public boolean imn$isNodeInPowerableNetwork() {
        return !this.cioCf$sources.isEmpty() || cioCf$node().appliancePowered();
    }

    /** Crayfish's {@code ElectricityTicker} calls this every tick for every module node. */
    public void imn$moduleTick(Level level) {
        if (level.isClientSide) {
            return;
        }
        cioCf$node().reconcileAppliancePower();
        cioCf$node().setApplianceReceivingPower(false);
    }

    @Inject(method = "write(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;Z)V",
            at = @At("TAIL"))
    private void cioCf$saveNode(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket, CallbackInfo ci) {
        cioCf$imn().writeNodeNbt(tag);
    }

    @Inject(method = "read(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;Z)V",
            at = @At("TAIL"))
    private void cioCf$loadNode(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket, CallbackInfo ci) {
        cioCf$imn().readNodeNbt(tag);
    }
}
