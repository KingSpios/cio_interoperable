package com.cio.createinteroperable;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Purely a goggle-info source — {@link RadiatorMiddleBlock} is otherwise
 * still deliberately inert (no ticking, no capability, no kinetics; see its
 * own class doc). Exists ONLY because Create's real goggle overlay requires
 * an actual {@code BlockEntity} implementing {@link IHaveGoggleInformation}
 * to show anything at all — confirmed by reading
 * {@code GoggleOverlayRenderer#renderOverlay}: it gates the entire overlay on
 * {@code be instanceof IHaveGoggleInformation}, with no fallback that shows
 * even just the block's name. Without this, a middle segment showed nothing
 * whatsoever when goggled.
 */
public class RadiatorMiddleBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    public RadiatorMiddleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.text("Steam Hearth Radiator")
                .style(ChatFormatting.WHITE)
                .forGoggles(tooltip);

        BlockState state = getBlockState();
        if (state.getBlock() instanceof RadiatorMiddleBlock) {
            CreateLang.text("Heat: ")
                    .style(ChatFormatting.GRAY)
                    .add(CreateLang.text(state.getValue(RadiatorMiddleBlock.HEAT_LEVEL).name())
                            .style(ChatFormatting.GOLD))
                    .forGoggles(tooltip, 1);
        }
        return true;
    }
}
