package com.cio.createinteroperable.iden;

import com.cio.createinteroperable.TelephoneRegistry;
import com.cio.createinteroperable.grid.ApplianceGrid;
import com.cio.createinteroperable.grid.CrayfishCompat;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Iden's Decor telephone on an Electro-Energetics-only install (no Power Grid
 * class anywhere in its hierarchy, so it loads without PG). Its only tap is
 * the CEE node, walked by {@link IdenPhoneCeeTaps}; see
 * {@link IdenPgTelephoneBlockEntity} for the PG-installed twin.
 */
public class IdenCeeTelephoneBlockEntity extends SmartBlockEntity implements IdenPhone, IHaveGoggleInformation {

    private final IdenPhoneCore core = new IdenPhoneCore(this, this);

    public IdenCeeTelephoneBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void tick() {
        super.tick();
        core.tick();
    }

    @Override
    public IdenPhoneCore phoneCore() {
        return core;
    }

    @Override
    public BlockEntity applianceOwner() {
        return this;
    }

    @Override
    public Object pgTapNetwork() {
        return null;
    }

    @Override
    public boolean ceeTapReaches(BlockPos otherPos) {
        return IdenPhoneCeeTaps.reaches(level, worldPosition, otherPos);
    }

    @Override
    public boolean pgTapConnected() {
        return false;
    }

    @Override
    public boolean ceeTapConnected() {
        return IdenPhoneCeeTaps.wired(level, worldPosition);
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        TelephoneRegistry.add(this);
        if (!CrayfishCompat.present()) {
            ApplianceGrid.get(level).addNode(this);
        }
    }

    @Override
    public void remove() {
        super.remove();
        TelephoneRegistry.remove(this);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        core.write(tag);
        if (!CrayfishCompat.present()) {
            writeApplianceNbt(tag);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        core.read(tag);
        if (!CrayfishCompat.present()) {
            readApplianceNbt(tag);
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        core.addGoggleTooltip(tooltip);
        return true;
    }
}
