package com.cio.createinteroperable.deb;

import com.mrcrayfish.furniture.refurbished.blockentity.IHomeControlDevice;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;

import java.util.HashMap;
import java.util.Map;

/**
 * The rated electrical load of every Crayfish Refurbished Furniture appliance,
 * for the DEB.
 *
 * <p>Keyed by {@link net.minecraft.world.level.block.entity.BlockEntityType}
 * <em>registry id</em> (e.g. {@code refurbished_furniture:microwave}) rather
 * than the type object itself — Crayfish's {@code ModBlockEntities} fields are
 * typed as {@code com.mrcrayfish.framework.api.registry.RegistryEntry}, which
 * lives in MrCrayfish's separate Framework library and isn't on this addon's
 * classpath. Every cosmetic variant of a family (16 lamp colours, 20 ceiling
 * fans, light/dark pairs) shares one BE type, so this is ~13 rows, not ~78.</p>
 *
 * <p>Wattages are anchored to Power Grid's own light bulbs (from the 0.6.0.1
 * jar): the standard {@code LIGHT_BULB} is 30&nbsp;W&nbsp;@&nbsp;120&nbsp;V,
 * the {@code LV_LIGHT_BULB} is 3&nbsp;W&nbsp;@&nbsp;12&nbsp;V. A Crayfish room
 * light is one LV bulb; everything else scales from there by real-world
 * appliance ratios.</p>
 *
 * <p>The DEB sums the rated watts of every <em>linked</em> appliance on a rail,
 * but an appliance with a manual power switch (fridge, stove, microwave,
 * recycle bin — everything implementing Crayfish's {@link IHomeControlDevice})
 * only counts while it is switched <em>on</em> (see {@link #isDrawingPower}).
 * Everything without a switch draws its rated load whenever it is linked. The
 * on/off test reads the appliance's own manual switch, never its
 * {@code isNodePowered()} state — the latter is what the DEB itself drives, so
 * reading it back would close a feedback loop.</p>
 */
public final class ApplianceLoads {
    private ApplianceLoads() {}

    private static final String CRAYFISH = "refurbished_furniture";

    /** Rail assignment plus rated power draw (W at the pool's nominal voltage). */
    public record Spec(Pool pool, double watts) {}

    private static final Map<ResourceLocation, Spec> SPECS = new HashMap<>();

    static {
        // --- 12 V rail: lighting + low-power electronics ---
        put("ceiling_light", Pool.LV, 3);     // one BE type behind ceiling light + all 16 lamp colours (1 CPG LV bulb)
        put("television",    Pool.LV, 8);
        put("computer",      Pool.LV, 15);
        put("doorbell",      Pool.LV, 2);
        put("lightswitch",   Pool.LV, 0.5);   // a relay, effectively free

        // --- 120 V rail: motors + heating ---
        put("ceiling_fan",   Pool.MV, 40);
        put("range_hood",    Pool.MV, 75);
        put("workbench",     Pool.MV, 100);
        put("freezer",       Pool.MV, 150);
        put("recycle_bin",   Pool.MV, 400);
        put("toaster",       Pool.MV, 900);
        put("microwave",     Pool.MV, 1000);
        put("stove",         Pool.MV, 1500);
        // electricity_generator is deliberately absent — its recipe is
        // data-pack'd out and generation is 100% CPG and CEE via the DEB.

        // --- other mods (soft; a no-op unless installed) ---
        // Let's Do Furniture gramophone — a powered speaker. Made a Crayfish
        // consumer by com.cio.createinteroperable.mixin.letsdo.GramophoneBlockEntityMixin
        // (implements MeteredAppliance), so it is billed only while a disc plays.
        put("furniture", "gramophone", Pool.LV, 10);
        // Let's Do Furniture + Candlelight lamps / street lanterns + Alpine
        // Whispers fairy lights — one CIO BE type
        // (com.cio.createinteroperable.letsdo.LetsDoLampBlockEntity) rides on
        // every one; 3 W each, mirroring a Crayfish lamp, billed only while lit.
        put("createinteroperable", "letsdo_lamp", Pool.LV, 3);
        // Let's Do Beachparty — radio (a powered speaker, billed only while a
        // track plays via MeteredAppliance) and mini-fridge (a compressor: draws
        // its rated load whenever linked, and won't ferment while unpowered).
        // Made Crayfish consumers by com.cio.createinteroperable.mixin.letsdo
        // Radio*/MiniFridge* mixins.
        put("beachparty", "radio", Pool.LV, 10);
        put("beachparty", "mini_fridge", Pool.MV, 85);
        // Bibliocraft Fancy Crafter — an auto-crafter whose own ticker already
        // refuses to run without a vanilla POWERED blockstate; CIO drives that
        // from the grid instead of redstone (see FancyCrafterNodeMixin). One BE
        // type behind every wood variant. Billed only while it has a recipe
        // staged (MeteredAppliance), like a real workbench (100 W, 120 V).
        put("bibliocraft", "fancy_crafter", Pool.MV, 100);
        // Create Train Navigator Advanced Display — a lit information panel. One
        // BE type behind every display shape and any board size; drawn like a
        // television (8 W, 12 V) whenever a block of it is wired. A freestanding
        // display renders its texts only while powered (see
        // com.cio.createinteroperable.mixin.crn.CrnDisplayNodeMixin); contraption
        // displays are left alone.
        put("createrailwaysnavigator", "advanced_display_block_entity", Pool.LV, 8);

        // WaterFrames displays — STOP-GAP integration, only meaningful when the
        // third-party `waterframes_refurbished_compat` jar is installed. That jar
        // makes WaterFrames' DisplayTile a Crayfish IElectricityNode (so it can
        // be wrench-linked to the DEB) but ships no load figures, so the board
        // never recognised or energised it. These rows are exactly that missing
        // table: rail + rated watts, keyed by WaterFrames' five BE type ids
        // (each display block registers its own type). Billed whenever linked
        // (a display has no "off" switch and isn't a MeteredAppliance from a
        // foreign jar). Gated on the compat jar so a future first-party
        // WaterFrames integration can't double-count; harmless dead rows
        // otherwise since nothing else makes those BEs nodes. Wattages are
        // alpha guesses anchored to the Crayfish television (8 W, 12 V).
        if (ModList.get() != null && ModList.get().isLoaded("waterframes_refurbished_compat")) {
            put("waterframes", "frame",     Pool.LV, 6);
            put("waterframes", "tv",        Pool.LV, 8);
            put("waterframes", "tv_box",    Pool.LV, 8);
            put("waterframes", "projector", Pool.LV, 10);
            put("waterframes", "big_tv",    Pool.LV, 12);
        }
    }

    private static void put(String beTypePath, Pool pool, double watts) {
        put(CRAYFISH, beTypePath, pool, watts);
    }

    private static void put(String namespace, String beTypePath, Pool pool, double watts) {
        SPECS.put(ResourceLocation.fromNamespaceAndPath(namespace, beTypePath), new Spec(pool, watts));
    }

    /** @return the appliance's rail + rated watts, or {@code null} if this BE is not a DEB-known appliance. */
    public static Spec lookup(BlockEntity be) {
        if (be == null) {
            return null;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
        return id == null ? null : SPECS.get(id);
    }

    /**
     * Whether a linked appliance is actually drawing its rated load right now.
     *
     * <p>A {@link MeteredAppliance} reports its own live state (e.g. the
     * gramophone bills only while a disc plays). Otherwise: appliances with a
     * manual power switch — everything implementing Crayfish's
     * {@link IHomeControlDevice} (fridge, stove, microwave, recycle bin) — only
     * draw when switched on; everything else draws whenever it is linked. Keyed
     * on the manual switch ({@code isDeviceEnabled()}), never on
     * {@code isNodePowered()}: the DEB drives the latter, so reading it back
     * here would close a feedback loop.</p>
     */
    public static boolean isDrawingPower(BlockEntity be) {
        if (be instanceof MeteredAppliance metered) {
            return metered.cio$isConsuming();
        }
        return !(be instanceof IHomeControlDevice device) || device.isDeviceEnabled();
    }
}
