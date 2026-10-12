package dev.krutlexik.skyfront.drone.network;

import dev.krutlexik.skyfront.drone.DroneMod;
import dev.krutlexik.skyfront.drone.entity.PilotInput;
import dev.krutlexik.skyfront.port.CustomPacketPayload;
import dev.krutlexik.skyfront.port.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;

/** C2S: one tick of the pilot's controls (see {@link PilotInput}). */
public record DroneInputPayload(int entityId, PilotInput input) implements CustomPacketPayload {
    public static final int FORWARD = 1;
    public static final int BACKWARD = 1 << 1;
    public static final int LEFT = 1 << 2;
    public static final int RIGHT = 1 << 3;
    public static final int UP = 1 << 4;
    public static final int DOWN = 1 << 5;
    /**
     * The arming switch rides in the input as a wanted position: ARM_SET says this input carries one, ARM_ON
     * what it is.
     */
    public static final int ARM_SET = 1 << 6;
    public static final int ARM_ON = 1 << 7;

    public static byte pack(boolean forward, boolean backward, boolean left, boolean right,
                            boolean up, boolean down) {
        int keys = 0;
        if (forward) keys |= FORWARD;
        if (backward) keys |= BACKWARD;
        if (left) keys |= LEFT;
        if (right) keys |= RIGHT;
        if (up) keys |= UP;
        if (down) keys |= DOWN;
        return (byte) keys;
    }

    public static final Type<DroneInputPayload> TYPE = new Type<>(DroneMod.id("drone_input"));

    public static final StreamCodec<FriendlyByteBuf, DroneInputPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.entityId);
                payload.input.write(buf);
            },
            buf -> new DroneInputPayload(buf.readVarInt(), PilotInput.read(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
