package com.cio.createinteroperable.letsdo;

import com.cio.createinteroperable.CIOBlockEntities;
import com.cio.createinteroperable.deb.MeteredAppliance;
import com.cio.createinteroperable.grid.ApplianceGrid;
import com.cio.createinteroperable.grid.ApplianceNode;
import com.cio.createinteroperable.grid.CrayfishCompat;
import com.cio.createinteroperable.grid.GridConnection;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Attached (via {@code LetsDoLampBlockMixin} and friends) to every registered
 * Let's Do Furniture / Candlelight lamp &amp; street-lantern block, plus Alpine
 * Whispers fairy lights and the Another Furniture lamp head, making each a Create:
 * Interoperable {@link ApplianceNode} consumer.
 *
 * <p>The block's own {@code lit} / {@code luminance} property is driven to
 * {@code switchedOn && appliancePowered}: an unwired or unpowered lamp is dark,
 * and it is billed its rated 3&nbsp;W ({@link MeteredAppliance}) only while
 * actually lit. Blocks with a manual toggle flip {@link #switchedOn} instead of
 * the blockstate directly (see {@code LetsDoLampToggleMixin}).</p>
 *
 * <p><b>Backends.</b> With MrCrayfish's Refurbished Furniture installed,
 * {@code com.cio.createinteroperable.mixin.crayfish.LetsDoLampNodeMixin} bolts
 * Crayfish's {@code IModuleNode} onto this class and forwards it here, so the
 * Crayfish ticker, wrench linking, wire renderer and "no power" overlay drive
 * the node exactly as before. Without Crayfish the lamp is a plain (inert) block
 * entity until the Phase 2 native appliance grid drives {@link #setAppliancePowered}
 * / {@link #reconcileAppliancePower} instead.</p>
 */
public class LetsDoLampBlockEntity extends BlockEntity implements ApplianceNode, MeteredAppliance {

    /** Rated draw of one Let's Do lamp, mirroring Crayfish's own lamps. */
    public static final double WATTS = 3.0;

    private boolean receivingPower;
    private boolean nodePowered;
    /** Player intent (default on); the lamp lights only when this AND power are true. */
    private boolean switchedOn = true;
    /** Last lit value pushed to a block with no lit blockstate (fairy lights); null = never pushed. */
    private Boolean litApplied;
    /** Native appliance-grid links. Only used when Crayfish is absent (its adapter carries its own set). */
    private final Set<GridConnection> connections = new HashSet<>();

    public LetsDoLampBlockEntity(net.minecraft.core.BlockPos pos, BlockState state) {
        super(CIOBlockEntities.LETSDO_LAMP.get(), pos, state);
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
    public int applianceConnectionLimit() {
        return 6;
    }

    /**
     * The lamp already round-trips a full BE update packet for its lit state, so
     * the default's extra {@code sendBlockUpdated} would be redundant &mdash; but
     * we still need {@link #setChanged()} for save, which the interface default
     * callers already do.
     */
    @Override
    public void syncApplianceNode() {
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public boolean appliancePowered() {
        return this.nodePowered;
    }

    @Override
    public void setAppliancePowered(boolean powered) {
        if (this.nodePowered == powered) {
            return;
        }
        this.nodePowered = powered;
        this.setChanged();
        this.applyLit();
    }

    @Override
    public boolean applianceReceivingPower() {
        return this.receivingPower;
    }

    @Override
    public void setApplianceReceivingPower(boolean receiving) {
        this.receivingPower = receiving;
    }

    /**
     * On top of the default receiving&rarr;powered fold, always reconcile the
     * blockstate and self-destruct a stale non-head segment.
     * <ul>
     *   <li>If this segment is no longer a lamp head (a post was rebuilt around
     *       it), drop the block entity &mdash; only the head carries the node.</li>
     *   <li>Otherwise always re-apply the light: {@link #setAppliancePowered}
     *       alone never fires {@link #applyLit()} for a lamp that starts (and
     *       stays) unpowered, so a block that defaults {@code lit=true} (the
     *       street lanterns) would never go dark. {@link #applyLit()} is a cheap
     *       no-op once the blockstate already matches.</li>
     * </ul>
     */
    @Override
    public void reconcileAppliancePower() {
        if (this.level != null && !this.level.isClientSide
                && !LetsDoLampStates.isHeadSegment(this.getBlockState())) {
            this.level.removeBlockEntity(this.worldPosition);
            return;
        }
        ApplianceNode.super.reconcileAppliancePower();
        this.applyLit();
    }

    // --- switch + lit state -------------------------------------------

    /** Flips the manual switch and re-applies the light. */
    public void toggleSwitch() {
        this.switchedOn = !this.switchedOn;
        this.setChanged();
        this.applyLit();
    }

    public boolean isSwitchedOn() {
        return this.switchedOn;
    }

    /** True while the lamp is actually lit (switch on AND powered). */
    public boolean isLampLit() {
        return this.switchedOn && this.nodePowered;
    }

    /**
     * Repaint the block's {@code lit} / {@code luminance} property (or, for a
     * block with none, poke its light engine + client) to match
     * {@link #isLampLit()}. Public so the Crayfish node adapter can call it from
     * its own tick.
     */
    public void applyLit() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        boolean want = this.isLampLit();
        BlockState state = this.getBlockState();
        Boolean current = LetsDoLampStates.isLit(state);
        if (current != null) {
            if (current != want) {
                this.level.setBlock(this.worldPosition, LetsDoLampStates.withLit(state, want), Block.UPDATE_ALL);
            }
            return;
        }
        // No 'lit' / 'luminance' blockstate (Alpine Whispers fairy lights): the
        // block keeps one model, so drive its NeoForge getLightEmission()
        // (AlpineFairyLightsBlockMixin) by forcing a light re-check + client
        // redraw whenever the lit target changes — and once on the first run,
        // since the engine's initial emission for this position was computed
        // before this block entity existed (so it defaulted to "lit").
        if (this.litApplied == null || this.litApplied != want) {
            this.litApplied = want;
            this.level.getLightEngine().checkBlock(this.worldPosition);
            this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    // --- metered load: only while actually lit ---------------------------

    @Override
    public boolean cio$isConsuming() {
        return this.isLampLit();
    }

    // --- client sync (vanilla-Block lamps ship no BE sync) -------------

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        return this.saveWithoutMetadata(provider);
    }

    /**
     * Vanilla {@code BlockEntity.onDataPacket} is a no-op, so a plain
     * {@code getUpdatePacket()} never reaches the fields on the client at
     * runtime. Apply it here, and for a block with no {@code lit} blockstate
     * (fairy lights) force a section re-mesh &mdash; there is no blockstate
     * change to trigger one, so {@code emissiveRendering} / the model would
     * otherwise never re-evaluate.
     */
    @Override
    public void onDataPacket(net.minecraft.network.Connection connection, ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider provider) {
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            this.loadAdditional(tag, provider);
        }
        if (this.level != null && this.level.isClientSide
                && LetsDoLampStates.isLit(this.getBlockState()) == null) {
            BlockState state = this.getBlockState();
            this.level.setBlocksDirty(this.worldPosition, state, state);
            this.level.getLightEngine().checkBlock(this.worldPosition);
        }
    }

    // --- persistence -------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putBoolean("SwitchedOn", this.switchedOn);
        tag.putBoolean("NodePowered", this.nodePowered);
        // Native links; the Crayfish adapter owns the same keys when it is active.
        if (!CrayfishCompat.present()) {
            this.writeApplianceNbt(tag);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        this.switchedOn = !tag.contains("SwitchedOn") || tag.getBoolean("SwitchedOn");
        this.nodePowered = tag.getBoolean("NodePowered");
        if (!CrayfishCompat.present()) {
            this.readApplianceNbt(tag);
        }
    }

    /** Register with the native grid once we know our level (mirrors Crayfish's setLevel adoption). */
    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (!CrayfishCompat.present()) {
            ApplianceGrid.get(level).addNode(this);
        }
    }

    // No teardown on setRemoved (fires on chunk unload too). Surviving nodes
    // prune dead links lazily once the far position is loaded and confirmed gone.

    @Override
    public void saveToItem(net.minecraft.world.item.ItemStack stack, HolderLookup.Provider provider) {
        super.saveToItem(stack, provider);
        if (!CrayfishCompat.present()) {
            this.saveApplianceNbtToItem(stack, provider);
        }
    }
}
