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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
 * Native (no-Crayfish) power node for Bibliocraft's Fancy Crafter &mdash; the
 * {@link ApplianceNode} half. See {@code FancyCrafterBlockEntityMixin} for the
 * Crayfish adapter, and {@link GramophoneNodeMixin} for the general backend
 * split this mirrors.
 *
 * <p>Fancy Crafter's own ticker ({@code FancyCrafterBlock#getTicker}) already
 * refuses to run unless the vanilla {@code POWERED} blockstate (ordinarily
 * redstone-driven) is set, so rather than injecting into the crafting logic
 * itself, this simply takes over that one property: {@link #cioNode$applyPowered}
 * writes it from the grid instead of redstone. Unlike Radio/Mini Fridge,
 * Bibliocraft's own {@code neighborChanged} keeps independently writing that
 * same property from redstone, so {@link #reconcileAppliancePower} re-applies
 * it every pass (not just on a power transition) rather than fighting to
 * suppress the block's own handler outright.</p>
 *
 * <p>Deliberately does <b>not</b> block the menu or hopper automation when
 * unpowered: loading and arranging the crafting grid ahead of time is
 * harmless and useful even before it's wired up &mdash; only the automatic
 * craft tick needs power.</p>
 */
@Mixin(targets = "com.github.minecraftschurlimods.bibliocraft.content.fancycrafter.FancyCrafterBlockEntity")
public abstract class FancyCrafterNodeMixin implements ApplianceNode, MeteredAppliance {

    @Shadow private RecipeHolder<?> recipe;

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
        cioNode$applyPowered();
    }

    @Override
    public boolean applianceReceivingPower() {
        return this.cioNode$receiving;
    }

    @Override
    public void setApplianceReceivingPower(boolean receiving) {
        this.cioNode$receiving = receiving;
    }

    /**
     * Always re-applies, not just on a power transition &mdash; see the class
     * doc for why (Bibliocraft's own redstone handler is a standing
     * competing writer of the same blockstate property, unlike anything
     * Radio/Mini Fridge have to contend with).
     */
    @Override
    public void reconcileAppliancePower() {
        ApplianceNode.super.reconcileAppliancePower();
        cioNode$applyPowered();
    }

    @Unique
    private void cioNode$applyPowered() {
        if (!CIOConfig.BIBLIOCRAFT_APPLIANCES_REQUIRE_POWER.get()) {
            return;
        }
        BlockEntity be = cioNode$be();
        Level level = be.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = be.getBlockState();
        boolean want = this.cioNode$powered;
        if (state.getValue(BlockStateProperties.POWERED) != want) {
            level.setBlock(be.getBlockPos(), state.setValue(BlockStateProperties.POWERED, want), Block.UPDATE_ALL);
        }
    }

    /** Draws its rated load only while it actually has a recipe staged &mdash; an idle crafter draws nothing. */
    @Override
    public boolean cio$isConsuming() {
        return this.recipe != null;
    }

    @Nullable
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(cioNode$be());
    }

    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        cioNode$ensureRegistered();
        return cioNode$be().saveWithoutMetadata(provider);
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
