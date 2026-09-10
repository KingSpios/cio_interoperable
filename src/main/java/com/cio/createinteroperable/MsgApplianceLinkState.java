package com.cio.createinteroperable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

/**
 * Server &rarr; client: "you are now linking from this node" ({@code from}
 * present) or "linking cleared" ({@code from} empty). Drives the client-side
 * unfinished-link render in {@code ApplianceLinkClient}.
 */
public record MsgApplianceLinkState(Optional<BlockPos> from) implements CustomPacketPayload {

    public static final Type<MsgApplianceLinkState> TYPE =
            new Type<>(CreateInteroperable.rl("appliance_link_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MsgApplianceLinkState> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.optional(BlockPos.STREAM_CODEC), MsgApplianceLinkState::from,
                    MsgApplianceLinkState::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MsgApplianceLinkState msg, IPayloadContext context) {
        context.enqueueWork(() -> com.cio.createinteroperable.grid.client.ApplianceLinkClient.setArmedFrom(msg.from().orElse(null)));
    }
}
