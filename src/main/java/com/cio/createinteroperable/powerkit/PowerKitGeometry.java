package com.cio.createinteroperable.powerkit;

import com.cio.createinteroperable.CIOValueBox;
import com.cio.createinteroperable.powerkit.cee.CeeDebRectifierBlock;
import com.cio.createinteroperable.powerkit.cpg.DebRectifierBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
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
public final class PowerKitGeometry {
    private PowerKitGeometry() {}

    /** NORTH-authored outline for the tier-2 model, from {@code deb_rectifier_tier_2.json}'s own elements. */
    public static final VoxelShape TIER2_SHAPE = PowerKitSignalNub.withNub(Shapes.or(
            Block.box(2, 2, 0, 14, 14, 1),     // back_plate
            Block.box(2, 2, 1, 14, 14, 4),     // body_box
            Block.box(3, 14, 1, 13, 15, 3),    // top_rail
            Block.box(3, 4, 4, 7, 7, 4.5),     // viewer_120
            Block.box(9, 4, 4, 13, 7, 4.5),    // viewer_12
            Block.box(6, 8, 4, 10, 11, 4.5),   // viewer_lever (usage)
            Block.box(1.5, 9, 1, 2, 13, 4),    // control_plate
            Block.box(1, 10, 2, 2, 12, 3),     // plate_switch, full on/off travel (see PowerKitSwitch)
            Block.box(6, 1, 0, 10, 2, 1),      // 120 V intake nub strip
            Block.box(4, 15, 1, 12, 16, 2)),   // Power Feed nub strip (all four top nubs)
            PowerKitSignalNub.TIER2_BOX);

    /** NORTH-authored outline for the tier-1 model, from {@code deb_rectifier_tier_1.json}'s own elements. */
    public static final VoxelShape TIER1_SHAPE = Shapes.or(
            Block.box(4, 1, 0, 12, 14, 1),         // back plate
            Block.box(5, 1, 1, 11, 13, 4),         // body
            Block.box(6.55, 5.5, 2.3, 9.55, 8.5, 4.3),  // usage_gauge (dial)
            Block.box(6.45, 5.4, 3.6, 9.65, 8.6, 4.6),  // usage_gauge (glass lid; holds clock_pin + pointer)
            Block.box(7, 2, 3.5, 9, 4, 4.5),       // flip_switch_collar
            Block.box(7.5, 2.04, 4, 8.5, 4.11, 5.66), // flip_switch_lever, both poses (see PowerKitSwitch)
            Block.box(6, 0, 2, 10, 1, 3),          // 120 V intake nub strip
            Block.box(6, 13, 2, 10, 14, 3));       // 12 V Power Feed nub strip

    /** NORTH-authored outline for the tier-3 model, from {@code deb_rectifier_tier_3.json}'s own elements. */
    public static final VoxelShape TIER3_SHAPE = PowerKitSignalNub.withNub(Shapes.or(
            Block.box(2, 2, 0, 14, 14, 1),         // back_plate
            Block.box(2, 2, 1, 14, 15, 4),         // body_box (near slab)
            Block.box(2, 2, 4, 14, 15, 7),         // body_box (far slab)
            Block.box(3, 15, 1, 13, 16, 3),        // top_rail
            Block.box(3, 15, 4, 13, 16, 6),        // top_rail_forward
            Block.box(1, 10, 2, 2, 14, 6),         // side_plate_up
            Block.box(1, 3, 2, 2, 7, 6),           // side_plate_down
            Block.box(3, 3.1, 6.7, 13, 14.1, 7.5), // five viewer plates
            Block.box(13.875, 10, 2.125, 14.375, 14, 6.125), // e_stop_plate
            Block.box(14, 11, 3.125, 14.875, 13, 5.125),     // e_stop_button, released pose (see PowerKitSwitch)
            Block.box(6, 1, 2, 10, 2, 3)),         // intake nub strip
            PowerKitSignalNub.TIER3_BOX);

    /** NORTH-authored outline for the tier-4 model, from {@code deb_rectifier_tier_4.json}'s own elements. */
    public static final VoxelShape TIER4_SHAPE = PowerKitSignalNub.withNub(Shapes.or(
            Block.box(0, 1, 0, 16, 16, 1),        // back_plate
            Block.box(0, 1, 1, 16, 16, 5),        // body_box
            Block.box(0, 1, 5, 16, 16, 6),        // front_plate
            Block.box(0, 15, 0, 16, 16, 6),       // top_plate (clamped down 1 px)
            Block.box(3, 4.1, 6, 13, 15.1, 6.5),  // five viewer plates
            Block.box(5, 0, 2, 11, 1, 4)),        // intake nub strip
            PowerKitSignalNub.TIER4_BOX);

    /**
     * NORTH-authored outline for {@code redstone_switch.json}'s own elements.
     * The moving contact's full vertical travel (y 6&ndash;10) is covered so it
     * stays clickable in either the OFF (up) or ON (dropped) position.
     */
    public static final VoxelShape REDSTONE_SWITCH_SHAPE = Shapes.or(
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
     * Floor/ceiling mounts only: the way the board's top edge points, taken from
     * the placing player's look so the faceplate reads upright from where they
     * stood. Wall mounts ignore it (their top is always up) and keep the default.
     */
    public static final DirectionProperty TOP = DirectionProperty.create("top", Direction.Plane.HORIZONTAL);

    /**
     * Back plate against the clicked surface; on a floor or ceiling the top edge
     * points so the board reads upright to the placer (away from them on a
     * floor, toward them on a ceiling, where looking up turns the view over).
     */
    public static BlockState placed(BlockState state, BlockPlaceContext context) {
        Direction facing = context.getClickedFace().getOpposite();
        state = state.setValue(BlockStateProperties.FACING, facing);
        if (facing.getAxis().isVertical() && state.hasProperty(TOP)) {
            Direction look = context.getHorizontalDirection();
            state = state.setValue(TOP, facing == Direction.DOWN ? look : look.getOpposite());
        }
        return state;
    }

    /** Turn a placed kit with a structure rotation, its floor/ceiling top edge included. */
    public static BlockState rotate(BlockState state, Rotation rotation) {
        state = state.setValue(BlockStateProperties.FACING, rotation.rotate(state.getValue(BlockStateProperties.FACING)));
        return state.hasProperty(TOP) ? state.setValue(TOP, rotation.rotate(state.getValue(TOP))) : state;
    }

    /**
     * X-rotation (deg) the blockstate JSON applies: 90 on a floor (back plate
     * down), 270 (= -90) on a ceiling, 0 on a wall. Applied before {@link #modelY}.
     */
    public static int modelX(Direction facing) {
        return switch (facing) {
            case DOWN -> 90;
            case UP -> 270;
            default -> 0;
        };
    }

    /**
     * Y-rotation (deg) the blockstate JSON applies after {@link #modelX}: the wall
     * angle, or on a floor/ceiling the turn that carries the model's top edge
     * from where the X-rotation leaves it (north on a floor, south on a ceiling)
     * to {@link #TOP}.
     */
    public static int modelY(Direction facing, Direction top) {
        if (facing.getAxis().isHorizontal()) {
            return clockwiseFromNorth(facing);
        }
        return Math.floorMod(clockwiseFromNorth(top) - clockwiseFromNorth(restingTop(facing)), 360);
    }

    public static int modelX(BlockState state) {
        return modelX(facing(state));
    }

    public static int modelY(BlockState state) {
        Direction facing = facing(state);
        return modelY(facing, top(state, facing));
    }

    /** Where {@link #modelX} alone leaves the model's top edge: north on a floor, south on a ceiling. */
    private static Direction restingTop(Direction facing) {
        return facing == Direction.UP ? Direction.SOUTH : Direction.NORTH;
    }

    public static Direction facing(BlockState state) {
        return state.hasProperty(BlockStateProperties.FACING) ? state.getValue(BlockStateProperties.FACING) : Direction.NORTH;
    }

    /** {@link #TOP}, or the untouched x-only turn for blocks without it (Mini Grid Coupler, CEE Redstone Switch). */
    private static Direction top(BlockState state, Direction facing) {
        return state.hasProperty(TOP) ? state.getValue(TOP) : restingTop(facing);
    }

    /** N=0, E=90, S=180, W=270: the blockstate JSON's own y convention. */
    private static int clockwiseFromNorth(Direction d) {
        return Math.floorMod((int) d.toYRot() + 180, 360);
    }

    /**
     * Y-rotation (deg) the blockstate JSON applies for this facing; 0 for the
     * vertical facings. {@code BlockStateProperties.FACING} is literally the
     * same property object PG's {@code DirectionalElectricBlock.FACING} and
     * CEE's {@code CeeDebRectifierBlock.FACING} both use, so this reads either
     * side's blockstate identically.
     */
    public static int angleFor(BlockState state) {
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

    /**
     * Carry a block-space point from the model's authored (FACING=NORTH) pose to the state's
     * actual mount &mdash; exactly the blockstate JSON's rotation ({@link #modelX}, then
     * {@link #modelY}), which {@code DebRectifierRenderer.applyFacing} also applies. Every
     * piece of a kit (outline, PG terminals, CEE nodes, switch, slider, Crayfish node box)
     * goes through here, so none can drift from the model.
     */
    public static Vec3 orient(BlockState state, Vec3 v) {
        Direction f = facing(state);
        return orient(f, top(state, f), v);
    }

    public static Vec3 orient(Direction facing, Direction top, Vec3 v) {
        double dy = v.y - 0.5;
        double dz = v.z - 0.5;
        // x=90 (floor): (dy,dz) -> (dz,-dy).  x=270 (ceiling): (dy,dz) -> (-dz,dy).
        Vec3 r = switch (modelX(facing)) {
            case 90 -> new Vec3(v.x, 0.5 + dz, 0.5 - dy);
            case 270 -> new Vec3(v.x, 0.5 - dz, 0.5 + dy);
            default -> v;
        };
        return rotateY(r, modelY(facing, top));
    }

    /**
     * A NORTH-authored box size (block units) turned with the mount, as {@link #orient} turns
     * positions: floor/ceiling swap y and z, then a quarter turn swaps x and z.
     */
    public static Vec3 orientSize(BlockState state, Vec3 size) {
        Vec3 s = modelX(state) != 0 ? new Vec3(size.x, size.z, size.y) : size;
        return modelY(state) % 180 != 0 ? new Vec3(s.z, s.y, s.x) : s;
    }

    /**
     * A NORTH-authored shape turned for every mount by {@link #orient}, built once.
     * Replaces Create's {@code VoxelShaper.forDirectional}, whose UP/DOWN shapes put
     * the back plate on the opposite side from the model (and mirror it).
     */
    public static final class OrientedShape {
        private final VoxelShape[] shapes = new VoxelShape[24];

        public OrientedShape(VoxelShape north) {
            for (Direction facing : Direction.values()) {
                for (Direction top : Direction.Plane.HORIZONTAL) {
                    shapes[index(facing, top)] = turn(north, facing, top);
                }
            }
        }

        public VoxelShape get(BlockState state) {
            Direction facing = facing(state);
            return shapes[index(facing, top(state, facing))];
        }

        private static int index(Direction facing, Direction top) {
            return facing.ordinal() * 4 + top.get2DDataValue();
        }

        private static VoxelShape turn(VoxelShape north, Direction facing, Direction top) {
            VoxelShape[] out = {Shapes.empty()};
            north.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
                Vec3 a = orient(facing, top, new Vec3(x1, y1, z1));
                Vec3 b = orient(facing, top, new Vec3(x2, y2, z2));
                out[0] = Shapes.or(out[0], Shapes.box(
                        Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z),
                        Math.max(a.x, b.x), Math.max(a.y, b.y), Math.max(a.z, b.z)));
            });
            return out[0].optimize();
        }
    }

    /** The tier-3/4-style intake slider: authored on the front (+Z) face at the default facing. */
    public static com.cio.createinteroperable.CIOValueBox intakeSlot(net.minecraft.world.phys.Vec3 base) {
        return new com.cio.createinteroperable.CIOValueBox(base, net.minecraft.core.Direction.SOUTH,
                5 / 16f, PowerKitGeometry::orient);
    }

    /** Rotate {@code v} about the block centre (0.5, y, 0.5) by {@code deg} around Y. */
    public static Vec3 rotateY(Vec3 v, int deg) {
        // Quarter turns exact, so turned shapes land on whole pixels.
        switch (Math.floorMod(deg, 360)) {
            case 0: return v;
            case 90: return new Vec3(1 - v.z, v.y, v.x);
            case 180: return new Vec3(1 - v.x, v.y, 1 - v.z);
            case 270: return new Vec3(v.z, v.y, 1 - v.x);
            default: break;
        }
        double rad = Math.toRadians(deg);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double x = v.x - 0.5;
        double z = v.z - 0.5;
        return new Vec3(0.5 + x * cos - z * sin, v.y, 0.5 + x * sin + z * cos);
    }
}
