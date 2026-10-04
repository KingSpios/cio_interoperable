package com.cio.createinteroperable.iden;

import com.cio.createinteroperable.CIODevices;
import com.cio.createinteroperable.compat.PnwTerminalDevice;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

/**
 * {@link IdenPgTelephoneBlock} plus an Electro Energetics tap node (id 2, the
 * CEE nub beside the CPG one) &mdash; the swap used when both grids are
 * installed. The node has no circuit behind it ({@link PnwTerminalDevice} is
 * CIO's inert placeholder device); it exists only to be walked for
 * reachability.
 *
 * <p>{@code onPlace}/{@code tick} are copied from CEE's own
 * {@code SimpleElectricalDeviceBlock} (as CIO's Interoperable Telephone does),
 * since this already extends PG's {@code ElectricBlock}: CEE only learns a
 * block's nodes exist via {@code registerOrUpdateNodes} from a scheduled tick.</p>
 */
public class IdenInteropTelephoneBlock extends IdenPgTelephoneBlock implements ElectricalDeviceBlock<PnwTerminalDevice> {

    @Override
    public SimulatedDeviceType<PnwTerminalDevice> getDevice() {
        return CIODevices.PNW_TERMINAL.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return Map.of(IdenPhoneBlocks.CEE_TAP_NODE, IdenPhoneBlocks.ceeTapPoint(state));
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        return id == IdenPhoneBlocks.CEE_TAP_NODE ? IdenPhoneBlocks.ceeTapPoint(state) : null;
    }

    @Override
    public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int id) {
        return com.cio.createinteroperable.TelephoneLabels.tap();
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        InfrastructureSavedData.load(level).registerOrUpdateNodes(pos, List.copyOf(getNodePositions(level, pos, state).keySet()));
        super.tick(state, level, pos, random);
    }
}
