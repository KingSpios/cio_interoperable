package com.cio.createinteroperable.mts;

import com.cio.createinteroperable.CIOBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The invisible power terminal CIO drops onto a ground-placed Immersive
 * Vehicles AA Base Plate, so the plate can be wired to a Domestic Electrical
 * Board like any furniture appliance.
 *
 * <p>IV's placed parts are entities, not blocks, and every appliance-grid
 * backend (Crayfish's electricity ticker and wrench, CIO's native grid) keys a
 * node by its block entity position. So this block carries the node on the
 * plate's behalf: it sits in the cell under one corner of the 2&times;2 plate
 * (see {@code MtsAaSearchlights}) and its node cube is drawn resting on the
 * plate itself. It has no model, no collision and no outline &mdash; the plate
 * stays IV's to click and break &mdash; and it removes itself once the plate it
 * belongs to is gone ({@link MtsAaPowerNodeBlockEntity#serverTick}).</p>
 */
public class MtsAaPowerNodeBlock extends Block implements EntityBlock {

    public MtsAaPowerNodeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MtsAaPowerNodeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != CIOBlockEntities.MTS_AA_POWER_NODE.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((MtsAaPowerNodeBlockEntity) be).serverTick();
    }
}
