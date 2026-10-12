package dev.krutlexik.skyfront.drone.network;

import dev.krutlexik.skyfront.drone.DroneMod;
import dev.krutlexik.skyfront.port.CustomPacketPayload;
import dev.krutlexik.skyfront.port.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;

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
