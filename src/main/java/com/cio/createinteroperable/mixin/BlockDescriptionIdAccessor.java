package com.cio.createinteroperable.mixin;

import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets CIO repoint a foreign block's (lazily cached) description id &mdash;
 * used to rename Iden's Decor's redstone buttons/switches "Redstone &hellip;"
 * in game (see {@code com.cio.createinteroperable.iden.ElectricSwitches}).
 */
@Mixin(Block.class)
public interface BlockDescriptionIdAccessor {

    @Accessor("descriptionId")
    void cio$setDescriptionId(String descriptionId);
}
