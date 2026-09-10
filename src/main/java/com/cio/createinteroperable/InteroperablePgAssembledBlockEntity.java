package com.cio.createinteroperable;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.sim.node.ProvidedVoltageSourceCoupling;

/**
 * The Power Grid side of the bridge — now living at its own position within
 * the assembled 2x3 structure, 2 blocks away from the CEE device instead of
 * sharing one BlockPos with it.
 *
 * Both TOP states build the same 2-terminal circuit — PG's own
 * BlockStateTerminalCollection.Builder requires every blockstate of a Block
 * to report the same terminal count (confirmed by a real runtime crash,
 * "IllegalStateException: All states must map the same number of
 * terminals", see InteroperablePgAssembledBlock), so buildCircuit() has to
 * match that shape too. The cap (TOP=true) keeps its 2-terminal coupling
 * electrically real but never looks up or syncs a CEE device — see the TOP
 * check in electricalTick() — so it stays inert in practice, just without
 * lying to PG's terminal system about how many terminals it has.
 *
 * Two terminals (not one referenced to network-zero/ground): matches
 * DeviceConnectorBlock's convention for an actual two-terminal device, and
 * is also the electrically correct shape for a transformer winding — an
 * isolated loop, not a grounded tap.
 */
public class InteroperablePgAssembledBlockEntity extends ElectricBlockEntity {
    private ProvidedVoltageSourceCoupling coupling;

    /**
     * Cached because InteroperableDevice lives in Electro Energetics' own
     * DevicesSavedData, keyed by the CEE column's BlockPos — 2 blocks away
     * from this entity's own position, computed via findCeePosition().
     */
    private InteroperableDevice ceeDevice;

    public InteroperablePgAssembledBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        builder.setTerminalCount(2);
        // This feature has no direction slider — PG is always source, CEE
        // always sink — so this coupling only ever plays the "sense" role.
        // Fixed at SENSE_RESISTANCE (not a shared small value) for the same
        // reason as InteroperableSmallBlockEntity: a small coupling
        // resistance here would make this device a near dead-short in the
        // source's own series loop, collapsing almost the entire source
        // voltage across whatever real resistor is out in the world instead
        // of across this block. See InteroperableDevice.SENSE_RESISTANCE.
        coupling = builder.addInternalNode(ProvidedVoltageSourceCoupling.class,
                builder.terminalNode(0), builder.terminalNode(1), InteroperableDevice.SENSE_RESISTANCE);
        coupling.setVoltageProvider(() -> 0.0);
    }

    /**
     * The CEE column's bottom position sits 2 blocks past this one, in the
     * direction opposite PG_FACING (PG_FACING points core->PG, so walking
     * that direction backwards twice from the PG position crosses the core
     * column and reaches the CEE column). Mirrors how TransformerMediumBlock's
     * non-owning PARTs compute the entity-owning part's position via offset
     * math from their own PART + HORIZONTAL_AXIS state.
     */
    private BlockPos findCeePosition() {
        var pgFacing = getBlockState().getValue(CIOProperties.PG_FACING);
        var towardCee = pgFacing.getOpposite();
        return worldPosition.relative(towardCee).relative(towardCee);
    }

    @Override
    public void electricalTick() {
        super.electricalTick();
        if (!(level instanceof ServerLevel serverLevel) || coupling == null
                || getBlockState().getValue(CIOProperties.TOP))
            return;

        if (ceeDevice == null || !ceeDevice.isValid()) {
            ceeDevice = DevicesSavedData.load(serverLevel).getDevice(findCeePosition(), InteroperableDevice.class);
        }
        if (ceeDevice != null) {
            double potentialDifference = coupling.getPositive().getVoltage() - coupling.getNegative().getVoltage();
            ceeDevice.setPowerGridVoltage(potentialDifference);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
    }
}
