package com.cio.createinteroperable;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.LevelTickAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The CEE column of an assembled Interoperable Transformer. TOP=false is
 * the functional bottom piece (real node pair, real InteroperableDevice);
 * TOP=true is a non-functional cap exposing zero nodes. Never placed
 * directly — only produced by InteroperableAssembly.
 *
 * PLACEHOLDER node coordinates below — swap once the real Blockbench
 * model's terminal cubes are final, same caveat as the PG side.
 */
public class InteroperableCeeAssembledBlock extends Block implements ElectricalDeviceBlock<InteroperableDevice> {
    private static final Vec3 CEE_NODE_0 = new Vec3(0.3, 0.5, 1.0);
    private static final Vec3 CEE_NODE_1 = new Vec3(0.7, 0.5, 1.0);

    public InteroperableCeeAssembledBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(CIOProperties.PG_FACING, Direction.NORTH)
                .setValue(CIOProperties.TOP, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CIOProperties.PG_FACING, CIOProperties.TOP);
    }

    @Override
    public SimulatedDeviceType<InteroperableDevice> getDevice() {
        return CIODevices.INTEROPERABLE.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        if (state.getValue(CIOProperties.TOP))
            return Map.of();
        return Map.of(0, CEE_NODE_0, 1, CEE_NODE_1);
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        if (state.getValue(CIOProperties.TOP))
            return null;
        return switch (id) {
            case 0 -> CEE_NODE_0;
            case 1 -> CEE_NODE_1;
            default -> null;
        };
    }

    // Copied from CEE's own SimpleElectricalDeviceBlock — this block can't
    // extend that class since it also has to extend/implement the PG-side
    // contracts on the other assembled blocks... actually it doesn't here,
    // but kept consistent with InteroperableAssembledBlock's approach for
    // the same reason: staying a plain Block, not SimpleElectricalDeviceBlock.

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        LevelTickAccess<Block> blockTicks = level.getBlockTicks();
        if (!blockTicks.hasScheduledTick(pos, this))
            level.scheduleTick(pos, this, 1);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        List<Integer> nodes = new ArrayList<>(getNodePositions(level, pos, state).keySet());
        InfrastructureSavedData sd = InfrastructureSavedData.load(level);
        sd.registerOrUpdateNodes(pos, nodes);
        super.tick(state, level, pos, random);
    }
}
