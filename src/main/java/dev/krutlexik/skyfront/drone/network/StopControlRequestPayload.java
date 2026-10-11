package dev.krutlexik.skyfront.drone.network;

import dev.krutlexik.skyfront.drone.DroneMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S: the pilot has left drone view. */
public record StopControlRequestPayload(int entityId) implements CustomPacketPayload {
    public static final Type<StopControlRequestPayload> TYPE = new Type<>(DroneMod.id("stop_control_request"));

    public static final StreamCodec<FriendlyByteBuf, StopControlRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeVarInt(payload.entityId),
            buf -> new StopControlRequestPayload(buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
