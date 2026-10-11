package dev.krutlexik.skyfront.drone.client;

import dev.krutlexik.skyfront.drone.entity.DroneEntity;
import dev.krutlexik.skyfront.drone.entity.DroneFlightState;
import dev.krutlexik.skyfront.drone.entity.InterceptorDroneEntity;
import dev.krutlexik.skyfront.drone.entity.PilotInput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

/** The pilot's own copy of the drone, flown ahead of the server. */
final class DronePrediction {
    /** Inputs kept for replay: over six seconds, far past any round trip worth flying on. */
    private static final int HISTORY = 128;
    private static final int MASK = HISTORY - 1;

    /** What is left of a correction after each tick. 0.7 is gone to a twentieth in about eight. */
    private static final double OFFSET_KEEP = 0.7D;
    private static final float TURN_BLEED = 0.3F;
    /** Beyond these a correction is not drift but a different place altogether, and is taken outright. */
    private static final double SNAP_DISTANCE = 8.0D;
    private static final float SNAP_ANGLE = (float) Math.toRadians(90.0D);
    private static final double OFFSET_FLOOR = 1.0E-4D;

    private final int[] seqs = new int[HISTORY];
    private final PilotInput[] inputs = new PilotInput[HISTORY];
    private final DroneFlightState[] states = new DroneFlightState[HISTORY];

    /** The newest input flown, or MIN_VALUE before the first. */
    private int newestSeq = Integer.MIN_VALUE;

    /** Where the flight actually is — what is shown is this plus {@link #offset}. */
    @Nullable
    private Vec3 flown;
    private Vec3 offset = Vec3.ZERO;
    /** What is left of an attitude correction: shown = turn × flown. */
    private final Quaternionf turn = new Quaternionf();
    /** The yaw and pitch the flight itself left the copy at, on an airframe flown on sticks. */
    private float flownYaw;
    private float flownPitch;
    private boolean flownAngles;

    /** The server's latest word, taken up at the next tick rather than mid-frame. */
    @Nullable
    private DroneFlightState pendingState;
    private int pendingAck;
    /** Set when the copy itself has been swapped out and its internal state cannot be trusted. */
    private boolean forceResync;

    /** Corrections made this flight — for the debug readout, and nothing else. */
    private int corrections;

    DronePrediction() {
        for (int i = 0; i < HISTORY; i++) {
            this.states[i] = new DroneFlightState();
        }
        reset();
    }

    /** A flight starting or ending: nothing predicted, nothing owed. */
    void reset() {
        java.util.Arrays.fill(this.seqs, Integer.MIN_VALUE);
        java.util.Arrays.fill(this.inputs, null);
        this.newestSeq = Integer.MIN_VALUE;
        this.flown = null;
        this.offset = Vec3.ZERO;
        this.turn.identity();
        this.flownAngles = false;
        this.pendingState = null;
        this.forceResync = false;
        this.corrections = 0;
    }

    int corrections() {
        return this.corrections;
    }

    /** The server's state after input {@code ackSeq}; only the newest one is ever needed. */
    void acceptServerState(int ackSeq, DroneFlightState state) {
        this.pendingAck = ackSeq;
        this.pendingState = state;
    }

    /**
     * The copy was swapped for a fresh entity (see {@code DroneControl#rebind}): whatever matched before, the
     * new one must be put back to the server's word and replayed.
     */
    void forceResync() {
        this.forceResync = true;
    }

    /**
     * One live tick: put right whatever the server has said since the last one, then fly this tick's input on
     * the copy, and lay the result out to be shown.
     */
    void step(DroneEntity drone, PilotInput input) {
        Vec3 shownBefore = drone.position();
        float shownYaw = drone.getYRot();
        float shownPitch = drone.getXRot();
        float shownYawO = drone.yRotO;
        float shownPitchO = drone.xRotO;
        if (this.flown == null) {
            this.flown = shownBefore;
        }
        boolean sticks = drone instanceof InterceptorDroneEntity && drone.flownOnSticks();
        if (sticks && this.flownAngles) {
            drone.setYRot(this.flownYaw);
            drone.setXRot(this.flownPitch);
        }

        reconcile(drone);

        drone.setPos(this.flown.x, this.flown.y, this.flown.z);
        drone.flyPilotStep(input);
        drone.notePredictedImpact();
        record(drone, input);
        this.flown = drone.position();
        this.flownYaw = drone.getYRot();
        this.flownPitch = drone.getXRot();
        this.flownAngles = true;

        this.offset = this.offset.scale(OFFSET_KEEP);
        if (this.offset.lengthSqr() < OFFSET_FLOOR * OFFSET_FLOOR) {
            this.offset = Vec3.ZERO;
        }
        this.turn.slerp(new Quaternionf(), TURN_BLEED).normalize();

        Vec3 shown = this.flown.add(this.offset);
        drone.setPos(shown.x, shown.y, shown.z);
        drone.xo = shownBefore.x;
        drone.yo = shownBefore.y;
        drone.zo = shownBefore.z;

        if (sticks) {
            ((InterceptorDroneEntity) drone).presentPredictedAttitude(this.turn, shownYaw, shownPitch);
        } else {
            drone.setYRot(shownYaw);
            drone.setXRot(shownPitch);
            drone.yRotO = shownYawO;
            drone.xRotO = shownPitchO;
        }
    }

    private void record(DroneEntity drone, PilotInput input) {
        int slot = input.seq() & MASK;
        this.seqs[slot] = input.seq();
        this.inputs[slot] = input;
        drone.captureFlightState(this.states[slot]);
        this.newestSeq = input.seq();
    }

    @Nullable
    private DroneFlightState predictedFor(int seq) {
        int slot = seq & MASK;
        return this.seqs[slot] == seq ? this.states[slot] : null;
    }

    @Nullable
    private PilotInput inputFor(int seq) {
        int slot = seq & MASK;
        return this.seqs[slot] == seq ? this.inputs[slot] : null;
    }

    private void reconcile(DroneEntity drone) {
        DroneFlightState server = this.pendingState;
        if (server == null) {
            return;
        }
        this.pendingState = null;
        int ack = this.pendingAck;

        DroneFlightState predicted = predictedFor(ack);
        if (!this.forceResync && predicted != null && predicted.matches(server)) {
            return;
        }
        this.forceResync = false;
        this.corrections++;

        Vec3 flownBefore = this.flown;
        Quaternionf attitudeBefore = drone instanceof InterceptorDroneEntity airframe
                ? new Quaternionf(airframe.flightAttitude()) : null;

        drone.restoreFlightState(server);
        if (predicted != null) {
            predicted.copyFrom(server);
        }
        drone.setReplaying(true);
        try {
            if (this.newestSeq != Integer.MIN_VALUE) {
                int from = Math.max(ack + 1, this.newestSeq - HISTORY + 1);
                for (int seq = from; seq <= this.newestSeq; seq++) {
                    PilotInput input = inputFor(seq);
                    if (input == null) {
                        continue;
                    }
                    drone.flyPilotStep(input);
                    drone.captureFlightState(this.states[seq & MASK]);
                }
            }
        } finally {
            drone.setReplaying(false);
        }
        this.flown = drone.position();

        if (flownBefore != null) {
            this.offset = this.offset.add(flownBefore.subtract(this.flown));
            if (this.offset.lengthSqr() > SNAP_DISTANCE * SNAP_DISTANCE) {
                this.offset = Vec3.ZERO;
            }
        }
        if (attitudeBefore != null && drone instanceof InterceptorDroneEntity airframe) {
            Quaternionf after = new Quaternionf(airframe.flightAttitude()).conjugate();
            this.turn.mul(attitudeBefore).mul(after).normalize();
            float angle = 2.0F * (float) Math.acos(Math.min(1.0F, Math.abs(this.turn.w)));
            if (angle > SNAP_ANGLE) {
                this.turn.identity();
            }
        }
    }
}
