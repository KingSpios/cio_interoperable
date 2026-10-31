package com.cio.createinteroperable.streetlight.cee;

import com.cio.createinteroperable.modules.ElectroEnergeticsModule;
import com.cio.createinteroperable.streetlight.Streetlight;
import com.cio.createinteroperable.streetlight.StreetlightCore;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The Electro Energetics Streetlight's block entity: the shared
 * {@link StreetlightCore} (photocell, projected light, goggles). The lamp
 * itself runs on the {@link CeeStreetlightDevice}, which reads the switch from
 * here and hands its output back.
 */
public class CeeStreetlightBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    /**
     * Made on first use, without an initialiser: {@code addBehaviours} runs
     * inside the superclass constructor, before field initialisers would.
     */
    private StreetlightCore core;

    public CeeStreetlightBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(20);
    }

    private StreetlightCore core() {
        if (core == null) {
            core = new StreetlightCore(this, ElectroEnergeticsModule.CEE_STREETLIGHT_LIGHT);
        }
        return core;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        core().addBehaviours(behaviours);
    }

    /** Read by {@link CeeStreetlightDevice#preTick} every tick. */
    boolean isSwitchedOn() {
        return core().isSwitchedOn();
    }

    void applyOutput(Streetlight.Bulb bulb, double effectiveness, double voltage, double power) {
        core().applyOutput(bulb, effectiveness, voltage, power);
    }

    @Override
    public void initialize() {
        super.initialize();
        core().initialize();
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        core().lazyTick();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        return core().addToGoggleTooltip(tooltip);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        core().write(tag, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        core().read(tag, clientPacket);
    }
}
