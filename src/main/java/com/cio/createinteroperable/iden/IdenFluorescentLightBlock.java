package com.cio.createinteroperable.iden;

import com.cio.createinteroperable.CIOConfig;
import com.cio.createinteroperable.letsdo.LetsDoLampBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Stand-in for Iden's Decor's Fluorescent Light Block ({@code
 * iden_decor:fluorescent_light_block}). Iden registers it as a bare vanilla
 * {@link Block} &mdash; no class of its own to mix into &mdash; so
 * {@code mixin.letsdo.IdenModBlocksMixin} swaps this subclass in at Iden's own
 * registration call (same id, same properties: strength 2, light 14).
 *
 * <p>It then behaves like the Wall Lamp / Flood Lamp / Floodlight ({@code
 * IdenLampBlockMixin}): carries the shared {@link LetsDoLampBlockEntity} node
 * (3&nbsp;W on 12&nbsp;V, 6 links) and casts no light while unwired or
 * unpowered. There is no "off" model, so only the light it casts changes.
 * Gated by {@code lamps.requirePower}.</p>
 */
public class IdenFluorescentLightBlock extends Block implements EntityBlock {

    public IdenFluorescentLightBlock() {
        super(BlockBehaviour.Properties.of().strength(2.0f).lightLevel(state -> 14));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return CIOConfig.LETSDO_LAMPS_REQUIRE_POWER.get()
                ? new LetsDoLampBlockEntity(pos, state)
                : null;
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof LetsDoLampBlockEntity lamp && !lamp.isLampLit()
                ? 0
                : state.getLightEmission();
    }

    @Override
    public boolean hasDynamicLightEmission(BlockState state) {
        return true;
    }
}
