package com.cio.createinteroperable;

import com.cio.createinteroperable.appliance.ApplianceHumPacket;
import com.cio.createinteroperable.grid.MsgApplianceLinkState;
import com.cio.createinteroperable.pole.StreetSignPacket;
import com.cio.createinteroperable.powerkit.PowerKitPanelPacket;
import com.cio.createinteroperable.telephone.TelephoneDialPacket;
import com.cio.createinteroperable.telephone.TelephoneLabelPacket;
import com.cio.createinteroperable.telephone.TelephoneSettingsPacket;
import com.cio.createinteroperable.telephone.RadioKeepLoadedPacket;
import com.cio.createinteroperable.telephone.RadioRoutePacket;
import com.cio.createinteroperable.telephone.RadioTuningPacket;
import com.cio.createinteroperable.telephone.TelephoneSoundPacket;
import com.cio.createinteroperable.telephone.TelephoneSoundReportPacket;
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
        registrar.playToServer(RadioTuningPacket.TYPE, RadioTuningPacket.STREAM_CODEC, RadioTuningPacket::handle);
        registrar.playToServer(RadioKeepLoadedPacket.TYPE, RadioKeepLoadedPacket.STREAM_CODEC, RadioKeepLoadedPacket::handle);
        registrar.playToServer(RadioRoutePacket.TYPE, RadioRoutePacket.STREAM_CODEC, RadioRoutePacket::handle);
        // Tier-4 Power Kit control panel (levers + intake tap).
        registrar.playToServer(PowerKitPanelPacket.TYPE, PowerKitPanelPacket.STREAM_CODEC, PowerKitPanelPacket::handle);
        // Utility Pole street sign text.
        registrar.playToServer(StreetSignPacket.TYPE, StreetSignPacket.STREAM_CODEC, StreetSignPacket::handle);
        // Sounds heard down a telephone line: client-only sounds near a phone (C2S), played at the far phone (S2C).
        registrar.playToServer(TelephoneSoundReportPacket.TYPE, TelephoneSoundReportPacket.STREAM_CODEC, TelephoneSoundReportPacket::handle);
        registrar.playToClient(TelephoneSoundPacket.TYPE, TelephoneSoundPacket.STREAM_CODEC, TelephoneSoundPacket::handle);
        // The flip switch in an Urban Decor oven / microwave / stove GUI.
        // Native appliance grid linking (S2C link-arm state; a no-op with Refurbished Furniture installed).
        registrar.playToClient(MsgApplianceLinkState.TYPE, MsgApplianceLinkState.STREAM_CODEC, MsgApplianceLinkState::handle);
        registrar.playToClient(ApplianceHumPacket.TYPE, ApplianceHumPacket.STREAM_CODEC, ApplianceHumPacket::handle);
    }
}
