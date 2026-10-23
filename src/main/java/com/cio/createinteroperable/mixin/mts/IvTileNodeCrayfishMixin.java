package com.cio.createinteroperable.mixin.mts;

import com.cio.createinteroperable.grid.ApplianceNode;
import com.cio.createinteroperable.mts.IvNodeState;
import com.mrcrayfish.furniture.refurbished.electricity.Connection;
import com.mrcrayfish.furniture.refurbished.electricity.IModuleNode;
import mcinterface1211.BuilderTileEntity;
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
 * Re-attaches MrCrayfish's {@code IModuleNode} to Immersive Vehicles' block
 * entity when Refurbished Furniture is installed, forwarding to its
 * {@link ApplianceNode} half ({@code IvTileNodeMixin}) &mdash; the same adapter
 * shape as the Vista TV's.
 *
 * <p>Crayfish adopts every block entity implementing its interface, so every IV
 * tile joins its ticker; the ones that aren't poles or Signal Controllers get
 * zero link slots and a node box parked out of reach, so they can never be
 * wired and their per-tick cost is an early return. The look-at
 * "Missing power" label shows only on a pole that actually carries lamps and
 * has none ({@link IvNodeState}) &mdash; never on a bare pole or a controller,
 * which don't need power.</p>
 *
 * <p>Applied only with both mods present ({@code MtsCrayfishMixinPlugin}).</p>
 */
@Mixin(value = BuilderTileEntity.class, remap = false)
@Implements(@Interface(iface = IModuleNode.class, prefix = "imn$"))
public abstract class IvTileNodeCrayfishMixin {

    @Unique
    private final Set<Connection> cioCf$connections = new HashSet<>();
    @Unique
    private final Set<BlockPos> cioCf$powerSources = new HashSet<>();

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
    public Set<Connection> imn$getNodeConnections() { return this.cioCf$connections; }
    public Set<BlockPos> imn$getPowerSources() { return this.cioCf$powerSources; }
    public void imn$setNodeReceivingPower(boolean state) { cioCf$node().setApplianceReceivingPower(state); }
    public boolean imn$isNodeReceivingPower() { return cioCf$node().applianceReceivingPower(); }
    public boolean imn$isNodePowered() { return cioCf$node().appliancePowered(); }
    public void imn$setNodePowered(boolean powered) { cioCf$node().setAppliancePowered(powered); }
    public AABB imn$getNodeInteractBox() { return cioCf$node().applianceNodeBox(); }
    public int imn$getNodeMaximumConnections() { return cioCf$node().applianceConnectionLimit(); }

    /** Client-side overlay check: "Missing power" only for a pole whose lamps are dark for lack of it. */
    public boolean imn$isNodeInPowerableNetwork() {
        IvNodeState state = (IvNodeState) (Object) this;
        return !state.cio$needsPower() || state.cio$lightsPowered();
    }

    public void imn$moduleTick(Level level) {
        if (level.isClientSide || cioCf$node().applianceConnectionLimit() == 0) {
            return;
        }
        cioCf$node().reconcileAppliancePower();
        cioCf$node().setApplianceReceivingPower(false);
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
