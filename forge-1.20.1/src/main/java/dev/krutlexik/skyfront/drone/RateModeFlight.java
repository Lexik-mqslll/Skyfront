package dev.krutlexik.skyfront.drone;

import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

/**
 * The flight maths behind the interceptor's rateMode mode, kept apart from the entity that flies with it so both
 * sides of the game and the tests can read the same curves.
 */
public final class RateModeFlight {
    /**
     * Betaflight's "actual rates": {@code centre} degrees/second per unit of stick at the middle of the
     * travel, opening out to {@code max} at the stop, with {@code expo} deciding how much of that opening
     * happens late.
     */
    public static double actualRate(double stick, double centre, double max, double expo) {
        stick = Mth.clamp(stick, -1.0D, 1.0D);
        double amount = Math.abs(stick);
        if (amount <= 0.0D) {
            return 0.0D;
        }
        centre = Math.max(0.0D, centre);
        max = Math.max(centre, max);
        expo = Mth.clamp(expo, 0.0D, 0.95D);

        double quadratic = amount * amount * (2.0D - amount);
        double cubic = amount * amount * amount;
        double blend = quadratic * (1.0D - expo) + cubic * expo;
        double rate = centre * amount + (max - centre) * blend;
        return stick < 0.0D ? -rate : rate;
    }

    /** One step of a first-order approach toward {@code target}; never overshoots. */
    public static double approach(double current, double target, double rate, double dt) {
        if (rate <= 0.0D) {
            return target;
        }
        double blend = 1.0D - Math.exp(-rate * Math.max(0.0D, dt));
        double value = current + (target - current) * Mth.clamp(blend, 0.0D, 1.0D);
        return Math.abs(value) < 5.0E-4D && Math.abs(target) < 5.0E-4D ? 0.0D : value;
    }

    /** Samples a curve stored as {@code {x, y, inTangent, outTangent}} points, with cubic Hermite interpolation. */
    private static double sampleCurve(double[][] points, double x) {
        if (x <= points[0][0]) {
            return points[0][1];
        }
        for (int i = 0; i < points.length - 1; i++) {
            double[] left = points[i];
            double[] right = points[i + 1];
            if (x <= right[0]) {
                double span = right[0] - left[0];
                double t = span > 0.0D ? (x - left[0]) / span : 0.0D;
                double t2 = t * t;
                double t3 = t2 * t;
                return (2.0D * t3 - 3.0D * t2 + 1.0D) * left[1]
                        + (t3 - 2.0D * t2 + t) * span * left[3]
                        + (-2.0D * t3 + 3.0D * t2) * right[1]
                        + (t3 - t2) * span * right[2];
            }
        }
        return points[points.length - 1][1];
    }

    /**
     * Thrust against airspeed along the airframe's own up axis — which is the axis the rotors push along, so
     * this is how fast the air is already moving through them.
     */
    private static final double[][] THRUST_CURVE = {
            {-0.1D, 0.8D, 0.0D, 0.0D},
            {30.2D, 1.0D, -0.00083333254D, -0.00083333254D},
            {59.9D, 0.75D, -0.01390821D, -0.01390821D},
            {102.1D, 0.0D, 0.0D, 0.0D},
    };

    /** Ground effect against height above the floor, in centimetres. */
    private static final double[][] GROUND_CURVE = {
            {0.1D, 1.0D, 0.051369887D, 0.0D},
            {10.1D, 2.0D, 0.00028834687D, 0.00028834687D},
            {59.9D, 1.25D, -0.009389674D, -0.009389674D},
            {116.6D, 1.0D, 0.0D, 0.0D},
    };

    /** Airspeed at which the thrust curve reaches zero, km/h. */
    private static final double CURVE_ZERO_KMH = 102.1D;
    /** How far past the configured top speed the rotors run out altogether. */
    private static final double AIRSPEED_STRETCH = 1.25D;

    /**
     * How much of its thrust the airframe still makes with air coming through the rotors this fast, for an
     * airframe whose configured top speed is {@code topSpeedKmh}.
     */
    public static double thrustAtAirspeed(double axisKmh, double topSpeedKmh) {
        double scale = CURVE_ZERO_KMH / (Math.max(topSpeedKmh, 1.0D) * AIRSPEED_STRETCH);
        return Math.max(0.0D, sampleCurve(THRUST_CURVE, axisKmh * scale));
    }

    /** What the rotors make standing still — the figure a hover is worked out from. */
    public static double thrustAtRest() {
        return thrustAtAirspeed(0.0D, 1.0D);
    }

    /** How much the floor is helping, this many blocks below the airframe. */
    public static double groundEffect(double blocksAboveGround) {
        return sampleCurve(GROUND_CURVE, blocksAboveGround * 100.0D);
    }

    /** Above this there is no ground effect left, so there is no point looking for the floor. */
    public static final double GROUND_EFFECT_REACH = 1.2D;

    /**
     * The roll to hand {@code ViewportEvent.ComputeCameraAngles} for a camera bolted to an airframe banked
     * this far, in the sign {@link net.minecraft.client.Camera} expects.
     */
    public static float cameraRoll(float bodyRoll) {
        return -bodyRoll;
    }

    /**
     * Where a camera bolted to this airframe is looking, as the three angles {@code
     * ViewportEvent.ComputeCameraAngles} wants: {@code {yaw, pitch, roll}}.
     */
    public static float[] cameraAnglesOf(Quaternionfc attitude, float mountTilt) {
        Quaternionf camera = new Quaternionf(attitude).rotateX(mountTilt * Mth.DEG_TO_RAD);
        Vector3f euler = camera.normalize().getEulerAnglesYXZ(new Vector3f());
        return new float[] {
                Mth.wrapDegrees(180.0F - euler.y() * Mth.RAD_TO_DEG),
                -euler.x() * Mth.RAD_TO_DEG,
                cameraRoll(euler.z() * Mth.RAD_TO_DEG),
        };
    }

    private RateModeFlight() {
    }
}
