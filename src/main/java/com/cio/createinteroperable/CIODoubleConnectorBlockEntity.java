package com.cio.createinteroperable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.wireconnector.ConnectorBlockEntity;

/**
 * Same zero-resistance, no-coupling circuit as PG's own {@link ConnectorBlockEntity}
 * (see its {@code buildCircuit}, decompiled: just {@code setTerminalCount(1)}, no
 * coupling between anything) — this just declares 2 terminals instead of 1, so the
 * two physical connector points on {@link CIODoubleConnectorBlock} are independent,
 * unconnected nodes (PG's own {@link org.patryk3211.powergrid.electricity.wireconnector.CordJunctionBlockEntity}
 * follows the identical pattern for its own 2 terminals). Not wired to each other on
 * purpose — this block only exists to let two separate wire runs terminate at one
 * block position instead of needing two.
 */
public class CIODoubleConnectorBlockEntity extends ConnectorBlockEntity {
    public CIODoubleConnectorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void buildCircuit(IElectricEntity.CircuitBuilder builder) {
        builder.setTerminalCount(2);
    }
}
