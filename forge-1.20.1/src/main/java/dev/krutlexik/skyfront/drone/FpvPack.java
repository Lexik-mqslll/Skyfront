package dev.krutlexik.skyfront.drone;

import net.minecraft.util.Mth;

/** The lithium pack behind the interceptor, as a pure function of how much charge is left in it. */
public final class FpvPack {
    /** A full cell, resting. */
    public static final double CELL_FULL = 4.2D;
    /** The first warning: still plenty of flying left, but the pack is past halfway. */
    public static final double CELL_LOW = 3.5D;
    /** Put it down. */
    public static final double CELL_LAND = 3.0D;
    /** Where the motors start losing the airframe. */
    public static final double CELL_CUTOFF = 2.75D;
    /** Nothing usable left at all. */
    public static final double CELL_EMPTY = 2.6D;

    /** Resting volts per cell against state of charge. */
    private static final double[][] CELL_CURVE = {
            {0.00D, CELL_EMPTY}, {0.03D, CELL_CUTOFF}, {0.06D, 2.88D}, {0.10D, CELL_LAND},
            {0.25D, 3.20D}, {0.475D, CELL_LOW}, {0.70D, 3.80D}, {0.85D, 4.00D}, {1.00D, CELL_FULL},
    };

    /** Resting volts of one cell at this state of charge. */
    public static double openCircuitCellVolts(float charge) {
        double soc = Mth.clamp(charge, 0.0F, 1.0F);
        for (int i = 1; i < CELL_CURVE.length; i++) {
            if (soc <= CELL_CURVE[i][0]) {
                double span = CELL_CURVE[i][0] - CELL_CURVE[i - 1][0];
                double factor = span > 0.0D ? (soc - CELL_CURVE[i - 1][0]) / span : 0.0D;
                return Mth.lerp(factor, CELL_CURVE[i - 1][1], CELL_CURVE[i][1]);
            }
        }
        return CELL_CURVE[CELL_CURVE.length - 1][1];
    }

    /** What the motors can still deliver at {@link #CELL_CUTOFF}. */
    private static final double FADE_AT_CUTOFF = 0.45D;
    /** And what is left at the very bottom: nothing. */
    private static final double FADE_AT_EMPTY = 0.0D;

    /** What the motors can still deliver, 0..1, at this per-cell voltage. */
    public static float powerFade(double cellVolts) {
        if (cellVolts >= CELL_LAND) {
            return 1.0F;
        }
        if (cellVolts >= CELL_CUTOFF) {
            double through = (CELL_LAND - cellVolts) / (CELL_LAND - CELL_CUTOFF);
            return (float) Mth.lerp(Mth.clamp(through, 0.0D, 1.0D), 1.0D, FADE_AT_CUTOFF);
        }
        double through = (CELL_CUTOFF - cellVolts) / (CELL_CUTOFF - CELL_EMPTY);
        return (float) Mth.lerp(Mth.clamp(through, 0.0D, 1.0D), FADE_AT_CUTOFF, FADE_AT_EMPTY);
    }

    private FpvPack() {
    }
}
