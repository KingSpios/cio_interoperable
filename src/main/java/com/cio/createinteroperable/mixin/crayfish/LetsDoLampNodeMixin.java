package com.cio.createinteroperable.mixin.crayfish;

import com.cio.createinteroperable.letsdo.LetsDoLampBlockEntity;
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
 * Re-attaches MrCrayfish's {@code IModuleNode} to Create: Interoperable's own
 * {@link LetsDoLampBlockEntity} when Refurbished Furniture is installed, so
 * Crayfish's electricity ticker, wrench linking, wire renderer and "no power"
 * overlay keep driving the lamp node exactly as they did before the node model
 * was moved onto {@link com.cio.createinteroperable.grid.ApplianceNode}.
 *
 * <p>Soft {@link Implements} rather than a hard {@code implements} clause, and
 * the whole {@code createinteroperable.crayfish.mixin.json} config is skipped by
 * {@link CrayfishMixinPlugin} when Crayfish is absent &mdash; so nothing here
 * ever resolves {@code com.mrcrayfish.*} in that case.</p>
 *
 * <p>The one intentional behavioural difference from the old direct
 * implementation: Crayfish's debug "everything is powered" cheat is not
 * reproduced (it lived in {@code IModuleNode#updateNodePoweredState}'s default,
 * which this no longer routes through). Everything else &mdash; per-tick relight,
 * stale non-head self-destruct, NBT round-trip, wrench-item persistence, teardown
 * on removal &mdash; is preserved via {@link LetsDoLampBlockEntity#reconcileAppliancePower()}
 * and the injections below.</p>
 */
@Mixin(LetsDoLampBlockEntity.class)
@Implements(@Interface(iface = IModuleNode.class, prefix = "imn$"))
public abstract class LetsDoLampNodeMixin {

    @Unique
    private final Set<Connection> cio$connections = new HashSet<>();
    @Unique
    private final Set<BlockPos> cio$powerSources = new HashSet<>();

    @Unique
    private LetsDoLampBlockEntity cio$self() {
        return (LetsDoLampBlockEntity) (Object) this;
    }

    @Unique
    private IModuleNode cio$node() {
        return (IModuleNode) (Object) this;
    }

    // --- IElectricityNode / IModuleNode identity + state ------------------

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

    /**
     * Crayfish's {@code ElectricityTicker} calls this every tick. Fold
     * receiving&rarr;powered and re-apply the light (both inside
     * {@link LetsDoLampBlockEntity#reconcileAppliancePower()}), then clear the
     * per-pass receiving flag exactly like {@code IModuleNode#moduleTick}'s
     * default.
     */
    public void imn$moduleTick(Level level) {
        if (level.isClientSide) {
            return;
        }
        this.cio$self().reconcileAppliancePower();
        this.cio$self().setApplianceReceivingPower(false);
    }

    // --- NBT + lifecycle (were inline calls in the old BlockEntity) -------

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void cio$saveNode(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        this.cio$node().writeNodeNbt(tag);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void cio$loadNode(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        this.cio$node().readNodeNbt(tag);
    }

    // No setRemoved teardown: it fires on chunk unload too, which would sever
    // links when the player just leaves the area. Crayfish's own ticker +
    // updateNodeConnections prune stale links when a block is genuinely gone.

    @Inject(method = "saveToItem", at = @At("TAIL"))
    private void cio$saveNodeToItem(ItemStack stack, HolderLookup.Provider provider, CallbackInfo ci) {
        this.cio$node().saveNodeNbtToItem(stack, provider);
    }
}
