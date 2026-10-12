package dev.krutlexik.skyfront.drone.entity;

import net.minecraft.network.FriendlyByteBuf;
import org.joml.Quaternionf;

/** Everything a drone's flight carries from one tick into the next — the whole of it, not just where the drone is. */
public final class DroneFlightState {
    public double x;
    public double y;
    public double z;
    public double vx;
    public double vy;
    public double vz;
    public float yRot;
    public float xRot;
    /** The Mavics' eased key axes. */
    public float inputSideways;
    public float inputVertical;
    public float inputForward;
    /** Synced on their own too, but taken from here on a replay so it starts from the same tick. */
    public float charge;
    public boolean armed;

    public boolean airframe;
    public boolean attitudeReady;
    public final Quaternionf attitude = new Quaternionf();
    public double ratePitch;
    public double rateYaw;
    public double rateRoll;
    public double demandPitch;
    public double demandYaw;
    public double demandRoll;
    public int holdX;
    public int holdY;
    public int holdZ;
    public int lastVerticalDir;
    public float leanRateX;
    public float leanRateZ;
    public float power;
    public double planeThrottle;
    /** Blocks flown on a dead-stick glide since the motor died, so the range cap replays identically. */
    public double glideRun;
    /** Whether it is still sitting on a launch rail, so a replay holds it there or launches it alike. */
    public boolean railSeated;
    /** The rail's incline, degrees: a copy built for a far link-up has no other way to know it. */
    public float railPitch;

    public DroneFlightState copyFrom(DroneFlightState other) {
        this.x = other.x;
        this.y = other.y;
        this.z = other.z;
        this.vx = other.vx;
        this.vy = other.vy;
        this.vz = other.vz;
        this.yRot = other.yRot;
        this.xRot = other.xRot;
        this.inputSideways = other.inputSideways;
        this.inputVertical = other.inputVertical;
        this.inputForward = other.inputForward;
        this.charge = other.charge;
        this.armed = other.armed;
        this.airframe = other.airframe;
        this.attitudeReady = other.attitudeReady;
        this.attitude.set(other.attitude);
        this.ratePitch = other.ratePitch;
        this.rateYaw = other.rateYaw;
        this.rateRoll = other.rateRoll;
        this.demandPitch = other.demandPitch;
        this.demandYaw = other.demandYaw;
        this.demandRoll = other.demandRoll;
        this.holdX = other.holdX;
        this.holdY = other.holdY;
        this.holdZ = other.holdZ;
        this.lastVerticalDir = other.lastVerticalDir;
        this.leanRateX = other.leanRateX;
        this.leanRateZ = other.leanRateZ;
        this.power = other.power;
        this.planeThrottle = other.planeThrottle;
        this.glideRun = other.glideRun;
        this.railSeated = other.railSeated;
        this.railPitch = other.railPitch;
        return this;
    }

    public DroneFlightState copy() {
        return new DroneFlightState().copyFrom(this);
    }

    private static final double POSITION_TOLERANCE = 1.0E-3D;
    private static final double VELOCITY_TOLERANCE = 1.0E-3D;
    private static final double RATE_TOLERANCE = 0.5D;
    private static final double AXIS_TOLERANCE = 1.0E-3D;
    /** Quaternion dot product short of 1 that still counts as the same attitude: about a tenth of a degree. */
    private static final double ATTITUDE_TOLERANCE = 1.0E-6D;

    /** Whether a prediction landed close enough to the server's state to be left alone. */
    public boolean matches(DroneFlightState other) {
        if (Math.abs(this.x - other.x) > POSITION_TOLERANCE
                || Math.abs(this.y - other.y) > POSITION_TOLERANCE
                || Math.abs(this.z - other.z) > POSITION_TOLERANCE
                || Math.abs(this.vx - other.vx) > VELOCITY_TOLERANCE
                || Math.abs(this.vy - other.vy) > VELOCITY_TOLERANCE
                || Math.abs(this.vz - other.vz) > VELOCITY_TOLERANCE
                || Math.abs(this.inputSideways - other.inputSideways) > AXIS_TOLERANCE
                || Math.abs(this.inputVertical - other.inputVertical) > AXIS_TOLERANCE
                || Math.abs(this.inputForward - other.inputForward) > AXIS_TOLERANCE
                || this.armed != other.armed
                || this.airframe != other.airframe) {
            return false;
        }
        if (!this.airframe) {
            return true;
        }
        return this.attitudeReady == other.attitudeReady
                && Math.abs(Math.abs(this.attitude.dot(other.attitude)) - 1.0D) < ATTITUDE_TOLERANCE
                && Math.abs(this.ratePitch - other.ratePitch) < RATE_TOLERANCE
                && Math.abs(this.rateYaw - other.rateYaw) < RATE_TOLERANCE
                && Math.abs(this.rateRoll - other.rateRoll) < RATE_TOLERANCE
                && Math.abs(this.demandPitch - other.demandPitch) < RATE_TOLERANCE
                && Math.abs(this.demandYaw - other.demandYaw) < RATE_TOLERANCE
                && Math.abs(this.demandRoll - other.demandRoll) < RATE_TOLERANCE
                && this.holdX == other.holdX && this.holdY == other.holdY && this.holdZ == other.holdZ
                && this.lastVerticalDir == other.lastVerticalDir
                && Math.abs(this.leanRateX - other.leanRateX) < AXIS_TOLERANCE
                && Math.abs(this.leanRateZ - other.leanRateZ) < AXIS_TOLERANCE
                && Math.abs(this.power - other.power) < AXIS_TOLERANCE
                && Math.abs(this.planeThrottle - other.planeThrottle) < AXIS_TOLERANCE
                && Math.abs(this.glideRun - other.glideRun) < POSITION_TOLERANCE
                && this.railSeated == other.railSeated;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeDouble(this.x);
        buf.writeDouble(this.y);
        buf.writeDouble(this.z);
        buf.writeDouble(this.vx);
        buf.writeDouble(this.vy);
        buf.writeDouble(this.vz);
        buf.writeFloat(this.yRot);
        buf.writeFloat(this.xRot);
        buf.writeFloat(this.inputSideways);
        buf.writeFloat(this.inputVertical);
        buf.writeFloat(this.inputForward);
        buf.writeFloat(this.charge);
        buf.writeBoolean(this.armed);
        buf.writeBoolean(this.airframe);
        if (!this.airframe) {
            return;
        }
        buf.writeBoolean(this.attitudeReady);
        buf.writeFloat(this.attitude.x);
        buf.writeFloat(this.attitude.y);
        buf.writeFloat(this.attitude.z);
        buf.writeFloat(this.attitude.w);
        buf.writeDouble(this.ratePitch);
        buf.writeDouble(this.rateYaw);
        buf.writeDouble(this.rateRoll);
        buf.writeDouble(this.demandPitch);
        buf.writeDouble(this.demandYaw);
        buf.writeDouble(this.demandRoll);
        buf.writeVarInt(this.holdX);
        buf.writeVarInt(this.holdY);
        buf.writeVarInt(this.holdZ);
        buf.writeByte(this.lastVerticalDir);
        buf.writeFloat(this.leanRateX);
        buf.writeFloat(this.leanRateZ);
        buf.writeFloat(this.power);
        buf.writeDouble(this.planeThrottle);
        buf.writeDouble(this.glideRun);
        buf.writeBoolean(this.railSeated);
        buf.writeFloat(this.railPitch);
    }

    public static DroneFlightState read(FriendlyByteBuf buf) {
        DroneFlightState state = new DroneFlightState();
        state.x = buf.readDouble();
        state.y = buf.readDouble();
        state.z = buf.readDouble();
        state.vx = buf.readDouble();
        state.vy = buf.readDouble();
        state.vz = buf.readDouble();
        state.yRot = buf.readFloat();
        state.xRot = buf.readFloat();
        state.inputSideways = buf.readFloat();
        state.inputVertical = buf.readFloat();
        state.inputForward = buf.readFloat();
        state.charge = buf.readFloat();
        state.armed = buf.readBoolean();
        state.airframe = buf.readBoolean();
        if (!state.airframe) {
            return state;
        }
        state.attitudeReady = buf.readBoolean();
        state.attitude.set(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
        state.ratePitch = buf.readDouble();
        state.rateYaw = buf.readDouble();
        state.rateRoll = buf.readDouble();
        state.demandPitch = buf.readDouble();
        state.demandYaw = buf.readDouble();
        state.demandRoll = buf.readDouble();
        state.holdX = buf.readVarInt();
        state.holdY = buf.readVarInt();
        state.holdZ = buf.readVarInt();
        state.lastVerticalDir = buf.readByte();
        state.leanRateX = buf.readFloat();
        state.leanRateZ = buf.readFloat();
        state.power = buf.readFloat();
        state.planeThrottle = buf.readDouble();
        state.glideRun = buf.readDouble();
        state.railSeated = buf.readBoolean();
        state.railPitch = buf.readFloat();
        return state;
    }
}
