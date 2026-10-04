package com.cio.createinteroperable.mts;

import com.cio.createinteroperable.CIOBlockEntities;
import com.cio.createinteroperable.deb.MeteredAppliance;
import com.cio.createinteroperable.deb.ScalableAppliance;
import com.cio.createinteroperable.grid.ApplianceGrid;
import com.cio.createinteroperable.grid.ApplianceNode;
import com.cio.createinteroperable.grid.CrayfishCompat;
import com.cio.createinteroperable.grid.GridConnection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * The appliance-grid node of a ground-placed Immersive Vehicles AA Base Plate
 * (see {@link MtsAaPowerNodeBlock} for why it has to be a separate, invisible
 * block). Deliberately free of any IV type: the plate side
 * ({@code MtsAaSearchlights}, run from the plate's own tick) pushes everything
 * this needs into it &mdash; which plate it belongs to, where its nub sits on
 * that plate, whether the plate was seen this tick, and what the mounted
 * Spotlight is currently asking for &mdash; and reads {@link #appliancePowered()}
 * back to decide whether the Spotlight may run.
 *
 * <p><b>Load.</b> The Spotlight is a searchlight-class arc lamp, so it is
 * billed as one: {@link #BEAM_WATTS} on the 120&nbsp;V rail while the beam is
 * lit, plus a small traverse-servo share while it is auto-rotating. Idle (no
 * Spotlight, or Spotlight switched off and still) it draws nothing, but the
 * plate still reports powered/unpowered so it can be wired and checked before
 * the lamp goes on.</p>
 *
 * <p><b>Backends.</b> Native {@link ApplianceNode} here; with Refurbished
 * Furniture installed {@code mixin.crayfish.MtsAaPowerNodeCrayfishMixin} bolts
 * Crayfish's {@code IModuleNode} on and forwards to it, exactly like
 * {@code LetsDoLampBlockEntity}.</p>
 */
public class MtsAaPowerNodeBlockEntity extends BlockEntity implements ApplianceNode, MeteredAppliance, ScalableAppliance {

    /** Rated draw of the lit Spotlight: a 120 V searchlight arc lamp. See ApplianceLoads. */
    public static final double BEAM_WATTS = 6000.0;
    /** Auto-rotate traverse servo, as a share of {@link #BEAM_WATTS} (300 W). */
    public static final double SERVO_SHARE = 0.05;

    /** Ticks after load before a missing plate may remove this node (IV entities load after blocks). */
    private static final int GRACE_TICKS = 200;
    /** Ticks without a plate check-in (at an entity-ticking position) before this node removes itself. */
    private static final long STALE_TICKS = 100L;

    /** Half-width of the nub cube, and its height span resting on the 5/16-thick plate. */
    private static final double NUB_HALF = 0.125;
    private static final double NUB_MIN_Y = 0.3125;
    private static final double NUB_MAX_Y = 0.5625;

    private final Set<GridConnection> connections = new HashSet<>();
    private boolean receivingPower;
    private boolean nodePowered;

    /** The block cell of the plate this node serves (the cell its IV entity stands in). */
    @Nullable
    private BlockPos basePos;
    /** Block-local centre (x, z) of the nub, on the plate's corner. */
    private double nubX = 0.5;
    private double nubZ = 0.5;

    // Transient, server-only.
    private boolean beamOn;
    private boolean servoOn;
    private long lastSeen = -1L;
    private long lastFed = Long.MIN_VALUE / 2;
    private int age;

    public MtsAaPowerNodeBlockEntity(BlockPos pos, BlockState state) {
        super(CIOBlockEntities.MTS_AA_POWER_NODE.get(), pos, state);
    }

    // --- plate -> node ---------------------------------------------------

    @Nullable
    public BlockPos basePos() {
        return this.basePos;
    }

    /** Tie this node to a plate and place its nub; a no-op when unchanged. */
    public void bind(BlockPos base, double nubX, double nubZ) {
        if (base.equals(this.basePos) && this.nubX == nubX && this.nubZ == nubZ) {
            return;
        }
        this.basePos = base.immutable();
        this.nubX = nubX;
        this.nubZ = nubZ;
        this.setChanged();
        this.syncApplianceNode();
    }

    /** The plate checked in this tick; keeps the node alive. */
    public void markBaseSeen(long gameTime) {
        this.lastSeen = gameTime;
    }

    /** What the mounted Spotlight is asking for right now (billing only). */
    public void setDemand(boolean beam, boolean servo) {
        this.beamOn = beam;
        this.servoOn = servo;
    }

    /**
     * Self-removal once the plate is gone. Only counts time while the plate's
     * cell is entity-ticking: at the edge of simulation distance block entities
     * still tick but IV's entities don't, and that must not read as "plate
     * broken". Links on the far end are pruned lazily by the grid, as for any
     * broken appliance.
     */
    void serverTick() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }
        // Re-decided every tick from the live feed, independent of any grid ticker.
        setAppliancePowered(fedRecently());
        long now = serverLevel.getGameTime();
        if (this.lastSeen < 0) {
            this.lastSeen = now;
        }
        if (++this.age < GRACE_TICKS || now % 20L != 0L) {
            return;
        }
        if (this.basePos == null) {
            serverLevel.removeBlock(this.worldPosition, false);
            return;
        }
        if (!serverLevel.isPositionEntityTicking(this.basePos)) {
            this.lastSeen = now;
            return;
        }
        if (now - this.lastSeen > STALE_TICKS) {
            serverLevel.removeBlock(this.worldPosition, false);
        }
    }

    // --- ApplianceNode ---------------------------------------------------

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
        return 4;
    }

    /** The nub rests on the plate's corner rather than floating at cell centre. */
    @Override
    public AABB applianceNodeBox() {
        return new AABB(this.nubX - NUB_HALF, NUB_MIN_Y, this.nubZ - NUB_HALF,
                this.nubX + NUB_HALF, NUB_MAX_Y, this.nubZ + NUB_HALF);
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
        this.syncApplianceNode();
    }

    @Override
    public boolean applianceReceivingPower() {
        return this.receivingPower;
    }

    /** A {@code true} here is a live rail feeding us this tick: timestamp it. */
    @Override
    public void setApplianceReceivingPower(boolean receiving) {
        this.receivingPower = receiving;
        if (receiving && this.level != null) {
            this.lastFed = this.level.getGameTime();
        }
    }

    /**
     * Power is a live feed, not a latch: powered only if a live rail marked us
     * this tick or the one before. A board stops marking the moment its supply
     * is cut (and 3 s into a brownout), so the Spotlight goes dark within two
     * ticks of that, every time, whichever backend runs this.
     */
    @Override
    public void reconcileAppliancePower() {
        setAppliancePowered(fedRecently());
    }

    private boolean fedRecently() {
        return this.level != null && this.level.getGameTime() - this.lastFed <= 1L;
    }

    // --- metered + scaled load -------------------------------------------

    @Override
    public boolean cio$isConsuming() {
        return this.beamOn || this.servoOn;
    }

    @Override
    public double cio$loadScale() {
        return (this.beamOn ? 1.0 : 0.0) + (this.servoOn ? SERVO_SHARE : 0.0);
    }

    // --- client sync -----------------------------------------------------

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        return this.saveWithoutMetadata(provider);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection connection, ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider provider) {
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            this.loadAdditional(tag, provider);
        }
    }

    // --- persistence -------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        if (this.basePos != null) {
            tag.putLong("BasePos", this.basePos.asLong());
        }
        tag.putDouble("NubX", this.nubX);
        tag.putDouble("NubZ", this.nubZ);
        tag.putBoolean("NodePowered", this.nodePowered);
        // Native links; the Crayfish adapter owns the same keys when it is active.
        if (!CrayfishCompat.present()) {
            this.writeApplianceNbt(tag);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        this.basePos = tag.contains("BasePos", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("BasePos")) : null;
        if (tag.contains("NubX")) {
            this.nubX = tag.getDouble("NubX");
            this.nubZ = tag.getDouble("NubZ");
        }
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
}
