package com.cio.createinteroperable.letsdo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.HashSet;
import java.util.Set;

/**
 * Dependency-light helpers shared by the Let's Do sink/bathtub integration (the
 * mixin, the block entity, the fluid handler, the capability registration and
 * the interaction guard all use these).
 *
 * <p>The sink block class is Farm &amp; Charm's {@code SinkBlock}; Candlelight
 * and Bakery (via {@code BrickSinkBlock}) register instances of that same class
 * under their own ids, and Alpine Whispers ships a byte-for-byte copy of it as
 * {@code net.satisfy.alpinewhispers.core.block.SinkBlock} (its
 * {@code arolla_pine_sink} / {@code arolla_pine_washbasin}), extending vanilla
 * {@code Block} directly rather than Farm &amp; Charm's class. We therefore match
 * on <em>any</em> {@code net.satisfy.*.core.block.SinkBlock} in the class chain
 * that also carries the {@code filled} + {@code half} properties, and read every
 * property by name — so CIO needs no compile- or run-time dependency on any of
 * those mods.
 *
 * <p>Alpine Whispers' bathtub is a different shape again
 * ({@code net.satisfy.alpinewhispers.core.block.BathtubBlock}: {@code full} +
 * {@code part} + {@code facing}, its own block entity) and is recognised
 * separately by {@link #bathtubBlocks()}.
 */
public final class SinkStates {
    /** Fully-qualified name of the shared sink block class shipped by Farm &amp; Charm. */
    public static final String SINK_BLOCK_CLASS = "net.satisfy.farm_and_charm.core.block.SinkBlock";

    private static Set<Block> sinkBlocks;
    private static Set<Block> bathtubBlocks;

    private SinkStates() {
    }

    /** Every registered Farm &amp; Charm–style sink block (see {@link #isSinkClass}). Scanned once, then cached. */
    public static Set<Block> sinkBlocks() {
        Set<Block> cached = sinkBlocks;
        if (cached != null) {
            return cached;
        }
        Set<Block> found = new HashSet<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (isSinkClass(block)) {
                found.add(block);
            }
        }
        return sinkBlocks = Set.copyOf(found);
    }

    /** Every registered Alpine Whispers–style bathtub block (see {@link #isBathtubClass}). Scanned once, then cached. */
    public static Set<Block> bathtubBlocks() {
        Set<Block> cached = bathtubBlocks;
        if (cached != null) {
            return cached;
        }
        Set<Block> found = new HashSet<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (isBathtubClass(block)) {
                found.add(block);
            }
        }
        return bathtubBlocks = Set.copyOf(found);
    }

    public static boolean isSink(BlockState state) {
        return sinkBlocks().contains(state.getBlock());
    }

    public static boolean isBathtub(BlockState state) {
        return bathtubBlocks().contains(state.getBlock());
    }

    /**
     * True for Farm &amp; Charm's {@code SinkBlock}, anything extending it, and any
     * standalone {@code net.satisfy.*.core.block.SinkBlock} copy (Alpine Whispers)
     * that carries the {@code filled} + {@code half} properties.
     */
    public static boolean isSinkClass(Block block) {
        BlockState def = block.defaultBlockState();
        if (filled(def) == null || halfName(def) == null) {
            return false;
        }
        for (Class<?> c = block.getClass(); c != null && c != Block.class; c = c.getSuperclass()) {
            String name = c.getName();
            if (name.equals(SINK_BLOCK_CLASS)
                    || (name.startsWith("net.satisfy.") && name.endsWith(".core.block.SinkBlock"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * True for a {@code net.satisfy.*.core.block.BathtubBlock} (Alpine Whispers'
     * Arolla Pine Bathtub) carrying the {@code full} + {@code part} properties.
     */
    public static boolean isBathtubClass(Block block) {
        BlockState def = block.defaultBlockState();
        if (bool(def, "full") == null || partName(def) == null) {
            return false;
        }
        for (Class<?> c = block.getClass(); c != null && c != Block.class; c = c.getSuperclass()) {
            String name = c.getName();
            if (name.startsWith("net.satisfy.") && name.endsWith(".core.block.BathtubBlock")) {
                return true;
            }
        }
        return false;
    }

    private static Property<?> property(BlockState state, String name) {
        return state.getBlock().getStateDefinition().getProperty(name);
    }

    /** Value of an arbitrary boolean blockstate property by name, or {@code null} if absent. */
    public static Boolean bool(BlockState state, String name) {
        return property(state, name) instanceof BooleanProperty p ? state.getValue(p) : null;
    }

    /** {@code null} if the block has no {@code filled} property. */
    public static Boolean filled(BlockState state) {
        return property(state, "filled") instanceof BooleanProperty p ? state.getValue(p) : null;
    }

    public static BlockState withFilled(BlockState state, boolean value) {
        return property(state, "filled") instanceof BooleanProperty p ? state.setValue(p, value) : state;
    }

    /** "lower" / "upper", or {@code null} if the block has no {@code half} property. */
    public static String halfName(BlockState state) {
        Property<?> p = property(state, "half");
        if (p == null) {
            return null;
        }
        Comparable<?> value = state.getValue(p);
        return value instanceof StringRepresentable sr ? sr.getSerializedName() : String.valueOf(value);
    }

    /** True for the lower half — or for a sink with no {@code half} property at all. */
    public static boolean isLowerHalf(BlockState state) {
        String name = halfName(state);
        return name == null || "lower".equalsIgnoreCase(name);
    }

    public static boolean isUpperHalf(BlockState state) {
        return "upper".equalsIgnoreCase(halfName(state));
    }

    /** The sink's / bathtub's horizontal {@code facing}, or {@code null} if it has no such property. */
    public static Direction facing(BlockState state) {
        Property<?> p = property(state, "facing");
        if (p instanceof EnumProperty<?> ep && ep.getValueClass() == Direction.class) {
            return (Direction) state.getValue(ep);
        }
        return null;
    }

    // --- Alpine Whispers bathtub ---------------------------------------------

    /** Serialized value of the {@code part} property ("head" / "foot"), or {@code null} if absent. */
    public static String partName(BlockState state) {
        Property<?> p = property(state, "part");
        if (p == null) {
            return null;
        }
        Comparable<?> value = state.getValue(p);
        return value instanceof StringRepresentable sr ? sr.getSerializedName() : String.valueOf(value);
    }

    public static boolean isBathtubHead(BlockState state) {
        return "head".equalsIgnoreCase(partName(state));
    }

    /**
     * The position of the bathtub's head half (which owns the block entity),
     * given a click on either half. Mirrors Alpine Whispers' own math: the foot
     * sits one block along {@code facing.getOpposite()} from the head, so from a
     * foot click the head is {@code pos.relative(facing)}.
     */
    public static BlockPos bathtubHead(BlockState state, BlockPos clicked) {
        if (isBathtubHead(state)) {
            return clicked;
        }
        Direction f = facing(state);
        return f == null ? clicked : clicked.relative(f);
    }
}
