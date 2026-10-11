package dev.krutlexik.skyfront.drone.network;

import dev.krutlexik.skyfront.drone.DroneMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S: the pilot hit the self-destruct. */
public record DroneSelfDestructPayload(int entityId, int atSeq) implements CustomPacketPayload {
    public static final Type<DroneSelfDestructPayload> TYPE = new Type<>(DroneMod.id("drone_self_destruct"));

    public static final StreamCodec<FriendlyByteBuf, DroneSelfDestructPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.entityId);
                buf.writeVarInt(payload.atSeq);
            },
            buf -> new DroneSelfDestructPayload(buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
