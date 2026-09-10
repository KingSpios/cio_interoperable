package com.cio.createinteroperable;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.sim.node.ProvidedVoltageSourceCoupling;
import org.patryk3211.powergrid.utility.sound.SoundScapes;

import java.util.List;

/**
 * Functional half of {@link InteroperableDoubleCouplerBlock} — the current-carrying
 * PG&harr;CEE bridge. Electrically identical to what {@link InteroperableCouplerBlockEntity}
 * did before the single coupler was repurposed as a to-ground signal relay: a real
 * two-terminal winding on each side, the source side sensing at
 * {@link InteroperableDevice#SENSE_RESISTANCE} and the sink side delivering at
 * {@link InteroperableDevice#DELIVERY_RESISTANCE}, so a downstream load draws real
 * current and reflects back onto the source grid.
 *
 * Flow control ({@code mode}): 0 = {@code CEE -> CPG}, 1 = {@code OFF}
 * (the default a freshly placed coupler starts in), 2 = {@code CPG -> CEE} (PG
 * source). These ordinals only matter for two things: the {@code ScrollValue}
 * NBT int persisted per coupler, and left-to-right position in Create's own
 * {@code ValueSettingsScreen} popup (column {@code == value}, column 0 pinned
 * to the left of that popup's track — confirmed by reading
 * {@code ValueSettingsScreen#getCoordinateOfValue}; there's no separate flag to
 * mirror that layout, the ordinal assignment IS the layout). 0/2 were swapped
 * 2026-09-06 purely to flip that popup track left-to-right to match the real
 * block (CPG south, CEE north, scroll slot on the east face) — everything
 * electrical ({@link #pgIsSource()}) and the on-model needle
 * ({@link #targetAngle()}) key off the named {@code MODE_*} constants, not the
 * raw ordinal, so neither changed behavior. (Already-placed couplers' saved
 * {@code ScrollValue} will read as the other direction after this change —
 * only the freshly-authored ordinal mapping is "correct" going forward.) The
 * gauge needle eases toward {@link #targetAngle()} at
 * {@value InteroperableCouplerBlockEntity#STEP_DEG_PER_TICK}
 * deg/tick; a full +45&harr;-45 reversal is 60 ticks (3&nbsp;s) and transfer is
 * hard-cut ({@link InteroperableDevice#setTransferEnabled}) until the needle
 * settles &mdash; so every direction change costs ~3&nbsp;s of dead time. Four
 * needle plates (two per unit) are drawn by {@link InteroperableDoubleCouplerRenderer}.
 */
public class InteroperableDoubleCouplerBlockEntity extends ElectricBlockEntity implements IHaveGoggleInformation {

    private static final float SETTLE_EPS = 0.5f;

    /** EMA weight for the reflected source resistance (0..1); higher = snappier, lower = steadier. */
    private static final float REFLECT_SMOOTHING = 0.5f;

    /** Goggle-tooltip lang key prefix. */
    private static final String GK = "createinteroperable.goggle.coupler.";

    public static final int MODE_CEE_TO_CPG = 0;
    public static final int MODE_OFF = 1;
    public static final int MODE_CPG_TO_CEE = 2;

    /**
     * The three flow options. {@link ScrollOptionBehaviour} maps this enum
     * one-value-per-scroll-step (enum ordinal == popup column, column 0 pinned
     * left in Create's {@code ValueSettingsScreen}), and its {@code createBoard()}
     * uses {@code ScrollOptionSettingsFormatter} — the value-settings screen
     * then runs in "icon mode": one auto-sized panel that fits the widest
     * option name and shows a single centred label instead of cramped
     * overlapping bubbles. Ordinals must stay aligned with MODE_CEE_TO_CPG /
     * MODE_OFF / MODE_CPG_TO_CEE.
     */
    public enum CIOFlowMode implements INamedIconOptions {
        CEE_TO_CPG(AllIcons.I_MTD_RIGHT, "cee_cpg"),
        OFF(AllIcons.I_STOP, "off"),
        CPG_TO_CEE(AllIcons.I_MTD_LEFT, "cpg_cee");

        private final AllIcons icon;
        private final String key;

        CIOFlowMode(AllIcons icon, String key) {
            this.icon = icon;
            this.key = "createinteroperable.coupler.mode." + key;
        }

        @Override
        public AllIcons getIcon() {
            return icon;
        }

        @Override
        public String getTranslationKey() {
            return key;
        }
    }

    private ProvidedVoltageSourceCoupling coupling;
    private InteroperableDevice ceeDevice;
    private ScrollValueBehaviour mode;

    // Init to the OFF resting angle (needle straight up) — the default mode.
    private float pointerAngle = 0f;
    private float pointerAnglePrev = 0f;
    private float lastTransferCurrent = 0f;
    /** Volts across the bridge right now (source sensed / sink delivered). Synced for the goggle readout. */
    private float lastTransferVoltage = 0f;

    /**
     * Server-side, EMA-smoothed. What the SOURCE side of the bridge presents as
     * its series resistance: {@code V_sensed / I_delivered}, so the source grid
     * feels the sink-side load reflected through (1:1, converges to the sink's
     * load resistance). Sits at {@link InteroperableDevice#SENSE_RESISTANCE} when
     * nothing is drawing on the sink side (pure voltmeter). Read by the PG-side
     * resistance provider when PG is source, and pushed to {@link #ceeDevice} for
     * the mirror case.
     */
    private float reflectedSourceResistance = InteroperableDevice.SENSE_RESISTANCE;

    public InteroperableDoubleCouplerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        mode = new FlowModeBehaviour(
                Component.translatable("createinteroperable.coupler.direction"), this, new HingeTipSlot());
        // Default position = OFF. FlowModeBehaviour.read() keeps this for a
        // freshly placed coupler; already-placed ones restore from NBT.
        mode.value = MODE_OFF;
        behaviours.add(mode);
    }

    /** Plain-text label for the goggle overlay (the value-settings screen uses the enum's own icon+key). */
    private static String modeLabel(int m) {
        return switch (m) {
            case MODE_OFF -> "OFF";
            case MODE_CEE_TO_CPG -> "CEE → CPG";
            default -> "CPG → CEE";
        };
    }

    private static class FlowModeBehaviour extends ScrollOptionBehaviour<CIOFlowMode> {
        FlowModeBehaviour(Component label, SmartBlockEntity be, ValueBoxTransform slot) {
            super(CIOFlowMode.class, label, be, slot);
        }

        @Override
        public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
            int fallback = value;               // constructed default (OFF)
            super.read(nbt, registries, clientPacket);
            if (!nbt.contains("ScrollValue")) {
                value = fallback;               // fresh placement -> keep OFF, don't snap to 0
            } else if (value < MODE_CEE_TO_CPG || value > MODE_CPG_TO_CEE) {
                // ScrollValueBehaviour.read() assigns nbt.getInt("ScrollValue")
                // with no clamp, and ScrollOptionBehaviour.get() indexes a bare
                // options[value]. A value persisted by an earlier build whose
                // slider had a wider range (the needle-angle math sits in 0..45,
                // so a stale ~40 is exactly what that looks like) — or any
                // external NBT edit — would otherwise crash ScrollValueRenderer
                // on the client the moment the player looks at the block. Snap
                // anything out of range back to the safe OFF position.
                value = MODE_OFF;
            }
        }
    }

    private int getMode() {
        return mode == null ? MODE_OFF : mode.getValue();
    }

    /**
     * Keyed off the named {@code MODE_*} constants, not raw ordinal arithmetic
     * ({@code getMode()*45} would have re-tied the needle to whichever ordinal
     * a mode happens to have, which changed under the popup-track reorder
     * above) — so the on-model needle's swing direction per mode is exactly
     * what it always was, untouched by that reorder.
     */
    private float targetAngle() {
        return switch (getMode()) {
            case MODE_CPG_TO_CEE -> 45f;
            case MODE_CEE_TO_CPG -> -45f;
            default -> 0f; // OFF
        };
    }

    private boolean settled() {
        return Math.abs(pointerAngle - targetAngle()) <= SETTLE_EPS;
    }

    private boolean transferEnabled() {
        return getMode() != MODE_OFF && settled();
    }

    private boolean pgIsSource() {
        return getMode() == MODE_CPG_TO_CEE;
    }

    private static float approach(float cur, float target, float step) {
        if (cur < target) return Math.min(cur + step, target);
        if (cur > target) return Math.max(cur - step, target);
        return target;
    }

    @Override
    public void tick() {
        super.tick();
        pointerAnglePrev = pointerAngle;
        pointerAngle = approach(pointerAngle, targetAngle(), InteroperableCouplerBlockEntity.STEP_DEG_PER_TICK);
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        builder.setTerminalCount(2);
        coupling = builder.addInternalNode(ProvidedVoltageSourceCoupling.class,
                builder.terminalNode(0), builder.terminalNode(1), InteroperableDevice.SENSE_RESISTANCE);
        coupling.setVoltageProvider(() ->
                transferEnabled() && !pgIsSource() && ceeDevice != null ? ceeDevice.getLastVoltage() : 0.0);
        coupling.setResistanceProvider(() ->
                !transferEnabled() ? InteroperableDevice.SENSE_RESISTANCE
                        : pgIsSource() ? reflectedSourceResistance          // PG is source: reflect the CEE-side load
                        : InteroperableDevice.DELIVERY_RESISTANCE);          // PG is sink: drive the PG-side load
    }

    @Override
    public void electricalTick() {
        super.electricalTick();
        if (!(level instanceof ServerLevel serverLevel) || coupling == null)
            return;

        if (ceeDevice == null || !ceeDevice.isValid())
            ceeDevice = DevicesSavedData.load(serverLevel).getDevice(worldPosition, InteroperableDevice.class);
        if (ceeDevice == null)
            return;

        boolean enabled = transferEnabled();
        boolean pgSource = pgIsSource();
        ceeDevice.setPgIsSource(pgSource);
        ceeDevice.setTransferEnabled(enabled);

        double pgVolts = coupling.getPositive().getVoltage() - coupling.getNegative().getVoltage();
        if (enabled && pgSource)
            ceeDevice.setPowerGridVoltage(pgVolts);

        // Reflected impedance: the SOURCE side presents R = V_sensed / I_delivered
        // instead of a fixed near-open SENSE_RESISTANCE, so a sink-side load pulls
        // real current back through the source grid (1:1, so R converges to the
        // sink-side load resistance; power in ~= power out). Degrades to a pure
        // voltmeter at no load (I -> 0). Clamped to [DELIVERY, SENSE] and lightly
        // EMA-smoothed against co-sim lag; the fixed point R_source = R_load is
        // voltage-independent, so it settles rather than oscillating.
        float target = InteroperableDevice.SENSE_RESISTANCE;
        if (enabled) {
            double vSensed = pgSource ? Math.abs(pgVolts) : Math.abs(ceeDevice.getLastVoltage());
            double iSink = pgSource ? ceeDevice.getLastCurrent() : Math.abs(coupling.getCurrent());
            if (iSink > 1.0e-4) {
                double r = vSensed / iSink;
                target = (float) Math.max(InteroperableDevice.DELIVERY_RESISTANCE,
                        Math.min(InteroperableDevice.SENSE_RESISTANCE, r));
            }
        }
        reflectedSourceResistance += (target - reflectedSourceResistance) * REFLECT_SMOOTHING;
        ceeDevice.setSourceResistance(reflectedSourceResistance);

        lastTransferCurrent = !enabled ? 0f
                : pgSource ? (float) ceeDevice.getLastCurrent()
                : (float) Math.abs(coupling.getCurrent());
        lastTransferVoltage = !enabled ? 0f
                : pgSource ? (float) Math.abs(pgVolts)
                : (float) Math.abs(ceeDevice.getLastVoltage());
        if ((level.getGameTime() & 3) == 0)
            notifyUpdate();
    }

    @Override
    public void tickAudio() {
        if (lastTransferCurrent > 0.001f)
            SoundScapes.play(SoundScapes.AmbienceGroup.HUM, worldPosition, 1f, lastTransferCurrent / 20f);
    }

    // --- renderer hooks ---
    public float getPointerAngle() {
        return pointerAngle;
    }

    public float getPointerAnglePrev() {
        return pointerAnglePrev;
    }

    /** Amps crossing the bridge (synced) — the needle-buzz amplitude scales with it. */
    public float getLastTransferCurrent() {
        return lastTransferCurrent;
    }

    public boolean isTransferring() {
        return transferEnabled() && lastTransferCurrent > 0.001f;
    }

    // --- value box slot: centre of the EAST face of the model's `gauge_side`
    // cube (x 15.1..16.1, y 1.5..4.5, z 6.5..9.5 -> east-face centre 16.1,3,8). ---

    private static final Vec3 SLOT_BASE = VecHelper.voxelSpace(16.1, 3.0, 8.0);

    private static class HingeTipSlot extends ValueBoxTransform {
        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            return InteroperableSmallBlock.rotateY(SLOT_BASE, InteroperableSmallBlock.angleFor(state));
        }

        @Override
        public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
            int angle = InteroperableSmallBlock.angleFor(state);
            // Face east (+X) at the default facing; carry the block's Y rotation.
            TransformStack.of(ms).rotateYDegrees(-90 + angle);
        }

        @Override
        public float getScale() {
            // gauge_side's east face is only 3x3px; keep the decal / hit-sphere
            // tight to it (hit radius = scale/2 = 0.125 block = 2px).
            return 4 / 16f;
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        lastTransferCurrent = tag.getFloat("LastTransferCurrent");
        lastTransferVoltage = tag.getFloat("LastTransferVoltage");
        if (tag.contains("ReflectedR"))
            reflectedSourceResistance = tag.getFloat("ReflectedR");
        if (tag.contains("PointerAngle")) {
            pointerAngle = tag.getFloat("PointerAngle");
            pointerAnglePrev = pointerAngle;
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("LastTransferCurrent", lastTransferCurrent);
        tag.putFloat("LastTransferVoltage", lastTransferVoltage);
        tag.putFloat("ReflectedR", reflectedSourceResistance);
        tag.putFloat("PointerAngle", pointerAngle);
    }

    // --- goggle overlay -----------------------------------------------------

    private static String num(float v) {
        return Float.toString(Math.round(v * 100f) / 100f);
    }

    /**
     * Live goggle readout. The first line uses {@code forGoggles(tooltip)} (row 0)
     * so it gets the 4-space indent buffer past the goggle icon; every following
     * line is {@code forGoggles(tooltip, 1)}.
     */
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.builder()
                .add(getBlockState().getBlock().getName().withStyle(ChatFormatting.WHITE))
                .forGoggles(tooltip);

        int m = getMode();
        CreateLang.builder()
                .add(Component.translatable(GK + "flow",
                        Component.literal(modeLabel(m)).withStyle(ChatFormatting.AQUA)))
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);

        String status = m == MODE_OFF ? "status.off"
                : !settled() ? "status.reversing"
                : lastTransferCurrent > 0.001f ? "status.transferring"
                : "status.idle";
        CreateLang.builder()
                .add(Component.translatable(GK + status).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC))
                .forGoggles(tooltip, 1);

        if (m != MODE_OFF && settled() && lastTransferCurrent > 0.001f) {
            CreateLang.builder()
                    .add(Component.translatable(GK + "readout",
                            num(lastTransferVoltage), num(lastTransferCurrent),
                            num(lastTransferVoltage * lastTransferCurrent)))
                    .style(ChatFormatting.GRAY)
                    .forGoggles(tooltip, 1);

            if (reflectedSourceResistance < InteroperableDevice.SENSE_RESISTANCE * 0.5f) {
                CreateLang.builder()
                        .add(Component.translatable(GK + "reflected",
                                Component.literal(num(reflectedSourceResistance)).withStyle(ChatFormatting.AQUA)))
                        .style(ChatFormatting.GRAY)
                        .forGoggles(tooltip, 1);
            }
        }
        return true;
    }
}
