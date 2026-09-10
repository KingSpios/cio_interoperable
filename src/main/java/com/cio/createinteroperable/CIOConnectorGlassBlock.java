package com.cio.createinteroperable;

import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.electricity.base.terminals.BlockStateTerminalCollection;
import org.patryk3211.powergrid.electricity.wireconnector.ConnectorBlockEntity;
import org.patryk3211.powergrid.electricity.wireconnector.HeavyConnectorBlock;

/**
 * Purely aesthetic reskin of PG's own {@link HeavyConnectorBlock} (the
 * large/heavy/HV connector) — same accepts-any-wire-type override, same
 * zero-resistance single-terminal circuit (shared ConnectorBlockEntity).
 * Not CEE-compatible, same reasoning as CIOConnectorBlock.
 * <p>
 * Unlike CIOConnectorBlock, the terminal geometry is NOT inherited from
 * HeavyConnectorBlock's own TERMINAL_DOWN — that box was sized/positioned
 * for PG's own (differently-shaped) model. This model's own top cube is
 * named "main_pin" (cio_connector_glass.json, from [7,10,7] to [9,12,9],
 * origin [8,11,8]) — the actual wire-attach nub — so the terminal is
 * rebuilt from those exact coordinates instead. Rotation logic (the
 * per-FACING switch) is copied verbatim from HeavyConnectorBlock's own
 * constructor — same rotateAroundX/Y composition, just applied to this
 * model-derived box instead of PG's.
 */
public class CIOConnectorGlassBlock extends HeavyConnectorBlock {
    /** = the model's own "main_pin" cube (cio_connector_glass.json), origin matching its own rotation origin. */
    private static final TerminalBoundingBox MAIN_PIN_TERMINAL_DOWN =
            new TerminalBoundingBox(IDecoratedTerminal.CONNECTOR, 7, 10, 7, 9, 12, 9)
                    .withOrigin(8, 11, 8);

    /**
     * Outline / pick / collision shape, authored for FACING=DOWN (cio_connector_glass.json
     * as-modelled) and rotated for the other five facings. See CIOConnectorBlock.BODY_DOWN
     * for why this is needed instead of the Shapes.empty() PG's own connectors pass:
     * ElectricBlock#getShape = (this mapper) UNION (terminal boxes), and our main_pin
     * terminal is a tight 1/16 nub, so an empty base made only the nub left-clickable.
     */
    private static final VoxelShape BODY_DOWN = Shapes.or(
            box(6, 1.6, 6, 10, 10.3, 10),  // stacked insulator discs
            box(7, 0, 7, 9, 12.3, 9));     // connector_pin segments + main_pin tip
    private static final VoxelShaper BODY_SHAPER = VoxelShaper.forDirectional(BODY_DOWN, Direction.DOWN);

    public CIOConnectorGlassBlock(Properties settings) {
        super(settings);
        // Overwrites the terminal collection HeavyConnectorBlock's own
        // constructor already set via super(settings) above — setTerminalCollection
        // just stores the field, safe to call again with our own collection.
        setTerminalCollection(BlockStateTerminalCollection
                .builder(this)
                .forAllStates(state -> {
                    var terminal = switch (state.getValue(FACING)) {
                        case UP -> MAIN_PIN_TERMINAL_DOWN.rotateAroundX(180);
                        case DOWN -> MAIN_PIN_TERMINAL_DOWN;
                        case NORTH -> MAIN_PIN_TERMINAL_DOWN.rotateAroundX(-90);
                        case SOUTH -> MAIN_PIN_TERMINAL_DOWN.rotateAroundX(90);
                        case EAST -> MAIN_PIN_TERMINAL_DOWN.rotateAroundX(90).rotateAroundY(-90);
                        case WEST -> MAIN_PIN_TERMINAL_DOWN.rotateAroundX(90).rotateAroundY(90);
                    };
                    return new TerminalBoundingBox[] { terminal };
                })
                .withShapeMapper(state -> BODY_SHAPER.get(state.getValue(FACING)))
                .build()
        );
    }

    @Override
    public BlockEntityType<? extends ConnectorBlockEntity> getBlockEntityType() {
        return CIOBlockEntities.CONNECTOR.get();
    }
}
