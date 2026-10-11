package dev.krutlexik.skyfront.drone.client;

import org.jetbrains.annotations.Nullable;

/**
 * The video link dying, played out the way a real drone's app does it: the picture stops on the last frame
 * that arrived, drains to grey a moment later as the app gives up on the feed, and only then admits it with a
 * "No signal" badge.
 */
public final class DroneSignalLoss {
    /** Why the feed dropped. */
    public enum Cause {
        /** Flown into something hard enough to break the link. */
        CRASH,
        /** Out of controller range. */
        OUT_OF_RANGE,
        /** Shot down or broken under us. */
        DESTROYED
    }

    /** Ticks the frozen frame keeps its colour before the app gives up and drains it to grey. */
    static final int COLOUR_TICKS = 12;
    /** Further ticks of grey before the "No signal" badge appears. */
    private static final int GREY_TICKS = 8;

    @Nullable
    private static Cause cause;
    private static int ticks;

    public static boolean isActive() {
        return cause != null;
    }

    /** True once the app has given up on the feed: frame drained to grey, readings dashed out. */
    public static boolean isBlanked() {
        return isActive() && ticks >= COLOUR_TICKS;
    }

    /** True once the "No signal" badge has replaced the flight-status text. */
    public static boolean showNoSignal() {
        return isActive() && ticks >= COLOUR_TICKS + GREY_TICKS;
    }

    /** Ticks elapsed since the link dropped. */
    static int elapsed() {
        return ticks;
    }

    @Nullable
    public static Cause cause() {
        return cause;
    }

    /** Starts the sequence. */
    public static boolean begin(Cause reason) {
        if (cause != null) {
            return false;
        }
        cause = reason;
        ticks = 0;
        return true;
    }

    /** Advances the sequence. */
    public static void tick() {
        if (cause != null) {
            ticks++;
        }
    }

    public static void clear() {
        cause = null;
        ticks = 0;
    }

    private DroneSignalLoss() {
    }
}
