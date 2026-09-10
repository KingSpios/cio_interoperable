package com.cio.createinteroperable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: "set my dial-out target to this number." Sent when the dial-out screen closes. */
public record TelephoneDialPacket(BlockPos pos, String number) implements CustomPacketPayload {
    public static final Type<TelephoneDialPacket> TYPE = new Type<>(CreateInteroperable.rl("telephone_dial"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TelephoneDialPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TelephoneDialPacket::pos,
            ByteBufCodecs.stringUtf8(100), TelephoneDialPacket::number,
            TelephoneDialPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TelephoneDialPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null || player.isSpectator()) {
                return;
            }
            if (!player.level().isLoaded(packet.pos()) || !player.canInteractWithBlock(packet.pos(), 20)) {
                return;
            }
            if (player.level().getBlockEntity(packet.pos()) instanceof TelephoneNode be) {
                be.setDialingTarget(packet.number());
            }
        });
    }
}
