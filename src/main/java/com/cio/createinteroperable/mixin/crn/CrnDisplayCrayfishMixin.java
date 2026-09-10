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
 * IModuleNode}'s default) so the controller's whole-panel power aggregation in
 * {@link CrnDisplayNodeMixin#reconcileAppliancePower()} runs under the Crayfish
 * backend too &mdash; the same shape as {@code LetsDoLampNodeMixin}.</p>
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
