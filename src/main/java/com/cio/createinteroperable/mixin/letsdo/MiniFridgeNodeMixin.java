package com.cio.createinteroperable.mixin.letsdo;

import com.cio.createinteroperable.CIOConfig;
import com.cio.createinteroperable.grid.ApplianceGrid;
import com.cio.createinteroperable.grid.ApplianceNode;
import com.cio.createinteroperable.grid.CrayfishCompat;
import com.cio.createinteroperable.grid.GridConnection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;

/**
 * Native (no-Crayfish) power node for Let's Do Beachparty's Mini Fridge &mdash;
 * the {@link ApplianceNode} half. A compressor draws its rated load whenever it
 * is linked (no metered gate); losing power stops fermentation and seals the
 * inventory to automation. See {@link GramophoneNodeMixin} for the backend split.
 */
@Mixin(targets = "net.satisfy.beachparty.core.block.entity.MiniFridgeBlockEntity")
public abstract class MiniFridgeNodeMixin implements ApplianceNode {

    @Unique private final Set<GridConnection> cioNode$conns = new HashSet<>();
    @Unique private boolean cioNode$powered;
    @Unique private boolean cioNode$receiving;
    @Unique private boolean cioNode$registered;

    @Unique
    private BlockEntity cioNode$be() {
        return (BlockEntity) (Object) this;
    }

    @Unique
    private void cioNode$ensureRegistered() {
        if (this.cioNode$registered || CrayfishCompat.present()) {
            return;
        }
        Level level = cioNode$be().getLevel();
        if (level != null) {
            ApplianceGrid.get(level).addNode(this);
            this.cioNode$registered = true;
        }
    }

    @Override
    public BlockEntity applianceOwner() {
        return cioNode$be();
    }

    @Override
    public Set<GridConnection> applianceConnections() {
        return this.cioNode$conns;
    }

    @Override
    public int applianceConnectionLimit() {
        return 6;
    }

    @Override
    public boolean appliancePowered() {
        return this.cioNode$powered;
    }

    @Override
    public void setAppliancePowered(boolean powered) {
        if (this.cioNode$powered == powered) {
            return;
        }
        this.cioNode$powered = powered;
        BlockEntity be = cioNode$be();
        be.setChanged();
        Level level = be.getLevel();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 2);
        }
    }

    @Override
    public boolean applianceReceivingPower() {
        return this.cioNode$receiving;
    }

    @Override
    public void setApplianceReceivingPower(boolean receiving) {
        this.cioNode$receiving = receiving;
    }

    @Nullable
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(cioNode$be());
    }

    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        cioNode$ensureRegistered();
        return cioNode$be().saveWithoutMetadata(provider);
    }

    // --- power-gated fermentation + automation ----------------------

    @Unique
    private boolean cioNode$sealed() {
        return CIOConfig.BEACHPARTY_APPLIANCES_REQUIRE_POWER.get() && !this.cioNode$powered;
    }

    @Inject(
            method = "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/satisfy/beachparty/core/block/entity/MiniFridgeBlockEntity;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void cioNode$gateFermentation(Level world, BlockPos pos, BlockState state, @Coerce BlockEntity be,
                                          CallbackInfo ci) {
        cioNode$ensureRegistered();
        if (!world.isClientSide && cioNode$sealed()) {
            ci.cancel();
        }
    }

    @Inject(method = "getSlotsForFace", at = @At("HEAD"), cancellable = true, require = 0)
    private void cioNode$sealSlots(Direction side, CallbackInfoReturnable<int[]> cir) {
        if (cioNode$sealed()) {
            cir.setReturnValue(new int[0]);
        }
    }

    @Inject(method = "canPlaceItemThroughFace", at = @At("HEAD"), cancellable = true, require = 0)
    private void cioNode$sealInsert(int slot, ItemStack stack, Direction dir, CallbackInfoReturnable<Boolean> cir) {
        if (cioNode$sealed()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "canTakeItemThroughFace", at = @At("HEAD"), cancellable = true, require = 0)
    private void cioNode$sealExtract(int slot, ItemStack stack, Direction dir, CallbackInfoReturnable<Boolean> cir) {
        if (cioNode$sealed()) {
            cir.setReturnValue(false);
        }
    }

    // --- native persistence / registration ------------------------

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void cioNode$save(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        if (!CrayfishCompat.present()) {
            writeApplianceNbt(tag);
            tag.putBoolean("CioPowered", this.cioNode$powered);
        }
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void cioNode$load(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        if (!CrayfishCompat.present()) {
            readApplianceNbt(tag);
            this.cioNode$powered = tag.getBoolean("CioPowered");
        }
        cioNode$ensureRegistered();
    }
}
