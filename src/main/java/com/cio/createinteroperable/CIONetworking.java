package com.cio.createinteroperable;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = CreateInteroperable.ID)
public class CIONetworking {
    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(CreateInteroperable.ID);
        registrar.playToServer(TelephoneDialPacket.TYPE, TelephoneDialPacket.STREAM_CODEC, TelephoneDialPacket::handle);
        registrar.playToServer(TelephoneLabelPacket.TYPE, TelephoneLabelPacket.STREAM_CODEC, TelephoneLabelPacket::handle);
        registrar.playToServer(TelephoneNumberPacket.TYPE, TelephoneNumberPacket.STREAM_CODEC, TelephoneNumberPacket::handle);
        // Native appliance grid linking (S2C link-arm state; a no-op with Refurbished Furniture installed).
        registrar.playToClient(MsgApplianceLinkState.TYPE, MsgApplianceLinkState.STREAM_CODEC, MsgApplianceLinkState::handle);
    }
}
