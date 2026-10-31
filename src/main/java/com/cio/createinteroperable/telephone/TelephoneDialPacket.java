package com.cio.createinteroperable.telephone;

import com.cio.createinteroperable.CreateInteroperable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> server: "set my dial-out target to this number." Sent when the dial-out screen closes,
 * and with {@code call} by its Dial key, which then works the line as the handset does (dial, or
 * hang up / answer / stop a dial attempt).
 */
public record TelephoneDialPacket(BlockPos pos, String number, boolean call) implements CustomPacketPayload {
    public static final Type<TelephoneDialPacket> TYPE = new Type<>(CreateInteroperable.rl("telephone_dial"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TelephoneDialPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TelephoneDialPacket::pos,
            ByteBufCodecs.stringUtf8(100), TelephoneDialPacket::number,
            ByteBufCodecs.BOOL, TelephoneDialPacket::call,
            TelephoneDialPacket::new);

    public TelephoneDialPacket(BlockPos pos, String number) {
        this(pos, number, false);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TelephoneDialPacket packet, IPayloadContext context) {
        var player = context.player();
        if (player == null || player.isSpectator()) {
            return;
        }
        if (!player.level().isLoaded(packet.pos()) || !player.canInteractWithBlock(packet.pos(), 20)) {
            return;
        }
        if (player.level().getBlockEntity(packet.pos()) instanceof TelephoneNode be) {
            be.setDialingTarget(packet.number());
            if (packet.call() && be instanceof CioTelephone phone) {
                phone.telephoneCore().onHandsetUsed(player);
            }
        }
    }
}
