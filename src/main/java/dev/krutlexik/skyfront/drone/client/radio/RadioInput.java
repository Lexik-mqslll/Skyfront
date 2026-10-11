package dev.krutlexik.skyfront.drone.client.radio;

import dev.krutlexik.skyfront.drone.DroneMod;
import dev.krutlexik.skyfront.drone.radio.RadioProfile;
import org.lwjgl.glfw.GLFW;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/** The transmitter, read live. */
public final class RadioInput {
    private static RadioProfile profile;

    /** GLFW's id for the selected device, or -1 when it is not plugged in. */
    private static int joystick = -1;
    private static long lastScanNanos;
    /** How often to look for the device while it is missing. */
    private static final long RESCAN_NANOS = 1_000_000_000L;

    private static float[] axes = new float[0];
    private static boolean[] buttons = new boolean[0];

    private static double rollSum;
    private static double pitchSum;
    private static double yawSum;
    private static double throttleSum;
    private static double sampleTime;
    private static long lastFrameNanos;

    private static float roll;
    private static float pitch;
    private static float yaw;
    private static float throttle;

    /** Whether the throttle has been seen at the bottom since the flight began. */
    private static boolean throttleSeenLow;
    /** Low enough to count as the bottom of the throttle. */
    public static final float THROTTLE_LOW = 0.05F;

    private static boolean armWas;
    private static boolean thermalWas;
    private static boolean selfDestructWas;
    private static float cameraWas = Float.NaN;

    /** The pilot's setup, loaded from disk the first time anything asks for it. */
    public static RadioProfile profile() {
        if (profile == null) {
            profile = RadioStore.load();
        }
        return profile;
    }

    public static void save() {
        RadioStore.save(profile());
    }

    /** One joystick the system can see. */
    public record Device(int id, String name, String guid) {
    }

    /** Every joystick plugged in right now. */
    public static List<Device> devices() {
        List<Device> found = new ArrayList<>();
        for (int id = GLFW.GLFW_JOYSTICK_1; id <= GLFW.GLFW_JOYSTICK_LAST; id++) {
            if (GLFW.glfwJoystickPresent(id)) {
                String name = GLFW.glfwGetJoystickName(id);
                String guid = GLFW.glfwGetJoystickGUID(id);
                found.add(new Device(id, name != null ? name : "#" + id, guid != null ? guid : ""));
            }
        }
        return found;
    }

    /** Makes this the radio. */
    public static void select(Device device) {
        RadioProfile setup = profile();
        setup.deviceGuid = device.guid();
        setup.deviceName = device.name();
        joystick = device.id();
        throttleSeenLow = false;
        DroneMod.LOGGER.info("Radio set to {} ({})", device.name(), device.guid());
    }

    /** Whether the selected radio is plugged in. */
    public static boolean connected() {
        return joystick >= 0;
    }

    /** The radio's name as the system reports it, or the saved one while it is unplugged. */
    public static String deviceName() {
        return profile().deviceName;
    }

    /** Whether the radio is flying the drone: switched on, and plugged in. */
    public static boolean active() {
        return profile().enabled && connected();
    }

    /** Reads the device as it is this instant. */
    public static void poll() {
        RadioProfile setup = profile();
        if (joystick >= 0 && !GLFW.glfwJoystickPresent(joystick)) {
            DroneMod.LOGGER.info("Radio {} unplugged", setup.deviceName);
            joystick = -1;
        }
        if (joystick < 0) {
            long now = System.nanoTime();
            if (now - lastScanNanos < RESCAN_NANOS) {
                clearReadings();
                return;
            }
            lastScanNanos = now;
            joystick = find(setup);
            if (joystick < 0) {
                clearReadings();
                return;
            }
            throttleSeenLow = false;
            DroneMod.LOGGER.info("Radio {} found", setup.deviceName);
        }

        FloatBuffer axisBuffer = GLFW.glfwGetJoystickAxes(joystick);
        ByteBuffer buttonBuffer = GLFW.glfwGetJoystickButtons(joystick);
        if (axisBuffer == null) {
            clearReadings();
            return;
        }
        if (axes.length != axisBuffer.remaining()) {
            axes = new float[axisBuffer.remaining()];
        }
        axisBuffer.get(axes);
        int buttonCount = buttonBuffer != null ? buttonBuffer.remaining() : 0;
        if (buttons.length != buttonCount) {
            buttons = new boolean[buttonCount];
        }
        for (int i = 0; i < buttonCount; i++) {
            buttons[i] = buttonBuffer.get(i) == GLFW.GLFW_PRESS;
        }
    }

    /** The selected device's current id, by GUID first and by name if that finds nothing. */
    private static int find(RadioProfile setup) {
        if (setup.deviceGuid.isEmpty() && setup.deviceName.isEmpty()) {
            return -1;
        }
        int byName = -1;
        for (Device device : devices()) {
            if (!setup.deviceGuid.isEmpty() && device.guid().equals(setup.deviceGuid)) {
                return device.id();
            }
            if (byName < 0 && device.name().equals(setup.deviceName)) {
                byName = device.id();
            }
        }
        return byName;
    }

    private static void clearReadings() {
        if (axes.length != 0) {
            axes = new float[0];
        }
        if (buttons.length != 0) {
            buttons = new boolean[0];
        }
    }

    /** The raw axes as they stood at the last poll, -1..1. */
    public static float[] axes() {
        return axes;
    }

    /** The raw buttons as they stood at the last poll. */
    public static boolean[] buttons() {
        return buttons;
    }

    /** One rendered frame: read the radio and add this frame's sticks to the tick's running total. */
    public static void sampleFrame() {
        long now = System.nanoTime();
        double dt = lastFrameNanos == 0L ? 0.0D : Math.min((now - lastFrameNanos) / 1.0E9D, 0.2D);
        lastFrameNanos = now;

        poll();
        if (!active() || dt <= 0.0D) {
            return;
        }
        RadioProfile setup = profile();
        rollSum += setup.stick(setup.roll, axes) * dt;
        pitchSum += setup.stick(setup.pitch, axes) * dt;
        yawSum += setup.stick(setup.yaw, axes) * dt;
        throttleSum += setup.throttleOf(axes) * dt;
        sampleTime += dt;
    }

    /** Closes the tick: the sticks become their average over the frames it was made of. */
    public static void latchTick() {
        RadioProfile setup = profile();
        if (sampleTime > 1.0E-6D) {
            roll = (float) (rollSum / sampleTime);
            pitch = (float) (pitchSum / sampleTime);
            yaw = (float) (yawSum / sampleTime);
            throttle = (float) (throttleSum / sampleTime);
        } else {
            poll();
            roll = setup.stick(setup.roll, axes);
            pitch = setup.stick(setup.pitch, axes);
            yaw = setup.stick(setup.yaw, axes);
            throttle = setup.throttleOf(axes);
        }
        rollSum = 0.0D;
        pitchSum = 0.0D;
        yawSum = 0.0D;
        throttleSum = 0.0D;
        sampleTime = 0.0D;

        if (throttle <= THROTTLE_LOW) {
            throttleSeenLow = true;
        }
    }

    /** Roll, -1..1, positive right. */
    public static float roll() {
        return roll;
    }

    /** Pitch, -1..1, positive FORWARD — which is nose down. */
    public static float pitch() {
        return pitch;
    }

    /** Yaw, -1..1, positive right. */
    public static float yaw() {
        return yaw;
    }

    /** Throttle, 0..1 — held at nothing until the stick has been seen at the bottom. */
    public static float throttle() {
        return throttleSeenLow ? throttle : 0.0F;
    }

    /** Whether the throttle stick is down, which is the one position arming will accept. */
    public static boolean throttleLow() {
        return throttle <= THROTTLE_LOW;
    }

    /**
     * Starts a flight: the throttle has to be seen at the bottom again, and every switch is taken as it
     * stands without doing anything about it.
     */
    public static void beginFlight() {
        throttleSeenLow = false;
        rebaseline();
        cameraWas = Float.NaN;
    }

    /** Takes every switch as it stands now, without acting on any of it. */
    public static void rebaseline() {
        poll();
        RadioProfile setup = profile();
        armWas = setup.auxOn(setup.arm, axes, buttons);
        thermalWas = setup.auxOn(setup.thermal, axes, buttons);
        selfDestructWas = setup.auxOn(setup.selfDestruct, axes, buttons);
        cameraWas = setup.camera.assigned() ? setup.auxLevel(setup.camera, axes) : Float.NaN;
    }

    /** +1 if the switch went on since last tick, -1 if it went off, 0 if it did not move. */
    public static int armEdge() {
        RadioProfile setup = profile();
        boolean now = setup.auxOn(setup.arm, axes, buttons);
        int edge = now == armWas ? 0 : (now ? 1 : -1);
        armWas = now;
        return edge;
    }

    public static int thermalEdge() {
        RadioProfile setup = profile();
        boolean now = setup.auxOn(setup.thermal, axes, buttons);
        int edge = now == thermalWas ? 0 : (now ? 1 : -1);
        thermalWas = now;
        return edge;
    }

    /** +1 if the self-destruct switch went on since last tick, -1 if it went off, 0 if it did not move. */
    public static int selfDestructEdge() {
        RadioProfile setup = profile();
        boolean now = setup.auxOn(setup.selfDestruct, axes, buttons);
        int edge = now == selfDestructWas ? 0 : (now ? 1 : -1);
        selfDestructWas = now;
        return edge;
    }

    /** Where the camera knob has been turned to, 0..1 — or NaN if it has not moved since last asked. */
    public static float cameraMove() {
        RadioProfile setup = profile();
        if (!setup.camera.assigned()) {
            return Float.NaN;
        }
        float now = setup.auxLevel(setup.camera, axes);
        if (!Float.isNaN(cameraWas) && Math.abs(now - cameraWas) < 0.01F) {
            return Float.NaN;
        }
        cameraWas = now;
        return now;
    }

    private RadioInput() {
    }
}
