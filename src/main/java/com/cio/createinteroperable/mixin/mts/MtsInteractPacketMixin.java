package com.cio.createinteroperable.mixin.mts;

import com.cio.createinteroperable.mts.MtsAaSearchlights;
import minecrafttransportsimulator.baseclasses.Point3D;
import minecrafttransportsimulator.entities.components.AEntityE_Interactable;
import minecrafttransportsimulator.mcinterface.AWrapperWorld;
import minecrafttransportsimulator.mcinterface.IWrapperPlayer;
import minecrafttransportsimulator.packets.instances.PacketEntityInteract;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Server side of a player's click on an Immersive Vehicles entity: when it hits
 * an unpowered AA Spotlight's beam button or spin lever, show the action-bar
 * "Missing power" hint. The click itself is refused further down, in
 * {@code performAction} ({@link MtsDefinableMixin}); this only adds the feedback,
 * since that method never learns who clicked.
 */
@Mixin(value = PacketEntityInteract.class, remap = false)
public abstract class MtsInteractPacketMixin {

    @Shadow(remap = false) @Final private Point3D hitBoxLocalCenter;
    @Shadow(remap = false) @Final private boolean leftClick;
    @Shadow(remap = false) @Final private boolean rightClick;

    @Inject(
            method = "handle(Lminecrafttransportsimulator/mcinterface/AWrapperWorld;Lminecrafttransportsimulator/entities/components/AEntityE_Interactable;Lminecrafttransportsimulator/mcinterface/IWrapperPlayer;)Z",
            at = @At("HEAD"), remap = false)
    private void cio$warnUnpoweredSpotlight(AWrapperWorld world, AEntityE_Interactable<?> entity, IWrapperPlayer player,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (this.rightClick && !this.leftClick) {
            MtsAaSearchlights.warnIfLockedOut(entity, this.hitBoxLocalCenter, player);
        }
    }
}
