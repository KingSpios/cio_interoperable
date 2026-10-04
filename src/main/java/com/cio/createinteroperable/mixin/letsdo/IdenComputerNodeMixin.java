package com.cio.createinteroperable.mixin.letsdo;

import com.cio.createinteroperable.deb.MeteredAppliance;
import com.cio.createinteroperable.grid.ApplianceGrid;
import com.cio.createinteroperable.grid.ApplianceNode;
import com.cio.createinteroperable.grid.CrayfishCompat;
import com.cio.createinteroperable.grid.GridConnection;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;

/**
 * Native (no-Crayfish) power node for Iden's Decor's Computer &mdash; the
 * {@link ApplianceNode} half; {@link IdenComputerBlockEntityMixin} is the
 * Crayfish adapter on top. The computer shows its floppy disk's text only while
 * {@link #appliancePowered()} (gated client-side by
 * {@link IdenComputerRendererMixin}), so the powered flag is written to the
 * block entity's NBT on <em>both</em> backends: Iden's update tag is
 * {@code saveCustomOnly}, i.e. it runs {@code saveAdditional}, which is how the
 * flag reaches the client.
 *
 * <p>Billed a Crayfish computer's 15&nbsp;W only while a disk is in
 * ({@link MeteredAppliance}). The node box sits on top of the tower, behind the
 * monitor: the cell centre (Crayfish's default) is buried inside the solid
 * body, where neither the wrench raycast nor the "Missing power" label can
 * reach it &mdash; the same trap the Vista TV hit.</p>
 *
 * <p>Name-targeted, non-required &mdash; a no-op without Iden's Decor.</p>
 */
@Mixin(targets = "net.identidade.iden_decor.blockentity.ComputerBlockEntity", remap = false)
public abstract class IdenComputerNodeMixin implements ApplianceNode, MeteredAppliance {

    @Shadow public abstract boolean hasDisk();

    @Unique private final Set<GridConnection> cioNode$conns = new HashSet<>();
    @Unique private boolean cioNode$powered;
    @Unique private boolean cioNode$receiving;
    @Unique private boolean cioNode$registered;

    /** Node boxes on top of the tower (4 px cube, y 12-16), per facing. */
    @Unique private static final AABB CIO$BOX_NORTH = new AABB(6 / 16D, 12 / 16D, 9 / 16D, 10 / 16D, 1D, 13 / 16D);
    @Unique private static final AABB CIO$BOX_SOUTH = new AABB(6 / 16D, 12 / 16D, 3 / 16D, 10 / 16D, 1D, 7 / 16D);
    @Unique private static final AABB CIO$BOX_EAST = new AABB(3 / 16D, 12 / 16D, 6 / 16D, 7 / 16D, 1D, 10 / 16D);
    @Unique private static final AABB CIO$BOX_WEST = new AABB(9 / 16D, 12 / 16D, 6 / 16D, 13 / 16D, 1D, 10 / 16D);

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
    public AABB applianceNodeBox() {
        BlockState state = cioNode$be().getBlockState();
        Direction facing = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
        return switch (facing) {
            case SOUTH -> CIO$BOX_SOUTH;
            case EAST -> CIO$BOX_EAST;
            case WEST -> CIO$BOX_WEST;
            default -> CIO$BOX_NORTH;
        };
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

    @Override
    public boolean cio$isConsuming() {
        return this.hasDisk();
    }

    @Inject(method = "getUpdateTag", at = @At("HEAD"), remap = false)
    private void cioNode$registerOnSync(HolderLookup.Provider provider, CallbackInfoReturnable<CompoundTag> cir) {
        cioNode$ensureRegistered();
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"), remap = false)
    private void cioNode$save(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        tag.putBoolean("CioPowered", this.cioNode$powered);
        if (!CrayfishCompat.present()) {
            writeApplianceNbt(tag);
        }
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"), remap = false)
    private void cioNode$load(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        this.cioNode$powered = tag.getBoolean("CioPowered");
        if (!CrayfishCompat.present()) {
            readApplianceNbt(tag);
        }
        cioNode$ensureRegistered();
    }
}
