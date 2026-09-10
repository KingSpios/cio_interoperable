package com.cio.createinteroperable.letsdo;

import com.cio.createinteroperable.CIOConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;

/**
 * Drives all player interaction with a managed Let's Do sink when
 * {@link CIOConfig#SINK_REQUIRE_PIPE_SUPPLY} is on, replacing Farm &amp; Charm's
 * own {@code useItemOn} (we cancel the interaction, so its handler never runs).
 *
 * <ul>
 *   <li>Right-click the <b>faucet</b> (upper half), or empty-hand the basin, to
 *       fill the basin — costs {@link CIOConfig#SINK_FILL_COST_MB} from the buffer
 *       the pipes feed. Not enough → white smoke + the same messages the Crayfish
 *       fixtures use ({@code fixture.no_supply} / {@code fixture.dry}).</li>
 *   <li>Empty bucket on a filled basin → a bucket of whatever fluid filled it;
 *       glass bottle → a water bottle (water only).</li>
 *   <li>Filled bucket on the basin (if allowed) → tops up the buffer.</li>
 * </ul>
 */
@EventBusSubscriber(modid = com.cio.createinteroperable.CreateInteroperable.ID)
public final class SinkInteractionHandler {
    private SinkInteractionHandler() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!CIOConfig.SINK_REQUIRE_PIPE_SUPPLY.get()) {
            return;
        }
        Level level = event.getLevel();
        BlockState state = level.getBlockState(event.getPos());
        if (!SinkStates.isSink(state)) {
            return;
        }
        SinkBlockEntity be = SinkBlockEntity.atEitherHalf(level, event.getPos(), state);
        if (be == null) {
            return;
        }

        InteractionResult result = handle(level, state, be, event.getEntity(), event.getItemStack());
        if (result != null) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }

    /** @return result to cancel the event with, or {@code null} to let Farm & Charm / vanilla run. */
    private static InteractionResult handle(Level level, BlockState clickedState, SinkBlockEntity be,
                                            Player player, ItemStack held) {
        boolean server = !level.isClientSide;
        boolean creative = player.getAbilities().instabuild;
        boolean faucet = SinkStates.isUpperHalf(clickedState);

        BlockState lowerState = be.getBlockState();
        Boolean filled = SinkStates.filled(lowerState);
        if (filled == null) {
            return null;
        }

        // --- basin already has liquid: draining -----------------------------
        if (filled) {
            if (held.is(Items.BUCKET)) {
                if (server) {
                    Fluid f = be.drainBasin();
                    if (!creative) {
                        held.shrink(1);
                        give(player, filledBucketOf(f));
                    }
                    playAt(level, be.getBlockPos(), SoundEvents.BUCKET_FILL, 1.0F, 1.0F);
                }
                return InteractionResult.sidedSuccess(!server);
            }
            if (held.is(Items.GLASS_BOTTLE) && be.basinFluid() == Fluids.WATER) {
                if (server) {
                    be.drainBasin();
                    if (!creative) {
                        held.shrink(1);
                        give(player, waterBottle());
                    }
                    playAt(level, be.getBlockPos(), SoundEvents.BOTTLE_FILL, 1.0F, 1.0F);
                }
                return InteractionResult.sidedSuccess(!server);
            }
            return null;
        }

        // --- manual buffer top-up with a filled bucket (basin/lower only) ---
        if (!faucet && !held.isEmpty() && CIOConfig.SINK_ALLOW_MANUAL_BUCKET_FILL.get()) {
            FluidStack contained = FluidUtil.getFluidContained(held).orElse(FluidStack.EMPTY);
            if (!contained.isEmpty()) {
                if (be.roomFor(contained) >= contained.getAmount()) {
                    if (server) {
                        be.addToBuffer(contained.getFluid(), contained.getAmount());
                        be.markDelivery();
                        if (!creative) {
                            held.shrink(1);
                            give(player, new ItemStack(Items.BUCKET));
                        }
                        playAt(level, be.getBlockPos(), SoundEvents.BUCKET_EMPTY, 1.0F, 1.0F);
                    }
                    return InteractionResult.sidedSuccess(!server);
                }
                if (server) {
                    smoke(level, be, clickedState);
                    player.displayClientMessage(Component.translatable("message.createinteroperable.fixture.dry"), true);
                }
                return InteractionResult.CONSUME;
            }
        }

        // --- fill the basin from the faucet --------------------------------
        if (faucet || held.isEmpty()) {
            if (be.fillBasinFromBuffer()) {
                if (server) {
                    playAt(level, be.getBlockPos(), SoundEvents.BUCKET_EMPTY, 1.0F, 1.0F);
                }
                return InteractionResult.sidedSuccess(!server);
            }
            if (server) {
                smoke(level, be, clickedState);
                // Same wording as the Crayfish fixtures: a connected-but-not-yet-
                // ready buffer reads as "dry", nothing connected reads as "no supply".
                String key = be.hasLiveSupply()
                        ? "message.createinteroperable.fixture.dry"
                        : "message.createinteroperable.fixture.no_supply";
                player.displayClientMessage(Component.translatable(key), true);
                playAt(level, be.getBlockPos(), SoundEvents.FIRE_EXTINGUISH, 0.25F, 1.6F);
            }
            return InteractionResult.CONSUME;
        }

        return null;
    }

    // --- helpers --------------------------------------------------------------

    private static ItemStack filledBucketOf(Fluid fluid) {
        var bucket = fluid.getBucket();
        return new ItemStack(bucket != Items.AIR ? bucket : Items.WATER_BUCKET);
    }

    private static ItemStack waterBottle() {
        ItemStack bottle = new ItemStack(Items.POTION);
        bottle.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.WATER));
        return bottle;
    }

    private static void give(Player player, ItemStack stack) {
        player.getInventory().placeItemBackInInventory(stack);
    }

    private static void playAt(Level level, BlockPos pos, SoundEvent sound, float vol, float pitch) {
        level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sound, SoundSource.BLOCKS, vol, pitch);
    }

    private static void smoke(Level level, SinkBlockEntity be, BlockState clickedState) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        BlockPos faucet = be.getBlockPos().above();
        Direction facing = SinkStates.facing(clickedState);
        double fx = facing == null ? 0 : facing.getOpposite().getStepX() * 0.28;
        double fz = facing == null ? 0 : facing.getOpposite().getStepZ() * 0.28;
        server.sendParticles(ParticleTypes.CLOUD,
                faucet.getX() + 0.5 + fx, faucet.getY() + 0.35, faucet.getZ() + 0.5 + fz,
                8, 0.06, 0.04, 0.06, 0.01);
    }
}
