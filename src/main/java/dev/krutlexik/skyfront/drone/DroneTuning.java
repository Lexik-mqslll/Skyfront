package dev.krutlexik.skyfront.drone;

import dev.krutlexik.skyfront.config.SkyfrontConfig;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/** The config figures the flight model is built from, as the SERVER has them. */
public final class DroneTuning {
    /** Every figure the flight simulation depends on. */
    public enum Value {
        MAVIC_CRASH_SPEED(() -> SkyfrontConfig.MAVIC_CRASH_SPEED.get()),
        MAVIC_MAX_DESCENT_SPEED(() -> SkyfrontConfig.MAVIC_MAX_DESCENT_SPEED.get()),
        MAVIC_FLIGHT_SECONDS(() -> (double) SkyfrontConfig.MAVIC_FLIGHT_SECONDS.get()),
        MAVIC_3T_TOP_SPEED(() -> SkyfrontConfig.MAVIC_3T_TOP_SPEED.get()),
        MAVIC_3T_FLIGHT_SECONDS(() -> (double) SkyfrontConfig.MAVIC_3T_FLIGHT_SECONDS.get()),
        INTERCEPTOR_TOP_SPEED(() -> SkyfrontConfig.INTERCEPTOR_TOP_SPEED.get()),
        INTERCEPTOR_CRASH_SPEED(() -> SkyfrontConfig.INTERCEPTOR_CRASH_SPEED.get()),
        INTERCEPTOR_FLIGHT_SECONDS(() -> (double) SkyfrontConfig.INTERCEPTOR_FLIGHT_SECONDS.get()),
        INTERCEPTOR_RATE_MODE(() -> SkyfrontConfig.INTERCEPTOR_RATE_MODE.get() ? 1.0D : 0.0D),
        INTERCEPTOR_ROLL_PITCH_RATE(() -> SkyfrontConfig.INTERCEPTOR_ROLL_PITCH_RATE.get()),
        INTERCEPTOR_YAW_RATE(() -> SkyfrontConfig.INTERCEPTOR_YAW_RATE.get()),
        INTERCEPTOR_EXPO(() -> SkyfrontConfig.INTERCEPTOR_EXPO.get()),
        INTERCEPTOR_THRUST_TO_WEIGHT(() -> SkyfrontConfig.INTERCEPTOR_THRUST_TO_WEIGHT.get());

        private final Supplier<Double> local;

        Value(Supplier<Double> local) {
            this.local = local;
        }
    }

    private static final Value[] VALUES = Value.values();

    /** What the server sent, client side; null when nothing has been (or on the server itself). */
    @Nullable
    private static volatile double[] fromServer;

    public static double get(Value value) {
        double[] received = fromServer;
        return received != null ? received[value.ordinal()] : value.local.get();
    }

    public static boolean flag(Value value) {
        return get(value) > 0.5D;
    }

    /** Server side: this server's own figures, for sending down. */
    public static double[] snapshot() {
        double[] values = new double[VALUES.length];
        for (Value value : VALUES) {
            values[value.ordinal()] = value.local.get();
        }
        return values;
    }

    /** Client side: the server's figures arrived. */
    public static void acceptFromServer(double[] values) {
        if (values.length == VALUES.length) {
            fromServer = values.clone();
        } else {
            DroneMod.LOGGER.warn("Drone tuning from the server has {} figures, expected {} — ignoring it",
                    values.length, VALUES.length);
        }
    }

    /** Client side, on leaving a server: whatever comes next answers to its own figures. */
    public static void clear() {
        fromServer = null;
    }

    private DroneTuning() {
    }
}
