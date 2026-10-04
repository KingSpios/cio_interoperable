package com.cio.createinteroperable.mixin.letsdo;

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
 * Crayfish adapter for Iden's Decor's Computer &mdash; bolts {@code IModuleNode}
 * onto {@link IdenComputerNodeMixin} and forwards power state to it. Same shape
 * as {@code VistaTvBlockEntityMixin}: every {@link ApplianceNode} cast is
 * null-checked (the node half lives in a separate {@code required: false}
 * config, so a failed merge there must degrade, not crash Crayfish's ticker),
 * the interact box is the node half's (clear of the computer's solid body), and
 * {@code isNodeInPowerableNetwork()} reports whether it's actually powered so
 * the look-at "Missing power" label is truthful. Applied only with Refurbished
 * Furniture installed ({@code CrayfishMixinPlugin}).
 */
@Mixin(targets = "net.identidade.iden_decor.blockentity.ComputerBlockEntity", remap = false)
@Implements(@Interface(iface = IModuleNode.class, prefix = "imn$"))
public abstract class IdenComputerBlockEntityMixin {

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

    public void imn$setNodePowered(boolean powered) {
        ApplianceNode node = cioCf$node();
        if (node != null) {
            node.setAppliancePowered(powered);
        }
    }

    public AABB imn$getNodeInteractBox() {
        ApplianceNode node = cioCf$node();
        return node != null ? node.applianceNodeBox() : ApplianceNode.APPLIANCE_NODE_BOX;
    }

    /** Client-side overlay check: "Missing power" whenever the screen would be dark. */
    public boolean imn$isNodeInPowerableNetwork() {
        ApplianceNode node = cioCf$node();
        return node != null && node.appliancePowered();
    }

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

    @Inject(method = "saveAdditional", at = @At("TAIL"), remap = false)
    private void cioCf$saveNode(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        cioCf$imn().writeNodeNbt(tag);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"), remap = false)
    private void cioCf$loadNode(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        cioCf$imn().readNodeNbt(tag);
    }
}
