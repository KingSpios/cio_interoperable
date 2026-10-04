package com.cio.createinteroperable.mixin.letsdo;

import com.cio.createinteroperable.CIOConfig;
import com.cio.createinteroperable.letsdo.LetsDoLampBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.extensions.IBlockExtension;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Iden's Decor Wall Lamp, Flood Lamp and Floodlight are plain always-lit blocks
 * (fixed {@code lightLevel}, no block entity, no on/off blockstate, and no
 * Crayfish node of Iden's own). Same treatment as Alpine Whispers' fairy lights
 * ({@link AlpineFairyLightsBlockMixin}): each carries the shared
 * {@link LetsDoLampBlockEntity} node (3&nbsp;W on 12&nbsp;V, 6 links) and
 * casts no light while unwired or unpowered. Their models aren't emissive, so
 * they look the same on and off &mdash; only the light they cast changes.
 *
 * <p>Gated by {@code lamps.requirePower}. Name-targeted, non-required &mdash; a
 * no-op without Iden's Decor.</p>
 */
@Mixin(targets = {
        "net.identidade.iden_decor.block.custom.WallLampBlock",
        "net.identidade.iden_decor.block.custom.FloodLampBlock",
        "net.identidade.iden_decor.block.custom.FloodlightBlock"})
public abstract class IdenLampBlockMixin implements EntityBlock, IBlockExtension {

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
