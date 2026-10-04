package com.cio.createinteroperable.iden;

import com.cio.createinteroperable.CIOBlockEntities;
import com.cio.createinteroperable.deb.MeteredAppliance;
import com.cio.createinteroperable.grid.ApplianceGrid;
import com.cio.createinteroperable.grid.ApplianceNode;
import com.cio.createinteroperable.grid.CrayfishCompat;
import com.cio.createinteroperable.grid.GridConnection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * The appliance-grid node behind every Electric Iden's Decor button / lever /
 * switch / control panel &mdash; a wire-in-wire-out switch, exactly like
 * Crayfish's own {@code LightswitchBlockEntity}: power reaching it passes
 * <em>through</em> to whatever is linked beyond it only while the block's own
 * vanilla {@code powered} state is true (lever thrown, button held in).
 *
 * <p>The block itself emits no redstone (see {@link ElectricSwitches}); the
 * {@code powered} blockstate is purely its contact position here, and still
 * drives Iden's original on/off models.</p>
 *
 * <p><b>Backends</b> as {@link com.cio.createinteroperable.letsdo.LetsDoLampBlockEntity}:
 * with Refurbished Furniture installed, {@code mixin.crayfish.ElectricSwitchNodeMixin}
 * bolts Crayfish's {@code IModuleNode} on (its {@code canPowerTraverseNode()}
 * forwards to {@link #applianceTraversable()}); without it, the native grid
 * walks it via {@link ApplianceNode#searchApplianceNetwork(int, boolean, net.minecraft.world.phys.AABB, boolean)}.
 * Billed a relay's 0.5&nbsp;W only while closed ({@link MeteredAppliance}),
 * mirroring how the DEB bills a Crayfish lightswitch only while switched on.</p>
 */
public class ElectricSwitchBlockEntity extends BlockEntity implements ApplianceNode, MeteredAppliance {

    private boolean receivingPower;
    private boolean nodePowered;
    /** Native appliance-grid links. Only used when Crayfish is absent (its adapter carries its own set). */
    private final Set<GridConnection> connections = new HashSet<>();

    public ElectricSwitchBlockEntity(BlockPos pos, BlockState state) {
        super(CIOBlockEntities.ELECTRIC_SWITCH.get(), pos, state);
    }

    /** True while the switch's contacts are closed (lever on / button pressed). */
    public boolean isClosed() {
        BlockState state = this.getBlockState();
        return state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED);
    }

    // --- ApplianceNode -------------------------------------------------

    @Override
    public BlockEntity applianceOwner() {
        return this;
    }

    @Override
    public Set<GridConnection> applianceConnections() {
        return this.connections;
    }

    @Override
    public boolean applianceTraversable() {
        return this.isClosed();
    }

    @Override
    public boolean appliancePowered() {
        return this.nodePowered;
    }

    @Override
    public void setAppliancePowered(boolean powered) {
        if (this.nodePowered != powered) {
            this.nodePowered = powered;
            this.setChanged();
        }
    }

    @Override
    public boolean applianceReceivingPower() {
        return this.receivingPower;
    }

    @Override
    public void setApplianceReceivingPower(boolean receiving) {
        this.receivingPower = receiving;
    }

    @Override
    public boolean cio$isConsuming() {
        return this.isClosed();
    }

    // --- client sync -------------------------------------------------

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        return this.saveWithoutMetadata(provider);
    }

    // --- persistence -------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putBoolean("NodePowered", this.nodePowered);
        // Native links; the Crayfish adapter owns the same keys when it is active.
        if (!CrayfishCompat.present()) {
            this.writeApplianceNbt(tag);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        this.nodePowered = tag.getBoolean("NodePowered");
        if (!CrayfishCompat.present()) {
            this.readApplianceNbt(tag);
        }
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (!CrayfishCompat.present()) {
            ApplianceGrid.get(level).addNode(this);
        }
    }

    @Override
    public void saveToItem(ItemStack stack, HolderLookup.Provider provider) {
        super.saveToItem(stack, provider);
        if (!CrayfishCompat.present()) {
            this.saveApplianceNbtToItem(stack, provider);
        }
    }
}
