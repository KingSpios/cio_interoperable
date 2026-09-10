package com.cio.createinteroperable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: "set my own Area Code and Number to these." Sent when the number screen closes. */
public record TelephoneNumberPacket(BlockPos pos, int areaCode, String number) implements CustomPacketPayload {
    public static final Type<TelephoneNumberPacket> TYPE = new Type<>(CreateInteroperable.rl("telephone_number"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TelephoneNumberPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TelephoneNumberPacket::pos,
            ByteBufCodecs.VAR_INT, TelephoneNumberPacket::areaCode,
            ByteBufCodecs.stringUtf8(6), TelephoneNumberPacket::number,
            TelephoneNumberPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TelephoneNumberPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null || player.isSpectator()) {
                return;
            }
            if (!player.level().isLoaded(packet.pos()) || !player.canInteractWithBlock(packet.pos(), 20)) {
                return;
            }
            if (player.level().getBlockEntity(packet.pos()) instanceof TelephoneNode be) {
                if (!be.setOwnNumber(packet.areaCode(), packet.number())) {
                    be.denyFeedback();
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("That number is already in use.")
                                    .withStyle(net.minecraft.ChatFormatting.RED),
                            true);
                }
            }
        });
    }
}
