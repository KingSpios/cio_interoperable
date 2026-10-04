package com.cio.createinteroperable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client &rarr; server: a telephone's settings screen ({@link TelephoneSettingsScreen})
 * was saved. Works on any {@link TelephoneNode} (CIO's three telephones and
 * Iden's Decor's). {@code dialTarget} is already formatted ({@code AAA-NNNNNN})
 * or empty. Pulse only applies to phones that {@link TelephoneNode#supportsPulse()}.
 * The own number is refused, with the usual deny feedback, if another phone
 * already has it; everything else always applies.
 */
public record TelephoneSettingsPacket(BlockPos pos, int areaCode, String number, String label, String dialTarget,
                                      boolean autoAnswer, boolean pulse) implements CustomPacketPayload {

    public static final Type<TelephoneSettingsPacket> TYPE = new Type<>(CreateInteroperable.rl("telephone_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TelephoneSettingsPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TelephoneSettingsPacket::pos,
            ByteBufCodecs.VAR_INT, TelephoneSettingsPacket::areaCode,
            ByteBufCodecs.stringUtf8(6), TelephoneSettingsPacket::number,
            ByteBufCodecs.stringUtf8(32), TelephoneSettingsPacket::label,
            ByteBufCodecs.stringUtf8(16), TelephoneSettingsPacket::dialTarget,
            ByteBufCodecs.VAR_INT, p -> (p.autoAnswer() ? 1 : 0) | (p.pulse() ? 2 : 0),
            (pos, area, number, label, dial, flags) ->
                    new TelephoneSettingsPacket(pos, area, number, label, dial, (flags & 1) != 0, (flags & 2) != 0));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TelephoneSettingsPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null || player.isSpectator()) {
                return;
            }
            if (!player.level().isLoaded(packet.pos()) || !player.canInteractWithBlock(packet.pos(), 20)) {
                return;
            }
            if (player.level().getBlockEntity(packet.pos()) instanceof TelephoneNode phone) {
                phone.setLabel(packet.label().length() > 32 ? packet.label().substring(0, 32) : packet.label());
                phone.setDialingTarget(packet.dialTarget());
                phone.setAutoAnswer(packet.autoAnswer());
                if (phone.supportsPulse()) {
                    phone.setPulse(packet.pulse());
                }
                int area = Math.max(0, Math.min(999, packet.areaCode()));
                boolean unchanged = area == phone.getAreaCode()
                        && TelephoneNumbers.sanitizeNumberText(packet.number()).equals(phone.getOwnNumberText());
                if (!unchanged && !phone.setOwnNumber(area, packet.number())) {
                    phone.denyFeedback();
                    player.displayClientMessage(Component.literal("That number is already in use.")
                            .withStyle(ChatFormatting.RED), true);
                }
            }
        });
    }
}
