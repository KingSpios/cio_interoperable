package com.cio.createinteroperable.mixin.pnw;

import com.george_vi.electroenergetics.content.railway_electrification.pantograph.TrainPantographEntry;
import com.george_vi.electroenergetics.mixin_interfaces.IPantographList;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps CEE's per-pantograph {@code active} flag in step with PnW's own
 * raised/lowered toggle ({@code IsExpandable}, which PnW's click handler writes
 * into the contraption's block-entity data on the server). A lowered PnW
 * pantograph therefore stops collecting current, like a lowered CEE one.
 */
@Mixin(targets = "de.mrjulsen.paw.blockentity.PantographMovementBehaviour", remap = false)
abstract class PnwPantographStateMixin {
    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private void cio$syncCeeActive(MovementContext context, CallbackInfo ci) {
        if (context.world.isClientSide() || !(context.contraption.entity instanceof CarriageContraptionEntity entity)) {
            return;
        }
        Carriage carriage = entity.getCarriage();
        if (!(carriage instanceof IPantographList pantographs)) {
            return;
        }
        boolean raised = context.blockEntityData.getBoolean("IsExpandable");
        TrainPantographEntry entry = pantographs.getPantographState(context.localPos);
        if (entry != null && entry.active != raised) {
            pantographs.changePantographState(context.localPos, raised);
        }
    }
}
