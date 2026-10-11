package dev.krutlexik.skyfront.drone.radio;

import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the mod knows about the pilot's transmitter, and every sum that turns what it sends into sticks
 * the flight model can use.
 */
public final class RadioProfile {
    /** Whether the radio flies the drone at all, rather than the mouse and keyboard. */
    public boolean enabled = false;

    /** The device, by GLFW's GUID for it and by name. */
    public String deviceGuid = "";
    public String deviceName = "";

    public Channel roll = new Channel(0);
    public Channel pitch = new Channel(1);
    public Channel throttle = new Channel(2);
    public Channel yaw = new Channel(3);

    public Aux arm = new Aux();
    public Aux thermal = new Aux();
    public Aux camera = new Aux();
    /** Sets the warhead off on the interceptors and the Molniya, the moment it is switched ON. */
    public Aux selfDestruct = new Aux();

    /** Centre deadband on roll, pitch and yaw, as a fraction of stick travel. */
    public float deadband = 0.02F;

    /** A gamepad rather than a transmitter. */
    public boolean gamepad = false;

    /** Where each raw axis actually starts, rests and stops. */
    public List<Calibration> calibration = new ArrayList<>();

    public static final float MAX_DEADBAND = 0.15F;

    /** One stick: the raw axis that carries it, and whether it runs the wrong way. */
    public static final class Channel {
        public int axis;
        public boolean reverse;

        public Channel() {
        }

        public Channel(int axis) {
            this.axis = axis;
        }
    }

    /** Where a switch is read from. */
    public enum Source {
        /** Not assigned. */
        NONE,
        /** An axis — how EdgeTX sends channels five to eight. */
        AXIS,
        /** A button — how EdgeTX sends channels nine and up. */
        BUTTON
    }

    /** How a switch is read. */
    public enum AuxMode {
        /** Its position is the state: up is on, down is off. */
        SWITCH,
        /** Each press flips the state. */
        BUTTON
    }

    /** One switch on the radio and the job it has been given. */
    public static final class Aux {
        public Source source = Source.NONE;
        public int index;
        /** True when "on" is the switch's LOW position, or the button released. */
        public boolean reverse;
        public AuxMode mode = AuxMode.SWITCH;

        public boolean assigned() {
            return this.source != Source.NONE;
        }
    }

    /** Where one raw axis really goes. */
    public static final class Calibration {
        public float min = -1.0F;
        public float centre = 0.0F;
        public float max = 1.0F;

        public Calibration() {
        }

        public Calibration(float min, float centre, float max) {
            this.min = min;
            this.centre = centre;
            this.max = max;
        }
    }

    private static final Calibration IDENTITY = new Calibration();

    /** Below this much travel an axis was plainly never moved, and its calibration is not to be trusted. */
    private static final float MIN_TRAVEL = 0.2F;

    public Calibration calibrationFor(int axis) {
        if (axis < 0 || axis >= this.calibration.size() || this.calibration.get(axis) == null) {
            return IDENTITY;
        }
        return this.calibration.get(axis);
    }

    /**
     * A self-centring stick, -1..1, through its own three points: each half of the travel is scaled on its
     * own, so a gimbal that rests a touch off the middle still reads zero at rest AND reaches a full one at
     * both stops.
     */
    public static float centred(float raw, Calibration cal) {
        if (cal.max - cal.min < MIN_TRAVEL) {
            return Mth.clamp(raw, -1.0F, 1.0F);
        }
        float centre = Mth.clamp(cal.centre, cal.min + 0.01F, cal.max - 0.01F);
        if (raw >= centre) {
            return Mth.clamp((raw - centre) / Math.max(0.01F, cal.max - centre), 0.0F, 1.0F);
        }
        return Mth.clamp((raw - centre) / Math.max(0.01F, centre - cal.min), -1.0F, 0.0F);
    }

    /** An axis that does not centre — a throttle, a slider, a switch — as 0..1 between its two ends. */
    public static float span(float raw, Calibration cal) {
        if (cal.max - cal.min < MIN_TRAVEL) {
            return Mth.clamp((raw + 1.0F) * 0.5F, 0.0F, 1.0F);
        }
        return Mth.clamp((raw - cal.min) / (cal.max - cal.min), 0.0F, 1.0F);
    }

    /**
     * Deadband that does not cost any travel: inside it reads nothing, and the rest of the stick is stretched
     * back out to a full one at the stop rather than simply having the middle cut out.
     */
    public static float deadband(float value, float deadband) {
        float band = Mth.clamp(deadband, 0.0F, MAX_DEADBAND);
        float magnitude = Math.abs(value);
        if (magnitude <= band) {
            return 0.0F;
        }
        float scaled = Math.min(1.0F, (magnitude - band) / (1.0F - band));
        return value < 0.0F ? -scaled : scaled;
    }

    /** One of the three self-centring sticks, -1..1, positive right / forward. */
    public float stick(Channel channel, float[] axes) {
        if (channel.axis < 0 || channel.axis >= axes.length) {
            return 0.0F;
        }
        float value = centred(axes[channel.axis], calibrationFor(channel.axis));
        if (channel.reverse) {
            value = -value;
        }
        return deadband(value, this.deadband);
    }

    /** The throttle, 0..1, positive up: from the bottom stop on a transmitter, from the middle on a gamepad. */
    public float throttleOf(float[] axes) {
        if (this.gamepad) {
            return Math.max(0.0F, throttleStick(axes));
        }
        if (this.throttle.axis < 0 || this.throttle.axis >= axes.length) {
            return 0.0F;
        }
        float value = span(axes[this.throttle.axis], calibrationFor(this.throttle.axis));
        return this.throttle.reverse ? 1.0F - value : value;
    }

    /** Where the throttle stick itself stands, -1 at the bottom to 1 at the top — for drawing it. */
    public float throttleStick(float[] axes) {
        if (this.gamepad) {
            return stick(this.throttle, axes);
        }
        return throttleOf(axes) * 2.0F - 1.0F;
    }

    /** Whether a switch is in its "on" position — or, for a momentary one, held. */
    public boolean auxOn(Aux aux, float[] axes, boolean[] buttons) {
        switch (aux.source) {
            case AXIS -> {
                if (aux.index < 0 || aux.index >= axes.length) {
                    return false;
                }
                float level = span(axes[aux.index], calibrationFor(aux.index)) * 2.0F - 1.0F;
                if (aux.reverse) {
                    level = -level;
                }
                return level > 0.5F;
            }
            case BUTTON -> {
                if (aux.index < 0 || aux.index >= buttons.length) {
                    return false;
                }
                return buttons[aux.index] != aux.reverse;
            }
            default -> {
                return false;
            }
        }
    }

    /** A knob or slider, 0..1 — for jobs with a position rather than a state, like the camera's angle. */
    public float auxLevel(Aux aux, float[] axes) {
        if (aux.source != Source.AXIS || aux.index < 0 || aux.index >= axes.length) {
            return 0.0F;
        }
        float value = span(axes[aux.index], calibrationFor(aux.index));
        return aux.reverse ? 1.0F - value : value;
    }

    /**
     * A calibration being taken: every axis's furthest reach either way, gathered while the pilot sweeps the
     * sticks and flicks the switches, and then its resting point once they let go.
     */
    public static final class Capture {
        private float[] min = new float[0];
        private float[] max = new float[0];

        public void accumulate(float[] axes) {
            if (axes.length > this.min.length) {
                int old = this.min.length;
                this.min = java.util.Arrays.copyOf(this.min, axes.length);
                this.max = java.util.Arrays.copyOf(this.max, axes.length);
                for (int i = old; i < axes.length; i++) {
                    this.min[i] = axes[i];
                    this.max[i] = axes[i];
                }
            }
            for (int i = 0; i < axes.length; i++) {
                this.min[i] = Math.min(this.min[i], axes[i]);
                this.max[i] = Math.max(this.max[i], axes[i]);
            }
        }

        /** How far an axis has been seen to travel so far — for showing the pilot it is working. */
        public float travel(int axis) {
            return axis < this.min.length ? this.max[axis] - this.min[axis] : 0.0F;
        }

        /** The finished calibration, with each axis's resting point taken from where it sits now. */
        public List<Calibration> finish(float[] resting) {
            List<Calibration> result = new ArrayList<>();
            for (int i = 0; i < this.min.length; i++) {
                if (this.max[i] - this.min[i] < MIN_TRAVEL) {
                    result.add(new Calibration());
                } else {
                    float centre = i < resting.length ? resting[i] : (this.min[i] + this.max[i]) * 0.5F;
                    result.add(new Calibration(this.min[i], Mth.clamp(centre, this.min[i], this.max[i]), this.max[i]));
                }
            }
            return result;
        }
    }

    /**
     * What moving a control told us: which axis or button it is, and whether the direction the pilot was
     * asked for came out negative — in which case the channel is reversed.
     */
    public record Found(Source source, int index, boolean reverse) {
    }

    /** Finds a control by watching it move. */
    public static final class Detection {
        private final float[] baseAxes;
        private final boolean[] baseButtons;
        private final boolean buttons;

        /** Movement, in raw units of a -1..1 axis, that counts as the pilot having moved something. */
        private static final float THRESHOLD = 0.5F;

        public Detection(float[] axes, boolean[] buttons, boolean acceptButtons) {
            this.baseAxes = axes.clone();
            this.baseButtons = buttons.clone();
            this.buttons = acceptButtons;
        }

        /** The control that has moved, or null while nothing has moved far enough yet. */
        public Found poll(float[] axes, boolean[] currentButtons) {
            int best = -1;
            float bestDelta = 0.0F;
            for (int i = 0; i < Math.min(axes.length, this.baseAxes.length); i++) {
                float delta = axes[i] - this.baseAxes[i];
                if (Math.abs(delta) > Math.abs(bestDelta)) {
                    best = i;
                    bestDelta = delta;
                }
            }
            if (best >= 0 && Math.abs(bestDelta) >= THRESHOLD) {
                return new Found(Source.AXIS, best, bestDelta < 0.0F);
            }
            if (this.buttons) {
                for (int i = 0; i < Math.min(currentButtons.length, this.baseButtons.length); i++) {
                    if (currentButtons[i] != this.baseButtons[i]) {
                        return new Found(Source.BUTTON, i, !currentButtons[i]);
                    }
                }
            }
            return null;
        }
    }
}
