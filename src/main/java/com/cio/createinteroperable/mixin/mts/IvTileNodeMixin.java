package com.cio.createinteroperable.mixin.mts;

import com.cio.createinteroperable.CIOConfig;
import com.cio.createinteroperable.deb.MeteredAppliance;
import com.cio.createinteroperable.deb.ScalableAppliance;
import com.cio.createinteroperable.grid.ApplianceGrid;
import com.cio.createinteroperable.grid.ApplianceNode;
import com.cio.createinteroperable.grid.CrayfishCompat;
import com.cio.createinteroperable.grid.GridConnection;
import com.cio.createinteroperable.mts.IvCrayfishBridge;
import com.cio.createinteroperable.mts.IvNodeState;
import com.cio.createinteroperable.mts.IvPoleLights;
import com.cio.createinteroperable.mts.IvPowerState;
import mcinterface1211.BuilderTileEntity;
import minecrafttransportsimulator.blocks.tileentities.components.ATileEntityBase;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
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
 * Native (no-Crayfish) grid node on Immersive Vehicles' own block entity, for
 * the two IV tile kinds that should be wireable: a pole block carrying a lamp,
 * and the Signal Controller (see {@link IvPoleLights}). Every other IV tile
 * sharing this class (bare poles, roads, pumps, loaders, decor) is inert: no
 * link slots, a node box parked far out of reach, never registered, never
 * billed &mdash; and a pole that loses its last lamp drops whatever wires it
 * still held.
 *
 * <p><b>Power is a live feed, not a latch.</b> A board marks a node as
 * receiving once per tick, and only while its rail is live (it stops the
 * moment a supply is cut, and 3&nbsp;s into a brownout). This node remembers
 * the game tick of the last such mark and counts as powered only if it was
 * this tick or the one before; every server tick re-decides that from scratch,
 * so a lamp goes dark within two ticks of its feed stopping, every time, no
 * matter which grid backend (or whether its ticker is still tracking this node
 * at all) delivered the power.</p>
 *
 * <p>Every tick it also bills a lamp pole ({@link MeteredAppliance} +
 * {@link ScalableAppliance} over a 1&nbsp;W base row), syncs "lamps may light"
 * to clients, and pushes it onto the pole tile ({@link IvPowerState}) on both
 * sides for the lamp gates to read.</p>
 *
 * <p><b>Client sync.</b> IV syncs its tiles with its own handshake and gives
 * this block entity no vanilla update packet, so this adds a small one carrying
 * only the node state + connections, marked {@value #SYNC_MARKER}. IV parks
 * every incoming block-entity tag in its own {@code lastLoadedNBT} and rebuilds
 * the client tile from that field, so a marked tag is intercepted at the head
 * of {@code loadAdditional} and never reaches IV's code. Re-sent on every
 * change and, as insurance, every few seconds.</p>
 *
 * <p>With Refurbished Furniture installed, {@code IvTileNodeCrayfishMixin}
 * bolts Crayfish's {@code IModuleNode} on and forwards to this half.</p>
 */
@Mixin(value = BuilderTileEntity.class, remap = false)
public abstract class IvTileNodeMixin implements ApplianceNode, MeteredAppliance, ScalableAppliance, IvNodeState {

    @Unique
    private static final String SYNC_MARKER = "CioNodeSync";

    /** Node cube for a non-node IV tile: far below the world, never hit by any raycast. */
    @Unique
    private static final AABB CIO$HIDDEN_BOX = new AABB(0.5, -4096.0, 0.5, 0.5, -4096.0, 0.5);
    /** Nub on top of the Signal Controller's box, straddling its top face so it stays visible. */
    @Unique
    private static final AABB CIO$CONTROLLER_BOX = new AABB(0.375, 0.875, 0.375, 0.625, 1.125, 0.625);

    /** A feed mark older than this many ticks means the rail stopped feeding us. */
    @Unique
    private static final long CIO$FEED_TIMEOUT = 1L;
    /** Periodic client resync for a node, in ticks (staggered by position). */
    @Unique
    private static final int CIO$RESYNC_TICKS = 100;

    @Shadow(remap = false)
    protected ATileEntityBase<?> tileEntity;

    @Unique private final Set<GridConnection> cio$conns = new HashSet<>();
    @Unique private boolean cio$powered;
    @Unique private boolean cio$receiving;
    /** Game tick of the last time a live rail marked this node as receiving. */
    @Unique private long cio$lastFed = Long.MIN_VALUE / 2;
    /** Whether this node's lamps may light (live feed, or the gate is off). Server-decided, synced. */
    @Unique private boolean cio$lit;
    @Unique private IvPoleLights.Kind cio$kind = IvPoleLights.Kind.NONE;
    @Unique private double cio$watts;
    @Unique private boolean cio$needsPower;
    @Unique private boolean cio$registered;

    @Unique
    private BlockEntity cio$be() {
        return (BlockEntity) (Object) this;
    }

    @Unique
    private boolean cio$fedRecently() {
        Level level = cio$be().getLevel();
        return level != null && level.getGameTime() - this.cio$lastFed <= CIO$FEED_TIMEOUT;
    }

    // --- per-tick upkeep ------------------------------------------------------

    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void cio$nodeTick(CallbackInfo ci) {
        BlockEntity be = cio$be();
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        this.cio$kind = IvPoleLights.kindOf(this.tileEntity);
        if (this.cio$kind == IvPoleLights.Kind.NONE) {
            this.cio$registered = false;
            this.cio$needsPower = false;
            this.cio$watts = 0.0;
            if (!level.isClientSide && IvPoleLights.isLamplessPole(this.tileEntity)) {
                cio$dropAllConnections();
            }
            return;
        }
        double lampWatts = IvPoleLights.lampWatts(this.tileEntity);
        this.cio$needsPower = lampWatts > 0.0;
        if (!this.cio$registered && !CrayfishCompat.present()) {
            ApplianceGrid.get(level).addNode(this);
            this.cio$registered = true;
        }
        if (!level.isClientSide) {
            setAppliancePowered(cio$fedRecently());
            boolean enabled = CIOConfig.MTS_POLE_LIGHTS_REQUIRE_POWER.get();
            this.cio$watts = enabled ? lampWatts : 0.0;
            boolean lit = !enabled || this.cio$powered;
            boolean resync = (level.getGameTime() + be.getBlockPos().hashCode()) % CIO$RESYNC_TICKS == 0L;
            if (lit != this.cio$lit || resync) {
                if (lit != this.cio$lit) {
                    this.cio$lit = lit;
                    be.setChanged();
                }
                syncApplianceNode();
            }
        }
        if (this.tileEntity instanceof IvPowerState state) {
            state.cio$setIvPowered(this.cio$lit);
        }
    }

    /** A pole that lost its last lamp is no longer a node: cut every wire, on both ends. */
    @Unique
    private void cio$dropAllConnections() {
        boolean changed;
        if (CrayfishCompat.present()) {
            changed = IvCrayfishBridge.removeAllConnections(cio$be());
        } else {
            changed = !this.cio$conns.isEmpty();
            if (changed) {
                removeAllApplianceConnections();
            }
        }
        if (changed) {
            syncApplianceNode();
        }
        this.cio$lastFed = Long.MIN_VALUE / 2;
        this.cio$powered = false;
        this.cio$lit = false;
    }

    // --- IvNodeState ------------------------------------------------------------

    @Override
    public boolean cio$lightsPowered() {
        return this.cio$lit;
    }

    @Override
    public boolean cio$needsPower() {
        return this.cio$needsPower;
    }

    // --- ApplianceNode ------------------------------------------------------------

    @Override
    public BlockEntity applianceOwner() {
        return cio$be();
    }

    @Override
    public Set<GridConnection> applianceConnections() {
        return this.cio$conns;
    }

    @Override
    public int applianceConnectionLimit() {
        return switch (this.cio$kind) {
            case POLE -> 8;
            case CONTROLLER -> 16;
            case NONE -> 0;
        };
    }

    @Override
    public AABB applianceNodeBox() {
        return switch (this.cio$kind) {
            case POLE -> ApplianceNode.APPLIANCE_NODE_BOX;
            case CONTROLLER -> CIO$CONTROLLER_BOX;
            case NONE -> CIO$HIDDEN_BOX;
        };
    }

    @Override
    public boolean applianceValid() {
        return !cio$be().isRemoved() && this.cio$kind != IvPoleLights.Kind.NONE;
    }

    @Override
    public boolean appliancePowered() {
        return this.cio$powered;
    }

    @Override
    public void setAppliancePowered(boolean powered) {
        if (this.cio$powered == powered) {
            return;
        }
        this.cio$powered = powered;
        cio$be().setChanged();
    }

    @Override
    public boolean applianceReceivingPower() {
        return this.cio$receiving;
    }

    /** A {@code true} here is a live rail feeding us this tick: timestamp it. */
    @Override
    public void setApplianceReceivingPower(boolean receiving) {
        this.cio$receiving = receiving;
        if (receiving) {
            Level level = cio$be().getLevel();
            if (level != null) {
                this.cio$lastFed = level.getGameTime();
            }
        }
    }

    /** Whatever backend asks, the answer is the live feed, never a stale latch. */
    @Override
    public void reconcileAppliancePower() {
        setAppliancePowered(cio$fedRecently());
    }

    // --- metered + scaled load ---------------------------------------------------

    @Override
    public boolean cio$isConsuming() {
        return this.cio$watts > 0.0;
    }

    @Override
    public double cio$loadScale() {
        return this.cio$watts;
    }

    // --- client sync (IV ships none for its block entity) ------------------------

    /** Overrides {@code BlockEntity#getUpdatePacket} on the target (BuilderTileEntity declares none). */
    @Nullable
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(cio$be());
    }

    /** Overrides {@code BlockEntity#getUpdateTag}: node state + connections only, never IV's tile data. */
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(SYNC_MARKER, true);
        tag.putBoolean("CioLit", this.cio$lit);
        if (CrayfishCompat.present()) {
            IvCrayfishBridge.writeNodeNbt(cio$be(), tag);
        } else {
            writeApplianceNbt(tag);
        }
        return tag;
    }

    /**
     * A marked sync tag (chunk load, a data packet, or Crayfish's wire update)
     * is ours alone: apply it here and stop, so it never lands in IV's
     * {@code lastLoadedNBT}, which IV would otherwise try to rebuild the tile from.
     */
    @Inject(method = "loadAdditional", at = @At("HEAD"), cancellable = true, remap = false)
    private void cio$applySyncTag(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        if (!tag.getBoolean(SYNC_MARKER)) {
            return;
        }
        this.cio$lit = tag.getBoolean("CioLit");
        if (CrayfishCompat.present()) {
            IvCrayfishBridge.readNodeNbt(cio$be(), tag);
        } else if (tag.contains(ApplianceNode.NBT_CONNECTIONS)) {
            readApplianceNbt(tag);
        }
        if (this.tileEntity instanceof IvPowerState state) {
            state.cio$setIvPowered(this.cio$lit);
        }
        ci.cancel();
    }

    // --- persistence ------------------------------------------------------------

    @Inject(method = "saveAdditional", at = @At("TAIL"), remap = false)
    private void cio$saveNode(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        if (!CrayfishCompat.present()) {
            writeApplianceNbt(tag);
        }
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"), remap = false)
    private void cio$loadNode(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        if (!CrayfishCompat.present() && tag.contains(ApplianceNode.NBT_CONNECTIONS)) {
            readApplianceNbt(tag);
        }
    }
}
