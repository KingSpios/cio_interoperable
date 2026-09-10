package com.cio.createinteroperable.mixin.letsdo;

import com.cio.createinteroperable.CIOConfig;
import com.cio.createinteroperable.letsdo.LetsDoLampBlockEntity;
import com.cio.createinteroperable.letsdo.LetsDoLampStates;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes every Let's Do Furniture / Candlelight lamp &amp; street-lantern block
 * an {@link EntityBlock}, so a {@link LetsDoLampBlockEntity} (a Crayfish
 * electricity node) rides on the <b>head</b> segment of each &mdash; the lamp
 * then only lights while powered from a network that, via a CIO Power Kit,
 * reaches Power Grid. Crayfish ignores blocks with no block entity, so this is
 * the only way to give them a node.
 *
 * <p>Only the head ({@code type = none}/{@code top}, or any non-stacking lamp)
 * gets a node; pole segments ({@code middle}/{@code bottom}) stay dumb. Because
 * a segment's {@code type} changes as a post is built/broken and a same-block
 * {@code setBlock} keeps the block entity, {@link #cio$reconcileLampNode} adds
 * or drops the node in {@code neighborChanged} when a segment crosses that
 * boundary (the stacking blocks that don't override {@code neighborChanged}, and
 * the non-stacking lamps, simply never trip it &mdash; {@code require = 0}).
 *
 * <p>Name-targeted, non-required &mdash; a no-op without those mods.
 */
@Mixin(targets = {
        "com.berksire.furniture.core.block.LampBlock",
        "com.berksire.furniture.core.block.LampWallBlock",
        "com.berksire.furniture.core.block.StreetLanternBlock",
        "com.berksire.furniture.core.block.StreetLanternWallBlock",
        "net.satisfy.candlelight.core.block.LampBlock"
})
public abstract class LetsDoLampBlockMixin implements EntityBlock {

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return CIOConfig.LETSDO_LAMPS_REQUIRE_POWER.get() && LetsDoLampStates.isHeadSegment(state)
                ? new LetsDoLampBlockEntity(pos, state)
                : null;
    }

    /**
     * A freshly-placed lamp that will carry a node must be placed <b>dark</b>.
     * The street lanterns default {@code LIT=true}, so without this they flash
     * lit for the tick or two before the node's first
     * {@code updateNodePoweredState()} runs. (Furniture / Candlelight table
     * lamps already default off, so in practice this only touches the lanterns.)
     * {@code applyLit()} still repaints the correct state every tick, so a
     * mistaken darken here would self-heal regardless.
     */
    @Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true, require = 0)
    private void cio$placeUnlit(BlockPlaceContext context, CallbackInfoReturnable<BlockState> cir) {
        BlockState placed = cir.getReturnValue();
        if (placed != null
                && CIOConfig.LETSDO_LAMPS_REQUIRE_POWER.get()
                && LetsDoLampStates.isHeadSegment(placed)
                && LetsDoLampStates.isLit(placed) == Boolean.TRUE) {
            cir.setReturnValue(LetsDoLampStates.withLit(placed, false));
        }
    }

    @Inject(method = "neighborChanged", at = @At("TAIL"), require = 0)
    private void cio$reconcileLampNode(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
                                      boolean isMoving, CallbackInfo ci) {
        if (level.isClientSide) {
            return;
        }
        BlockState now = level.getBlockState(pos);
        boolean head = LetsDoLampStates.isHeadSegment(now);
        boolean hasNode = level.getBlockEntity(pos) instanceof LetsDoLampBlockEntity;
        if (head && !hasNode && CIOConfig.LETSDO_LAMPS_REQUIRE_POWER.get()) {
            level.setBlockEntity(new LetsDoLampBlockEntity(pos, now));
        } else if (!head && hasNode) {
            level.removeBlockEntity(pos);
        }
    }
}
