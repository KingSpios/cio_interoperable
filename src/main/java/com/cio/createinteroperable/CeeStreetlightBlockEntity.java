package com.cio.createinteroperable;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import net.createmod.catnip.lang.Lang;
import net.createmod.catnip.lang.LangNumberFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Streetlight block entity: the photocell mode slider, the switch decision the
 * device reads each tick, and the projected light below.
 *
 * <p><b>Photocell.</b> Uses the vanilla Daylight Detector's own sky reading
 * ({@code sky light - sky darken}); its inverted form is the "night light"
 * signal. {@link Mode#AUTO} closes the switch once that night signal reaches
 * {@link #NIGHT_THRESHOLD} &mdash; dusk under open sky, also a thunderstorm
 * (plain rain is not enough), and always under a roof, exactly where an
 * inverted Daylight Detector would also read high.</p>
 *
 * <p><b>Projection.</b> Same scheme as Power Grid's Factory Light: every air
 * block straight below, down to the first non-air block, becomes an invisible
 * {@link CeeStreetlightLightBlock} carrying the lamp's light level. Reach and
 * level both scale with the bulb's effectiveness: {@link #MAX_REACH} blocks at
 * light 15 at 120 V, half that (10 blocks, light 8) at the 100 V strike.</p>
 */
public class CeeStreetlightBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    public static final int MAX_REACH = 20;
    /** Inverted-Daylight-Detector signal at/above which AUTO turns on (and below which INVERTED does). */
    static final int NIGHT_THRESHOLD = 4;

    public enum Mode implements INamedIconOptions {
        AUTO(AllIcons.I_REFRESH),
        ALWAYS_ON(AllIcons.I_PLAY),
        INVERTED(AllIcons.I_FLIP),
        ALWAYS_OFF(AllIcons.I_STOP);

        private final AllIcons icon;

        Mode(AllIcons icon) {
            this.icon = icon;
        }

        @Override
        public AllIcons getIcon() {
            return icon;
        }

        @Override
        public String getTranslationKey() {
            return "createinteroperable.streetlight.mode." + name().toLowerCase();
        }
    }

    private ScrollOptionBehaviour<Mode> mode;
    private boolean switchedOn;
    private int projectedLevel;
    private int projectedReach;
    private double voltage;
    private double power;

    public CeeStreetlightBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(20);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        mode = new ScrollOptionBehaviour<>(Mode.class,
                Component.translatable("createinteroperable.streetlight.mode"), this,
                new CIOValueBox(CeeStreetlightBlock.PUCK_TOP, Direction.UP, 0.4f, CeeStreetlightBlock::orient));
        mode.withCallback(i -> updateSwitch());
        behaviours.add(mode);
    }

    /** Read by {@link CeeStreetlightDevice#preTick} every tick. */
    boolean isSwitchedOn() {
        return switchedOn;
    }

    @Override
    public void initialize() {
        super.initialize();
        updateSwitch();
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (level == null || level.isClientSide) {
            return;
        }
        updateSwitch();
        project(projectedLevel, projectedReach);
        sendData();
    }

    private void updateSwitch() {
        if (level == null || level.isClientSide) {
            return;
        }
        switchedOn = switch (mode.get()) {
            case ALWAYS_ON -> true;
            case ALWAYS_OFF -> false;
            case AUTO -> nightSignal() >= NIGHT_THRESHOLD;
            case INVERTED -> nightSignal() < NIGHT_THRESHOLD;
        };
    }

    /** The inverted vanilla Daylight Detector's output at this block (0 at noon in the open, 15 in the dark). */
    private int nightSignal() {
        int daylight = level.getBrightness(LightLayer.SKY, worldPosition) - level.getSkyDarken();
        return 15 - Math.max(0, Math.min(15, daylight));
    }

    /**
     * Called by the device after every solve: the bulb to show and how
     * effective it is (0 when dark, 0.5..1 when lit). Only touches the world
     * when something visible changed.
     */
    void applyOutput(CeeStreetlightBlock.Bulb bulb, double effectiveness, double voltage, double power) {
        this.voltage = voltage;
        this.power = power;
        int light = bulb.lit() ? (int) Math.round(15 * effectiveness) : 0;
        int reach = bulb.lit() ? (int) Math.round(MAX_REACH * effectiveness) : 0;

        BlockState state = getBlockState();
        if (state.getValue(CeeStreetlightBlock.BULB) != bulb || state.getValue(CeeStreetlightBlock.LIGHT) != light) {
            level.setBlock(worldPosition, state.setValue(CeeStreetlightBlock.BULB, bulb).setValue(CeeStreetlightBlock.LIGHT, light),
                    Block.UPDATE_ALL);
        }
        if (light != projectedLevel || reach != projectedReach) {
            project(light, reach);
        }
    }

    /** Fills the air below with light blocks down to {@code reach}, and clears any of ours beyond it. */
    private void project(int light, int reach) {
        projectedLevel = light;
        projectedReach = reach;
        for (int i = 1; i <= MAX_REACH; i++) {
            BlockPos pos = worldPosition.below(i);
            if (!level.isLoaded(pos)) {
                return;
            }
            BlockState below = level.getBlockState(pos);
            boolean ours = below.getBlock() instanceof CeeStreetlightLightBlock;
            if (light > 0 && i <= reach) {
                if (ours) {
                    if (below.getValue(CeeStreetlightLightBlock.LEVEL) != light) {
                        level.setBlock(pos, below.setValue(CeeStreetlightLightBlock.LEVEL, light), Block.UPDATE_ALL);
                    }
                } else if (below.isAir()) {
                    level.setBlock(pos, CIOBlocks.CEE_STREETLIGHT_LIGHT.get().defaultBlockState()
                            .setValue(CeeStreetlightLightBlock.LEVEL, light), Block.UPDATE_ALL);
                } else {
                    return;
                }
            } else if (ours) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            } else {
                return;
            }
        }
    }

    /** Removes the column of light blocks under a streetlight that is going away. */
    static void clearProjection(Level level, BlockPos lamp) {
        if (level.isClientSide) {
            return;
        }
        for (int i = 1; i <= MAX_REACH; i++) {
            BlockPos pos = lamp.below(i);
            if (!level.isLoaded(pos) || !(level.getBlockState(pos).getBlock() instanceof CeeStreetlightLightBlock)) {
                return;
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        BlockState state = getBlockState();
        Lang.builder("electroenergetics").translate("gui.goggles.electric_stats").forGoggles(tooltip);
        Lang.builder("createinteroperable")
                .translate("streetlight.goggles.bulb." + state.getValue(CeeStreetlightBlock.BULB).getSerializedName())
                .style(state.getValue(CeeStreetlightBlock.BULB).lit() ? ChatFormatting.GOLD : ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);
        Lang.builder("createinteroperable")
                .translate("streetlight.goggles.mode")
                .add(Component.translatable(mode.get().getTranslationKey()))
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);
        Lang.builder("electroenergetics").translate("gui.goggles.voltage").style(ChatFormatting.GRAY).forGoggles(tooltip);
        Lang.builder("electroenergetics")
                .text(LangNumberFormat.format(Math.round(voltage)))
                .translate("generic.volts")
                .style(voltage > CeeStreetlightDevice.MAX_VOLTAGE ? ChatFormatting.RED : ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);
        Lang.builder("electroenergetics").translate("gui.goggles.power").style(ChatFormatting.GRAY).forGoggles(tooltip);
        Lang.builder("electroenergetics")
                .text(LangNumberFormat.format(Math.round(power)))
                .translate("generic.watts")
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);
        return true;
    }

    @Override
    protected void write(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) {
            tag.putDouble("Voltage", voltage);
            tag.putDouble("Power", power);
        }
    }

    @Override
    protected void read(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) {
            voltage = tag.getDouble("Voltage");
            power = tag.getDouble("Power");
        }
    }
}
