package dev.krutlexik.skyfront.drone.network;

import dev.krutlexik.skyfront.drone.DroneMod;
import dev.krutlexik.skyfront.port.CustomPacketPayload;
import dev.krutlexik.skyfront.port.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;

/** S2C: enter drone view for the given drone entity. */
public record StartControlPayload(int entityId, double controlRange,
                                  double x, double y, double z,
                                  float yRot, float xRot, int typeId,
                                  boolean armed, float charge) implements CustomPacketPayload {
    public static final Type<StartControlPayload> TYPE = new Type<>(DroneMod.id("start_control"));

    public static final StreamCodec<FriendlyByteBuf, StartControlPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.entityId);
                buf.writeDouble(payload.controlRange);
                buf.writeDouble(payload.x);
                buf.writeDouble(payload.y);
                buf.writeDouble(payload.z);
                buf.writeFloat(payload.yRot);
                buf.writeFloat(payload.xRot);
                buf.writeVarInt(payload.typeId);
                buf.writeBoolean(payload.armed);
                buf.writeFloat(payload.charge);
            },
            buf -> new StartControlPayload(buf.readVarInt(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readFloat(), buf.readFloat(), buf.readVarInt(),
                    buf.readBoolean(), buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
