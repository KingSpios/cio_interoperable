package com.cio.createinteroperable.mixin.crayfish;

import com.cio.createinteroperable.iden.ElectricSwitchBlockEntity;
import com.mrcrayfish.furniture.refurbished.electricity.Connection;
import com.mrcrayfish.furniture.refurbished.electricity.IModuleNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
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
 * Re-attaches MrCrayfish's {@code IModuleNode} to CIO's
 * {@link ElectricSwitchBlockEntity} (the Iden's Decor Electric buttons and
 * switches) when Refurbished Furniture is installed &mdash; the same adapter
 * shape as {@link LetsDoLampNodeMixin}, plus {@code canPowerTraverseNode()},
 * which is what makes it a switch: Crayfish's {@code searchNodeNetwork} only
 * walks past it while its contacts are closed, exactly like Crayfish's own
 * {@code LightswitchBlockEntity}.
 */
@Mixin(ElectricSwitchBlockEntity.class)
@Implements(@Interface(iface = IModuleNode.class, prefix = "imn$"))
public abstract class ElectricSwitchNodeMixin {

    @Unique
    private final Set<Connection> cio$connections = new HashSet<>();
    @Unique
    private final Set<BlockPos> cio$powerSources = new HashSet<>();

    @Unique
    private ElectricSwitchBlockEntity cio$self() {
        return (ElectricSwitchBlockEntity) (Object) this;
    }

    @Unique
    private IModuleNode cio$node() {
        return (IModuleNode) (Object) this;
    }

    public BlockPos imn$getNodePosition() {
        return this.cio$self().getBlockPos();
    }

    public Level imn$getNodeLevel() {
        return this.cio$self().getLevel();
    }

    public BlockEntity imn$getNodeOwner() {
        return this.cio$self();
    }

    public Set<Connection> imn$getNodeConnections() {
        return this.cio$connections;
    }

    public Set<BlockPos> imn$getPowerSources() {
        return this.cio$powerSources;
    }

    public void imn$setNodeReceivingPower(boolean state) {
        this.cio$self().setApplianceReceivingPower(state);
    }

    public boolean imn$isNodeReceivingPower() {
        return this.cio$self().applianceReceivingPower();
    }

    public boolean imn$isNodePowered() {
        return this.cio$self().appliancePowered();
    }

    public void imn$setNodePowered(boolean powered) {
        this.cio$self().setAppliancePowered(powered);
    }

    public boolean imn$canPowerTraverseNode() {
        return this.cio$self().applianceTraversable();
    }

    public void imn$moduleTick(Level level) {
        if (level.isClientSide) {
            return;
        }
        this.cio$self().reconcileAppliancePower();
        this.cio$self().setApplianceReceivingPower(false);
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void cio$saveNode(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        this.cio$node().writeNodeNbt(tag);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void cio$loadNode(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        this.cio$node().readNodeNbt(tag);
    }

    @Inject(method = "saveToItem", at = @At("TAIL"))
    private void cio$saveNodeToItem(ItemStack stack, HolderLookup.Provider provider, CallbackInfo ci) {
        this.cio$node().saveNodeNbtToItem(stack, provider);
    }
}
