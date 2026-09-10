package com.cio.createinteroperable;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.sim.node.ProvidedVoltageSourceCoupling;
import org.patryk3211.powergrid.utility.sound.SoundScapes;

import java.util.List;

/**
 * Functional half of {@link InteroperableCouplerBlock}. Reuses the same one-way
 * PG&harr;CEE bridging as {@link InteroperableSmallBlockEntity} /
 * {@link InteroperableDevice}, with three differences:
 *
 * <ol>
 *   <li><b>Three-position flow control</b> ({@code mode} ScrollValueBehaviour):
 *       0 = {@code CPG -> CEE} (default, PG source), 1 = {@code OFF}, 2 =
 *       {@code CEE -> CPG} (CEE source). Right-click-and-hold the east hinge tip.</li>
 *   <li><b>Gauge needle with a reversal dead time.</b> {@link #pointerAngle} (deg,
 *       south-positive) eases toward a target set by {@code mode}
 *       (0&rarr;+45&deg; south, 1&rarr;0&deg; up, 2&rarr;-45&deg; north) at a
 *       fixed {@value #STEP_DEG_PER_TICK}&deg;/tick &mdash; a full +45&rarr;-45
 *       swing is 60 ticks (3s). Power only crosses the bridge once the needle has
 *       arrived ({@link #transferEnabled()}); while it is travelling, transfer is
 *       hard-cut via {@link InteroperableDevice#setTransferEnabled}. So flipping
 *       direction imposes a ~3s cut-off, exactly the needle's travel time.</li>
 *   <li>The transformer hum only plays while power is actually crossing.</li>
 * </ol>
 *
 * Both client and server run the identical needle easing each tick (deterministic
 * from the synced {@code mode}); the server re-syncs {@link #pointerAngle} every
 * {@value #SYNC_INTERVAL} ticks as a drift correction. The renderer
 * ({@link InteroperableCouplerRenderer}) lerps {@link #pointerAnglePrev}&rarr;
 * {@link #pointerAngle} by partialTicks and adds a small idle wobble while
 * transferring.
 */
public class InteroperableCouplerBlockEntity extends ElectricBlockEntity {

    /** 90&deg; of needle travel over 3s at 20 tps. */
    public static final float STEP_DEG_PER_TICK = 90f / 60f;
    private static final float SETTLE_EPS = 0.5f;

    public static final int MODE_CPG_TO_CEE = 0;
    public static final int MODE_OFF = 1;
    public static final int MODE_CEE_TO_CPG = 2;

    private ProvidedVoltageSourceCoupling coupling;
    private InteroperableDevice ceeDevice;
    private ScrollValueBehaviour mode;

    /** Needle angle, degrees, south-positive. Server-authoritative, synced. */
    private float pointerAngle = 45f;
    /** Previous tick's {@link #pointerAngle}, for render interpolation. */
    private float pointerAnglePrev = 45f;

    /** Amps crossing the bridge on whichever leg is delivering — see {@link InteroperableSmallBlockEntity}'s field of the same name. */
    private float lastTransferCurrent = 0f;

    public InteroperableCouplerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        mode = new ModeScrollValueBehaviour(
                Component.translatable("createinteroperable.coupler.direction"), this, new HingeTipSlot())
                .between(MODE_CPG_TO_CEE, MODE_CEE_TO_CPG)
                .withFormatter(InteroperableCouplerBlockEntity::modeLabel);
        behaviours.add(mode);
    }

    private static String modeLabel(int m) {
        return switch (m) {
            case MODE_OFF -> "OFF";
            case MODE_CEE_TO_CPG -> "CEE -> CPG";
            default -> "CPG -> CEE";
        };
    }

    /** See {@link InteroperableSmallBlockEntity.DirectionScrollValueBehaviour} — createBoard() must be overridden to relabel the row / show text. */
    private static class ModeScrollValueBehaviour extends ScrollValueBehaviour {
        public ModeScrollValueBehaviour(Component label, SmartBlockEntity be, ValueBoxTransform slot) {
            super(label, be, slot);
        }

        @Override
        public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
            return new ValueSettingsBoard(label, max, 10, ImmutableList.of(Component.literal("Flow")),
                    new ValueSettingsFormatter(vs -> Component.literal(modeLabel(vs.value()))));
        }

        @Override
        public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
            super.read(nbt, registries, clientPacket);
            // ScrollValueBehaviour.read() is unclamped; guard against a stale
            // out-of-range value persisted by an earlier build (see the sibling
            // guard in InteroperableDoubleCouplerBlockEntity.FlowModeBehaviour).
            if (value < MODE_CPG_TO_CEE || value > MODE_CEE_TO_CPG)
                value = MODE_OFF;
        }
    }

    private int getMode() {
        return mode == null ? MODE_CPG_TO_CEE : mode.getValue();
    }

    /** Angle the needle is easing toward for the current mode: +45 south / 0 up / -45 north. */
    private float targetAngle() {
        return 45f - getMode() * 45f;
    }

    private boolean settled() {
        return Math.abs(pointerAngle - targetAngle()) <= SETTLE_EPS;
    }

    /** true only once the needle has arrived AND mode isn't OFF. */
    private boolean transferEnabled() {
        return getMode() != MODE_OFF && settled();
    }

    /** true = PG is source ("CPG -> CEE"); false = CEE is source. Only meaningful while {@link #transferEnabled()}. */
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
        // Deterministic needle easing, run identically on both sides. The
        // server value is pushed to clients by electricalTick()'s periodic
        // notifyUpdate(); between packets both sides ease the same way from the
        // same synced mode, so they stay locked together.
        pointerAnglePrev = pointerAngle;
        pointerAngle = approach(pointerAngle, targetAngle(), STEP_DEG_PER_TICK);
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
                        : pgIsSource() ? InteroperableDevice.SENSE_RESISTANCE
                        : InteroperableDevice.DELIVERY_RESISTANCE);
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
        if (enabled && pgSource) {
            double pd = coupling.getPositive().getVoltage() - coupling.getNegative().getVoltage();
            ceeDevice.setPowerGridVoltage(pd);
        }

        lastTransferCurrent = !enabled ? 0f
                : pgSource ? (float) ceeDevice.getLastCurrent()
                : (float) Math.abs(coupling.getCurrent());
        if ((level.getGameTime() & 3) == 0)
            notifyUpdate();
    }

    /** PG's transformer hum (see {@link InteroperableSmallBlockEntity#tickAudio()}), gated on real transfer. */
    @Override
    public void tickAudio() {
        if (lastTransferCurrent > 0.001f)
            SoundScapes.play(SoundScapes.AmbienceGroup.HUM, worldPosition, 1f, lastTransferCurrent / 20f);
    }

    // --- renderer hooks ------------------------------------------------------

    public float getPointerAngle() {
        return pointerAngle;
    }

    public float getPointerAnglePrev() {
        return pointerAnglePrev;
    }

    /** Needle at rest at a ±45 end and power actually flowing → renderer adds the magnetic wobble. */
    public boolean isTransferring() {
        return transferEnabled() && lastTransferCurrent > 0.001f;
    }

    // --- value box slot: east tip of the hinge (model: cpg_east_pin ~[12.5,3,8]) ---

    private static final Vec3 SLOT_BASE = VecHelper.voxelSpace(13.0, 3.0, 8.0);

    private static class HingeTipSlot extends ValueBoxTransform {
        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            return InteroperableSmallBlock.rotateY(SLOT_BASE, InteroperableSmallBlock.angleFor(state));
        }

        @Override
        public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
            int angle = InteroperableSmallBlock.angleFor(state);
            // Slot faces east (+X) at angle 0; rotateY by the block's facing angle.
            TransformStack.of(ms).rotateYDegrees(-90 + angle);
        }

        @Override
        public float getScale() {
            return 5 / 16f;
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        lastTransferCurrent = tag.getFloat("LastTransferCurrent");
        if (tag.contains("PointerAngle")) {
            pointerAngle = tag.getFloat("PointerAngle");
            pointerAnglePrev = pointerAngle;
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("LastTransferCurrent", lastTransferCurrent);
        tag.putFloat("PointerAngle", pointerAngle);
    }
}
