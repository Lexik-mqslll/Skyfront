package dev.krutlexik.skyfront.drone.network;

import dev.krutlexik.skyfront.drone.DroneMod;
import dev.krutlexik.skyfront.drone.DroneTuning;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S2C: the server's flight figures (see {@link DroneTuning}). */
public record DroneTuningPayload(double[] values) implements CustomPacketPayload {
    public static final Type<DroneTuningPayload> TYPE = new Type<>(DroneMod.id("drone_tuning"));

    public static final StreamCodec<FriendlyByteBuf, DroneTuningPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.values.length);
                for (double value : payload.values) {
                    buf.writeDouble(value);
                }
            },
            buf -> {
                int count = buf.readVarInt();
                if (count < 0 || count > 256) {
                    throw new IllegalArgumentException("Bad drone tuning length " + count);
                }
                double[] values = new double[count];
                for (int i = 0; i < count; i++) {
                    values[i] = buf.readDouble();
                }
                return new DroneTuningPayload(values);
            });

    public static DroneTuningPayload current() {
        return new DroneTuningPayload(DroneTuning.snapshot());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
