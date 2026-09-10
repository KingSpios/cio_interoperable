package com.cio.createinteroperable.mixin.letsdo;

import com.cio.createinteroperable.grid.ApplianceNode;
import com.mrcrayfish.furniture.refurbished.electricity.Connection;
import com.mrcrayfish.furniture.refurbished.electricity.IModuleNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
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
 * Crayfish adapter for the Gramophone: bolts {@code IModuleNode} on top of
 * {@link GramophoneNodeMixin} (the real implementation) so Crayfish's ticker,
 * wrench linking, wire renderer and "no power" overlay work, forwarding power
 * state straight through to the {@link ApplianceNode} half. Applied only when
 * Refurbished Furniture is installed ({@code CrayfishMixinPlugin}).
 */
@Mixin(targets = "com.berksire.furniture.core.block.entity.GramophoneBlockEntity")
@Implements(@Interface(iface = IModuleNode.class, prefix = "imn$"))
public abstract class GramophoneBlockEntityMixin {

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

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void cioCf$saveNode(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        cioCf$imn().writeNodeNbt(tag);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void cioCf$loadNode(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        cioCf$imn().readNodeNbt(tag);
    }
}
