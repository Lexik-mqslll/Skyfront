package dev.krutlexik.skyfront.drone.network;

import dev.krutlexik.skyfront.drone.DroneMod;
import dev.krutlexik.skyfront.drone.entity.DroneFlightState;
import dev.krutlexik.skyfront.port.CustomPacketPayload;
import dev.krutlexik.skyfront.port.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;

/**
 * S2C, every tick, to the pilot only: where the server's drone is, and which of the pilot's inputs it has
 * flown to get there.
 */
public record DroneLinkPayload(int entityId, int typeId, int ackSeq, DroneFlightState state)
        implements CustomPacketPayload {
    public static final Type<DroneLinkPayload> TYPE = new Type<>(DroneMod.id("drone_link"));

    public static final StreamCodec<FriendlyByteBuf, DroneLinkPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.entityId);
                buf.writeVarInt(payload.typeId);
                buf.writeInt(payload.ackSeq);
                payload.state.write(buf);
            },
            buf -> new DroneLinkPayload(buf.readVarInt(), buf.readVarInt(), buf.readInt(),
                    DroneFlightState.read(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
