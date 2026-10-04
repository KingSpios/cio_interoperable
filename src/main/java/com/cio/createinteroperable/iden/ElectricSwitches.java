package com.cio.createinteroperable.iden;

import com.cio.createinteroperable.CreateInteroperable;
import com.cio.createinteroperable.mixin.BlockDescriptionIdAccessor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * CIO's "Electric" twins of Iden's Decor's buttons and switches.
 *
 * <p>Each Iden block below gets a CIO counterpart registered as
 * {@code createinteroperable:electric_<name>} (see {@code CIOBlocks}), which
 * reuses Iden's own models, drops no redstone signal, and is instead an
 * appliance-grid switch ({@link ElectricSwitchBlockEntity}). To tell the two
 * apart in game, Iden's originals are renamed "Redstone &hellip;" via
 * {@link #renameOriginals()} &mdash; purely in-game (their description id is
 * repointed at a CIO lang key); Iden's own files are untouched.</p>
 */
public final class ElectricSwitches {

    private ElectricSwitches() {
    }

    /** Iden's Decor block ids (path only) that have an Electric twin. */
    public static final List<String> IDEN_NAMES = List.of(
            "heavy_button",
            "gate_button",
            "heavy_lever",
            "emergency_lever",
            "light_switch",
            "power_switch",
            "valve_switch",
            "blast_lever",
            "core_button_control_panel",
            "core_lever_control_panel");

    /**
     * Point each Iden original's description id (block and its BlockItem, which
     * defers to the block) at {@code block.createinteroperable.redstone_<name>}.
     * A description id, not a lang-file override of Iden's key, because the
     * order two mods' lang files merge in isn't something CIO controls.
     */
    public static void renameOriginals() {
        for (String name : IDEN_NAMES) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("iden_decor", name);
            if (!BuiltInRegistries.BLOCK.containsKey(id)) {
                CreateInteroperable.LOGGER.warn("Iden's Decor block {} not found; not renaming it", id);
                continue;
            }
            Block block = BuiltInRegistries.BLOCK.get(id);
            ((BlockDescriptionIdAccessor) block).cio$setDescriptionId(
                    "block." + CreateInteroperable.ID + ".redstone_" + name);
        }
    }
}
