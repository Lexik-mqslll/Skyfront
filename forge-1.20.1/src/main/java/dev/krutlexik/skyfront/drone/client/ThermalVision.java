package dev.krutlexik.skyfront.drone.client;

import dev.krutlexik.skyfront.drone.entity.DroneEntity;
import dev.krutlexik.skyfront.drone.entity.ThermalDroneEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Whether the thermal drone's camera is currently on heat rather than on light — the switch itself, and
 * nothing else.
 */
public final class ThermalVision {
    private static boolean active;

    public static boolean isOn() {
        return active;
    }

    /** True while the pilot is flying a drone whose camera has the mode at all. */
    public static boolean isAvailable(@Nullable DroneEntity drone) {
        return drone != null && drone.hasThermalCamera();
    }

    /** The thermal key (T by default) while flying a thermal drone: heat on, heat off. */
    public static void toggle(@Nullable DroneEntity drone) {
        if (isAvailable(drone)) {
            active = !active;
        }
    }

    /** The feed is over: the mode goes with it, so the next flight starts on the plain camera. */
    public static void clear() {
        active = false;
    }

    private ThermalVision() {
    }
}
