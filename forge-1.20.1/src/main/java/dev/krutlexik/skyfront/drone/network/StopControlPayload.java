package dev.krutlexik.skyfront.drone.network;

import dev.krutlexik.skyfront.drone.DroneMod;
import dev.krutlexik.skyfront.port.CustomPacketPayload;
import dev.krutlexik.skyfront.port.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;

/** S2C: leave drone view. */
public record StopControlPayload(Reason reason, double x, double y, double z) implements CustomPacketPayload {
    /** Why the server dropped the link, and how the pilot's client should end the feed. */
    public enum Reason {
        /** Ordinary release (a Mavic's battery flat, unpaired, picked up): camera returns at once, no freeze. */
        NORMAL,
        /** The drone strayed out of controller range: play the signal-loss freeze. */
        OUT_OF_RANGE,
        /** The drone was destroyed under it (shot down, broken): play the signal-loss freeze. */
        DESTROYED,
        /** The pilot flew it into something hard enough to write it off: the crash variant of the freeze. */
        CRASHED,
        /**
         * An FPV airframe's pack is flat: the video transmitter dies with it, so the goggles hold the last
         * frame as they would for any lost feed.
         */
        BATTERY
    }

    private static final Reason[] REASONS = Reason.values();

    public static final Type<StopControlPayload> TYPE = new Type<>(DroneMod.id("stop_control"));

    public static final StreamCodec<FriendlyByteBuf, StopControlPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeByte(payload.reason.ordinal());
                buf.writeDouble(payload.x);
                buf.writeDouble(payload.y);
                buf.writeDouble(payload.z);
            },
            buf -> new StopControlPayload(REASONS[buf.readByte()],
                    buf.readDouble(), buf.readDouble(), buf.readDouble()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
