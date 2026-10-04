package com.cio.createinteroperable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The one slider "highlighter" (Create value box) placement every CIO block
 * uses. Modelled on Electro Energetics' Voltage Regulator, which extends
 * Create's own {@link ValueBoxTransform.Sided}: the box is pinned to one face
 * of the block, only appears / accepts clicks while that face is the one being
 * looked at, and is turned to lie flat on it.
 *
 * <p>The previous hand-rolled transforms positioned the box correctly but
 * turned it by {@code baseAngle + facingAngle}. Create's rotation runs the
 * opposite way to the blockstate's clockwise {@code y}, so at East/West
 * placements the box sat on the right face but pointed 180&deg; into the block
 * &mdash; invisible. Here the face is derived from the very same transform that
 * places the box, and Create's own {@code Sided} formulas turn it, so position
 * and orientation can never disagree.</p>
 *
 * <p>The slot is authored once, at the block's default (unrotated) facing:
 * a point in 0&ndash;1 block space plus the face it lies on. An {@link Orient}
 * then maps any point through the block's own facing rotation &mdash; the same
 * function that rotates that block's PG terminals / CEE nodes.</p>
 */
public class CIOValueBox extends ValueBoxTransform.Sided {

    /** Standard Create value-box size (8&nbsp;px) &mdash; what the Voltage Regulator uses. */
    public static final float SCALE_STANDARD = 0.5f;

    /** Maps a block-space point (0..1 on each axis) from the default facing to the state's actual facing. */
    @FunctionalInterface
    public interface Orient {
        Vec3 apply(BlockState state, Vec3 point);
    }

    /** The face a legacy per-slot {@code rotateYDegrees(baseAngle)} was pointing the box at (0=north, 90=west, 180=south, 270=east). */
    public static Direction faceForBaseAngle(int baseAngle) {
        return switch (((baseAngle % 360) + 360) % 360) {
            case 90 -> Direction.WEST;
            case 180 -> Direction.SOUTH;
            case 270 -> Direction.EAST;
            default -> Direction.NORTH;
        };
    }

    private static final Vec3 CENTER = new Vec3(0.5, 0.5, 0.5);

    private final Vec3 base;
    private final Direction baseFace;
    private final Orient orient;
    private boolean anySide;

    /**
     * @param x         slot centre X in 1/16 units at the default facing
     * @param y         slot centre Y in 1/16 units at the default facing
     * @param z         slot centre Z in 1/16 units at the default facing (sit a hair proud of the face)
     * @param baseFace  which face of the model the slot lies on at the default facing
     * @param scale     box size in blocks ({@link #SCALE_STANDARD} = 8&nbsp;px)
     * @param orient    the block's own facing transform
     */
    public CIOValueBox(double x, double y, double z, Direction baseFace, float scale, Orient orient) {
        this(VecHelper.voxelSpace(x, y, z), baseFace, scale, orient);
    }

    /** As above, with the slot centre already in 0..1 block space (e.g. from {@code VecHelper.voxelSpace}). */
    public CIOValueBox(Vec3 base, Direction baseFace, float scale, Orient orient) {
        this.base = base;
        this.baseFace = baseFace;
        this.orient = orient;
        this.scale = scale;
    }

    /**
     * Show and accept clicks from every side instead of only the slot's own face. For slots on tiny
     * fixtures (a 1 px lever) where demanding one exact face makes the control nearly unclickable;
     * the box is still turned to lie on its own face.
     */
    public CIOValueBox fromAnySide() {
        this.anySide = true;
        return this;
    }

    /** The face this slot is on for the given state (the default face carried through the block's facing). */
    public Direction activeSide(BlockState state) {
        Vec3 tip = CENTER.add(Vec3.atLowerCornerOf(baseFace.getNormal()).scale(0.5));
        Vec3 d = orient.apply(state, tip).subtract(orient.apply(state, CENTER));
        return Direction.getNearest(d.x, d.y, d.z);
    }

    @Override
    protected Vec3 getSouthLocation() {
        return base; // unused: getLocalOffset is overridden — the slot is authored for its own face, not "south"
    }

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        return orient.apply(state, base);
    }

    @Override
    public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
        // Exactly ValueBoxTransform.Sided#rotate, but for the slot's own face instead of the looked-at one.
        Direction side = activeSide(state);
        float yRot = AngleHelper.horizontalAngle(side) + 180;
        float xRot = side == Direction.UP ? 90 : side == Direction.DOWN ? 270 : 0;
        TransformStack.of(ms).rotateYDegrees(yRot).rotateXDegrees(xRot);
    }

    @Override
    protected boolean isSideActive(BlockState state, Direction direction) {
        return anySide || direction == activeSide(state);
    }
}
