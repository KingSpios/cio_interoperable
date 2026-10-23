package com.cio.createinteroperable;

import com.cio.createinteroperable.grid.MsgApplianceLinkState;
import com.cio.createinteroperable.telephone.TelephoneDialPacket;
import com.cio.createinteroperable.telephone.TelephoneLabelPacket;
import com.cio.createinteroperable.telephone.TelephoneSettingsPacket;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = CreateInteroperable.ID)
public class CIONetworking {
    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        // Handlers run on the main thread (PayloadRegistrar's default), so they
        // touch the world directly; no enqueueWork needed.
        PayloadRegistrar registrar = event.registrar(CreateInteroperable.ID);
        registrar.playToServer(TelephoneDialPacket.TYPE, TelephoneDialPacket.STREAM_CODEC, TelephoneDialPacket::handle);
        registrar.playToServer(TelephoneLabelPacket.TYPE, TelephoneLabelPacket.STREAM_CODEC, TelephoneLabelPacket::handle);
        registrar.playToServer(TelephoneSettingsPacket.TYPE, TelephoneSettingsPacket.STREAM_CODEC, TelephoneSettingsPacket::handle);
        // Native appliance grid linking (S2C link-arm state; a no-op with Refurbished Furniture installed).
        registrar.playToClient(MsgApplianceLinkState.TYPE, MsgApplianceLinkState.STREAM_CODEC, MsgApplianceLinkState::handle);
    }
}
