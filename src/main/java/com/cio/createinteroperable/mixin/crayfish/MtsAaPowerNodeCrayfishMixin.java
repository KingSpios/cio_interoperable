package com.cio.createinteroperable.mixin.crayfish;

import com.cio.createinteroperable.mts.MtsAaPowerNodeBlockEntity;
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
 * Re-attaches MrCrayfish's {@code IModuleNode} to the Immersive Vehicles AA
 * Base Plate's power terminal ({@link MtsAaPowerNodeBlockEntity}) when
 * Refurbished Furniture is installed, forwarding power state to its
 * {@link com.cio.createinteroperable.grid.ApplianceNode} half &mdash; the same
 * adapter shape as {@link LetsDoLampNodeMixin}. The interact box is the nub on
 * the plate's corner, and {@code isNodeInPowerableNetwork()} reports actual
 * power so Crayfish's look-at "Missing power" label is truthful.
 */
@Mixin(MtsAaPowerNodeBlockEntity.class)
@Implements(@Interface(iface = IModuleNode.class, prefix = "imn$"))
public abstract class MtsAaPowerNodeCrayfishMixin {

    @Unique
    private final Set<Connection> cio$connections = new HashSet<>();
    @Unique
    private final Set<BlockPos> cio$powerSources = new HashSet<>();

    @Unique
    private MtsAaPowerNodeBlockEntity cio$self() {
        return (MtsAaPowerNodeBlockEntity) (Object) this;
    }

    @Unique
    private IModuleNode cio$node() {
        return (IModuleNode) (Object) this;
    }

    public BlockPos imn$getNodePosition() { return cio$self().getBlockPos(); }
    public Level imn$getNodeLevel() { return cio$self().getLevel(); }
    public BlockEntity imn$getNodeOwner() { return cio$self(); }
    public Set<Connection> imn$getNodeConnections() { return this.cio$connections; }
    public Set<BlockPos> imn$getPowerSources() { return this.cio$powerSources; }
    public void imn$setNodeReceivingPower(boolean state) { cio$self().setApplianceReceivingPower(state); }
    public boolean imn$isNodeReceivingPower() { return cio$self().applianceReceivingPower(); }
    public boolean imn$isNodePowered() { return cio$self().appliancePowered(); }
    public void imn$setNodePowered(boolean powered) { cio$self().setAppliancePowered(powered); }
    public AABB imn$getNodeInteractBox() { return cio$self().applianceNodeBox(); }

    /** Client-side overlay check: "Missing power" whenever the plate would leave its Spotlight dark. */
    public boolean imn$isNodeInPowerableNetwork() {
        return cio$self().appliancePowered();
    }

    public void imn$moduleTick(Level level) {
        if (level.isClientSide) {
            return;
        }
        cio$self().reconcileAppliancePower();
        cio$self().setApplianceReceivingPower(false);
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void cio$saveNode(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        cio$node().writeNodeNbt(tag);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void cio$loadNode(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        cio$node().readNodeNbt(tag);
    }
}
