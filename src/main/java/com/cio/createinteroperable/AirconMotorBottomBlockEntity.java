package com.cio.createinteroperable;

import com.cio.createinteroperable.compat.ColdSweatCompat;
import com.cio.createinteroperable.compat.ColdSweatWorldTemp;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.utility.sound.SoundScapes;

import java.util.List;

/**
 * The Aircon Motor's real engine: a load resistor across 2 CPG terminals
 * (see {@link AirconMotorBottomBlock#POSITIVE_TERMINAL}) — its resistance is
 * DYNAMIC, not fixed, driven by both the {@link #powerSetting} Create slider
 * (Low/Mid/Max) and the real ambient temperature at this exact position (see
 * {@link #currentResistance}) — and 3 internal fluid buffers exposed one per
 * world face:
 * <ul>
 *     <li>DOWN (fixed, not remapped by FACING) — {@link #waterTank}, output
 *     only while hot_air is genuinely arriving right now (see
 *     {@link #isReceivingHotAirSupply}), a byproduct of actually consuming
 *     that returning hot_air — "if working, output water".</li>
 *     <li>WEST (or EAST if {@link #reversed}, remapped through
 *     {@link AirconMotorBottomBlock#rotate}) — {@link #coldAirTank}, the
 *     "always-on pump" side: produced from {@link #efficiency} × the
 *     setting's own fraction, independent of any hot_air actually returning
 *     yet, or the loop (motor -&gt; venter -&gt; back to motor) could never
 *     bootstrap.</li>
 *     <li>EAST (or WEST if {@link #reversed}, same remap) — {@link #hotAirIntakeTank},
 *     a plain fillable tank an external Create pump/pipe pushes hot_air
 *     into (typically from a venter's own hot-air-out face).</li>
 * </ul>
 * {@link #reversed} mirrors a real heat pump's reversing valve: wiring the
 * CPG terminals backwards flips which physical face is cold-out vs hot-in
 * (water stays on DOWN regardless — it's a byproduct of the returning air,
 * not tied to polarity itself).
 * <p>
 * <b>How the Low/Mid/Max setting reaches the venter's flood-fill drop
 * (redesigned 2026-09-21)</b>: the setting used to throttle {@link #coldAirTank}'s
 * own production rate (Low ran at 25% of Max's mB/t) — a real reported
 * problem with that: at Low/Mid, production could dip below what even a
 * single venter's own conversion wanted, so the venter's buffer emptied and
 * refilled in a loop and the room's temperature audibly "bounced" as
 * {@code activity} oscillated with it. Production is now a FLAT rate
 * ({@link #BASE_COLD_AIR_RATE}, scaled only by {@link #efficiency} — real
 * voltage, not the setting) regardless of Low/Mid/Max, deliberately generous
 * enough that no reasonable venter or chain can ever starve it — so a
 * venter's own {@code activity} settles at a stable value instead of
 * hunting. The setting's actual effect moved to the OTHER side of the loop
 * instead: {@link AirconVenterBlockEntity} looks up this motor's own
 * {@link #getSettingDropC()} (same "no stored link, just scan nearby
 * chunks" approach as {@link #totalCooledRoomSize}, just reversed) and uses
 * THAT as its own cooling ceiling, so Low/Mid/Max now controls HOW COLD the
 * air effectively is once processed, not how MUCH of it exists to process.
 */
public class AirconMotorBottomBlockEntity extends ElectricBlockEntity
        implements IHaveGoggleInformation, AirconMotorBottomInfo {
    /** The voltage {@link #efficiency} ramps up to (linearly from 0 V); stays at 1 all the way through the {@link #SMOKE_THRESHOLD_VOLTS}/{@link #EXPLODE_THRESHOLD_VOLTS} danger zone — "works properly up to 149 V" means full rated performance, not a symmetric falloff above 120 V. */
    private static final float OPTIMAL_VOLTS = 120f;
    /**
     * Absolute floor on {@link #currentResistance()} — regardless of how hot
     * the ambient or how large the cooled room gets, the motor must never
     * draw more current than a standard PG "Insulated Copper Wire" can carry
     * CONTINUOUSLY without eventually overheating. Confirmed by decompiling
     * PG's real wire thermal model ({@code BaseWireEntity#temperatureUpdate},
     * {@code WireItemEntry#dissipationFactor}) and its own real
     * {@code insulated_copper_wire.json} datapack entry
     * ({@code maximumCurrent: 70.0}): {@code dissipationFactor} is defined as
     * {@code maximumCurrent² * resistancePerItem / 150}, which makes the
     * per-item resistance and dissipation scale identically with wire
     * length — so at EQUILIBRIUM the length cancels out entirely and the
     * steady-state temperature RISE above ambient reduces to a clean
     * {@code 150°C * (current / maximumCurrent)²}, independent of how long a
     * run of wire is used. At {@code current == maximumCurrent}, that's a
     * 150°C rise — i.e. a wire held exactly AT its own rated maximumCurrent
     * settles right at the edge of the real {@code overheatTemperature}
     * (175°C, a normal ambient of ~20-25°C already eating almost the entire
     * margin), not a genuinely safe steady-state target. This floor keeps
     * the motor's own WORST-CASE current (at {@link #OPTIMAL_VOLTS}, any
     * ambient, any cooled-room size) at 50 A — 71% of insulated copper's 70 A
     * rating, a 150°C x 0.71² ≈ 76°C equilibrium rise, genuine headroom
     * rather than hugging the overheat edge even before accounting for a
     * hot ambient eating into that margin further.
     */
    private static final float MIN_RESISTANCE_OHMS = OPTIMAL_VOLTS / 50f;

    /** Index into every {@code SETTING_*} array below for the "Off" position — see {@link #isOff()}. */
    private static final int OFF_INDEX = 0;
    /**
     * Off/Low/Mid/Max resistance at the {@link #AMBIENT_NEUTRAL_C} reference,
     * Ω. 4:2:1 ratio among Low/Mid/Max mirrors {@link #SETTING_DROP_C}'s
     * 6:12:24 (1:2:4) — a stronger cooling setting draws more power, same
     * relationship a real AC compressor has. Max (6 Ω) is the same ≈2400
     * W-at-120V figure this block always advertised. Off's own entry is
     * never actually applied to {@link #loadSwitch} (see
     * {@link #electricalTick()} — only Low/Mid/Max ever call
     * {@code setResistance}), so its value here is inert filler; genuine
     * disconnection while Off comes from {@code SwitchedWire#setState(false)}
     * instead — PG's own real open-switch mechanic (down to its own tiny
     * {@code OFF_CONDUCTANCE} floor, confirmed by decompiling the class),
     * not a very-large-resistance approximation.
     */
    private static final float[] SETTING_RESISTANCE = {24f, 24f, 12f, 6f};
    /** Off/Low/Mid/Max — °C the connected venter(s) settle toward dropping, at full (120-149 V) effectiveness — see this class's own doc for how the setting actually reaches the venter. Off's own 0 is never actually read: {@link #isOff()} zeroes {@link #efficiency}, so nothing ever calls into a venter's own conversion while off. */
    private static final float[] SETTING_DROP_C = {0f, 6f, 12f, 24f};
    /** Slider row labels — special characters per the design conversation (escaped, not typed literally, so the source file's own encoding can't mangle them). */
    private static final String[] SETTING_LABELS = {
            "Off", "Low " + '❄', "Mid " + '❄' + '❄', "Max " + '❄' + '❄' + '❄'
    };
    /** Off/Low/Mid/Max — {@link #smoothedHumIntensity}'s own multiplier on top of {@link #efficiency}, feeding ONLY {@link #tickAudio()}. Same 1:2:4 relative-power feel as {@link #SETTING_RESISTANCE}'s own Low:Mid:Max ratio (0.25:0.5:1 of Max), so the hum's own intensity tracks how hard the unit is actually working, not just whether it has voltage. */
    private static final float[] SETTING_HUM_FRACTION = {0f, 0.25f, 0.5f, 1f};
    /**
     * Off(unused)/Low/Mid/Max — {@link #smoothedHumPitch}'s own target, real
     * pitch values (NOT a continuous 0-1 fraction like {@link #SETTING_HUM_FRACTION}).
     * Chosen to land in three DIFFERENT {@code SoundScapes.RangeGroup}
     * buckets — confirmed by decompiling PG's real {@code SoundScapes}:
     * {@code getGroupFromPitch} buckets any {@code play()} call by pitch into
     * VERY_LOW (&lt;0.7) / LOW (0.7-0.9) / NORMAL (0.9-1.1) / HIGH (1.1-1.3) /
     * VERY_HIGH (&gt;=1.3), and {@code SoundScape}'s own constructor only ever
     * runs on the FIRST {@code play()} call that creates a given
     * (AmbienceGroup, RangeGroup) bucket — {@code addSound}'s
     * {@code computeIfAbsent} means every LATER call into an already-created
     * bucket only bumps its shared volume (a plain {@code max()}), never its
     * pitch. The OLD continuous 0.65-1.0 pitch range (from
     * {@code smoothedHumIntensity} alone) put Low (0.7375) and Mid (0.825)
     * in the exact same "LOW" bucket — provably, permanently pitch-identical
     * no matter what, which was the real bug behind "no humming difference
     * between Low/Mid/Max": PG's own ambient-sound pooling silently erased
     * it. These three are spread across LOW/NORMAL/HIGH with real margin
     * (>=0.1) from every boundary, so ordinary smoothing jitter can never
     * accidentally tip one into a neighboring bucket. Off shares Low's own
     * value (0.75) purely so {@link #smoothedHumPitch} has somewhere
     * harmless to sit while silent — it's never actually heard, since
     * {@link #tickAudio()} bails out before reading it whenever the hum is
     * inaudible anyway.
     */
    private static final float[] SETTING_HUM_PITCH = {0.75f, 0.75f, 1.0f, 1.2f};

    /** Ambient °C at/below which the setting's own base resistance applies unmodified — no wattage bonus for an already-mild environment. */
    private static final float AMBIENT_NEUTRAL_C = 20f;
    /** Each °C the ambient sits above {@link #AMBIENT_NEUTRAL_C} draws this much more power — a real compressor works harder against a bigger outside-to-target gap. Tunable placeholder. */
    private static final float WATTAGE_INCREASE_PER_DEGREE_ABOVE_NEUTRAL = 0.03f;
    /** Each block currently being cooled (see {@link #totalCooledRoomSize}) draws this much more power — a bigger cooled volume is a bigger real load. Tunable placeholder. */
    private static final float WATTAGE_INCREASE_PER_COOLED_BLOCK = 0.002f;
    /** How often (in ticks) the nearby-venter scan re-runs — cheap but not free, and this only feeds a slowly-changing wattage bonus, not anything time-critical. */
    private static final int VENTER_SCAN_INTERVAL_TICKS = 20;
    /** How many chunks out to look for connected venters — there's no stored link (see this class's own top-level doc), so this is the same real-simulated-flow-radius approximation used throughout the Aircon feature. */
    private static final int VENTER_SCAN_CHUNK_RADIUS = 2;

    /**
     * mB/t of cold_air produced at {@link #efficiency} = 1 (120-149 V) — a
     * FLAT rate now, the same at Low/Mid/Max (redesigned 2026-09-21, see
     * this class's own top-level doc): deliberately generous, well above
     * what any single venter ({@code AirconVenterBlockEntity#COLD_AIR_CONSUMED_PER_CYCLE},
     * 25 mB/t) or a full 5-venter chain (125 mB/t) could ever consume, so
     * production is never the bottleneck and a venter's own buffer never
     * starves.
     */
    private static final int BASE_COLD_AIR_RATE = 250;
    /**
     * mB/t of hot_air the motor can actually draw down from its own buffer
     * at {@link #efficiency} = 1 — raised to match {@link #BASE_COLD_AIR_RATE}
     * for the same reason: a full chain can send back up to 250 mB/t of
     * hot_air ({@code AirconVenterBlockEntity#HOT_AIR_PRODUCED_PER_CYCLE},
     * 50 mB/t x up to 5 active venters), and this shouldn't become a NEW
     * bottleneck on the return leg after removing the one on the outbound
     * side.
     */
    private static final int MAX_HOT_AIR_CONSUMPTION = 250;
    /** mB of water produced per mB of hot_air actually consumed. */
    private static final int HOT_AIR_PER_WATER = 4;
    /**
     * Deliberately NOT derived from {@link #MAX_HOT_AIR_CONSUMPTION} anymore
     * (that would now compute 1000 mB, defeating the point) — a flat "one
     * real pipe segment" figure, same as {@code AirconVenterBlockEntity}'s
     * own tanks. At a 250 mB/t production rate this buffer fills in a single
     * tick if nothing's draining it, which is exactly the point: it exists
     * to smooth real flow, not to let the motor coast independently.
     */
    private static final int GAS_TANK_CAPACITY_MB = 250;
    /**
     * Kept separate from the gas tanks (not shrunk to the same tiny
     * "leak" size) — this is a genuine accumulating byproduct meant to be
     * collected, not a pass-through buffer masking a live/dead supply
     * signal the way the gas tanks were. Still shrunk from the original
     * 1000 mB down to the same 250 mB "one real pipe segment" figure used
     * everywhere else in this feature, so it fills/empties on a comparable,
     * observable timescale.
     */
    private static final int WATER_TANK_CAPACITY_MB = 250;
    /** Same reasoning and value as {@code AirconVenterBlockEntity#SUPPLY_GRACE_TICKS} — goggle/particle-display only (see {@link #isReceivingHotAirSupply}'s own doc), loosened to match Create: Pipes n Physics' real bursty delivery cadence. */
    private static final int HOT_AIR_SUPPLY_GRACE_TICKS = 40;

    /** Set by {@link #hotAirIntakeTank}'s own overridden {@code fill()} whenever real hot_air is actually received — see {@link #isReceivingHotAirSupply}. */
    private long lastHotAirFillTick = Long.MIN_VALUE / 2;

    private final FluidTank coldAirTank = new FluidTank(GAS_TANK_CAPACITY_MB) {
        @Override
        public boolean isFluidValid(FluidStack stack) {
            return stack.getFluid() == CIOFluids.COLD_AIR_STILL.get();
        }
    };
    private final FluidTank hotAirIntakeTank = new FluidTank(GAS_TANK_CAPACITY_MB) {
        @Override
        public boolean isFluidValid(FluidStack stack) {
            return stack.getFluid() == CIOFluids.HOT_AIR_STILL.get();
        }

        /**
         * Tracks genuine incoming supply, not just "do I have a buffer" —
         * mirrors {@code AirconVenterBlockEntity#coldAirIntake}'s identical
         * fix, and for the identical reason: the full aircon loop (motor
         * WEST cold_air out -&gt; venter -&gt; motor EAST hot_air in -&gt;
         * water) is only real while every hop is actually live right now —
         * "if the cycle is broken at any point, [this half] cannot function"
         * either, not just coast on whatever's left in the buffer.
         */
        @Override
        public int fill(FluidStack resource, FluidAction action) {
            int filled = super.fill(resource, action);
            if (filled > 0 && action.execute() && level != null) {
                lastHotAirFillTick = level.getGameTime();
            }
            return filled;
        }
    };
    private final FluidTank waterTank = new FluidTank(WATER_TANK_CAPACITY_MB) {
        @Override
        public boolean isFluidValid(FluidStack stack) {
            return stack.getFluid() == Fluids.WATER;
        }
    };

    /**
     * @return whether hot_air has genuinely been received within the last
     * {@link #HOT_AIR_SUPPLY_GRACE_TICKS} — see {@link #hotAirIntakeTank}'s
     * own overridden {@code fill()}.
     * <p>
     * Display/cosmetic information only — the goggle "Hot Air Supply" line,
     * and {@link AirconMotorTopBlockEntity}'s own hot-ember particle gate.
     * Deliberately NOT read by {@link #electricalTick()}'s own water-
     * production math (see that method's own doc for the real bug this
     * avoided — a strict liveness gate on real, bursty Create: Pipes n
     * Physics delivery refused to consume an actively supplied buffer).
     * Public so the top half can read it directly.
     */
    @Override
    public boolean isReceivingHotAirSupply() {
        return level != null && level.getGameTime() - lastHotAirFillTick <= HOT_AIR_SUPPLY_GRACE_TICKS;
    }

    /** EMA smoothing factor for {@link #smoothedPerformance} — ~0.5s to mostly converge, same order as this codebase's other anti-flicker smoothing (see BrassHeaterBlockEntity#HEAT_FRACTION_SMOOTHING). */
    private static final float EFFICIENCY_SMOOTHING = 0.08f;

    /**
     * The real load: a {@link SwitchedWire} directly across the 2 terminals
     * rather than a {@code ProvidedVoltageSourceCoupling} internal node (what
     * this used before) — {@code SwitchedWire#setState(boolean)} is PG's own
     * genuine open/closed-switch mechanic (see {@code RedstoneSwitchBlockEntity}
     * for the precedent this follows), the real electrical meaning of "power
     * disconnected" for Off: closing it to {@code false} truly removes this
     * device from the network (down to PG's own tiny {@code OFF_CONDUCTANCE}
     * floor, not just a very large finite resistance), rather than emulating
     * disconnection with a huge-but-still-technically-present load. Also
     * genuinely supports a dynamic resistance ({@code SwitchedWire#setResistance},
     * confirmed by decompiling the real class — it extends {@code ElectricWire}),
     * so the Low/Mid/Max resistance swap this class already needed still
     * works exactly as before.
     */
    private SwitchedWire loadSwitch;
    /** The 2 terminal nodes {@link #loadSwitch} bridges — read directly for voltage, same pattern {@code RedstoneSwitchBlockEntity} uses for its own poles (no internal coupling node to read through anymore). */
    private IElectricNode positiveTerminal, negativeTerminal;
    /** Low(0)/Mid(1)/Max(2) — right-click-and-hold a spot on the SOUTH face to open Create's ValueSettings slider. */
    private ScrollValueBehaviour powerSetting;
    private float voltage = 0f;
    private boolean reversed = false;
    /** Cached sum of every nearby working venter's own cooled-room size — see {@link #totalCooledRoomSize}. Recomputed only every {@link #VENTER_SCAN_INTERVAL_TICKS}, not on every resistance-provider call (PG's solver can ask far more often than once a game tick). */
    private int cachedCooledRoomSize = 0;
    private long cooledRoomSizeComputedAtTick = -VENTER_SCAN_INTERVAL_TICKS;
    /** 0..1 — the pure voltage-ramp fraction (1 from 120 V up through the danger zone). Drives the actual gas math — deliberately NOT smoothed, so production/consumption stay honest to the real live voltage. */
    private float efficiency = 0f;
    /**
     * {@link #efficiency}, smoothed — use this one for anything the player
     * perceives continuously that should stay purely voltage-driven (blade
     * speed/particles via the top half, wattage's own visible proportion).
     * Deliberately NOT scaled by the current setting (see this class's own
     * top-level doc, "How the Low/Mid/Max setting reaches the venter's
     * flood-fill drop") — a real fan's blade speed doesn't change based on
     * how cold you set the thermostat, only based on power. {@link #smoothedHumIntensity}
     * is the one exception that DOES also factor in the setting.
     */
    private float smoothedPerformance = 0f;
    /**
     * {@link #efficiency} × the current setting's own {@link #SETTING_HUM_FRACTION},
     * smoothed the same way as {@link #smoothedPerformance} but kept as a
     * genuinely separate field — deliberately NOT folded into
     * smoothedPerformance itself, so this doesn't regress the 2026-09-21
     * decision that blade speed/particles stay purely voltage-driven. Feeds
     * ONLY {@link #tickAudio()}: a real motor's own hum audibly differs
     * between a gentle Low hum and a straining Max roar even at the exact
     * same voltage, the same way its blade speed and gas output don't.
     */
    private float smoothedHumIntensity = 0f;
    /**
     * Smoothed toward {@link #SETTING_HUM_PITCH}'s own current-setting entry
     * — same EMA cadence as {@link #smoothedHumIntensity} (see
     * {@link #electricalTick()}), so switching settings fades the pitch over
     * about a second instead of snapping. Deliberately a SEPARATE field from
     * {@link #smoothedHumIntensity} now (that field only ever fed a 0..1
     * continuous pitch multiplier, which is exactly what caused the "no
     * difference between Low/Mid" bug — see {@link #SETTING_HUM_PITCH}'s own
     * doc): this one tracks a real target PITCH value directly, so it can be
     * pinned solidly inside whichever PG SoundScapes RangeGroup bucket the
     * current setting is meant to occupy.
     */
    private float smoothedHumPitch = SETTING_HUM_PITCH[1];

    public AirconMotorBottomBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        powerSetting = new PowerSettingScrollValueBehaviour(
                Component.translatable("createinteroperable.aircon_power_setting"), this, new PowerSettingSlot())
                .between(0, 3)
                .withFormatter(i -> SETTING_LABELS[Mth.clamp(i, 0, 3)]);
        behaviours.add(powerSetting);
    }

    /**
     * Create's own {@code ScrollValueBehaviour#createBoard} hardcodes the
     * on-screen row label to the raw number and ignores {@code withFormatter}
     * — same real bug documented on {@code InteroperableSmallBlockEntity}'s
     * own {@code DirectionScrollValueBehaviour}. Overriding {@code createBoard}
     * is the only way to show "Low ❄ / Mid ❄❄ / Max ❄❄❄" instead of "0/1/2".
     */
    private static class PowerSettingScrollValueBehaviour extends ScrollValueBehaviour {
        PowerSettingScrollValueBehaviour(Component label, SmartBlockEntity be, ValueBoxTransform slot) {
            super(label, be, slot);
        }

        @Override
        public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
            return new ValueSettingsBoard(label, max, 1, ImmutableList.of(Component.literal("Cooling")),
                    new ValueSettingsFormatter(vs -> Component.literal(SETTING_LABELS[Mth.clamp(vs.value(), 0, 3)])));
        }
    }

    private int getPowerSettingIndex() {
        return powerSetting == null ? SETTING_RESISTANCE.length - 1 : Mth.clamp(powerSetting.getValue(), 0, 3);
    }

    /** @return whether the slider is currently set to "Off" — see {@link #electricalTick()}, which unconditionally zeroes {@link #efficiency} whenever this is true, regardless of whatever voltage the coupling happens to sense. */
    private boolean isOff() {
        return getPowerSettingIndex() == OFF_INDEX;
    }

    /**
     * @return whether the slider is on any setting OTHER than Off — the
     * switch's own position, not whether voltage/efficiency is currently
     * present. Public so {@link AirconMotorTopBlockEntity} can read it
     * directly (same pattern as {@link #getSmoothedPerformance()}) to drive
     * the top half's vent flaps: those are meant to open "if the motor is on,
     * in any setting, as long as it's not Off" — a real AC's vent flaps
     * track the switch position, not the live current draw, so this
     * deliberately does NOT read {@link #efficiency}/{@link #voltage}.
     */
    @Override
    public boolean isPoweredOn() {
        return !isOff();
    }

    /**
     * @return how many °C a venter fed from this motor should treat as its
     * own cooling ceiling right now — {@link AirconVenterBlockEntity} reads
     * this directly (see that class's own {@code currentDropC()}). This is
     * the setting's real effect now, not gas production (see this class's
     * own top-level doc for the redesign).
     */
    @Override
    public float getSettingDropC() {
        return SETTING_DROP_C[getPowerSettingIndex()];
    }

    /**
     * @return the current setting's own {@link #SETTING_HUM_FRACTION} (Off=0,
     * Low=0.25, Mid=0.5, Max=1) — the same relative "how hard is it working"
     * number {@link #tickAudio()} uses for the hum, reused publicly so
     * {@link AirconVenterBlockEntity} can scale its own particle speed by the
     * same setting, same "no stored link, just scan nearby chunks" approach
     * {@link #getSettingDropC()} already uses in the opposite direction.
     */
    @Override
    public float getSettingIntensityFraction() {
        return SETTING_HUM_FRACTION[getPowerSettingIndex()];
    }

    /**
     * Real, dynamic load resistance: the setting's own base value (see
     * {@link #SETTING_RESISTANCE}) divided down (= more power drawn) by
     * TWO independent real-world factors, each a genuine reason a real AC
     * compressor works harder:
     * <ul>
     *     <li>the real ambient temperature at this position climbing above
     *     {@link #AMBIENT_NEUTRAL_C} — "based on the biome and world
     *     temperature where the motor_bottom is, we need to tweak power
     *     consumption";</li>
     *     <li>how many blocks are actually being cooled right now (see
     *     {@link #totalCooledRoomSize}) — "even more wattage depending on
     *     how many blocks are being cooled (flood fill)".</li>
     * </ul>
     * Ambient reading is the same dual-path as {@code BrassHeaterBlockEntity#isCold}
     * (real Cold Sweat calculation when present, else the shared biome-based
     * approximation) so this needs no separate Cold-Sweat-only dependency of
     * its own.
     */
    private float currentResistance() {
        float base = SETTING_RESISTANCE[getPowerSettingIndex()];
        float ambientC = ambientTemperatureC();
        float hotExcess = Math.max(0f, ambientC - AMBIENT_NEUTRAL_C);
        float tempFactor = 1f + hotExcess * WATTAGE_INCREASE_PER_DEGREE_ABOVE_NEUTRAL;
        float roomFactor = 1f + totalCooledRoomSize() * WATTAGE_INCREASE_PER_COOLED_BLOCK;
        // Neither tempFactor nor roomFactor is bounded above (a hot enough
        // biome or a large enough cooled room can push either arbitrarily
        // high), so without this floor the resulting current has no ceiling
        // either — see MIN_RESISTANCE_OHMS's own doc for the real PG wire
        // math this is calibrated against.
        return Math.max(base / (tempFactor * roomFactor), MIN_RESISTANCE_OHMS);
    }

    /**
     * @return the cached sum of {@code AirconVenterBlockEntity#getCooledRoomSize()}
     * across every venter found within {@link #VENTER_SCAN_CHUNK_RADIUS}
     * chunks, refreshed at most once every {@link #VENTER_SCAN_INTERVAL_TICKS}.
     * Same "scan nearby chunks' block entities" technique the Cold-Sweat-only
     * mixins already use — there is deliberately no stored motor→venter link
     * (see this class's own top-level doc), so this is the closest thing to
     * one: a real (if approximate) nearby-presence check, not a hardcoded
     * pairing. A venter belonging to some unrelated nearby aircon system
     * would also count — accepted, same emergent-not-hardcoded tradeoff
     * already made for how the setting itself reaches a venter's own drop.
     */
    private int totalCooledRoomSize() {
        if (level == null) {
            return 0;
        }
        long gameTime = level.getGameTime();
        if (gameTime - cooledRoomSizeComputedAtTick < VENTER_SCAN_INTERVAL_TICKS) {
            return cachedCooledRoomSize;
        }
        cooledRoomSizeComputedAtTick = gameTime;
        int total = 0;
        ChunkPos centerChunk = new ChunkPos(worldPosition);
        for (int x = -VENTER_SCAN_CHUNK_RADIUS; x <= VENTER_SCAN_CHUNK_RADIUS; x++) {
            for (int z = -VENTER_SCAN_CHUNK_RADIUS; z <= VENTER_SCAN_CHUNK_RADIUS; z++) {
                int chunkX = centerChunk.x + x;
                int chunkZ = centerChunk.z + z;
                if (!level.hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                ChunkAccess chunk = level.getChunk(chunkX, chunkZ);
                for (BlockPos bePos : chunk.getBlockEntitiesPos()) {
                    if (chunk.getBlockEntity(bePos) instanceof AirconVenterBlockEntity venter) {
                        total += venter.getCooledRoomSize();
                    }
                }
            }
        }
        cachedCooledRoomSize = total;
        return total;
    }

    private float ambientTemperatureC() {
        if (level == null) {
            return AMBIENT_NEUTRAL_C;
        }
        if (ColdSweatCompat.present()) {
            return (float) ColdSweatWorldTemp.getWorldTemperatureC(level, worldPosition);
        }
        return BrassHeaterBlockEntity.ambientTemperatureC(level, worldPosition);
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        builder.setTerminalCount(2);
        positiveTerminal = builder.terminalNode(AirconMotorBottomBlock.POSITIVE_TERMINAL);
        negativeTerminal = builder.terminalNode(AirconMotorBottomBlock.NEGATIVE_TERMINAL);
        // A plain resistive load, not a bridge — closed (true) as a safe
        // initial state; #electricalTick corrects both the resistance and
        // the open/closed state to the real setting on the very next tick
        // regardless (same brief-startup-mismatch tolerance the old
        // hardcoded-index initial resistance already had).
        loadSwitch = builder.connectSwitch(SETTING_RESISTANCE[SETTING_RESISTANCE.length - 1],
                positiveTerminal, negativeTerminal, true);
    }

    @Override
    public void electricalTick() {
        super.electricalTick();
        if (loadSwitch == null) {
            return;
        }
        boolean off = isOff();
        // The real "power disconnected" mechanic — see #loadSwitch's own
        // doc. false genuinely opens the circuit (PG's own OFF_CONDUCTANCE
        // floor, confirmed by decompiling SwitchedWire), not just a very
        // large finite resistance.
        loadSwitch.setState(!off);
        if (!off) {
            loadSwitch.setResistance(currentResistance());
        }
        double potentialDifference = positiveTerminal.getVoltage() - negativeTerminal.getVoltage();
        voltage = (float) Math.abs(potentialDifference);
        reversed = potentialDifference < 0;
        // Forced to 0 while Off regardless of whatever voltage the terminals
        // sense — loadSwitch being genuinely open (see its own doc) already
        // means almost no current flows, but its terminals can still float
        // close to the network's own open-circuit voltage (same as a
        // voltmeter reading), so this stays an explicit, independent belt-
        // and-suspenders check rather than relying on the sensed voltage
        // alone to reach 0. Overvoltage smoke/explosion below still uses the
        // raw #voltage field, unaffected by this — a real switched-off
        // appliance can still be damaged by a badly overvolted line it's
        // still physically connected to.
        efficiency = off ? 0f : Mth.clamp(voltage / OPTIMAL_VOLTS, 0f, 1f);
        // A plain exponential moving average, not LerpedFloat: this only
        // feeds cosmetic output (particles/blade speed via the top half),
        // never network-synced state, so each side smoothing its own
        // last-known raw value independently is fine. Tracks efficiency
        // directly, not efficiency x settingFraction — the setting doesn't
        // throttle the motor's own mechanical output at all (see this
        // class's own top-level doc for the redesign), so blade speed stays
        // purely voltage-driven, same as a real fan's motor.
        smoothedPerformance += (efficiency - smoothedPerformance) * EFFICIENCY_SMOOTHING;
        // The hum's own separate target — see #smoothedHumIntensity's own
        // doc for why this stays independent of smoothedPerformance above.
        float humTarget = efficiency * SETTING_HUM_FRACTION[getPowerSettingIndex()];
        smoothedHumIntensity += (humTarget - smoothedHumIntensity) * EFFICIENCY_SMOOTHING;
        // The hum's own target PITCH — see SETTING_HUM_PITCH's own doc for
        // why this has to be a separate, real pitch value rather than
        // derived from smoothedHumIntensity's 0..1 fraction.
        float pitchTarget = SETTING_HUM_PITCH[getPowerSettingIndex()];
        smoothedHumPitch += (pitchTarget - smoothedHumPitch) * EFFICIENCY_SMOOTHING;

        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof AirconMotorBottomBlock)) {
            return;
        }

        if (level instanceof ServerLevel serverLevel && tickOvervoltage(serverLevel)) {
            // Just destroyed itself — nothing further to do this tick.
            return;
        }

        int coldProduced = Math.round(BASE_COLD_AIR_RATE * efficiency);
        if (coldProduced > 0) {
            coldAirTank.fill(new FluidStack(CIOFluids.COLD_AIR_STILL.get(), coldProduced), IFluidHandler.FluidAction.EXECUTE);
        }

        // Buffer + efficiency alone gate real water production —
        // isReceivingHotAirSupply is goggle/particle-display information
        // only now, NOT a function gate (see that method's own doc): the
        // same real bug as AirconVenterBlockEntity's identical cold_air
        // gate — Create: Pipes n Physics delivers fluid in real bursts, not
        // smoothly every tick, and a strict liveness gate here refused to
        // consume an actively, continuously supplied buffer. The tank is
        // now small enough (a few ticks' worth) that it can't meaningfully
        // coast on its own, so the honest fix was demoting the liveness
        // check rather than tightening it further.
        int desiredConsume = Math.round(MAX_HOT_AIR_CONSUMPTION * efficiency);
        if (desiredConsume > 0) {
            FluidStack drained = hotAirIntakeTank.drain(desiredConsume, IFluidHandler.FluidAction.EXECUTE);
            if (!drained.isEmpty()) {
                int waterProduced = drained.getAmount() / HOT_AIR_PER_WATER;
                if (waterProduced > 0) {
                    waterTank.fill(new FluidStack(Fluids.WATER, waterProduced), IFluidHandler.FluidAction.EXECUTE);
                }
            }
        }

        if ((level.getGameTime() & 3) == 0) {
            notifyUpdate();
        }
    }

    /** Below this, the motor "works properly" — performance just naturally follows the voltage ramp / setting, no separate handling needed. At/above it, overvoltage smoke starts. */
    private static final float SMOKE_THRESHOLD_VOLTS = 149f;
    /** At/above this, the motor destroys itself — a cosmetic explosion only, see {@link #explode}. */
    private static final float EXPLODE_THRESHOLD_VOLTS = 160f;

    /**
     * @return true if the motor just exploded (destroyed itself) this tick —
     * callers must stop touching {@code this} entity's fields/level
     * afterward. Between {@link #SMOKE_THRESHOLD_VOLTS} and
     * {@link #EXPLODE_THRESHOLD_VOLTS} it keeps running at full performance
     * (still clamped at 1, see {@link #efficiency}) while visibly smoking as
     * a warning.
     */
    private boolean tickOvervoltage(ServerLevel serverLevel) {
        if (voltage >= EXPLODE_THRESHOLD_VOLTS) {
            explode(serverLevel);
            return true;
        }
        if (voltage >= SMOKE_THRESHOLD_VOLTS) {
            tickSmoke(serverLevel);
        }
        return false;
    }

    private void tickSmoke(ServerLevel serverLevel) {
        float dangerFraction = Mth.clamp(
                (voltage - SMOKE_THRESHOLD_VOLTS) / (EXPLODE_THRESHOLD_VOLTS - SMOKE_THRESHOLD_VOLTS), 0f, 1f);
        int interval = Math.max(2, Math.round(24 * (1f - dangerFraction)) + 2);
        if ((serverLevel.getGameTime() + worldPosition.hashCode()) % interval != 0) {
            return;
        }
        int count = 1 + Math.round(dangerFraction * 3);
        for (int i = 0; i < count; i++) {
            double x = worldPosition.getX() + 0.5 + (serverLevel.random.nextDouble() - 0.5) * 0.6;
            double y = worldPosition.getY() + 1.0;
            double z = worldPosition.getZ() + 0.5 + (serverLevel.random.nextDouble() - 0.5) * 0.6;
            double riseSpeed = 0.02 + dangerFraction * 0.03;
            serverLevel.sendParticles(ParticleTypes.SMOKE, x, y, z, 0, 0.0, riseSpeed, 0.0, 1.0);
        }
    }

    /**
     * Cosmetic-only: real explosion particle + sound, then both halves of
     * the multiblock are removed via {@code removeBlock} (a plain
     * set-to-air, same as vanilla's own block-removal path — no
     * {@code Level#explode} call anywhere here) — "without doing damage"
     * means never touching entity damage or block-breaking at all, not just
     * tuning an explosion's power down to 0.
     */
    private void explode(ServerLevel serverLevel) {
        BlockPos bottomPos = worldPosition.immutable();
        BlockPos topPos = bottomPos.above();
        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                bottomPos.getX() + 0.5, bottomPos.getY() + 1.0, bottomPos.getZ() + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
        // Quartered along with every other aircon_motor sound below (see
        // #tickAudio's own comment) — "way too loud" per direct feedback.
        serverLevel.playSound(null, bottomPos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS,
                4.0f * 0.25f, 0.9f + serverLevel.random.nextFloat() * 0.2f);
        serverLevel.removeBlock(bottomPos, false);
        if (serverLevel.getBlockState(topPos).getBlock() instanceof AirconMotorTopBlock) {
            serverLevel.removeBlock(topPos, false);
        }
    }

    /**
     * Same hum call shape every other device in this codebase uses
     * (SoundScapes.play(AmbienceGroup.HUM, pos, pitch, volume)) — volume
     * scales with {@link #smoothedHumIntensity} (voltage ramp x setting, see
     * that field's own doc), while pitch now comes from the genuinely
     * separate {@link #smoothedHumPitch} (a real target pitch per setting,
     * see {@link #SETTING_HUM_PITCH}'s own doc for why this had to change:
     * the old single-fraction formula put Low and Mid in the SAME PG
     * SoundScapes pitch bucket, making them provably pitch-identical no
     * matter what — this was the actual bug behind "no humming difference
     * between Low/Mid/Max"). Silent while idle (or Off — {@link #SETTING_HUM_FRACTION}'s
     * own 0 already zeroes the intensity target this smooths toward) rather
     * than a constant idle drone.
     */
    @Override
    public void tickAudio() {
        if (smoothedHumIntensity <= 0.01f) {
            return;
        }
        // Quartered per direct feedback that the aircon_motor's sounds (this
        // hum, the top half's own hum, and both electrical variants' failure
        // explosion) were "way too loud" — same 0.25x applied everywhere
        // across the whole block, not just here.
        float volume = (0.2f + 0.8f * smoothedHumIntensity) * 0.25f;
        SoundScapes.play(SoundScapes.AmbienceGroup.HUM, worldPosition, smoothedHumPitch, volume);
    }

    public float getEfficiency() {
        return efficiency;
    }

    @Override
    public float getSmoothedPerformance() {
        return smoothedPerformance;
    }

    public float getVoltage() {
        return voltage;
    }

    public boolean isReversed() {
        return reversed;
    }

    @Nullable
    public IFluidHandler getFluidHandler(@Nullable Direction side) {
        if (side == null) {
            return null;
        }
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof AirconMotorBottomBlock)) {
            return null;
        }
        // Water moved from the FACING-relative NORTH face to a fixed DOWN
        // (bottom) face per the user's own request — hot_air arriving back
        // is what determines WHETHER water gets made at all (see
        // #electricalTick's #isReceivingHotAirSupply gate), not where it
        // comes out; DOWN needs no rotation remap since it's vertical,
        // unaffected by the block's horizontal FACING.
        if (side == Direction.DOWN) {
            return waterTank;
        }
        Direction facing = state.getValue(AirconMotorBottomBlock.FACING);
        Direction coldOut = AirconMotorBottomBlock.rotate(reversed ? Direction.EAST : Direction.WEST, facing);
        if (side == coldOut) {
            return coldAirTank;
        }
        Direction hotIn = AirconMotorBottomBlock.rotate(reversed ? Direction.WEST : Direction.EAST, facing);
        if (side == hotIn) {
            return hotAirIntakeTank;
        }
        return null;
    }

    /** NORTH=0, EAST=90, SOUTH=180, WEST=270 — same convention as {@link AirconMotorBottomBlock}'s own (private) clockwiseSteps, duplicated here since the slot classes below need it and the Block's copy isn't visible from here. */
    private static int angleDegrees(Direction facing) {
        return switch (facing) {
            case NORTH -> 0;
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }

    /** Rotates a block-local (0..1) offset about the block's own vertical center by a multiple of 90° — same pattern as {@code InteroperableSmallBlock#rotateY}. */
    private static Vec3 rotateY(Vec3 base, int angle) {
        double dx = base.x - 0.5, dz = base.z - 0.5;
        return switch (angle) {
            case 90 -> new Vec3(0.5 - dz, base.y, 0.5 + dx);
            case 180 -> new Vec3(0.5 - dx, base.y, 0.5 - dz);
            case 270 -> new Vec3(0.5 + dz, base.y, 0.5 - dx);
            default -> base;
        };
    }

    /**
     * Approximate placement on the SOUTH face (a face not already carrying a
     * pipe connection — DOWN/WEST/EAST are water/cold/hot) —
     * {@code aircon_motor_bottom.json} has no dedicated "slider plate"
     * element to derive exact coordinates from yet, unlike the Interoperable
     * Transformer's own slot, so this is a reasonable center-of-face
     * placement rather than a model-derived one; safe to retune visually
     * once/if the model grows a real decal there.
     */
    private static final Vec3 POWER_SLOT_BASE = VecHelper.voxelSpace(8, 8, 15.1);

    private static class PowerSettingSlot extends ValueBoxTransform {
        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            if (!(state.getBlock() instanceof AirconMotorBottomBlock)) {
                return POWER_SLOT_BASE;
            }
            return rotateY(POWER_SLOT_BASE, angleDegrees(state.getValue(AirconMotorBottomBlock.FACING)));
        }

        @Override
        public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
            int angle = state.getBlock() instanceof AirconMotorBottomBlock
                    ? angleDegrees(state.getValue(AirconMotorBottomBlock.FACING)) : 0;
            // South-facing decal at the model's own FACING=NORTH default (angle 0) — 180° from the "faces north" baseline, then rotated further with the block's own current facing.
            TransformStack.of(ms).rotateYDegrees(180 + angle);
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.text("AC Unit Motor (bottom)")
                .style(ChatFormatting.WHITE)
                .forGoggles(tooltip);

        BlockState state = getBlockState();
        boolean assembled = state.getBlock() instanceof AirconMotorBottomBlock
                && state.getValue(AirconMotorBottomBlock.ASSEMBLED);
        CreateLang.text("Assembled: ")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.text(assembled ? "Yes" : "No")
                        .style(assembled ? ChatFormatting.GREEN : ChatFormatting.RED))
                .forGoggles(tooltip, 1);

        boolean overvoltage = voltage >= SMOKE_THRESHOLD_VOLTS;
        CreateLang.text("Voltage: ")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.number(Math.round(voltage))
                        .text(" V" + (reversed ? " (reversed polarity)" : ""))
                        .style(overvoltage ? ChatFormatting.RED : ChatFormatting.AQUA))
                .forGoggles(tooltip, 1);

        boolean off = isOff();
        CreateLang.text("Setting: ")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.text(SETTING_LABELS[getPowerSettingIndex()])
                        .text(off ? " (power cut)" : " (venter cooling ceiling: " + Math.round(getSettingDropC()) + "°C)")
                        .style(off ? ChatFormatting.DARK_GRAY : ChatFormatting.BLUE))
                .forGoggles(tooltip, 1);

        // currentResistance() isn't meaningful while off (loadSwitch is
        // genuinely open, see its own doc) — shown as "Disconnected" instead
        // of whatever inert filler SETTING_RESISTANCE[OFF_INDEX] holds.
        float resistance = currentResistance();
        float watts = off || resistance <= 0f ? 0f : voltage * voltage / resistance;
        CreateLang.text("Resistance: ")
                .style(ChatFormatting.GRAY)
                .add(off ? CreateLang.text("Disconnected").style(ChatFormatting.DARK_GRAY)
                        : CreateLang.number(Math.round(resistance * 10) / 10f).text(" Ω").style(ChatFormatting.GOLD))
                .forGoggles(tooltip, 1);

        CreateLang.text("Power Draw: ")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.number(Math.round(watts)).text(" W").style(ChatFormatting.GOLD))
                .forGoggles(tooltip, 1);

        CreateLang.text("Efficiency: ")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.number(Math.round(efficiency * 100)).text("%").style(ChatFormatting.GREEN))
                .forGoggles(tooltip, 1);

        CreateLang.text("Cold Air Out: ")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.number(Math.round(BASE_COLD_AIR_RATE * efficiency))
                        .text(" / " + BASE_COLD_AIR_RATE + " mB/t")
                        .style(ChatFormatting.AQUA))
                .forGoggles(tooltip, 1);
        CreateLang.builder()
                .add(CreateLang.number(coldAirTank.getFluidAmount())
                        .text(" / " + coldAirTank.getCapacity() + " mB buffered")
                        .style(ChatFormatting.DARK_GRAY))
                .forGoggles(tooltip, 1);

        boolean receivingHotAir = isReceivingHotAirSupply();
        CreateLang.text("Hot Air In: ")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.number(Math.round(MAX_HOT_AIR_CONSUMPTION * efficiency))
                        .text(" / " + MAX_HOT_AIR_CONSUMPTION + " mB/t max")
                        .style(ChatFormatting.GOLD))
                .forGoggles(tooltip, 1);
        CreateLang.builder()
                .add(CreateLang.text(receivingHotAir ? "Pipe delivering" : "Nothing recently received")
                        .style(receivingHotAir ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY))
                .forGoggles(tooltip, 1);

        int waterPercent = waterTank.getCapacity() > 0
                ? Math.round(waterTank.getFluidAmount() / (float) waterTank.getCapacity() * 100) : 0;
        CreateLang.text("Water Buffer: ")
                .style(ChatFormatting.GRAY)
                .add(CreateLang.number(waterTank.getFluidAmount())
                        .text(" / " + waterTank.getCapacity() + " mB (" + waterPercent + "%)")
                        .style(ChatFormatting.BLUE))
                .forGoggles(tooltip, 1);

        if (overvoltage) {
            CreateLang.text("Warning: ")
                    .style(ChatFormatting.RED)
                    .add(CreateLang.text("Overvoltage — smoking").style(ChatFormatting.RED))
                    .forGoggles(tooltip, 1);
        }

        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("ColdAirTank", coldAirTank.writeToNBT(registries, new CompoundTag()));
        tag.put("HotAirIntakeTank", hotAirIntakeTank.writeToNBT(registries, new CompoundTag()));
        tag.put("WaterTank", waterTank.writeToNBT(registries, new CompoundTag()));
        tag.putFloat("Voltage", voltage);
        tag.putBoolean("Reversed", reversed);
        tag.putFloat("Efficiency", efficiency);
        tag.putFloat("SmoothedPerformance", smoothedPerformance);
        tag.putFloat("SmoothedHumIntensity", smoothedHumIntensity);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        coldAirTank.readFromNBT(registries, tag.getCompound("ColdAirTank"));
        hotAirIntakeTank.readFromNBT(registries, tag.getCompound("HotAirIntakeTank"));
        waterTank.readFromNBT(registries, tag.getCompound("WaterTank"));
        voltage = tag.getFloat("Voltage");
        reversed = tag.getBoolean("Reversed");
        efficiency = tag.getFloat("Efficiency");
        smoothedPerformance = tag.getFloat("SmoothedPerformance");
        smoothedHumIntensity = tag.getFloat("SmoothedHumIntensity");
    }
}
