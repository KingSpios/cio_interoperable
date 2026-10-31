package com.cio.createinteroperable.powerkit.cee;

import com.cio.createinteroperable.modules.ElectroEnergeticsModule;
import com.cio.createinteroperable.powerkit.PowerKitGeometry;
import com.cio.createinteroperable.powerkit.PowerKitPanel;
import com.cio.createinteroperable.powerkit.PowerKitSignalNub;
import com.cio.createinteroperable.powerkit.PowerKitSwitch;
import com.cio.createinteroperable.powerkit.cpg.PowerKitTier3BlockEntity;
import com.cio.createinteroperable.powerkit.cpg.PowerKitTier4Block;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * "CEE Industrial Power Kit" (tier 4) &mdash; CEE-wired twin of
 * {@link PowerKitTier4Block}. Same three-tap substation as its CPG counterpart;
 * eight CEE nodes at the tier-4 model's (wider) nub spots, ids matching
 * {@link PowerKitTier3BlockEntity#buildCircuit}.
 */
public class CeePowerKitTier4Block extends CeeDebRectifierBlock {

    private static final Map<Integer, Vec3> NODES = nodeMap(new double[][] {
            {10.0, 0.5, 3.0}, {6.0, 0.5, 3.0},
            {10.0, 17.5, 3.0}, {6.0, 17.5, 3.0},
            {14.5, 17.5, 4.5}, {1.5, 17.5, 4.5},
            {14.5, 17.5, 1.5}, {1.5, 17.5, 1.5},
    });

    public CeePowerKitTier4Block(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape outlineNorth() {
        return PowerKitGeometry.TIER4_SHAPE;
    }

    @Override
    public double[] signalNubBox() {
        return PowerKitSignalNub.TIER4_BOX;
    }

    @Override
    protected Map<Integer, Vec3> nodesNorth() {
        return NODES;
    }

    private static final String[] OUTLETS = {"HV", "120V", "12V"};

    @Override
    protected String[] outletsNorth() {
        return OUTLETS;
    }

    /** Intake (0/1) and HV Feed (2/3) nubs are 2&times;1&times;2 px; the 120 V / 12 V ones 1 px. */
    private static final Vec3 NUB_2X1X2 = new Vec3(2 / 16.0, 1 / 16.0, 2 / 16.0);

    @Override
    protected Vec3 nubSizeNorth(int id) {
        return id <= 3 ? NUB_2X1X2 : NUB_1PX;
    }

    /** Right-click with an empty hand opens the control panel ({@link PowerKitPanel}). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        InteractionResult result = PowerKitPanel.tryOpen(level, pos, player);
        return result != InteractionResult.PASS ? result : super.useWithoutItem(state, level, pos, player, hit);
    }

    /** The tier-4 model has no master switch; its main lever is on the control panel. */
    @Override
    public PowerKitSwitch.Kind masterSwitch() {
        return PowerKitSwitch.Kind.NONE;
    }

    /** The Industrial kit keeps its own look: no paint. */
    @Override
    public boolean dyeable() {
        return false;
    }

    @Override
    public BlockEntityType<? extends CeeDebRectifierBlockEntity> getBlockEntityType() {
        return ElectroEnergeticsModule.CEE_DEB_RECTIFIER_TIER4_BE.get();
    }
}
