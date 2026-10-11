package dev.krutlexik.skyfront.drone.network;

import dev.krutlexik.skyfront.drone.DroneMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S2C, to everyone watching a drone that is flying: one tick of it, stamped with the drone's own tick number. */
public record DroneStatePayload(int entityId, int simTick, double x, double y, double z,
                                double vx, double vy, double vz, float yRot, float xRot,
                                float qx, float qy, float qz, float qw) implements CustomPacketPayload {
    public static final Type<DroneStatePayload> TYPE = new Type<>(DroneMod.id("drone_state"));

    public static final StreamCodec<FriendlyByteBuf, DroneStatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.entityId);
                buf.writeVarInt(payload.simTick);
                buf.writeDouble(payload.x);
                buf.writeDouble(payload.y);
                buf.writeDouble(payload.z);
                buf.writeFloat((float) payload.vx);
                buf.writeFloat((float) payload.vy);
                buf.writeFloat((float) payload.vz);
                buf.writeFloat(payload.yRot);
                buf.writeFloat(payload.xRot);
                buf.writeFloat(payload.qx);
                buf.writeFloat(payload.qy);
                buf.writeFloat(payload.qz);
                buf.writeFloat(payload.qw);
            },
            buf -> new DroneStatePayload(buf.readVarInt(), buf.readVarInt(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
