package com.cio.createinteroperable.mixin.letsdo;

import com.cio.createinteroperable.CIOConfig;
import com.cio.createinteroperable.deb.MeteredAppliance;
import com.cio.createinteroperable.grid.ApplianceGrid;
import com.cio.createinteroperable.grid.ApplianceNode;
import com.cio.createinteroperable.grid.CrayfishCompat;
import com.cio.createinteroperable.grid.GridConnection;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
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
 * Native (no-Crayfish) power node for Let's Do Beachparty's Radio &mdash; the
 * {@link ApplianceNode} half. It only plays while powered; losing power
 * mid-track stops it. See {@link GramophoneNodeMixin} for the backend split.
 */
@Mixin(targets = "net.satisfy.beachparty.core.block.entity.RadioBlockEntity")
public abstract class RadioNodeMixin implements ApplianceNode, MeteredAppliance {

    @Shadow public boolean isPlaying;
    @Shadow public abstract void stop();

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
            if (!powered && this.isPlaying && CIOConfig.BEACHPARTY_APPLIANCES_REQUIRE_POWER.get()) {
                this.stop();
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

    @Override
    public boolean cio$isConsuming() {
        return this.isPlaying;
    }

    @Nullable
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(cioNode$be());
    }

    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        cioNode$ensureRegistered();
        return cioNode$be().saveWithoutMetadata(provider);
    }

    @Inject(method = "play(Lnet/minecraft/world/level/Level;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void cioNode$gatePlay(Level level, CallbackInfo ci) {
        cioNode$ensureRegistered();
        if (!level.isClientSide && CIOConfig.BEACHPARTY_APPLIANCES_REQUIRE_POWER.get() && !this.cioNode$powered) {
            ci.cancel();
        }
    }

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
