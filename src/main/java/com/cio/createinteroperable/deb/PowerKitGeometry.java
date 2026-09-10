package com.cio.createinteroperable.deb;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Protocol-neutral outline shapes and slider-slot rotation math shared by both
 * the Power Grid-rooted Power Kit blocks ({@link DebRectifierBlock} and its
 * tier subclasses) and the Electro Energetics-rooted twins
 * ({@link CeeDebRectifierBlock} and its tier subclasses).
 *
 * <p>These values used to live as {@code static} members on the PG-rooted
 * classes themselves, with the CEE-rooted classes reading them directly
 * (e.g. {@code DebRectifierBlock.SHAPE}). That is a real classloading hazard:
 * touching a static field or method on a class forces the JVM to resolve that
 * class's entire superclass chain, and every PG-rooted Power Kit block
 * ultimately extends PG's {@code DirectionalElectricBlock}. On a CEE-only
 * install (Power Grid absent) that resolution throws
 * {@code NoClassDefFoundError} the moment a CEE Power Kit block is
 * constructed during registration &mdash; confirmed by a real crash log,
 * not a hypothetical. This class holds no PG import at all, so both sides can
 * share one copy of the geometry with neither depending on the other.</p>
 */
final class PowerKitGeometry {
    private PowerKitGeometry() {}

    /** NORTH-authored outline for the tier-2 model, from {@code deb_rectifier_tier_2.json}'s own elements. */
    static final VoxelShape TIER2_SHAPE = Shapes.or(
            Block.box(2, 2, 0, 14, 14, 1),     // back_plate
            Block.box(2, 2, 1, 14, 14, 4),     // body_box
            Block.box(3, 14, 1, 13, 15, 3),    // top_rail
            Block.box(3, 4, 4, 7, 7, 4.5),     // viewer_120
            Block.box(9, 4, 4, 13, 7, 4.5),    // viewer_12
            Block.box(6, 8, 4, 10, 11, 4.5),   // viewer_lever (usage)
            Block.box(6, 1, 0, 10, 2, 1),      // 120 V intake nub strip
            Block.box(4, 15, 1, 12, 16, 2));   // Power Feed nub strip (all four top nubs)

    /** NORTH-authored outline for the tier-1 model, from {@code deb_rectifier_tier_1.json}'s own elements. */
    static final VoxelShape TIER1_SHAPE = Shapes.or(
            Block.box(4, 1, 0, 12, 14, 1),         // back plate
            Block.box(5, 1, 1, 11, 13, 4),         // body
            Block.box(6.55, 5.5, 2.3, 9.55, 8.5, 4.3),  // usage_gauge
            Block.box(7.8, 6.8, 3.5, 8.3, 8.5, 4.5),     // clock_pin + pointer
            Block.box(6, 0, 2, 10, 1, 3),          // 120 V intake nub strip
            Block.box(6, 13, 2, 10, 14, 3));       // 12 V Power Feed nub strip

    /** NORTH-authored outline for the tier-3 model, from {@code deb_rectifier_tier_3.json}'s own elements. */
    static final VoxelShape TIER3_SHAPE = Shapes.or(
            Block.box(2, 2, 0, 14, 14, 1),         // back_plate
            Block.box(2, 2, 1, 14, 15, 4),         // body_box (near slab)
            Block.box(2, 2, 4, 14, 15, 7),         // body_box (far slab)
            Block.box(3, 15, 1, 13, 16, 3),        // top_rail
            Block.box(3, 15, 4, 13, 16, 6),        // top_rail_forward
            Block.box(1, 10, 2, 2, 14, 6),         // side_plate_up
            Block.box(1, 3, 2, 2, 7, 6),           // side_plate_down
            Block.box(3, 3.1, 6.7, 13, 14.1, 7.5), // five viewer plates
            Block.box(6, 1, 2, 10, 2, 3));         // intake nub strip

    /** NORTH-authored outline for the tier-4 model, from {@code deb_rectifier_tier_4.json}'s own elements. */
    static final VoxelShape TIER4_SHAPE = Shapes.or(
            Block.box(0, 1, 0, 16, 16, 1),        // back_plate
            Block.box(0, 1, 1, 16, 16, 5),        // body_box
            Block.box(0, 1, 5, 16, 16, 6),        // front_plate
            Block.box(0, 15, 0, 16, 16, 6),       // top_plate (clamped down 1 px)
            Block.box(3, 4.1, 6, 13, 15.1, 6.5),  // five viewer plates
            Block.box(5, 0, 2, 11, 1, 4));        // intake nub strip

    /**
     * NORTH-authored outline for {@code redstone_switch.json}'s own elements.
     * The moving contact's full vertical travel (y 6&ndash;10) is covered so it
     * stays clickable in either the OFF (up) or ON (dropped) position.
     */
    static final VoxelShape REDSTONE_SWITCH_SHAPE = Shapes.or(
            Block.box(2, 2, 0, 14, 14, 1),      // back_plate
            Block.box(2, 2, 1, 14, 14, 4),      // body_box
            Block.box(3, 14, 1, 13, 15, 3),     // top_rail
            Block.box(0, 8, 0, 16, 9, 1.1),     // back band
            Block.box(6, 10, 4, 10, 13, 4.5),   // viewer_usageW (top / wattage)
            Block.box(6, 8, 4, 10, 11, 4.5),    // viewer_lever (contact housing)
            Block.box(6, 3, 4, 10, 6, 4.5),     // viewer_voltage (bottom)
            Block.box(6, 6, 2.5, 7, 10, 4.5),   // rail_left
            Block.box(9, 6, 2.5, 10, 10, 4.5),  // rail_right
            Block.box(7, 6, 3.5, 9, 10, 4.5),   // moving contact travel zone
            Block.box(6, 1, 0, 10, 2, 1),       // bottom (input) nub strip
            Block.box(6, 15, 1, 10, 16, 2));    // top (output) nub strip

    /**
     * Y-rotation (deg) the blockstate JSON applies for this facing; 0 for the
     * vertical facings. {@code BlockStateProperties.FACING} is literally the
     * same property object PG's {@code DirectionalElectricBlock.FACING} and
     * CEE's {@code CeeDebRectifierBlock.FACING} both use, so this reads either
     * side's blockstate identically.
     */
    static int angleFor(BlockState state) {
        if (!state.hasProperty(BlockStateProperties.FACING)) {
            return 0;
        }
        return switch (state.getValue(BlockStateProperties.FACING)) {
            case SOUTH -> 180;
            case EAST -> 90;
            case WEST -> 270;
            default -> 0; // NORTH, UP, DOWN
        };
    }

    /** Rotate {@code v} about the block centre (0.5, y, 0.5) by {@code deg} around Y. */
    static Vec3 rotateY(Vec3 v, int deg) {
        if (deg == 0) {
            return v;
        }
        double rad = Math.toRadians(deg);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double x = v.x - 0.5;
        double z = v.z - 0.5;
        return new Vec3(0.5 + x * cos - z * sin, v.y, 0.5 + x * sin + z * cos);
    }
}
