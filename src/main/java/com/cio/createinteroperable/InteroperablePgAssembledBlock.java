package com.cio.createinteroperable;

import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.ElectricBlock;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.electricity.base.terminals.BlockStateTerminalCollection;

/**
 * The PG column of an assembled Interoperable Transformer. TOP=false is
 * the functional bottom piece (real terminals, real BlockEntity); TOP=true
 * is a structural cap, matching how only Medium Transformer's PART 0
 * actually does anything. Never placed directly — only produced by
 * InteroperableAssembly.
 *
 * Both TOP states expose the SAME 2 terminals — confirmed by an actual
 * runtime crash (not just a compile error) that PG's own
 * BlockStateTerminalCollection.Builder#build throws
 * "IllegalStateException: All states must map the same number of terminals"
 * if different blockstates of one Block report different terminal counts.
 * An earlier draft gave TOP=true zero terminals to mean "non-functional
 * cap" and it crashed on RegisterEvent for exactly this reason — that
 * design is incompatible with PG's API, not just a style choice. The cap's
 * terminals are real (wire-attachable) but its BlockEntity keeps them
 * electrically inert (see InteroperablePgAssembledBlockEntity).
 *
 * PLACEHOLDER terminal coordinates below (base orientation = PG_FACING
 * SOUTH) — swap once the real Blockbench model's terminal cubes are final,
 * same as before. Rotation mechanism itself (rotateAroundY per PG_FACING,
 * same technique DeviceConnectorBlock uses for its own FACING) is real and
 * shouldn't need to change, just the base numbers.
 */
public class InteroperablePgAssembledBlock extends ElectricBlock implements IBE<InteroperablePgAssembledBlockEntity> {
    private static final TerminalBoundingBox PG_TERMINAL_POSITIVE_BASE =
            new TerminalBoundingBox(IDecoratedTerminal.POSITIVE, 3, 6, 14, 7, 10, 16)
                    .withColor(IDecoratedTerminal.RED);
    private static final TerminalBoundingBox PG_TERMINAL_NEGATIVE_BASE =
            new TerminalBoundingBox(IDecoratedTerminal.NEGATIVE, 9, 6, 14, 13, 10, 16)
                    .withColor(IDecoratedTerminal.BLUE);

    private static final VoxelShape DEFAULT_SHAPE = Shapes.box(0.25, 0, 0.25, 0.75, 1, 0.75);

    public InteroperablePgAssembledBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(CIOProperties.PG_FACING, Direction.NORTH)
                .setValue(CIOProperties.TOP, false));
        setTerminalCollection(BlockStateTerminalCollection.builder(this)
                .forAllStates(InteroperablePgAssembledBlock::terminalsFor)
                .withShapeMapper(state -> DEFAULT_SHAPE)
                .build());
    }

    private static TerminalBoundingBox[] terminalsFor(BlockState state) {
        int angle = switch (state.getValue(CIOProperties.PG_FACING)) {
            case SOUTH -> 0;
            case WEST -> 90;
            case NORTH -> 180;
            case EAST -> 270;
            default -> 0;
        };
        return new TerminalBoundingBox[] {
                PG_TERMINAL_POSITIVE_BASE.rotateAroundY(angle),
                PG_TERMINAL_NEGATIVE_BASE.rotateAroundY(angle)
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CIOProperties.PG_FACING, CIOProperties.TOP);
    }

    @Override
    public Class<InteroperablePgAssembledBlockEntity> getBlockEntityClass() {
        return InteroperablePgAssembledBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends InteroperablePgAssembledBlockEntity> getBlockEntityType() {
        return CIOBlockEntities.PG_ASSEMBLED.get();
    }
}
