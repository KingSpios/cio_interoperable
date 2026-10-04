package com.cio.createinteroperable.iden;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Locale;

/**
 * Electric twin of Iden's Decor's Heavy Lever: an {@link ElectricLeverBlock}
 * plus Iden's dyeable {@code color} property (same name and values as Iden's
 * own {@code ColorProperty}, so Iden's blockstate file resolves unchanged).
 */
public class ElectricHeavyLeverBlock extends ElectricLeverBlock {

    public static final EnumProperty<Color> COLOR = EnumProperty.create("color", Color.class);

    public ElectricHeavyLeverBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(COLOR, Color.BASE)
                .setValue(FACING, Direction.NORTH)
                .setValue(FACE, AttachFace.FLOOR)
                .setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(COLOR);
    }

    /** Dye it, exactly like Iden's: one dye per recolour, consumed outside creative. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof DyeItem dye)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        Color color = Color.valueOf(dye.getDyeColor().getSerializedName().toUpperCase(Locale.ROOT));
        if (state.getValue(COLOR) == color) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            level.setBlockAndUpdate(pos, state.setValue(COLOR, color));
            level.playSound(null, pos, SoundEvents.DYE_USE, player.getSoundSource(), 1, 1);
            if (!player.isCreative()) {
                stack.shrink(1);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Mirror of Iden's {@code ColorProperty} &mdash; the 16 dye colours plus the undyed {@code base}. */
    public enum Color implements StringRepresentable {
        WHITE, LIGHT_GRAY, GRAY, BLACK, BROWN, RED, ORANGE, YELLOW, LIME, GREEN, CYAN, LIGHT_BLUE, BLUE, PURPLE,
        MAGENTA, PINK, BASE;

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
