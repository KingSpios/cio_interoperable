package com.cio.createinteroperable;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * Builds real, model-derived VoxelShapes for a horizontally-rotatable block,
 * instead of the {@code Shapes.block()} full-cube placeholder several blocks
 * in this project were still shipping (Brass Heater, Steam Outlet, both Multi
 * Radiator valve ends). A full-cube shape on a model that doesn't actually
 * fill the cube is a real, previously-hit bug: Minecraft culls a neighboring
 * block's touching face whenever THIS block's declared shape is a full cube,
 * regardless of what the model actually draws there (see the real multiblock
 * work in cio-context / InteroperableSmallBlock's own history for the first
 * time this exact bug showed up) — so an oversized shape isn't just "wrong
 * collision," it visibly breaks adjacent blocks' rendering too.
 * <p>
 * Boxes are authored once, in 1/16-block voxel coordinates (0-16), against
 * the model's own default orientation (whatever the blockstate JSON's "y": 0
 * variant is — by convention in this project, FACING=NORTH or AXIS=Z), then
 * rotated to match the other 3 horizontal orientations the SAME way the
 * blockstate JSON itself rotates the model (y: 90/180/270 for east/south/west).
 * Shapes are precomputed once per block class (static fields) and looked up
 * by state, never rebuilt per query.
 */
final class ShapeRotation {
    private ShapeRotation() {
    }

    /** A single cuboid in 1/16-block voxel coordinates, matching Blockbench's own "from"/"to" units. */
    record Box(double x1, double y1, double z1, double x2, double y2, double z2) {
        /** Rotates this box around the block's vertical (Y) axis by a multiple of 90°, matching blockstate "y" rotation. */
        Box rotateY(int degrees) {
            int normalized = ((degrees % 360) + 360) % 360;
            return switch (normalized) {
                case 0 -> this;
                case 90 -> new Box(16 - z2, y1, x1, 16 - z1, y2, x2);
                case 180 -> new Box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
                case 270 -> new Box(z1, y1, 16 - x2, z2, y2, 16 - x1);
                default -> throw new IllegalArgumentException("degrees must be a multiple of 90, was " + degrees);
            };
        }

        VoxelShape toShape() {
            return Shapes.box(x1 / 16.0, y1 / 16.0, z1 / 16.0, x2 / 16.0, y2 / 16.0, z2 / 16.0);
        }
    }

    private static VoxelShape union(Box[] boxes, int degrees) {
        VoxelShape shape = Shapes.empty();
        for (Box box : boxes) {
            shape = Shapes.or(shape, box.rotateY(degrees).toShape());
        }
        return shape;
    }

    /** @return one shape per horizontal {@link Direction}, rotated from boxes authored at FACING=NORTH (0°) — the convention every block in this family except {@code RadiatorValveSouthBlock} uses. */
    static Map<Direction, VoxelShape> forHorizontalFacing(Box... boxesAtNorth) {
        return forHorizontalFacing(Direction.NORTH, boxesAtNorth);
    }

    /**
     * @return one shape per horizontal {@link Direction}, rotated from boxes
     * authored against whatever direction the model's own un-rotated (blockstate
     * "y": 0) geometry actually represents — {@code RadiatorValveSouthBlock}'s
     * model is authored with its valve already facing south at 0°, not north,
     * matching that block's own blockstate rotation cycle (south=0, west=90,
     * north=180, east=270) rather than the north=0 cycle everything else here uses.
     */
    static Map<Direction, VoxelShape> forHorizontalFacing(Direction modelDefaultFacing, Box... boxesAtDefault) {
        Map<Direction, VoxelShape> result = new EnumMap<>(Direction.class);
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            result.put(dir, union(boxesAtDefault, degreesBetween(modelDefaultFacing, dir)));
        }
        return result;
    }

    /** Clockwise steps (as 90° multiples) from {@code from} to {@code to} in the N->E->S->W cycle, matching blockstate "y" rotation. */
    private static int degreesBetween(Direction from, Direction to) {
        return ((clockwiseIndex(to) - clockwiseIndex(from) + 4) % 4) * 90;
    }

    private static int clockwiseIndex(Direction dir) {
        return switch (dir) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> throw new IllegalArgumentException("Not a horizontal direction: " + dir);
        };
    }

    /** @return one shape per horizontal {@link Direction.Axis}, rotated from boxes authored at AXIS=Z (0°). */
    static Map<Direction.Axis, VoxelShape> forHorizontalAxis(Box... boxesAtZ) {
        Map<Direction.Axis, VoxelShape> result = new EnumMap<>(Direction.Axis.class);
        result.put(Direction.Axis.Z, union(boxesAtZ, 0));
        result.put(Direction.Axis.X, union(boxesAtZ, 90));
        return result;
    }
}
