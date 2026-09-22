package com.cio.createinteroperable;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Makes ordinary vanilla cats (tame or stray — no different than how real
 * cats seek out warmth regardless of who they belong to) seek out an
 * assembled, WARM-or-hotter Steam Hearth and lie on it, the same general
 * shape as vanilla's own {@code CatSitOnBlockGoal} (a lit furnace/chest/bed)
 * — see that class and its {@code MoveToBlockGoal} parent, decompiled and
 * read directly while designing this. Deliberately NOT a subclass of either:
 * {@code MoveToBlockGoal}'s single {@code isValidTarget(pos)} callback has no
 * room for "which of several very different candidate-spot shapes applies
 * right now" (on top of a segment vs. on the floor beside one, chosen by the
 * run's current heat tier), and its block-by-block cube search is the wrong
 * shape for "is this cat inside a specific Hearth's already-computed
 * flood-filled room" — {@link RadiatorValveNorthBlockEntity#getRoomCache()}
 * already answers that far more cheaply than re-deriving it here.
 * <p>
 * One instance is added, once, per {@link Cat} — see {@link CatSteamHearthEvents}.
 * <p>
 * {@link Phase} state machine, entirely held in this Goal's own instance
 * fields (Goals are reused, never recreated, for as long as the entity stays
 * loaded):
 * <ol>
 *     <li><b>Idle</b> (no Phase yet) — {@link #canUse()} periodically (see
 *     {@link #scanCooldown}) asks {@link #findAttractiveHearth()} for a
 *     nearby Hearth; if found, the cat doesn't head there immediately —
 *     {@link #approachAtTick} is a random 0-10s out, so several cats noticing
 *     the same Hearth at once don't all get up in lockstep.</li>
 *     <li>{@link Phase#TRAVEL} — walking to {@link #travelTarget} via normal
 *     {@code PathNavigation}. For an on-top pursuit ({@link #wantsOnTop}),
 *     this is deliberately NOT {@link #finalTopPos} itself but a reachable
 *     floor cell beside the chosen segment ("staging") — see this class's
 *     own investigation note on why a direct nav-to-the-top request silently
 *     never arrives at all: the segment blocks' own collision shape is a
 *     partial box (not a full cube — see {@code RadiatorValveNorthBlock}'s
 *     own class doc), which never registers as a valid path NODE for
 *     vanilla's node-search pathfinder, so {@code PathNavigation#moveTo} can
 *     never route onto it, full stop, no matter how close or how long the
 *     cat tries.</li>
 *     <li>{@link Phase#WAITING_TO_JUMP} — once at the staging spot, a random
 *     1-6s pause ("after a random 1 to 6 seconds of having reached the
 *     radiator" per the design request) before actually attempting the hop.</li>
 *     <li>{@link Phase#JUMPING} — steers straight at {@link #finalTopPos}
 *     every tick via {@code Mob#getMoveControl()} instead of navigation —
 *     {@code MoveControl} (read directly while diagnosing this) drives the
 *     mob by raw physics/collision toward a world-space point, no path node
 *     required, and already auto-jumps on its own the moment the wanted Y
 *     sits above the mob's normal step height and it's close enough
 *     horizontally — the exact same mechanism every mob's everyday
 *     1-block-ledge autostep already uses, just triggered manually here since
 *     the segment's own shape can't produce that path node through the normal
 *     route. Gives up after a further random stretch ({@link
 *     #JUMP_GIVE_UP_MIN_TICKS}-{@link #JUMP_GIVE_UP_MAX_TICKS}) if the cat
 *     still hasn't actually landed up there — "if for some reason they cannot
 *     reach the top" per the design request — and falls back to a
 *     beside-the-segment spot instead, "if there is room" (reusing {@link
 *     #pickSpot}'s own escalating-radius fallback chain, the same one BLAZING
 *     and "every top spot already taken" already rely on).</li>
 *     <li>{@link Phase#SETTLED} — {@link Cat#setLying} is called exactly
 *     once on entry (not re-issued every tick — see that method's own
 *     "settled drift" doc for why this matters for vanilla's own purring
 *     cadence), and periodically re-validated (see {@link
 *     #currentTargetStillValid}) — this is how a cat sitting ON TOP gets
 *     moved off automatically the moment the run flares up to BLAZING, and
 *     how a knocked-off cat un-lies and resumes rather than being left
 *     silently stuck lying somewhere that no longer makes sense.</li>
 *     <li><b>Leaving</b> — once the tier drops below WARM,
 *     {@link #canContinueToUse()} doesn't bail immediately: it picks its own
 *     random 0-3 second grace ({@link #leaveAtTick}) first ("after up to 3
 *     seconds, the cats will be told to wake up and leave" per the design
 *     request), and cancels that plan (resumes lying) if the Hearth warms
 *     back up before the timer fires.</li>
 * </ol>
 */
public class CatSteamHearthGoal extends Goal {
    /** How often (ticks) an idle cat re-scans for a nearby Hearth — matches vanilla MoveToBlockGoal's own 200±200-ish idle cadence closely enough; this is a much cheaper lookup (see {@link #findAttractiveHearth()}) so a shorter interval isn't a real cost concern. */
    private static final int SCAN_INTERVAL_TICKS = 100;
    /** How far (blocks, real distance) an idle cat will notice a Hearth at all — generous enough to comfortably cover a Hearth's own max flood-fill reach (ROOM_MAX_RANGE=20) plus some slack for the "near the physical segments" fallback case. */
    private static final double SEARCH_RADIUS = 24.0;
    /** Matches {@code RadiatorValveNorthBlockEntity#NEAR_ENDPOINT_RADIUS} closely — "standing right at the Hearth" counts as attractive even on the rare frame the flood-fill hasn't (yet) reached the cat's exact cell. */
    private static final double NEAR_SEGMENT_RADIUS = 4.0;
    /** "A random short interval from 0 to 10 seconds" per the design request. */
    private static final int MAX_APPROACH_DELAY_TICKS = 200;
    /** "After up to 3 seconds" per the design request. */
    private static final int MAX_LEAVE_DELAY_TICKS = 60;
    /** "A random 1 to 6 seconds of having reached the radiator" per the design request, before attempting the jump. */
    private static final int JUMP_DELAY_MIN_TICKS = 20;
    private static final int JUMP_DELAY_MAX_TICKS = 120;
    /** "A subsequent random amount of seconds" per the design request — not itself specified further; picked to comfortably outlast a real jump-and-land arc without leaving a cat that genuinely can't make it flailing at the wall for ages. */
    private static final int JUMP_GIVE_UP_MIN_TICKS = 20;
    private static final int JUMP_GIVE_UP_MAX_TICKS = 60;
    /** Same acceptance radius vanilla's own MoveToBlockGoal uses. */
    private static final double ARRIVE_DISTANCE = 1.0;
    /** How close (real distance) counts as "actually landed on top" during {@link Phase#JUMPING} — tighter than {@link #ARRIVE_DISTANCE} since this also requires {@code onGround()}, so it only needs to rule out "still mid-air passing over." */
    private static final double LAND_TOLERANCE = 0.6;
    /**
     * How far (real distance) a {@link Phase#SETTLED} cat has to drift from
     * its own spot before it's treated as genuinely knocked away rather than
     * ordinary physics micro-jitter (a falling/landing settle, a light nudge
     * from another entity) — deliberately looser than {@link #ARRIVE_DISTANCE}.
     * Directly answers the "purr sound loops ruthlessly" report: the old code
     * re-derived "arrived" from a raw distance check every single tick with no
     * slack at all, so a one-tick jitter right at that boundary flipped {@link
     * Cat#setLying} off and back on — not something that changes vanilla's own
     * 5-tick purr-retrigger RATE (that timer runs regardless of how many times
     * setLying gets called), but it did restart the visual
     * lie-down-amount ramp and interrupt the purr's own natural continuity
     * for no reason. This class's new {@link Phase} transitions only ever
     * call {@code setLying} once per genuine state change, and this
     * tolerance is the settled-side half of that guarantee.
     */
    private static final double SETTLED_DRIFT_TOLERANCE = 1.5;
    /** Same speed modifier vanilla's own CatSitOnBlockGoal uses. */
    private static final double SPEED = 0.8;
    /** How often (ticks), once under way, the goal re-validates its current target — frequent enough to react quickly to another cat stealing the spot or the tier changing, cheap enough to not matter. */
    private static final int RECHECK_INTERVAL_TICKS = 20;
    /** Escalating search radii (blocks, from the nearest segment) tried when every ideal spot is already taken — "if the spots are full, the cats should want to come as close as possible" per the design request. */
    private static final double[] FALLBACK_RADII = {3.0, 5.0, 8.0};

    private enum Phase {
        TRAVEL, WAITING_TO_JUMP, JUMPING, SETTLED
    }

    private final Cat cat;

    private int scanCooldown;
    @Nullable
    private WeakReference<RadiatorValveNorthBlockEntity> hearthRef;
    /** Game time the cat should stop waiting and actually get up — see the class doc's "Idle" state. -1 until a Hearth has been noticed. */
    private long approachAtTick = -1;
    /** Game time the cat should give up on a now-cold Hearth and leave — see the class doc's "Leaving" state. -1 whenever not currently counting down (including "still warm"). */
    private long leaveAtTick = -1;

    private Phase phase = Phase.TRAVEL;
    /** Computed by {@link #canUse()}, consumed once by {@link #start()} — see that method's own doc for why the split exists. */
    @Nullable
    private SpotChoice pendingSpot;
    /** Whether the CURRENT pursuit's ultimate goal is on top of a segment (as opposed to a beside-segment floor spot) — see {@link Phase#JUMPING}'s own doc. */
    private boolean wantsOnTop;
    /** The on-top position itself, only meaningful while {@link #wantsOnTop}. */
    @Nullable
    private BlockPos finalTopPos;
    /** What {@link Phase#TRAVEL} is actually walking toward — {@link #finalTopPos}'s own staging (beside) spot when {@link #wantsOnTop}, otherwise the beside spot itself (nothing further needed once reached). */
    @Nullable
    private BlockPos travelTarget;
    private long jumpAtTick = -1;
    private long jumpGiveUpAtTick = -1;

    public CatSteamHearthGoal(Cat cat) {
        this.cat = cat;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cat.isOrderedToSit()) {
            return false;
        }
        Level level = cat.level();
        long gameTime = level.getGameTime();

        RadiatorValveNorthBlockEntity hearth = hearthRef != null ? hearthRef.get() : null;
        if (hearth == null || hearth.isRemoved() || hearth.getLevel() != level) {
            hearthRef = null;
            if (scanCooldown > 0) {
                scanCooldown--;
                return false;
            }
            hearth = findAttractiveHearth();
            if (hearth == null) {
                scanCooldown = SCAN_INTERVAL_TICKS + cat.getRandom().nextInt(SCAN_INTERVAL_TICKS);
                return false;
            }
            hearthRef = new WeakReference<>(hearth);
            // Random 0-10s "notice, then get up" delay — see class doc.
            approachAtTick = gameTime + cat.getRandom().nextInt(MAX_APPROACH_DELAY_TICKS + 1);
            return false;
        }

        if (!isAttractive(hearth.getHeatTier())) {
            // Went cold again before the cat ever got moving — forget the
            // plan entirely rather than waiting the rest of the delay out.
            hearthRef = null;
            return false;
        }
        if (gameTime < approachAtTick) {
            return false;
        }

        SpotChoice spot = pickSpot(hearth, cat, true);
        if (spot == null) {
            return false;
        }
        pendingSpot = spot;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (cat.isOrderedToSit()) {
            return false;
        }
        RadiatorValveNorthBlockEntity hearth = hearthRef != null ? hearthRef.get() : null;
        if (hearth == null || hearth.isRemoved() || hearth.getLevel() != cat.level()) {
            return false;
        }
        long gameTime = cat.level().getGameTime();
        if (!isAttractive(hearth.getHeatTier())) {
            if (leaveAtTick < 0) {
                // Random 0-3s "still enjoying the warmth" grace — see class doc.
                leaveAtTick = gameTime + cat.getRandom().nextInt(MAX_LEAVE_DELAY_TICKS + 1);
            }
            return gameTime < leaveAtTick;
        }
        // Warmed back up before the wake-up timer fired — cat changes its
        // mind and stays; cancel whatever grace period was pending.
        leaveAtTick = -1;
        return true;
    }

    /**
     * {@link #canUse()} only picks and stores {@link #pendingSpot} — the
     * actual pursuit (clearing jump timers, issuing the first navigation
     * call) is deliberately deferred to here, matching vanilla's own
     * {@code MoveToBlockGoal} split between {@code findNearestBlock()} (in
     * {@code canUse()}) and {@code moveMobToBlock()} (in {@code start()}).
     */
    @Override
    public void start() {
        leaveAtTick = -1;
        if (pendingSpot != null) {
            beginPursuit(pendingSpot);
            pendingSpot = null;
        }
    }

    @Override
    public void stop() {
        cat.setLying(false);
        cat.getNavigation().stop();
        phase = Phase.TRAVEL;
        wantsOnTop = false;
        finalTopPos = null;
        travelTarget = null;
        jumpAtTick = -1;
        jumpGiveUpAtTick = -1;
        leaveAtTick = -1;
        hearthRef = null;
        pendingSpot = null;
        // Short-ish rather than the full idle interval — if this Hearth (or
        // another one) is still genuinely warm nearby, the cat should notice
        // again fairly soon rather than wandering off for a full 5-10s first.
        scanCooldown = SCAN_INTERVAL_TICKS / 2 + cat.getRandom().nextInt(SCAN_INTERVAL_TICKS / 2);
    }

    @Override
    public void tick() {
        RadiatorValveNorthBlockEntity hearth = hearthRef != null ? hearthRef.get() : null;
        if (hearth == null) {
            return;
        }

        // Periodically re-check the CURRENT phase's real destination is
        // still legitimate — catches both "another cat beat me to it" and
        // "this run just flared up to BLAZING while I was aiming for the
        // top," without ever stopping/restarting the goal itself.
        if (cat.tickCount % RECHECK_INTERVAL_TICKS == 0 && !currentTargetStillValid(hearth) && !retarget(hearth, true)) {
            return;
        }

        long gameTime = cat.level().getGameTime();
        switch (phase) {
            case TRAVEL -> tickTravel(gameTime);
            case WAITING_TO_JUMP -> tickWaitingToJump(gameTime);
            case JUMPING -> tickJumping(hearth, gameTime);
            case SETTLED -> tickSettled();
        }
    }

    private void tickTravel(long gameTime) {
        BlockPos target = travelTarget;
        if (target == null) {
            return;
        }
        cat.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        if (hasArrived(target)) {
            cat.getNavigation().stop();
            if (wantsOnTop) {
                phase = Phase.WAITING_TO_JUMP;
                jumpAtTick = gameTime + JUMP_DELAY_MIN_TICKS
                        + cat.getRandom().nextInt(JUMP_DELAY_MAX_TICKS - JUMP_DELAY_MIN_TICKS + 1);
            } else {
                settleHere();
            }
        } else if (cat.getNavigation().isDone()) {
            cat.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, SPEED);
        }
    }

    private void tickWaitingToJump(long gameTime) {
        if (finalTopPos != null) {
            cat.getLookControl().setLookAt(finalTopPos.getX() + 0.5, finalTopPos.getY() + 0.5, finalTopPos.getZ() + 0.5);
        }
        if (travelTarget != null && !hasArrived(travelTarget)) {
            // Knocked off its staging spot before it even got to try — just
            // go back to approaching it normally.
            phase = Phase.TRAVEL;
            return;
        }
        if (gameTime >= jumpAtTick) {
            phase = Phase.JUMPING;
            jumpGiveUpAtTick = gameTime + JUMP_GIVE_UP_MIN_TICKS
                    + cat.getRandom().nextInt(JUMP_GIVE_UP_MAX_TICKS - JUMP_GIVE_UP_MIN_TICKS + 1);
            cat.getNavigation().stop();
        }
    }

    private void tickJumping(RadiatorValveNorthBlockEntity hearth, long gameTime) {
        BlockPos top = finalTopPos;
        if (top == null) {
            phase = Phase.TRAVEL;
            return;
        }
        if (cat.onGround() && cat.position().closerThan(Vec3.atBottomCenterOf(top), LAND_TOLERANCE)) {
            settleHere();
            return;
        }
        if (gameTime >= jumpGiveUpAtTick) {
            // Couldn't make the jump — "revert to lying around the radiator,
            // if there is room" per the design request: forced beside-only
            // so pickSpot doesn't just hand the same unreachable top spot
            // straight back.
            if (!retarget(hearth, false)) {
                phase = Phase.TRAVEL;
            }
            return;
        }
        // MoveControl (unlike PathNavigation) steers by raw physics/collision
        // toward a world-space point rather than a precomputed node path, and
        // already auto-jumps on its own whenever the wanted Y sits above the
        // mob's normal step height and it's close enough horizontally — see
        // this class's own doc on Phase.JUMPING for why PathNavigation itself
        // can never get a cat up here at all.
        cat.getMoveControl().setWantedPosition(top.getX() + 0.5, top.getY(), top.getZ() + 0.5, SPEED);
    }

    private void tickSettled() {
        BlockPos settledAt = wantsOnTop ? finalTopPos : travelTarget;
        if (settledAt == null) {
            return;
        }
        if (!cat.position().closerThan(Vec3.atBottomCenterOf(settledAt), SETTLED_DRIFT_TOLERANCE)) {
            // Genuinely knocked away (see SETTLED_DRIFT_TOLERANCE's own doc)
            // — go back and resettle rather than leaving IS_LYING stuck on
            // somewhere that no longer makes sense.
            cat.setLying(false);
            phase = Phase.TRAVEL;
            travelTarget = settledAt;
        }
    }

    private void settleHere() {
        phase = Phase.SETTLED;
        cat.getNavigation().stop();
        cat.setLying(true);
    }

    private boolean hasArrived(BlockPos pos) {
        return cat.position().closerThan(Vec3.atBottomCenterOf(pos), ARRIVE_DISTANCE);
    }

    /** @return whether the CURRENT phase's real destination is still legitimate — false forces {@link #retarget} in {@link #tick()}. */
    private boolean currentTargetStillValid(RadiatorValveNorthBlockEntity hearth) {
        if (wantsOnTop && hearth.getHeatTier() == BrassHeaterBlock.HeatLevel.BLAZING) {
            return false;
        }
        boolean checkingTop = wantsOnTop && (phase == Phase.JUMPING || phase == Phase.SETTLED);
        BlockPos check = checkingTop ? finalTopPos : travelTarget;
        return check != null && hearth.getRoomCache().contains(check);
    }

    /** Picks a fresh spot and begins pursuing it, mid-goal — used both by the periodic re-check in {@link #tick()} and by a failed jump attempt (with {@code allowOnTopChoice = false}, forcing a beside spot). @return false if nothing usable was found at all. */
    private boolean retarget(RadiatorValveNorthBlockEntity hearth, boolean allowOnTopChoice) {
        SpotChoice spot = pickSpot(hearth, cat, allowOnTopChoice);
        if (spot == null) {
            return false;
        }
        beginPursuit(spot);
        return true;
    }

    private void beginPursuit(SpotChoice spot) {
        wantsOnTop = spot.onTop();
        finalTopPos = spot.onTop() ? spot.pos() : null;
        travelTarget = spot.onTop() ? spot.stagingPos() : spot.pos();
        phase = Phase.TRAVEL;
        jumpAtTick = -1;
        jumpGiveUpAtTick = -1;
        if (cat.isLying()) {
            cat.setLying(false);
        }
        if (travelTarget != null) {
            cat.getNavigation().moveTo(travelTarget.getX() + 0.5, travelTarget.getY(), travelTarget.getZ() + 0.5, SPEED);
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private static boolean isAttractive(BrassHeaterBlock.HeatLevel tier) {
        return tier == BrassHeaterBlock.HeatLevel.WARM
                || tier == BrassHeaterBlock.HeatLevel.HOT
                || tier == BrassHeaterBlock.HeatLevel.BLAZING;
    }

    /**
     * @return the nearest assembled, attractive Hearth this cat is either
     * standing inside the flood-filled room of, or close enough to one of
     * its physical segments to count as "right there" — mirrors {@link
     * RadiatorValveNorthBlockEntity#hearthStrengthAt} from the opposite
     * direction (that method asks "is pos in my room"; this asks "which
     * nearby Hearth's room am I in").
     */
    @Nullable
    private RadiatorValveNorthBlockEntity findAttractiveHearth() {
        Level level = cat.level();
        BlockPos catPos = cat.blockPosition();
        RadiatorValveNorthBlockEntity best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (RadiatorValveNorthBlockEntity hearth : RadiatorValveNorthBlockEntity.activeHearthsSnapshot()) {
            if (hearth.isRemoved() || hearth.getLevel() != level) {
                continue;
            }
            BlockPos hearthPos = hearth.getBlockPos();
            if (!hearthPos.closerThan(catPos, SEARCH_RADIUS)) {
                continue;
            }
            if (!isAttractive(hearth.getHeatTier())) {
                continue;
            }

            Set<BlockPos> room = hearth.getRoomCache();
            boolean inRoom = room.contains(catPos) || room.contains(catPos.above());
            boolean nearSegment = !inRoom && hearth.getAllSegmentPositions().stream()
                    .anyMatch(seg -> seg.closerThan(catPos, NEAR_SEGMENT_RADIUS));
            if (!inRoom && !nearSegment) {
                continue;
            }

            double distSq = hearthPos.distSqr(catPos);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = hearth;
            }
        }
        return best;
    }

    /**
     * A chosen target position, plus whether it's an on-top-of-segment spot
     * (as opposed to a beside-segment floor spot) — see {@link #pickSpot}.
     * {@code stagingPos} is only present (non-null) when {@code onTop} is
     * true: the reachable floor cell beside that same segment {@link
     * Phase#TRAVEL} actually walks to first, since {@code pos} itself (on
     * top of the segment) generally can't be reached by normal navigation at
     * all — see this class's own doc on {@link Phase#JUMPING}.
     */
    private record SpotChoice(BlockPos pos, boolean onTop, @Nullable BlockPos stagingPos) {
    }

    /**
     * @return the best available lie-down spot for this run's current tier,
     * or {@code null} only if the run's own room is somehow completely empty
     * (shouldn't happen — the segments themselves always seed it). WARM/HOT
     * prefer directly on top of a segment (but only a segment that ALSO has
     * a free, reachable staging spot beside it — see {@link #stagingSpotFor});
     * BLAZING (and WARM/HOT once every viable on-top segment is already
     * occupied or unreachable) prefer the floor immediately beside one
     * instead — "cats will want to lie in the blocks around it, but not on
     * top" per the design request. Falls back to an escalating-radius
     * search, then finally to the single closest room cell regardless of
     * occupancy, for "come as close as possible" once everything nearby is
     * already taken. {@code allowOnTopChoice} lets a caller force a
     * beside-only result even when the tier would otherwise permit the top
     * (used when a jump attempt just failed).
     */
    @Nullable
    private static SpotChoice pickSpot(RadiatorValveNorthBlockEntity hearth, Cat cat, boolean allowOnTopChoice) {
        Level level = cat.level();
        Set<BlockPos> room = hearth.getRoomCache();
        boolean allowOnTop = allowOnTopChoice && hearth.getHeatTier() != BrassHeaterBlock.HeatLevel.BLAZING;

        if (allowOnTop) {
            List<BlockPos> topCandidates = new ArrayList<>();
            for (BlockPos seg : hearth.getAllSegmentPositions()) {
                topCandidates.add(seg.above());
            }
            sortByDistancePreferred(topCandidates, cat);
            for (BlockPos top : topCandidates) {
                if (!room.contains(top) || !isFree(level, top, cat)) {
                    continue;
                }
                BlockPos staging = stagingSpotFor(hearth, top.below(), cat);
                if (staging != null) {
                    return new SpotChoice(top, true, staging);
                }
                // This segment's own top is free, but nothing free/reachable
                // beside it to stage the jump from — try the next candidate
                // rather than falling straight through to "beside anywhere."
            }
        }

        for (double radius : FALLBACK_RADII) {
            List<BlockPos> nearby = nearbyRoomCells(hearth, radius);
            sortByDistancePreferred(nearby, cat);
            for (BlockPos pos : nearby) {
                if (isFree(level, pos, cat)) {
                    return new SpotChoice(pos, false, null);
                }
            }
        }

        List<BlockPos> anyNearby = nearbyRoomCells(hearth, FALLBACK_RADII[FALLBACK_RADII.length - 1]);
        if (!anyNearby.isEmpty()) {
            sortByDistancePreferred(anyNearby, cat);
            return new SpotChoice(anyNearby.get(0), false, null);
        }
        return null;
    }

    /** @return a free, in-room horizontal neighbor of {@code seg} to stage a jump onto {@code seg}'s own top from, or null if all 4 sides are blocked/occupied/outside the room. */
    @Nullable
    private static BlockPos stagingSpotFor(RadiatorValveNorthBlockEntity hearth, BlockPos seg, Cat cat) {
        Level level = cat.level();
        Set<BlockPos> room = hearth.getRoomCache();
        List<BlockPos> sides = new ArrayList<>(4);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            sides.add(seg.relative(dir));
        }
        sortByDistancePreferred(sides, cat);
        for (BlockPos pos : sides) {
            if (room.contains(pos) && isFree(level, pos, cat)) {
                return pos;
            }
        }
        return null;
    }

    /** Shuffles first so ties (equal distance) resolve randomly, then a stable sort by real distance to the cat puts the nearest genuinely-reachable candidates first — "come as close as possible" without every cat racing for the exact same single spot. */
    private static void sortByDistancePreferred(List<BlockPos> positions, Cat cat) {
        BlockPos catPos = cat.blockPosition();
        Util.shuffle(positions, cat.getRandom());
        positions.sort(Comparator.comparingDouble(p -> p.distSqr(catPos)));
    }

    /** Every one of this Hearth's own flood-filled room cells within {@code maxDist} (real distance) of ANY of its segments. */
    private static List<BlockPos> nearbyRoomCells(RadiatorValveNorthBlockEntity hearth, double maxDist) {
        List<BlockPos> segments = hearth.getAllSegmentPositions();
        double maxDistSq = maxDist * maxDist;
        List<BlockPos> result = new ArrayList<>();
        for (BlockPos pos : hearth.getRoomCache()) {
            for (BlockPos seg : segments) {
                if (seg.distSqr(pos) <= maxDistSq) {
                    result.add(pos);
                    break;
                }
            }
        }
        return result;
    }

    /** @return whether {@code pos} is free of any OTHER cat right now — deliberately a live physical-presence check (same idea as vanilla Cat's own {@code spaceIsOccupied}) rather than a claimed/reserved-spot registry, so a cat that wanders off, dies, or gets picked up never leaves a spot stuck "reserved" forever. */
    private static boolean isFree(Level level, BlockPos pos, Cat self) {
        for (Cat other : level.getEntitiesOfClass(Cat.class, new AABB(pos))) {
            if (other != self) {
                return false;
            }
        }
        return true;
    }
}
