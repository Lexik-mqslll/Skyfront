package dev.krutlexik.skyfront.drone.entity;

import dev.krutlexik.skyfront.drone.network.DroneInputPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;

/** One tick of the pilot's controls, numbered. */
public record PilotInput(int seq, byte keys, float gimbalYaw, float gimbalPitch,
                         float roll, float pitch, float yaw, float throttle) {
    /** Nothing held, nothing pointed: what a drone gets before its pilot has said anything at all. */
    public static final PilotInput NEUTRAL =
            new PilotInput(-1, (byte) 0, Float.NaN, Float.NaN, 0.0F, 0.0F, 0.0F, 0.0F);

    public boolean holds(int key) {
        return (this.keys & key) != 0;
    }

    public boolean hasGimbal() {
        return !Float.isNaN(this.gimbalYaw) && !Float.isNaN(this.gimbalPitch);
    }

    /**
     * The keys let go of and the sticks centred, the camera where it was pointed — and the throttle and the
     * arming switch where they were.
     */
    public PilotInput released() {
        byte arm = (byte) (this.keys & (DroneInputPayload.ARM_SET | DroneInputPayload.ARM_ON));
        return new PilotInput(this.seq, arm, this.gimbalYaw, this.gimbalPitch,
                0.0F, 0.0F, 0.0F, this.throttle);
    }

    /** The same input under another number — a guessed tick standing in for one not yet arrived. */
    public PilotInput withSeq(int newSeq) {
        return new PilotInput(newSeq, this.keys, this.gimbalYaw, this.gimbalPitch,
                this.roll, this.pitch, this.yaw, this.throttle);
    }

    /**
     * The same input made safe to fly on: the sticks held to their travel, and anything that is not a number
     * at all — which a client is perfectly able to send — turned into nothing rather than let loose in the
     * flight model, where one NaN poisons the drone's position for good.
     */
    public PilotInput sanitized() {
        boolean gimbal = Float.isFinite(this.gimbalYaw) && Float.isFinite(this.gimbalPitch);
        return new PilotInput(this.seq, this.keys,
                gimbal ? this.gimbalYaw : Float.NaN, gimbal ? this.gimbalPitch : Float.NaN,
                axis(this.roll, -1.0F), axis(this.pitch, -1.0F), axis(this.yaw, -1.0F),
                axis(this.throttle, 0.0F));
    }

    private static float axis(float value, float min) {
        return Float.isFinite(value) ? Mth.clamp(value, min, 1.0F) : 0.0F;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.seq);
        buf.writeByte(this.keys);
        buf.writeFloat(this.gimbalYaw);
        buf.writeFloat(this.gimbalPitch);
        buf.writeFloat(this.roll);
        buf.writeFloat(this.pitch);
        buf.writeFloat(this.yaw);
        buf.writeFloat(this.throttle);
    }

    public static PilotInput read(FriendlyByteBuf buf) {
        return new PilotInput(buf.readVarInt(), buf.readByte(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }
}
