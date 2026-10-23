package com.cio.createinteroperable.mixin.pnw;

import com.cio.createinteroperable.compat.PnwCeePantographTypes;
import com.george_vi.electroenergetics.content.railway_electrification.pantograph.IPantographBlock;
import com.george_vi.electroenergetics.content.railway_electrification.pantograph.PantographType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Makes PnW's pantograph a CEE pantograph as far as CEE's train logic goes:
 * when a train assembles, CEE records every {@link IPantographBlock} on it and
 * its {@code CatenaryModule} then collects current (and the electric fuel
 * bonus) through it from any CEE contact line. CEE reads the block's
 * {@code FACING} via the vanilla horizontal-facing property, which PnW's block
 * also uses.
 */
@Mixin(targets = "de.mrjulsen.paw.block.PantographBlock", remap = false)
abstract class PnwPantographBlockMixin implements IPantographBlock {
    @Override
    public PantographType getPantographType(BlockState state) {
        return PnwCeePantographTypes.PNW_PANTOGRAPH.get();
    }
}
