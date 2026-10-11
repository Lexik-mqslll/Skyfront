package dev.krutlexik.skyfront.drone.network;

import dev.krutlexik.skyfront.drone.DroneMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S: the pilot's copy of the drone flew into {@code targetId}. */
public record DroneRamPayload(int droneId, int targetId) implements CustomPacketPayload {
    public static final Type<DroneRamPayload> TYPE = new Type<>(DroneMod.id("drone_ram"));

    public static final StreamCodec<FriendlyByteBuf, DroneRamPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.droneId);
                buf.writeVarInt(payload.targetId);
            },
            buf -> new DroneRamPayload(buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
