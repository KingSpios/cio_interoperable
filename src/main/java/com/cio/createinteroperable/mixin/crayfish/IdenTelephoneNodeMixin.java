package com.cio.createinteroperable.mixin.crayfish;

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
 * Re-attaches MrCrayfish's {@code IModuleNode} to both Iden's Decor telephone
 * block entities when Refurbished Furniture is installed, forwarding power
 * state to their {@link ApplianceNode} half ({@code IdenPhone}) &mdash; the same
 * adapter shape as {@link ElectricSwitchNodeMixin}. The interact box is the
 * phone's own (above the handset, clear of the cradle), and
 * {@code isNodeInPowerableNetwork()} reports actual power so the look-at
 * "Missing power" label is truthful. NBT rides on Create's
 * {@code SmartBlockEntity} {@code read}/{@code write}, like the Power Kit's.
 */
@Mixin(targets = {
        "com.cio.createinteroperable.iden.IdenPgTelephoneBlockEntity",
        "com.cio.createinteroperable.iden.IdenCeeTelephoneBlockEntity"})
@Implements(@Interface(iface = IModuleNode.class, prefix = "imn$"))
public abstract class IdenTelephoneNodeMixin {

    @Unique
    private final Set<Connection> cio$connections = new HashSet<>();
    @Unique
    private final Set<BlockPos> cio$powerSources = new HashSet<>();

    @Unique
    private BlockEntity cio$be() {
        return (BlockEntity) (Object) this;
    }

    @Unique
    private ApplianceNode cio$node() {
        return (ApplianceNode) (Object) this;
    }

    @Unique
    private IModuleNode cio$imn() {
        return (IModuleNode) (Object) this;
    }

    public BlockPos imn$getNodePosition() { return cio$be().getBlockPos(); }
    public Level imn$getNodeLevel() { return cio$be().getLevel(); }
    public BlockEntity imn$getNodeOwner() { return cio$be(); }
    public Set<Connection> imn$getNodeConnections() { return this.cio$connections; }
    public Set<BlockPos> imn$getPowerSources() { return this.cio$powerSources; }
    public void imn$setNodeReceivingPower(boolean state) { cio$node().setApplianceReceivingPower(state); }
    public boolean imn$isNodeReceivingPower() { return cio$node().applianceReceivingPower(); }
    public boolean imn$isNodePowered() { return cio$node().appliancePowered(); }
    public void imn$setNodePowered(boolean powered) { cio$node().setAppliancePowered(powered); }
    public AABB imn$getNodeInteractBox() { return cio$node().applianceNodeBox(); }

    /** Client-side overlay check: "Missing power" whenever the phone would be dead. */
    public boolean imn$isNodeInPowerableNetwork() {
        return cio$node().appliancePowered();
    }

    public void imn$moduleTick(Level level) {
        if (level.isClientSide) {
            return;
        }
        cio$node().reconcileAppliancePower();
        cio$node().setApplianceReceivingPower(false);
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void cio$readNode(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        cio$imn().readNodeNbt(tag);
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void cio$writeNode(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        cio$imn().writeNodeNbt(tag);
    }
}
