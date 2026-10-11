package dev.krutlexik.skyfront.drone.client;

import dev.krutlexik.skyfront.config.SkyfrontConfig;
import dev.krutlexik.skyfront.drone.entity.DroneEntity;
import net.minecraft.util.Mth;

/**
 * The interceptor's battery, as the OSD sees it: a pack of cells with a real discharge curve and a real
 * internal resistance, so the numbers on screen behave the way a pilot expects them to.
 */
public final class FpvBattery {
    /** Internal resistance of one cell, ohms. */
    private static final double CELL_RESISTANCE = 0.006D;

    /** Draw with the motors idling but armed, amps — the flight controller and the ESCs themselves. */
    private static final double IDLE_CURRENT = 1.5D;
    /** Draw with the motors stopped, amps. */
    private static final double DISARMED_CURRENT = 0.4D;

    private static final double HOVER_LOAD = 0.35D;
    private static final double STICK_LOAD = 0.50D;
    private static final double SPEED_LOAD = 0.25D;

    /** How quickly the load itself follows the sticks — motors spool, they do not step. */
    private static final double LOAD_RESPONSE = 0.25D;
    /** Sag arrives quickly and lets go slowly, which is what makes it read as a battery and not a gauge. */
    private static final double SAG_FALL = 0.45D;
    private static final double SAG_RECOVER = 0.12D;

    private static boolean armed;
    /**
     * Each drone's flight clock, by drone: the game time it was last armed at while it is armed, and the
     * reading it stopped at once disarmed.
     */
    private static final java.util.Map<java.util.UUID, Long> ARMED_AT = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, Integer> STOPPED_AT = new java.util.HashMap<>();
    /** The drone whose clock the OSD reads. */
    @org.jetbrains.annotations.Nullable
    private static DroneEntity reading;
    private static double load;
    private static double voltage;

    /**
     * The pilot taking a drone that is already armed: the pack is read live, and the flight clock carries on
     * from when it was armed — or, for a drone this client never saw armed, from now.
     */
    public static void arm(DroneEntity drone) {
        armed = true;
        reading = drone;
        load = 0.0D;
        voltage = openCircuitVolts(drone.getCharge());
        ARMED_AT.putIfAbsent(drone.getUUID(), drone.level().getGameTime());
    }

    /** The arming switch thrown: the flight clock starts again from nothing. */
    public static void armedNow(DroneEntity drone) {
        STOPPED_AT.remove(drone.getUUID());
        ARMED_AT.put(drone.getUUID(), drone.level().getGameTime());
        arm(drone);
    }

    /** The motors stopping. */
    public static void disarm(DroneEntity drone) {
        armed = false;
        reading = drone;
        load = 0.0D;
        Long since = ARMED_AT.remove(drone.getUUID());
        if (since != null) {
            STOPPED_AT.put(drone.getUUID(), (int) ((drone.level().getGameTime() - since) / 20L));
        }
        if (voltage <= 0.0D) {
            voltage = openCircuitVolts(drone.getCharge()) - sagVolts();
        }
    }

    /** The link going: there is no pack being read any more. */
    public static void reset() {
        armed = false;
        reading = null;
        load = 0.0D;
        voltage = 0.0D;
    }

    /** One client tick of a disarmed pack: it settles back toward its resting voltage. */
    public static void rest(DroneEntity drone) {
        double target = openCircuitVolts(drone.getCharge()) - sagVolts();
        voltage += (target - voltage) * SAG_RECOVER;
    }

    public static boolean isArmed() {
        return armed;
    }

    /** Seconds since the last arming, for the OSD's flight timer — stopped where it was once disarmed. */
    public static int armedSeconds() {
        DroneEntity drone = reading;
        if (drone == null) {
            return 0;
        }
        Long since = ARMED_AT.get(drone.getUUID());
        if (since != null) {
            return (int) Math.max(0L, (drone.level().getGameTime() - since) / 20L);
        }
        return STOPPED_AT.getOrDefault(drone.getUUID(), 0);
    }

    /** One client tick of the pack, run while the drone is being flown. */
    public static void tick(DroneEntity drone) {
        if (!armed) {
            arm(drone);
        }

        double speedKmh = drone.getDeltaMovement().length() * SkyfrontConfig.KMH_PER_BLOCK_TICK;
        double demand = drone.isGrounded()
                ? 0.0D
                : HOVER_LOAD
                + STICK_LOAD * drone.inputLoad()
                + SPEED_LOAD * Math.min(1.0D, speedKmh / Math.max(1.0D, topSpeedKmh(drone)));
        load += (Mth.clamp(demand, 0.0D, 1.0D) - load) * LOAD_RESPONSE;

        double target = openCircuitVolts(drone.getCharge()) - sagVolts();
        double response = target < voltage ? SAG_FALL : SAG_RECOVER;
        voltage += (target - voltage) * response;
    }

    /** Cells in the pack — 8S by default, so a full one reads a shade under 34 volts. */
    public static int cells() {
        return SkyfrontConfig.INTERCEPTOR_BATTERY_CELLS.get();
    }

    /** Pack voltage under whatever it is doing right now. */
    public static double packVolts() {
        return voltage;
    }

    /** The same reading per cell, which is how a pilot actually judges a pack. */
    public static double cellVolts() {
        return voltage / Math.max(1, cells());
    }

    /** Instantaneous draw, amps. */
    public static double amps() {
        return armed ? IDLE_CURRENT + load * SkyfrontConfig.INTERCEPTOR_PEAK_CURRENT.get() : DISARMED_CURRENT;
    }

    /** Capacity used so far, mAh — taken from the charge actually spent rather than integrated here. */
    /** The airframe's own top speed, or the interceptor's for anything that has none. */
    private static double topSpeedKmh(DroneEntity drone) {
        return drone instanceof dev.krutlexik.skyfront.drone.entity.InterceptorDroneEntity interceptor
                ? interceptor.topSpeedKmh() : SkyfrontConfig.INTERCEPTOR_TOP_SPEED.get();
    }

    public static int mahDrawn(DroneEntity drone) {
        return (int) Math.round((1.0F - drone.getCharge()) * SkyfrontConfig.INTERCEPTOR_BATTERY_MAH.get());
    }

    /** Volts the pack would show with nothing drawing from it. */
    private static double openCircuitVolts(float charge) {
        return dev.krutlexik.skyfront.drone.FpvPack.openCircuitCellVolts(charge) * cells();
    }

    private static double sagVolts() {
        return amps() * CELL_RESISTANCE * cells();
    }

    private FpvBattery() {
    }
}
