package com.cio.createinteroperable.letsdo;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.HashSet;
import java.util.Set;

/**
 * Dependency-light helpers for the Let's Do lamp integration: which foreign
 * blocks are "lamps" and which boolean property carries their on/off state.
 *
 * <p>Matched by fully-qualified class name so CIO needs no compile- or run-time
 * dependency on Let's Do Furniture or Candlelight:</p>
 * <ul>
 *   <li>{@code com.berksire.furniture.core.block.LampBlock} / {@code LampWallBlock}
 *       &mdash; property {@code lit}</li>
 *   <li>{@code com.berksire.furniture.core.block.StreetLanternBlock} /
 *       {@code StreetLanternWallBlock} &mdash; property {@code lit}, no manual toggle</li>
 *   <li>{@code net.satisfy.candlelight.core.block.LampBlock} &mdash; property
 *       {@code luminance}</li>
 *   <li>{@code com.github.minecraftschurlimods.bibliocraft.content.fancylight.FancyLampBlock}
 *       &mdash; property {@code lit}, no manual toggle (redstone-only, standing/hanging/wall
 *       orientation only &mdash; does not stack)</li>
 * </ul>
 */
public final class LetsDoLampStates {

    private static final Set<String> LAMP_CLASSES = Set.of(
            "com.berksire.furniture.core.block.LampBlock",
            "com.berksire.furniture.core.block.LampWallBlock",
            "com.berksire.furniture.core.block.StreetLanternBlock",
            "com.berksire.furniture.core.block.StreetLanternWallBlock",
            "net.satisfy.candlelight.core.block.LampBlock",
            // Alpine Whispers fairy lights: no 'lit'/'luminance' blockstate, so
            // isLit()/withLit() return null/unchanged and the CIO node drives
            // NeoForge getLightEmission() instead (see AlpineFairyLightsBlockMixin).
            "net.satisfy.alpinewhispers.core.block.FairyLightsBlock",
            // Another Furniture lamp head (its LampConnectorBlock pole segments
            // are deliberately excluded — they carry no light and no node; see
            // AnotherFurnitureLampBlockMixin). Property is 'lit', defaults true.
            "com.starfish_studios.another_furniture.block.LampBlock",
            // Bibliocraft Fancy Lamp: no manual toggle, already redstone-gated
            // (see BibliocraftLampBlockMixin, which suppresses that once a node
            // is present). Property is 'lit'.
            "com.github.minecraftschurlimods.bibliocraft.content.fancylight.FancyLampBlock");

    /** Blocks that carry a manual light toggle we redirect into the switch (see LetsDoLampToggleMixin). */
    private static final Set<String> TOGGLEABLE_CLASSES = Set.of(
            "com.berksire.furniture.core.block.LampBlock",
            "com.berksire.furniture.core.block.LampWallBlock",
            "net.satisfy.candlelight.core.block.LampBlock");

    /**
     * Blocks whose {@code type} blockstate property means <em>stacking position</em>
     * ({@code none}/{@code top}/{@code middle}/{@code bottom}) rather than something
     * else entirely &mdash; only these should have {@link #isHeadSegment} consult that
     * property. Bibliocraft's Fancy Lamp, for instance, also happens to declare a property
     * literally named {@code type} (orientation: standing/hanging/wall) that means something
     * unrelated to stacking; blindly checking any {@code type} property's value against
     * {@code none}/{@code top} would misread it and wrongly conclude every state is a
     * non-head pole segment, silently skipping node creation everywhere.
     */
    private static final Set<String> STACKING_CLASSES = Set.of(
            "com.berksire.furniture.core.block.LampBlock",
            "com.berksire.furniture.core.block.LampWallBlock",
            "com.berksire.furniture.core.block.StreetLanternBlock",
            "com.berksire.furniture.core.block.StreetLanternWallBlock");

    /** Candidate on/off property names, in priority order. */
    private static final String[] LIT_PROPERTIES = { "lit", "luminance" };

    private static Set<Block> lampBlocks;
    private static Set<Block> noLitLampBlocks;

    private LetsDoLampStates() {
    }

    /** Every registered Let's Do lamp/lantern block (see {@link #isLampClass}). Scanned once, then cached. */
    public static Set<Block> lampBlocks() {
        Set<Block> cached = lampBlocks;
        if (cached != null) {
            return cached;
        }
        Set<Block> found = new HashSet<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (isLampClass(block)) {
                found.add(block);
            }
        }
        return lampBlocks = Set.copyOf(found);
    }

    /**
     * The subset of {@link #lampBlocks()} whose blockstate carries <b>no</b>
     * {@code lit} / {@code luminance} property (currently just Alpine Whispers'
     * fairy lights). Their on/off is driven purely through the block entity, so
     * {@code LampEmissiveMixin} needs a cheap {@code contains} check to know
     * which blocks to dim.
     */
    public static Set<Block> noLitLampBlocks() {
        Set<Block> cached = noLitLampBlocks;
        if (cached != null) {
            return cached;
        }
        Set<Block> found = new HashSet<>();
        for (Block block : lampBlocks()) {
            if (litProperty(block.defaultBlockState()) == null) {
                found.add(block);
            }
        }
        return noLitLampBlocks = Set.copyOf(found);
    }

    public static boolean isLampClass(Block block) {
        for (Class<?> c = block.getClass(); c != null && c != Block.class; c = c.getSuperclass()) {
            if (LAMP_CLASSES.contains(c.getName())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isToggleable(Block block) {
        for (Class<?> c = block.getClass(); c != null && c != Block.class; c = c.getSuperclass()) {
            if (TOGGLEABLE_CLASSES.contains(c.getName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isStackingClass(Block block) {
        for (Class<?> c = block.getClass(); c != null && c != Block.class; c = c.getSuperclass()) {
            if (STACKING_CLASSES.contains(c.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if this state is the "head" of a lamp &mdash; the segment that actually
     * emits light and should carry the electricity node. Any lamp that isn't a
     * {@link #STACKING_CLASSES known stacking class} is always a head. A stacking
     * lamp/lantern post is a head only at {@code type = none} or {@code top}
     * (pole segments are {@code middle} / {@code bottom}).
     */
    public static boolean isHeadSegment(BlockState state) {
        Block block = state.getBlock();
        if (!isStackingClass(block)) {
            return true;
        }
        Property<?> type = block.getStateDefinition().getProperty("type");
        if (type == null) {
            return true;
        }
        Comparable<?> value = state.getValue(type);
        String name = value instanceof net.minecraft.util.StringRepresentable sr
                ? sr.getSerializedName() : String.valueOf(value);
        return "none".equalsIgnoreCase(name) || "top".equalsIgnoreCase(name);
    }

    /** The block's own on/off boolean property ({@code lit} or {@code luminance}), or {@code null}. */
    private static BooleanProperty litProperty(BlockState state) {
        for (String name : LIT_PROPERTIES) {
            Property<?> p = state.getBlock().getStateDefinition().getProperty(name);
            if (p instanceof BooleanProperty bp) {
                return bp;
            }
        }
        return null;
    }

    public static Boolean isLit(BlockState state) {
        BooleanProperty p = litProperty(state);
        return p == null ? null : state.getValue(p);
    }

    /** Returns {@code state} with its on/off property set, or {@code state} unchanged if it has none. */
    public static BlockState withLit(BlockState state, boolean value) {
        BooleanProperty p = litProperty(state);
        return p == null ? state : state.setValue(p, value);
    }
}
