package dev.krutlexik.skyfront.drone.network;

import dev.krutlexik.skyfront.drone.DroneMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S: the pilot held the attack button long enough to release the grenade slung under the drone. */
public record DroneDropPayload(int entityId, int atSeq) implements CustomPacketPayload {
    public static final Type<DroneDropPayload> TYPE = new Type<>(DroneMod.id("drone_drop"));

    public static final StreamCodec<FriendlyByteBuf, DroneDropPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.entityId);
                buf.writeVarInt(payload.atSeq);
            },
            buf -> new DroneDropPayload(buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
