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
 * The functional half of InteroperableSmallBlock. Same bridging logic as
 * InteroperablePgAssembledBlockEntity, but the CEE device lives at this
 * entity's own BlockPos instead of 2 blocks away — this block IS both
 * sides at once.
 *
 * One-way, direction switchable via the {@code direction} ScrollValueBehaviour
 * (Create's "value settings" slider — right-click and hold the spot on top of
 * the block). 0 = default = "CPG -> CEE" (PG is source, CEE is sink); 1 =
 * reversed. See InteroperableDevice for why this replaced the earlier
 * always-bidirectional design (it decayed/oscillated under load).
 */
public class InteroperableSmallBlockEntity extends ElectricBlockEntity {
    private ProvidedVoltageSourceCoupling coupling;
    private InteroperableDevice ceeDevice;
    private ScrollValueBehaviour direction;

    /**
     * Amps actually flowing through whichever side is currently DELIVERING
     * (not sensing) — the leg doing real transfer work. Server-computed in
     * electricalTick(), synced to nearby clients via read/write since
     * tickAudio() (below) only ever runs client-side and can't reach
     * ceeDevice directly (it's a server-only lookup). Can't just read
     * coupling.getCurrent() unconditionally: when PG is the source, PG's own
     * coupling is deliberately near-open-circuit (SENSE_RESISTANCE) and
     * carries almost no current even while CEE is fully loaded and
     * delivering real power on its own side — the two mods' solvers share
     * no physical current path, only the bridged voltage, so PG's coupling
     * current can never reflect CEE's delivery current on its own.
     */
    private float lastTransferCurrent = 0f;

    public InteroperableSmallBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        direction = new DirectionScrollValueBehaviour(
                Component.translatable("createinteroperable.direction"), this, new DirectionSlot())
                .between(0, 1)
                .withFormatter(i -> i == 0 ? "CPG -> CEE" : "CEE -> CPG");
        behaviours.add(direction);
    }

    /**
     * Create's own ScrollValueBehaviour#createBoard hardcodes the on-screen
     * row label to the literal string "Value" and ignores whatever
     * .withFormatter(...) was set — it always shows the raw number (0/1)
     * via ValueSettings::format, not our CPG/CEE labels. Both confirmed by
     * reading ScrollValueBehaviour's real source. Overriding createBoard()
     * is the only way to change either.
     */
    private static class DirectionScrollValueBehaviour extends ScrollValueBehaviour {
        public DirectionScrollValueBehaviour(Component label, SmartBlockEntity be, ValueBoxTransform slot) {
            super(label, be, slot);
        }

        @Override
        public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
            return new ValueSettingsBoard(label, max, 10, ImmutableList.of(Component.literal("Mode")),
                    new ValueSettingsFormatter(vs -> Component.literal(vs.value() == 0 ? "CPG -> CEE" : "CEE -> CPG")));
        }
    }

    /** true = PG is the source (default "CPG -> CEE"); false = CEE is the source. */
    private boolean pgIsSource() {
        return direction == null || direction.getValue() == 0;
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        builder.setTerminalCount(2);
        coupling = builder.addInternalNode(ProvidedVoltageSourceCoupling.class,
                builder.terminalNode(0), builder.terminalNode(1), InteroperableDevice.SENSE_RESISTANCE);
        // Sink (CEE is source): inject CEE's last reading, low resistance
        // so little voltage is wasted driving a real load. Source (PG is
        // source): pin to 0 and use SENSE_RESISTANCE so this side reads its
        // own circuit like a voltmeter instead of loading it down — see
        // InteroperableDevice.SENSE_RESISTANCE's doc for why a *small*
        // shared resistance here was the real cause of a "generator reads
        // as ~0V on the other side" bug.
        coupling.setVoltageProvider(() -> !pgIsSource() && ceeDevice != null ? ceeDevice.getLastVoltage() : 0.0);
        coupling.setResistanceProvider(() -> pgIsSource() ? InteroperableDevice.SENSE_RESISTANCE : InteroperableDevice.DELIVERY_RESISTANCE);
    }

    @Override
    public void electricalTick() {
        super.electricalTick();
        if (!(level instanceof ServerLevel serverLevel) || coupling == null)
            return;

        if (ceeDevice == null || !ceeDevice.isValid()) {
            ceeDevice = DevicesSavedData.load(serverLevel).getDevice(worldPosition, InteroperableDevice.class);
        }
        if (ceeDevice != null) {
            boolean pgSource = pgIsSource();
            ceeDevice.setPgIsSource(pgSource);
            if (pgSource) {
                double potentialDifference = coupling.getPositive().getVoltage() - coupling.getNegative().getVoltage();
                ceeDevice.setPowerGridVoltage(potentialDifference);
            }

            // Whichever side is DELIVERING right now carries the real
            // transfer current — see lastTransferCurrent's doc.
            float current = pgSource ? (float) ceeDevice.getLastCurrent() : (float) Math.abs(coupling.getCurrent());
            lastTransferCurrent = current;
            if ((level.getGameTime() & 3) == 0)
                notifyUpdate();
        }
    }

    /**
     * PG's own TransformerBlockEntity plays its "transformer.ogg" hum via
     * SoundScapes.play(AmbienceGroup.HUM, pos, 1f, lastCurrent / 20f) —
     * decompiled and confirmed: pitch fixed at 1, only volume scales with
     * current, divided by 20 (their own tuning constant). Reused verbatim
     * (same sound event, same AmbienceGroup, same divisor) rather than
     * reimplemented, so this is genuinely the same noise PG's own
     * transformers make, scaled the same way. Overrides ElectricBlockEntity's
     * own default tickAudio() (a generic "isNoisy()" crackle), exactly as
     * TransformerBlockEntity itself does.
     */
    @Override
    public void tickAudio() {
        SoundScapes.play(SoundScapes.AmbienceGroup.HUM, worldPosition, 1f, lastTransferCurrent / 20f);
    }

    /**
     * = models/block/cio_transformer.json's "slider_plate" cube: a flat
     * quad flush against the west face (x=0.9/16, essentially 0), spanning
     * z=5-11, for the block's base (unrotated, facing=south) state. Y
     * history: 13-17 (original) -> -3..1 (downward-bleed rework) -> 2-6
     * (2026-08-26, current) — center now y=4. Re-derive this from the
     * model file's actual coordinates each time it moves, same discipline
     * as the PG terminal / CEE node coordinates, rather than eyeballing a
     * placement. The whole model rotates visually with PG_FACING via the
     * blockstate's own "y" rotation, so this slot has to rotate right
     * along with it (using the same angleFor/rotateY InteroperableSmallBlock
     * already uses for the PG terminals and CEE nodes) — otherwise, at any
     * non-default facing, the rendered slot would point at a totally
     * different, likely-obstructed direction than where the plate actually
     * ended up.
     */
    private static final Vec3 SLIDER_SLOT_BASE = VecHelper.voxelSpace(0.9, 4, 8);

    private static class DirectionSlot extends ValueBoxTransform {
        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            return InteroperableSmallBlock.rotateY(SLIDER_SLOT_BASE, InteroperableSmallBlock.angleFor(state));
        }

        @Override
        public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
            int angle = InteroperableSmallBlock.angleFor(state);
            TransformStack.of(ms).rotateYDegrees(90 + angle);
        }

        /**
         * Default is .5f (a 0.25-block hit radius / render size) — too
         * coarse for this plate, which is only 6/16 wide by 4/16 tall.
         * Matches the plate's larger dimension (z-span, 6/16 = 0.375) so
         * the value box's hit-sphere and rendered decal roughly match its
         * actual footprint instead of ballooning past its edges.
         */
        @Override
        public float getScale() {
            return 6 / 16f;
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        lastTransferCurrent = tag.getFloat("LastTransferCurrent");
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("LastTransferCurrent", lastTransferCurrent);
    }
}
