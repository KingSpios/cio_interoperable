package com.cio.createinteroperable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: "set my label to this text." Sent when the label screen closes. */
public record TelephoneLabelPacket(BlockPos pos, String label) implements CustomPacketPayload {
    public static final Type<TelephoneLabelPacket> TYPE = new Type<>(CreateInteroperable.rl("telephone_label"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TelephoneLabelPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TelephoneLabelPacket::pos,
            ByteBufCodecs.stringUtf8(100), TelephoneLabelPacket::label,
            TelephoneLabelPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TelephoneLabelPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null || player.isSpectator()) {
                return;
            }
            if (!player.level().isLoaded(packet.pos()) || !player.canInteractWithBlock(packet.pos(), 20)) {
                return;
            }
            if (player.level().getBlockEntity(packet.pos()) instanceof TelephoneNode be) {
                be.setLabel(packet.label());
            }
        });
    }
}
