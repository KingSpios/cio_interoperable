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
 * Alpine Whispers' Fairy Lights are a plain always-lit {@code Block} (no block
 * entity, no on/off blockstate &mdash; light comes from a fixed
 * {@code lightLevel(9)} lambda). To power-gate them the same way as the Let's Do
 * lamps, this makes the block an {@link EntityBlock} carrying the shared
 * {@link LetsDoLampBlockEntity} (one node per string) and overrides NeoForge's
 * position-aware {@code getLightEmission} so an unwired or unpowered string
 * casts no light. (The string keeps its own emissive texture &mdash; there is no
 * dark model to swap to &mdash; but it lights nothing and mobs spawn under it.)
 *
 * <p>Billing, wrench linking, the "Missing power" overlay and the node/wire
 * rendering all come from the existing {@code createinteroperable:letsdo_lamp}
 * plumbing (3&nbsp;W, 12&nbsp;V, via {@code ApplianceLoads}), gated by the same
 * {@code lamps.requirePower} config. Node teardown rides on
 * {@link LetsDoLampBlockEntity#setRemoved()} like every other lamp.
 *
 * <p>Name-targeted, non-required &mdash; a no-op without Alpine Whispers.
 */
@Mixin(targets = "net.satisfy.alpinewhispers.core.block.FairyLightsBlock")
public abstract class AlpineFairyLightsBlockMixin implements EntityBlock, IBlockExtension {

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return CIOConfig.LETSDO_LAMPS_REQUIRE_POWER.get()
                ? new LetsDoLampBlockEntity(pos, state)
                : null;
    }

    /**
     * NeoForge {@code IBlockExtension} hook &mdash; the 3-arg, position-aware
     * light query. {@code state.getLightEmission()} (no args) is the cached
     * vanilla value, so there is no recursion.
     */
    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof LetsDoLampBlockEntity lamp && !lamp.isLampLit()
                ? 0
                : state.getLightEmission();
    }

    /**
     * Without this, NeoForge treats the emission as static: it reads
     * {@link #getLightEmission(BlockState, BlockGetter, BlockPos)} once, caches
     * it, and never re-polls &mdash; so the string would stay lit forever. See
     * {@code ChunkAccess.findBlockLightSources} / {@code BlockLightEngine}.
     */
    @Override
    public boolean hasDynamicLightEmission(BlockState state) {
        return true;
    }
}
