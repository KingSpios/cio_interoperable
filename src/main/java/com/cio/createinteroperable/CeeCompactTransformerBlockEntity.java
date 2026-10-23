package com.cio.createinteroperable;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.google.common.collect.ImmutableList;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.lang.Lang;
import net.createmod.catnip.lang.LangNumberFormat;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Compact Transformer block entity &mdash; a port of CEE's own
 * {@code TransformerBlockEntity} (1.1.1): the same "Primary to Secondary Ratio"
 * winding slider (the identical 1/50 .. 1/1 .. 50/1 table and board layout,
 * reachable from both side fins), the same goggle readout and the same hum.
 * CEE's table and its index helpers are package-private there, so they are
 * copied here rather than reused.
 */
public class CeeCompactTransformerBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    protected ScrollValueBehaviour ratio;
    protected double power;
    protected double lastSentPower = -1.0;
    protected double primaryVoltage;
    protected double secondaryVoltage;
    /** Client-only hum handle, typed loosely so this common class never names a client class; see {@link CeeCompactTransformerClient}. */
    Object soundInstance;

    /** Copied verbatim from CEE's TransformerBlockEntity#ratios. */
    @SuppressWarnings("unchecked")
    static final Couple<Integer>[] RATIOS = new Couple[] {
            Couple.create(9, 10), Couple.create(8, 9), Couple.create(7, 8), Couple.create(6, 7),
            Couple.create(5, 6), Couple.create(4, 5), Couple.create(7, 9), Couple.create(3, 4),
            Couple.create(5, 7), Couple.create(2, 3), Couple.create(3, 5), Couple.create(4, 7),
            Couple.create(1, 2), Couple.create(3, 7), Couple.create(2, 5), Couple.create(3, 8),
            Couple.create(1, 3), Couple.create(2, 7), Couple.create(1, 4), Couple.create(2, 9),
            Couple.create(1, 5), Couple.create(2, 11), Couple.create(1, 6), Couple.create(1, 7),
            Couple.create(1, 8), Couple.create(1, 9), Couple.create(1, 10), Couple.create(1, 12),
            Couple.create(1, 14), Couple.create(1, 16), Couple.create(1, 18), Couple.create(1, 20),
            Couple.create(1, 22), Couple.create(1, 25), Couple.create(1, 28), Couple.create(1, 30),
            Couple.create(1, 33), Couple.create(1, 36), Couple.create(1, 40), Couple.create(1, 43),
            Couple.create(1, 46), Couple.create(1, 50)
    };

    public CeeCompactTransformerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(20);
    }

    /** Called by {@link CeeCompactTransformerDevice#postTick} with this tick's solved values. */
    void setReadings(double power, double primaryVoltage, double secondaryVoltage) {
        this.power = power;
        this.primaryVoltage = primaryVoltage;
        this.secondaryVoltage = secondaryVoltage;
    }

    double getPower() {
        return power;
    }

    int getRatioIndex() {
        return ratio.value;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder("electroenergetics").translate("gui.goggles.electric_stats").forGoggles(tooltip);
        Lang.builder("electroenergetics").translate("gui.goggles.primary_voltage").style(ChatFormatting.GRAY).forGoggles(tooltip);
        Lang.builder("electroenergetics")
                .text(LangNumberFormat.format(Math.round(Math.abs(primaryVoltage))))
                .translate("generic.volts")
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);
        Lang.builder("electroenergetics").translate("gui.goggles.secondary_voltage").style(ChatFormatting.GRAY).forGoggles(tooltip);
        Lang.builder("electroenergetics")
                .text(LangNumberFormat.format(Math.round(Math.abs(secondaryVoltage))))
                .translate("generic.volts")
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);
        Lang.builder("electroenergetics").translate("gui.goggles.power").style(ChatFormatting.GRAY).forGoggles(tooltip);
        Lang.builder("electroenergetics")
                .text(LangNumberFormat.format(Math.round(power)))
                .translate("generic.watts")
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);
        Lang.builder("electroenergetics")
                .text(LangNumberFormat.format(Math.round(CeeCompactTransformerDevice.MAX_POWER)))
                .translate("generic.watts")
                .text(" max")
                .style(ChatFormatting.DARK_GRAY)
                .forGoggles(tooltip, 1);
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level.isClientSide && Math.abs(lastSentPower - power) > 100.0) {
            lastSentPower = power;
            sendData();
        }
        if (level.isClientSide) {
            CatnipServices.PLATFORM.executeOnClientOnly(() -> () -> CeeCompactTransformerClient.tickAudio(this));
        }
    }

    @Override
    public void lazyTick() {
        if (!level.isClientSide) {
            lastSentPower = power;
            sendData();
        }
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        ratio = new ScrollValueBehaviour(Component.translatable("electroenergetics.transformer.ratio"), this, new ValueBox()) {
            @Override
            public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
                return new ValueSettingsBoard(label, max, RATIOS.length / 11,
                        ImmutableList.of(Component.translatable("electroenergetics.transformer.ratio_symbol")),
                        new ValueSettingsFormatter(valueSettings -> Component.literal(indexToRatioString(valueSettings.value()))));
            }
        };
        ratio.between(0, RATIOS.length * 2);
        ratio.value = RATIOS.length;
        ratio.withFormatter(CeeCompactTransformerBlockEntity::indexToRatioString);
        ratio.withCallback(i -> updateRatio());
        behaviours.add(ratio);
    }

    static String indexToRatioString(int i) {
        if (i == RATIOS.length) {
            return "1 / 1";
        } else if (i > RATIOS.length) {
            i -= RATIOS.length + 1;
            return RATIOS[i].getFirst() + " / " + RATIOS[i].getSecond();
        } else {
            i = RATIOS.length - i - 1;
            return RATIOS[i].getSecond() + " / " + RATIOS[i].getFirst();
        }
    }

    static double indexToRatio(int i) {
        if (i == RATIOS.length) {
            return 1.0;
        } else if (i > RATIOS.length) {
            i -= RATIOS.length + 1;
            return (double) RATIOS[i].getFirst() / RATIOS[i].getSecond();
        } else {
            i = RATIOS.length - i - 1;
            return (double) RATIOS[i].getSecond() / RATIOS[i].getFirst();
        }
    }

    private void updateRatio() {
        if (level instanceof ServerLevel sl) {
            CeeCompactTransformerDevice device = DevicesSavedData.load(sl).getDevice(worldPosition, CeeCompactTransformerDevice.class);
            if (device != null) {
                device.ratio = indexToRatio(ratio.value);
            }
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) {
            tag.putDouble("Power", power);
            tag.putDouble("PV", primaryVoltage);
            tag.putDouble("SV", secondaryVoltage);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) {
            power = tag.getDouble("Power");
            primaryVoltage = tag.getDouble("PV");
            secondaryVoltage = tag.getDouble("SV");
        }
    }

    /**
     * Same two-sided slot as CEE's Transformer (both faces across the winding
     * axis), pulled in from CEE's 15/16 to the compact model's fin faces at
     * 13/16 and centred on the fins' 2..12 height.
     */
    static class ValueBox extends ValueBoxTransform.Sided {
        @Override
        protected Vec3 getSouthLocation() {
            return VecHelper.voxelSpace(8.0, 7.0, 13.0);
        }

        @Override
        protected boolean isSideActive(BlockState state, Direction direction) {
            return direction.getAxis().isHorizontal()
                    && direction.getAxis() != state.getValue(CeeCompactTransformerBlock.FACING).getAxis();
        }
    }
}
