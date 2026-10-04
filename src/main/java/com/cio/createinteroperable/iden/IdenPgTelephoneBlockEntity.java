package com.cio.createinteroperable.iden;

import com.cio.createinteroperable.TelephoneRegistry;
import com.cio.createinteroperable.compat.ElectroEnergeticsCompat;
import com.cio.createinteroperable.grid.ApplianceGrid;
import com.cio.createinteroperable.grid.CrayfishCompat;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;

import java.util.List;

/**
 * Iden's Decor telephone with Power Grid installed (with or without Electro
 * Energetics). One PG terminal, the CPG tap, deliberately left with no
 * internal connection so its network identity reflects only real tap wiring
 * &mdash; the same reasoning as CIO's own {@code TelephoneBlockEntity#buildCircuit}.
 * The phone draws its power from the appliance grid, not from PG. With CEE
 * also installed, the block adds a CEE tap node and reachability checks it via
 * {@link IdenPhoneCeeTaps}.
 */
public class IdenPgTelephoneBlockEntity extends ElectricBlockEntity implements IdenPhone, IHaveGoggleInformation {

    private final IdenPhoneCore core = new IdenPhoneCore(this, this);
    private static final int TAP_TERMINAL = 0;
    private IElectricNode tapNode;

    public IdenPgTelephoneBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        builder.setTerminalCount(1);
        tapNode = builder.terminalNode(0);
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        core.tick();
    }

    @Override
    public IdenPhoneCore phoneCore() {
        return core;
    }

    @Override
    public BlockEntity applianceOwner() {
        return this;
    }

    @Override
    public Object pgTapNetwork() {
        boolean wired = electricBehaviour != null && electricBehaviour.getConnections()
                .containsKey(new org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint(worldPosition, TAP_TERMINAL));
        return tapNode == null || !wired ? null : tapNode.getNetwork();
    }

    @Override
    public boolean ceeTapReaches(BlockPos otherPos) {
        return ElectroEnergeticsCompat.present() && IdenPhoneCeeTaps.reaches(level, worldPosition, otherPos);
    }

    @Override
    public boolean pgTapConnected() {
        return pgTapNetwork() != null;
    }

    @Override
    public boolean ceeTapConnected() {
        return ElectroEnergeticsCompat.present() && IdenPhoneCeeTaps.wired(level, worldPosition);
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        TelephoneRegistry.add(this);
        if (!CrayfishCompat.present()) {
            ApplianceGrid.get(level).addNode(this);
        }
    }

    @Override
    public void remove() {
        super.remove();
        TelephoneRegistry.remove(this);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        core.write(tag);
        // Native links; the Crayfish adapter owns the same keys when it is active.
        if (!CrayfishCompat.present()) {
            writeApplianceNbt(tag);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        core.read(tag);
        if (!CrayfishCompat.present()) {
            readApplianceNbt(tag);
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        core.addGoggleTooltip(tooltip);
        return true;
    }
}
