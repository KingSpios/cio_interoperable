package com.cio.createinteroperable.mixin.letsdo;

import com.cio.createinteroperable.deb.MeteredAppliance;
import com.cio.createinteroperable.grid.ApplianceGrid;
import com.cio.createinteroperable.grid.ApplianceNode;
import com.cio.createinteroperable.grid.CrayfishCompat;
import com.cio.createinteroperable.grid.GridConnection;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

/**
 * Native (no-Crayfish) power node for Let's Do Furniture's Gramophone &mdash;
 * the {@link ApplianceNode} half, mirroring {@code LetsDoLampBlockEntity}. It
 * only plays while powered from a network that reaches a Domestic Electrical
 * Board; a disc inserted while unpowered is remembered ({@link #cio$wantsPlay})
 * and starts from the top once power arrives.
 *
 * <p>Always applied when Let's Do Furniture is present. With Refurbished
 * Furniture <em>also</em> present, {@code GramophoneBlockEntityMixin} bolts
 * Crayfish's {@code IModuleNode} on top and forwards its power state here, so
 * this stays the single source of truth for both backends; only the
 * connection persistence + grid registration below are gated to the native path.</p>
 */
@Mixin(targets = "com.berksire.furniture.core.block.entity.GramophoneBlockEntity")
public abstract class GramophoneNodeMixin implements ApplianceNode, MeteredAppliance {

    @Shadow private ItemStack recordItem;
    @Shadow private boolean isPlaying;
    @Shadow private void startPlaying() { throw new AssertionError(); }
    @Shadow public abstract void stopPlaying();

    @Unique private final Set<GridConnection> cioNode$conns = new HashSet<>();
    @Unique private boolean cioNode$powered;
    @Unique private boolean cioNode$receiving;
    /** A disc is loaded and would be playing if it had power. */
    @Unique private boolean cioNode$wantsPlay;
    @Unique private boolean cioNode$registered;

    @Unique
    private BlockEntity cioNode$be() {
        return (BlockEntity) (Object) this;
    }

    /** Foreign BEs can't self-register from {@code setLevel}; hook every path that has a live level instead. */
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

    // --- ApplianceNode ---------------------------------------------------

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
            if (!powered && this.isPlaying) {
                this.stopPlaying();
                this.cioNode$wantsPlay = true;
            }
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

    // --- metered load: only while a disc is actually playing -----------

    @Override
    public boolean cio$isConsuming() {
        return this.isPlaying && !this.recordItem.isEmpty();
    }

    // --- client sync (the vanilla BlockEntity ships none) -------------

    @Nullable
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(cioNode$be());
    }

    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        cioNode$ensureRegistered();
        return cioNode$be().saveWithoutMetadata(provider);
    }

    // --- power-gated playback ----------------------------------------

    @Inject(method = "startPlaying", at = @At("HEAD"), cancellable = true, require = 0)
    private void cioNode$gateStart(CallbackInfo ci) {
        if (!this.cioNode$powered) {
            this.cioNode$wantsPlay = true;
            ci.cancel();
        }
    }

    @Inject(method = "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At("HEAD"), require = 0)
    private void cioNode$powerTick(Level level, BlockState state, CallbackInfo ci) {
        cioNode$ensureRegistered();
        if (level.isClientSide) {
            return;
        }
        if (this.isPlaying && !this.cioNode$powered) {
            this.stopPlaying();
            this.cioNode$wantsPlay = true;
        } else if (!this.isPlaying && this.cioNode$powered && this.cioNode$wantsPlay && !this.recordItem.isEmpty()) {
            this.cioNode$wantsPlay = false;
            this.startPlaying();
        }
    }

    @Inject(method = "popOutRecord", at = @At("RETURN"), require = 0)
    private void cioNode$clearWantsPlay(CallbackInfo ci) {
        this.cioNode$wantsPlay = false;
    }

    // --- native persistence / registration (gated to the no-Crayfish path) ---

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void cioNode$save(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        if (!CrayfishCompat.present()) {
            writeApplianceNbt(tag);
            tag.putBoolean("CioPowered", this.cioNode$powered);
            tag.putBoolean("CioWantsPlay", this.cioNode$wantsPlay);
        }
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void cioNode$load(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        if (!CrayfishCompat.present()) {
            readApplianceNbt(tag);
            this.cioNode$powered = tag.getBoolean("CioPowered");
            this.cioNode$wantsPlay = tag.getBoolean("CioWantsPlay");
        }
        cioNode$ensureRegistered();
    }
}
