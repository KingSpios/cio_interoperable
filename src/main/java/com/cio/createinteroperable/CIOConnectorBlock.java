package com.cio.createinteroperable;

import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.electricity.base.terminals.BlockStateTerminalCollection;
import org.patryk3211.powergrid.electricity.wireconnector.ConnectorBlock;
import org.patryk3211.powergrid.electricity.wireconnector.ConnectorBlockEntity;

/**
 * Purely aesthetic reskin of PG's own {@link ConnectorBlock} (the light
 * wire connector) — same wire-type restriction (light wires only, via the
 * inherited default IElectric#accepts), same zero-resistance single-terminal
 * circuit (shared ConnectorBlockEntity). Only the model/texture differ
 * (cio_connector.json). Not CEE-compatible — deliberately PG-only, per this
 * being an aesthetic variant, not a new device.
 * <p>
 * Can't reuse PG's own registered BlockEntityType (ModdedBlockEntities.WIRE_CONNECTOR)
 * as-is — it's constructed with an explicit validBlocks(...) allow-list
 * (PG's own WIRE_CONNECTOR/HEAVY_WIRE_CONNECTOR blocks only), so a
 * different Block instance placed there would fail BlockEntityType's own
 * validity check. Overriding getBlockEntityType() to point at CIO's own
 * CIOBlockEntities.CONNECTOR (registered with THIS block in its validBlocks)
 * sidesteps that while still using the exact same ConnectorBlockEntity class.
 * <p>
 * Terminal geometry is NOT inherited from ConnectorBlock's own TERMINAL_DOWN
 * (sized for PG's own model) — same reasoning as CIOConnectorGlassBlock.
 * cio_connector.json now names its own small diamond-shaped tip nub
 * "main_pin" ([7.5,4.4,7.5]-[8.5,5.4,8.5], origin [8,4.9,8]), so the
 * terminal is rebuilt from those exact coordinates instead. The 45-degree
 * Y rotation on that element is a purely cosmetic diamond twist — doesn't
 * affect the axis-aligned bounding box used here, same as how the
 * Telephone's own diamond-nub terminals (pulse_sender_tap_above/below)
 * already ignore their own cosmetic rotation.
 */
public class CIOConnectorBlock extends ConnectorBlock {
    private static final TerminalBoundingBox MAIN_PIN_TERMINAL_DOWN =
            new TerminalBoundingBox(IDecoratedTerminal.CONNECTOR, 7.5, 4.4, 7.5, 8.5, 5.4, 8.5)
                    .withOrigin(8, 4.9, 8);

    /**
     * Outline / pick / collision shape, authored for FACING=DOWN (the model as it
     * sits in cio_connector.json) and rotated for the other five facings.
     * <p>
     * ElectricBlock#getShape is driven by BlockStateTerminalCollection#shapeMapper(),
     * which returns (this mapper's result) UNION (every TerminalBoundingBox#getShape()).
     * PG's own ConnectorBlock passes Shapes.empty() here and relies on its large
     * TERMINAL_DOWN box (5,0,5)-(11,10,11) to stand in as the body shape. Our
     * main_pin terminal is a deliberately tight 1/16 nub, so an empty base left the
     * block left-clickable only on that nub. Build the body from the model's own
     * elements instead and keep the terminal box tight.
     */
    private static final VoxelShape BODY_DOWN = Shapes.or(
            box(6, 0, 6, 10, 4.1, 10),   // stacked insulator discs
            box(7, 0, 7, 9, 5.4, 9));    // connector_pin + main_pin tip
    private static final VoxelShaper BODY_SHAPER = VoxelShaper.forDirectional(BODY_DOWN, Direction.DOWN);

    public CIOConnectorBlock(Properties settings) {
        super(settings);
        // Overwrites the terminal collection ConnectorBlock's own
        // constructor already set via super(settings) above.
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
