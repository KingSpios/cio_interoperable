package com.cio.createinteroperable.deb;

import com.cio.createinteroperable.grid.ApplianceNode;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * The DEB / Power Kit's node link box &mdash; a compact box just in front of
 * {@code body_box}'s front face (z=4 NORTH), low and centred so it clears the
 * viewer plates, rotated for all six facings. Used by both PG- and CEE-rooted
 * grid backends: {@code DebSourceNodeMixin#getNodeInteractBox} /
 * {@code CeeDebSourceNodeMixin#getNodeInteractBox} (Crayfish) and
 * {@link DebRectifierBlockEntity#applianceNodeBox()} (native).
 *
 * <p>Carries no {@code com.mrcrayfish.*} reference &mdash; the fallback box is
 * {@link ApplianceNode#APPLIANCE_NODE_BOX}, which is the identical centred 4&nbsp;px
 * cube as Crayfish's {@code ISourceNode.DEFAULT_NODE_BOX}. Reads the plain
 * vanilla {@link BlockStateProperties#FACING} rather than PG's
 * {@code DirectionalElectricBlock.FACING} (the very same property object,
 * confirmed elsewhere in this codebase) so that neither this class nor any
 * CEE-rooted caller ever touches a Power Grid type.</p>
 */
public final class DebNodeBox {

    private static final AABB NORTH =
            new AABB(6 / 16.0, 1 / 16.0, 4 / 16.0, 10 / 16.0, 4 / 16.0, 7 / 16.0);
    private static final VoxelShaper SHAPER = VoxelShaper
            .forDirectional(Shapes.create(NORTH), Direction.NORTH)
            .withVerticalShapes(Shapes.create(NORTH));

    private DebNodeBox() {
    }

    public static AABB forState(BlockState state) {
        if (!state.hasProperty(BlockStateProperties.FACING)) {
            return ApplianceNode.APPLIANCE_NODE_BOX;
        }
        return SHAPER.get(state.getValue(BlockStateProperties.FACING)).bounds();
    }
}
