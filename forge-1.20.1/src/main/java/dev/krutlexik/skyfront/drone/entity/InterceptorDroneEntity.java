package dev.krutlexik.skyfront.drone.entity;

import dev.krutlexik.skyfront.config.SkyfrontConfig;
import dev.krutlexik.skyfront.drone.DroneTuning;
import dev.krutlexik.skyfront.drone.RateModeFlight;
import dev.krutlexik.skyfront.drone.GrenadeAttachments;
import dev.krutlexik.skyfront.drone.registry.DroneItems;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

/**
 * An interceptor drone: a small, very fast airframe with its warhead built in, flown into a Shahed to bring
 * it down.
 */
public class InterceptorDroneEntity extends DroneEntity {
    /** Degrees of lean rate added per tick of holding a stick, and the hold it saturates at. */
    private static final float RATE_STEP = 0.3F;
    private static final int RATE_HOLD_CAP = 5;
    /** What the lean rate keeps each tick once the stick is released — and how fast a landed drone levels. */
    private static final float RATE_DECAY = 0.7F;
    /** Most the airframe will roll (bank into a turn), degrees. */
    private static final float MAX_TILT = 30.0F;
    /** Most the nose drops when flying flat out, degrees. */
    private static final float MAX_NOSE_TILT = 78.0F;

    private static final double AIR_DRAG = 0.965D;
    private static final double VERTICAL_DRAG = 0.7D;
    private static final double GROUND_DRAG = 0.8D;

    private static final float MAX_POWER = 0.2F;
    private static final float MIN_POWER = 0.02F;
    private static final float POWER_UP_STEP = 0.01F;
    private static final float POWER_DOWN_STEP = 0.02F;
    private static final float POWER_TRIM_STEP = 0.005F;
    private static final float LIFT_PER_POWER = 0.6F;
    /** Direct climb the throttle keys add per tick of holding, blocks; capped so it cannot outrun the drone. */
    private static final double CLIMB_STEP = 0.05D;
    private static final int CLIMB_HOLD_CAP = 40;
    /** What the collective is working against. */
    private static final double GRAVITY = 0.06D;

    /**
     * The lean rate a stick held down settles at: it gains {@code RATE_STEP * RATE_HOLD_CAP} a tick and keeps
     * {@code RATE_DECAY} of the total, so it converges on this.
     */
    private static final double STEADY_RATE =
            RATE_STEP * RATE_HOLD_CAP * RATE_DECAY / (1.0D - RATE_DECAY);

    /** A little more thrust than the sums need, so the cap does the limiting rather than the drag. */
    private static final double THRUST_MARGIN = 1.06D;

    /** Flight-controller runs per game tick. */
    private static final int RATE_MODE_SUBSTEPS = 8;
    /** Degrees/second the middle of the stick is geared for, before the curve opens out to the configured full rate. */
    private static final double CENTRE_RATE = 150.0D;
    private static final double YAW_CENTRE_RATE = 120.0D;
    /** Setpoint filter for the commanded rate, per second (a 22 Hz low-pass). */
    private static final double SETPOINT_FILTER = 2.0D * Math.PI * 22.0D;
    /** How quickly the airframe reaches a commanded rate, per second. */
    private static final double RATE_RESPONSE = 5.5D;
    private static final double YAW_RATE_RESPONSE = 3.0D;
    /** Nothing turns faster than this however it is asked, degrees/second. */
    private static final double HARD_RATE_LIMIT = 1200.0D;
    /**
     * How hard the rotor disc brakes the airframe along its own thrust axis with the throttle shut, as a
     * fraction of that speed per tick.
     */
    private static final double DISC_BRAKE = 0.09D;
    /**
     * Drag, as the fraction of speed kept per tick, is not a constant: it is worked back from the thrust and
     * the configured top speed so that full throttle, fully pitched over, settles at the figure in the
     * config.
     */
    private static final double RATE_MODE_MIN_DRAG = 0.80D;
    private static final double RATE_MODE_MAX_DRAG = 0.995D;

    /** Full-throttle forward acceleration, as a multiple of gravity: a healthy climb rate on the level. */
    private static final double PLANE_THRUST_WEIGHT = 3.0D;
    /** Idle thrust, so a plane at rest on its prop still creeps rather than stalling on the spot. */
    private static final double PLANE_IDLE_THROTTLE = 0.12D;
    /** How quickly the engine follows the throttle: a plane spools up, it does not snap. */
    private static final double PLANE_SPOOL = 0.13D;

    /** The speed the wings are trimmed to hold level flight at, as a fraction of the configured top. */
    private static final double PLANE_CRUISE_FRACTION = 0.55D;
    /**
     * Rotation speed, as a fraction of cruise: the aircraft is held on its gear until the airspeed reaches
     * this, so it has to make a proper take-off run and gather speed down the runway before the wings will
     * fly it off, rather than ballooning into the air the moment it starts to move.
     */
    private static final double PLANE_ROTATE_FRACTION = 0.92D;
    /** Airspeed at which the controls reach full authority, as a fraction of cruise. */
    private static final double PLANE_AUTHORITY_SPEED = 0.6D;
    /** What the controls can still do at a standstill: enough to nose over into a dive, no more. */
    private static final double PLANE_MIN_AUTHORITY = 0.12D;

    /** Full-authority turn rates, degrees a second. */
    private static final double PLANE_ROLL_RATE = 160.0D;
    private static final double PLANE_PITCH_RATE = 70.0D;
    private static final double PLANE_YAW_RATE = 22.0D;
    /** How sharply a rate closes on what the stick asks: crisper than the heavy quad. */
    private static final double PLANE_RATE_RESPONSE = 7.0D;
    private static final double PLANE_YAW_RESPONSE = 4.0D;
    /** Hands off the roll, the wings level themselves at this rate per degree of bank. */
    private static final double PLANE_ROLL_LEVEL = 1.6D;
    /** Wing lift kept with the motor dead, up to the glide range: a touch less than powered flight. */
    private static final double PLANE_GLIDE_LIFT = 0.88D;
    /** How far a dead-stick glide carries, in blocks. */
    private static final double PLANE_GLIDE_RANGE = 250.0D;
    private static final double PLANE_GLIDE_RANGE_HARD = 280.0D;

    /** Drag: a small constant bleed plus a quadratic term, set so it settles at the configured top speed. */
    private static final double PLANE_DRAG_BASE = 0.006D;
    /** Forward drag per tick with the motor dead: a clean airframe coasting. */
    private static final double PLANE_GLIDE_DRAG = 0.992D;
    /** Fraction of sideways (sideslip) speed kept each tick: nearly none — the fin will not allow it. */
    private static final double PLANE_KEEP_SIDE = 0.55D;
    /** Fraction of the across-wing vertical slip kept: lift and gravity own that axis, not momentum. */
    private static final double PLANE_KEEP_VERT = 0.80D;
    /** Rolling friction on the runway, per tick. */
    private static final double PLANE_GROUND_FRICTION = 0.99D;

    /**
     * The airframe's attitude — the whole of it, which is why this is a quaternion and not the pair of angles
     * an entity carries.
     */
    private final Quaternionf attitude = new Quaternionf();
    /** Whether {@link #attitude} has been taken over yet. */
    private boolean attitudeReady;
    /** The last two attitudes this client was sent, and a scratch to interpolate them into. */
    private final Quaternionf clientAttitude = new Quaternionf();
    private final Quaternionf clientAttitudeOld = new Quaternionf();
    private final Quaternionf clientBlend = new Quaternionf();
    /** Body rates, degrees/second: X pitches the nose up, Y yaws, Z banks to port. */
    private double ratePitch;
    private double rateYaw;
    private double rateRoll;
    /** The same three after the setpoint filter — what the airframe is actually being asked for. */
    private double demandPitch;
    private double demandYaw;
    private double demandRoll;

    /** The attitude, for every other client in the world as much as for the pilot. */
    private static final EntityDataAccessor<Quaternionf> DATA_ATTITUDE =
            SynchedEntityData.defineId(InterceptorDroneEntity.class, EntityDataSerializers.QUATERNION);

    /** The camera, which on this drone is a gimbal and not the airframe: it may look up as well as down. */
    private static final float CAMERA_UP_LIMIT = -60.0F;
    private static final float CAMERA_DOWN_LIMIT = 90.0F;

    private int holdX;
    private int holdY;
    private int holdZ;
    /** Which way the throttle was worked last tick (1 up, -1 down, 0 neither), so a reversal restarts the ramp. */
    private int lastVerticalDir;
    private float leanRateX;
    private float leanRateZ;
    private float power = MIN_POWER;

    private static final EntityDataAccessor<Boolean> DATA_RAIL_SEATED =
            SynchedEntityData.defineId(InterceptorDroneEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_RAIL_PITCH =
            SynchedEntityData.defineId(InterceptorDroneEntity.class, EntityDataSerializers.FLOAT);
    /** Throttle the pilot has to open to before the airframe leaves the rails. */
    private static final float RAIL_LAUNCH_THROTTLE = 0.5F;
    /** The speed it leaves the rails at, as a share of the speed its wings are trimmed for. */
    private static final double RAIL_LAUNCH_SPEED = 1.1D;

    /**
     * Lays the airframe on a launch rail: at {@code pos}, heading {@code yawDeg} up it, nose raised {@code
     * pitchDeg}.
     */
    public void seatOnRail(Vec3 pos, float yawDeg, float pitchDeg) {
        setPos(pos);
        setYRot(yawDeg);
        this.yRotO = yawDeg;
        this.entityData.set(DATA_RAIL_SEATED, true);
        this.entityData.set(DATA_RAIL_PITCH, pitchDeg);
        holdOnRail();
    }

    public boolean isRailSeated() {
        return this.entityData.get(DATA_RAIL_SEATED);
    }

    /** Sitting on the rail: still, at the rails' angle, its attitude told to everyone watching. */
    private void holdOnRail() {
        this.attitude.identity()
                .rotateY((180.0F - getYRot()) * Mth.DEG_TO_RAD)
                .rotateX(this.entityData.get(DATA_RAIL_PITCH) * Mth.DEG_TO_RAD);
        this.attitudeReady = true;
        this.ratePitch = 0.0D;
        this.rateYaw = 0.0D;
        this.rateRoll = 0.0D;
        this.demandPitch = 0.0D;
        this.demandYaw = 0.0D;
        this.demandRoll = 0.0D;
        setDeltaMovement(Vec3.ZERO);
        publishAttitude();
    }

    /** Standing on a rail counts as standing on the ground: no drain, nothing flying it. */
    @Override
    public boolean isGrounded() {
        return isRailSeated() || super.isGrounded();
    }

    /** The fixed-wing engine's spooled throttle, 0..1, lagging the stick (see {@link #flyPlane}). */
    private double planeThrottle = PLANE_IDLE_THROTTLE;
    /** Where the throttle stick was the moment the pilot let go of the link, 0..1. */
    private float heldThrottle;
    /**
     * Blocks flown on the glide since the motor died in the air, reset the moment it is armed again or back
     * on the ground.
     */
    private double glideRun;

    /** Share of the way the drawn lean closes on the velocity-implied lean per tick. */
    private static final float TILT_EASE = 0.35F;
    private float clientNose;
    private float clientNoseOld;
    private float clientBank;
    private float clientBankOld;

    private static final int WARHEAD_EFFECT = 2;

    /** Closing speed at which the contact fuse arms, in blocks/tick — about 11 km/h. */
    private static final double FUSE_SPEED = 0.15D;

    /**
     * Closing speed along the nose, blocks/tick, at which a nose-fused airframe's warhead goes off on
     * whatever its nose runs into — about 3.6 km/h, so even a slow nudge into a wall does it.
     */
    private static final double NOSE_FUSE_SPEED = 0.05D;

    /** Where the tick's move began, for telling what part of it something stopped (see impactSpeed). */
    @Nullable
    private Vec3 impactFrom;

    public InterceptorDroneEntity(EntityType<? extends InterceptorDroneEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ATTITUDE, new Quaternionf());
        this.entityData.define(DATA_RAIL_SEATED, false);
        this.entityData.define(DATA_RAIL_PITCH, 0.0F);
    }

    /** Whether this airframe is being flown the way a real FPV quad is. */
    public boolean rateMode() {
        return this.rateModeOverride != null ? this.rateModeOverride : DroneTuning.flag(DroneTuning.Value.INTERCEPTOR_RATE_MODE);
    }

    /** In rateMode the camera is bolted on and the mouse is a stick: an input's gimbal means nothing. */
    @Override
    public boolean flownOnSticks() {
        return rateMode();
    }

    /** Whether this airframe flies as a fixed-wing aircraft rather than as a quad — the Molniya does. */
    public boolean fliesAsAircraft() {
        return false;
    }

    /** The camera mount's uptilt a pilot starts from on this airframe, degrees. */
    public float defaultCameraTilt() {
        return 25.0F;
    }

    /** Whether the pilot can set the bolted camera to another angle (the scroll wheel, or a knob on the radio). */
    public boolean tiltableCamera() {
        return true;
    }

    /**
     * Whether the warhead rides in the nose on a contact fuse, so that any nose-first hit on a block sets it
     * off, however slow (see {@link #NOSE_FUSE_SPEED}).
     */
    public boolean noseFused() {
        return false;
    }

    /**
     * Whether this airframe's picture comes down an analog video link — soft, grainy, and breaking up with
     * distance (see {@code AnalogFeedPass}).
     */
    public boolean analogVideo() {
        return false;
    }

    /**
     * Keeps the throttle the pilot was holding when the link goes, before the sticks are released: a drone
     * left in the air flies on at it rather than being caught by an autopilot (see {@link #heldThrottle}).
     */
    @Override
    public void setController(@Nullable java.util.UUID id) {
        if (id == null && isControlled()) {
            this.heldThrottle = stickThrottle();
        } else if (id != null) {
            this.heldThrottle = 0.0F;
        }
        super.setController(id);
    }

    /** Puts one airframe on one flight model whatever the config says. */
    public void forceRateMode(boolean rateMode) {
        this.rateModeOverride = rateMode;
        this.attitudeReady = false;
    }

    /** Blocks glided since the motor died in the air. */
    public double glideRun() {
        return this.glideRun;
    }

    /** Presets the glide gauge so a test can fly the far end of a dead-stick glide without the run-up. */
    public void forceGlideRun(double blocks) {
        this.glideRun = blocks;
    }

    @Nullable
    private Boolean rateModeOverride;

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if (rateMode() && !this.attitudeReady && !isControlled()) {
                if (isRailSeated()) {
                    holdOnRail();
                } else {
                    attitudeFromYaw();
                    publishAttitude();
                }
            }
            return;
        }
        if (rateMode() && this.locallyControlled) {
            return;
        }
        if (isWrecked()) {
            return;
        }
        this.clientNoseOld = this.clientNose;
        this.clientBankOld = this.clientBank;
        if (rateMode()) {
            if (this.playedAttitude != null) {
                this.attitude.set(this.playedAttitude);
                this.attitudeReady = true;
                shiftClientAttitude();
            } else {
                readAttitude();
            }
            return;
        }
        this.clientNose += (tiltFromTravel(true) - this.clientNose) * TILT_EASE;
        this.clientBank += (tiltFromTravel(false) - this.clientBank) * TILT_EASE;
    }

    @Override
    public double controlRange() {
        return SkyfrontConfig.INTERCEPTOR_CONTROL_RANGE.get();
    }

    @Override
    public int fullFlightTicks() {
        return (int) DroneTuning.get(DroneTuning.Value.INTERCEPTOR_FLIGHT_SECONDS) * 20;
    }

    @Override
    public double crashSpeedKmh() {
        return DroneTuning.get(DroneTuning.Value.INTERCEPTOR_CRASH_SPEED);
    }

    @Override
    protected double topSpeed() {
        return topSpeedKmh() / SkyfrontConfig.KMH_PER_BLOCK_TICK;
    }

    /** The top speed, blocks per tick — for the renderer, which sits outside this package. */
    public double topSpeedPerTick() {
        return Math.max(1.0E-3D, topSpeed());
    }

    /**
     * The speed, blocks per tick, a fixed-wing's wings are trimmed to hold level flight at with the nose on
     * the horizon (see {@link #flyPlane}).
     */
    public double trimSpeed() {
        return topSpeedPerTick() * PLANE_CRUISE_FRACTION;
    }

    /** The fastest this airframe flies, km/h — the interceptor's from the config. */
    public double topSpeedKmh() {
        return DroneTuning.get(DroneTuning.Value.INTERCEPTOR_TOP_SPEED);
    }

    /** Rate-mode thrust-to-weight at full throttle — the interceptor's from the config. */
    protected double thrustToWeight() {
        return DroneTuning.get(DroneTuning.Value.INTERCEPTOR_THRUST_TO_WEIGHT);
    }

    /** The throttle that holds it level in still air in rateMode mode, 0..1: the lighter, the lower. */
    public double hoverThrottle() {
        return 1.0D / thrustToWeight();
    }

    /** How much quicker than the interceptor this airframe reaches the rate the sticks ask for. */
    protected double agility() {
        return 1.0D;
    }

    /** Thrust per unit of lean rate. */
    @Override
    protected float thrust() {
        double needed = topSpeed() * (1.0D - AIR_DRAG) / AIR_DRAG * THRUST_MARGIN;
        return (float) (needed / STEADY_RATE);
    }

    @Override
    protected double ramSpeed() {
        return FUSE_SPEED;
    }

    @Override
    public double lookGearing() {
        return SkyfrontConfig.INTERCEPTOR_LOOK_SENSITIVITY.get();
    }

    /** The interceptor keeps its brisk gimbal: the fixed per-frame share it has always flown with. */
    @Override
    public double gimbalLag() {
        return 0.0D;
    }

    @Override
    protected double maxHitpoints() {
        return SkyfrontConfig.INTERCEPTOR_HITPOINTS.get();
    }

    /** It carries no camera worth the name, no stores, and — for now — no OSD. */
    @Override
    protected boolean carriesStores() {
        return false;
    }

    /** Flown on a flight controller's own screen, in the Betaflight font — not a camera app's. */
    @Override
    public Osd osdStyle() {
        return Osd.BETAFLIGHT;
    }

    /** It has a motor loop of its own — the flat whine of an FPV quad rather than the Mavic's buzz. */
    @Override
    public boolean hasEngineSound() {
        return true;
    }

    /** No autolanding. */
    @Override
    public boolean autolandsWhenFlat() {
        return false;
    }

    /**
     * The two ways this airframe's motors stop short of being destroyed: the pilot throwing the arming
     * switch, and the pack going flat under them.
     */
    @Override
    public boolean motorsDead() {
        return super.motorsDead() || !isArmed() || packFade() <= 0.0F;
    }

    /** The motors are dead until the pilot throws the arming switch. */
    @Override
    public boolean requiresArming() {
        return true;
    }

    /** No zoom lens: this is an FPV camera bolted to the frame, not a camera app. */
    @Override
    public boolean hasCameraZoom() {
        return false;
    }

    @Override
    public float minPitch() {
        return CAMERA_UP_LIMIT;
    }

    @Override
    public float maxPitch() {
        return CAMERA_DOWN_LIMIT;
    }

    /**
     * How hard it is being worked, for the OSD's throttle reading and its battery: the collective above its
     * idle trim, and whatever the sticks are asking of the airframe on top.
     */
    @Override
    protected float computeInputLoad() {
        if (rateMode()) {
            return Mth.clamp(stickThrottle(), 0.0F, 1.0F);
        }
        float collective = (this.power - MIN_POWER) / (MAX_POWER - MIN_POWER);
        float sticks = (float) ((Math.abs(this.leanRateX) + Math.abs(this.leanRateZ)) / (2.0D * STEADY_RATE));
        return Mth.clamp(0.6F * collective + 0.4F * sticks, 0.0F, 1.0F);
    }

    @Override
    public ItemStack packedItem() {
        return new ItemStack(DroneItems.INTERCEPTOR_DRONE.get());
    }

    /** One tick of flight, following SuperbWarfare's drone step for step. */
    @Override
    public void applyControlInput(boolean forward, boolean backward, boolean left, boolean right,
                                  boolean up, boolean down) {
        float wrapped = Mth.wrapDegrees(getYRot());
        this.yRotO += wrapped - getYRot();
        setYRot(wrapped);

        if (fliesAsAircraft() && isRailSeated()) {
            holdOnRail();
            if (motorsDead() || stickThrottle() < RAIL_LAUNCH_THROTTLE) {
                return;
            }
            this.entityData.set(DATA_RAIL_SEATED, false);
            Vector3f nose = this.attitude.transform(0.0F, 0.0F, -1.0F, new Vector3f());
            setDeltaMovement(new Vec3(nose.x(), nose.y(), nose.z()).scale(trimSpeed() * RAIL_LAUNCH_SPEED));
            this.planeThrottle = stickThrottle();
        }

        boolean grounded = isGrounded();
        float touchdownSpeed = grounded ? (float) getDeltaMovement().length() : 0.0F;

        if (motorsDead()) {
            if (fliesAsAircraft() && !grounded) {
                flyPlane(grounded, touchdownSpeed, false);
            } else {
                this.glideRun = 0.0D;
                tickDisarmed(grounded, touchdownSpeed);
            }
            return;
        }

        this.glideRun = 0.0D;

        if (fliesAsAircraft()) {
            flyPlane(grounded, touchdownSpeed, true);
            return;
        }

        if (rateMode()) {
            flyRateMode(grounded, touchdownSpeed);
            return;
        }

        if (!grounded) {
            if (right) {
                this.holdX++;
                this.leanRateX -= RATE_STEP * Math.min(this.holdX, RATE_HOLD_CAP);
            } else if (left) {
                this.holdX++;
                this.leanRateX += RATE_STEP * Math.min(this.holdX, RATE_HOLD_CAP);
            } else {
                this.holdX = 0;
            }

            if (forward) {
                this.holdZ++;
                this.leanRateZ -= RATE_STEP * Math.min(this.holdZ, RATE_HOLD_CAP);
            } else if (backward) {
                this.holdZ++;
                this.leanRateZ += RATE_STEP * Math.min(this.holdZ, RATE_HOLD_CAP);
            } else {
                this.holdZ = 0;
            }

            setDeltaMovement(getDeltaMovement().multiply(AIR_DRAG, VERTICAL_DRAG, AIR_DRAG));
        } else {
            setDeltaMovement(getDeltaMovement().multiply(GROUND_DRAG, 1.0D, GROUND_DRAG));
            this.holdX = 0;
            this.holdZ = 0;
        }

        int verticalDir = up ? 1 : down ? -1 : 0;
        if (verticalDir != this.lastVerticalDir) {
            this.holdY = 0;
        }
        this.lastVerticalDir = verticalDir;

        float fade = packFade();

        Vec3 velocity = getDeltaMovement();
        if (up) {
            this.holdY++;
            this.power = Math.min(this.power + POWER_UP_STEP * Math.min(this.holdY, RATE_HOLD_CAP), MAX_POWER);
            setDeltaMovement(velocity.x,
                    CLIMB_STEP * climbAuthority(fade) * Math.min(this.holdY, CLIMB_HOLD_CAP), velocity.z);
        } else if (down) {
            this.holdY++;
            this.power = Math.max(this.power - POWER_DOWN_STEP * Math.min(this.holdY, RATE_HOLD_CAP),
                    grounded ? 0.0F : 0.06F);
            setDeltaMovement(velocity.x, -CLIMB_STEP * Math.min(this.holdY, CLIMB_HOLD_CAP), velocity.z);
        } else {
            this.power = getDeltaMovement().y < 0.0D
                    ? Math.min(this.power + POWER_TRIM_STEP, MAX_POWER)
                    : Math.max(this.power - (grounded ? POWER_TRIM_STEP / 10.0F : POWER_TRIM_STEP), MIN_POWER);
        }

        this.leanRateX *= RATE_DECAY;
        this.leanRateZ *= RATE_DECAY;

        if (!grounded) {
            setDeltaMovement(getDeltaMovement().add(0.0D, this.power * LIFT_PER_POWER * fade - GRAVITY, 0.0D));
        }

        float thrust = thrust() * fade;
        double yawRad = getYRot() * Mth.DEG_TO_RAD;
        double sin = Mth.sin((float) yawRad);
        double cos = Mth.cos((float) yawRad);
        Vec3 forwardAxis = new Vec3(-sin, 0.0D, cos);
        Vec3 sideAxis = new Vec3(cos, 0.0D, sin);
        setDeltaMovement(getDeltaMovement()
                .add(sideAxis.scale(this.leanRateX * thrust))
                .add(forwardAxis.scale(-this.leanRateZ * thrust)));

        double cap = topSpeed();
        Vec3 capped = getDeltaMovement();
        if (cap > 0.0D) {
            double horizontal = capped.horizontalDistance();
            if (horizontal > cap) {
                capped = new Vec3(capped.x * cap / horizontal, capped.y, capped.z * cap / horizontal);
            }
            capped = new Vec3(capped.x, Mth.clamp(capped.y, -cap, cap), capped.z);
        }
        if (capped.lengthSqr() < 1.0E-7D) {
            capped = Vec3.ZERO;
        }
        setDeltaMovement(capped);

        Vec3 approach = getDeltaMovement();
        Vec3 from = position();
        recordImpact(from, approach, touchdownSpeed);
    }

    /** One tick with the motors dead — the arming switch off, or a pack with nothing left in it. */
    private void tickDisarmed(boolean grounded, float touchdownSpeed) {
        this.power = MIN_POWER;
        this.leanRateX = 0.0F;
        this.leanRateZ = 0.0F;
        this.holdX = 0;
        this.holdY = 0;
        this.holdZ = 0;
        this.lastVerticalDir = 0;

        Vec3 velocity = getDeltaMovement();
        if (grounded) {
            setDeltaMovement(velocity.x * GROUND_DRAG, Math.max(velocity.y, 0.0D), velocity.z * GROUND_DRAG);
            if (rateMode()) {
                attitudeFromYaw();
                publishAttitude();
            } else {
                this.attitudeReady = false;
            }
        } else {
            setDeltaMovement(velocity.x * AIR_DRAG, velocity.y - GRAVITY, velocity.z * AIR_DRAG);
        }

        Vec3 approach = getDeltaMovement();
        Vec3 from = position();
        recordImpact(from, approach, touchdownSpeed);
    }

    /** One tick of rateMode flight. */
    private void flyRateMode(boolean grounded, float touchdownSpeed) {
        if (!this.attitudeReady) {
            attitudeFromYaw();
        }
        float fade = packFade();
        boolean piloted = isPiloted();
        double dtTicks = 1.0D / RATE_MODE_SUBSTEPS;
        double dtSeconds = dtTicks / 20.0D;

        double maxRate = DroneTuning.get(DroneTuning.Value.INTERCEPTOR_ROLL_PITCH_RATE);
        double maxYawRate = DroneTuning.get(DroneTuning.Value.INTERCEPTOR_YAW_RATE);
        double expo = DroneTuning.get(DroneTuning.Value.INTERCEPTOR_EXPO);

        double topSpeedKmh = topSpeedKmh();
        double maxThrust = thrustToWeight() * GRAVITY;
        double flatOut = maxThrust * RateModeFlight.thrustAtAirspeed(topSpeedKmh, topSpeedKmh);
        double drag = Mth.clamp(1.0D - flatOut / Math.max(topSpeed(), 1.0E-3D),
                RATE_MODE_MIN_DRAG, RATE_MODE_MAX_DRAG);
        double dragPerStep = Math.pow(drag, dtTicks);

        double ground = RateModeFlight.groundEffect(heightAboveGround());

        Vector3f up = new Vector3f();
        for (int step = 0; step < RATE_MODE_SUBSTEPS; step++) {
            double demandPitchRaw;
            double demandYawRaw;
            double demandRollRaw;
            double throttle;
            if (piloted) {
                demandPitchRaw = RateModeFlight.actualRate(stickPitch(), CENTRE_RATE, maxRate, expo);
                demandRollRaw = -RateModeFlight.actualRate(stickRoll(), CENTRE_RATE, maxRate, expo);
                demandYawRaw = -RateModeFlight.actualRate(stickYaw(), YAW_CENTRE_RATE, maxYawRate, expo);
                throttle = stickThrottle();
            } else {
                demandPitchRaw = 0.0D;
                demandRollRaw = 0.0D;
                demandYawRaw = 0.0D;
                throttle = this.heldThrottle;
            }

            this.demandPitch = RateModeFlight.approach(this.demandPitch, demandPitchRaw, SETPOINT_FILTER, dtSeconds);
            this.demandYaw = RateModeFlight.approach(this.demandYaw, demandYawRaw, SETPOINT_FILTER, dtSeconds);
            this.demandRoll = RateModeFlight.approach(this.demandRoll, demandRollRaw, SETPOINT_FILTER, dtSeconds);

            this.ratePitch = closeRate(this.ratePitch, this.demandPitch * fade, RATE_RESPONSE * agility(), dtSeconds);
            this.rateYaw = closeRate(this.rateYaw, this.demandYaw * fade, YAW_RATE_RESPONSE * agility(), dtSeconds);
            this.rateRoll = closeRate(this.rateRoll, this.demandRoll * fade, RATE_RESPONSE * agility(), dtSeconds);

            turnAirframe(dtSeconds);

            this.attitude.transform(0.0F, 1.0F, 0.0F, up);
            Vec3 lift = new Vec3(up.x(), up.y(), up.z());
            Vec3 velocity = getDeltaMovement();
            double climbKmh = velocity.dot(lift) * SkyfrontConfig.KMH_PER_BLOCK_TICK;
            double thrust = maxThrust * throttle * fade * ground
                    * RateModeFlight.thrustAtAirspeed(climbKmh, topSpeedKmh);
            velocity = velocity.add(lift.scale(thrust * dtTicks)).subtract(0.0D, GRAVITY * dtTicks, 0.0D);
            velocity = velocity.scale(dragPerStep);

            double idle = 1.0D - throttle;
            double brake = DISC_BRAKE * idle * idle;
            if (brake > 0.0D) {
                double along = velocity.dot(lift);
                velocity = velocity.subtract(lift.scale(along * (1.0D - Math.pow(1.0D - brake, dtTicks))));
            }
            setDeltaMovement(velocity);
        }

        if (grounded) {
            Vec3 velocity = getDeltaMovement();
            setDeltaMovement(velocity.x * GROUND_DRAG, Math.max(velocity.y, 0.0D), velocity.z * GROUND_DRAG);
            settleUpright();
        }

        double cap = topSpeed();
        Vec3 capped = getDeltaMovement();
        if (cap > 0.0D) {
            double horizontal = capped.horizontalDistance();
            if (horizontal > cap) {
                capped = new Vec3(capped.x * cap / horizontal, capped.y, capped.z * cap / horizontal);
            }
            capped = new Vec3(capped.x, Mth.clamp(capped.y, -cap, cap), capped.z);
        }
        if (capped.lengthSqr() < 1.0E-7D) {
            capped = Vec3.ZERO;
        }
        setDeltaMovement(capped);

        publishAttitude();

        Vec3 approach = getDeltaMovement();
        Vec3 from = position();
        recordImpact(from, approach, touchdownSpeed);
    }

    /** One tick of fixed-wing flight — the Molniya's model, and nothing like the quad's. */
    private void flyPlane(boolean grounded, float touchdownSpeed, boolean powered) {
        if (!this.attitudeReady) {
            attitudeFromYaw();
        }
        float fade = packFade();
        boolean piloted = isPiloted();
        double dtTicks = 1.0D / RATE_MODE_SUBSTEPS;
        double dtSeconds = dtTicks / 20.0D;

        double topSpeed = Math.max(1.0E-3D, topSpeed());
        double cruise = topSpeed * PLANE_CRUISE_FRACTION;
        double liftCoef = GRAVITY / (cruise * cruise);
        double maxThrust = PLANE_THRUST_WEIGHT * GRAVITY;
        double ground = RateModeFlight.groundEffect(heightAboveGround());

        double throttleTarget = !powered ? 0.0D
                : Math.max(PLANE_IDLE_THROTTLE, piloted ? stickThrottle() : this.heldThrottle);
        this.planeThrottle += (throttleTarget - this.planeThrottle) * PLANE_SPOOL;

        for (int step = 0; step < RATE_MODE_SUBSTEPS; step++) {
            double speed = getDeltaMovement().length();
            double authority = Mth.clamp(speed / (cruise * PLANE_AUTHORITY_SPEED),
                    PLANE_MIN_AUTHORITY, 1.0D);
            float[] angles = attitudeAngles();

            double pitchDemand;
            double rollDemand;
            double yawDemand;
            if (piloted) {
                pitchDemand = PLANE_PITCH_RATE * stickPitch() * authority;
                rollDemand = -PLANE_ROLL_RATE * stickRoll() * authority;
                yawDemand = -PLANE_YAW_RATE * stickYaw() * authority;
                if (powered && Math.abs(stickRoll()) < 0.05F) {
                    rollDemand += RateModeFlight.cameraRoll(angles[2]) * PLANE_ROLL_LEVEL * authority;
                }
            } else {
                pitchDemand = 0.0D;
                rollDemand = 0.0D;
                yawDemand = 0.0D;
            }

            double bank = RateModeFlight.cameraRoll(angles[2]) * Mth.DEG_TO_RAD;
            double turnSpeed = Math.max(speed, cruise * 0.5D);
            double coordinated = GRAVITY * Math.tan(Mth.clamp((float) bank, -1.4F, 1.4F)) / turnSpeed
                    * 20.0D * Mth.RAD_TO_DEG;
            yawDemand -= coordinated;

            this.ratePitch = closeRate(this.ratePitch, pitchDemand * fade, PLANE_RATE_RESPONSE, dtSeconds);
            this.rateRoll = closeRate(this.rateRoll, rollDemand * fade, PLANE_RATE_RESPONSE, dtSeconds);
            this.rateYaw = closeRate(this.rateYaw, yawDemand, PLANE_YAW_RESPONSE, dtSeconds);
            turnAirframe(dtSeconds);
        }

        Vector3f up = this.attitude.transform(0.0F, 1.0F, 0.0F, new Vector3f());
        Vector3f noseF = this.attitude.transform(0.0F, 0.0F, -1.0F, new Vector3f());
        Vector3f rightF = this.attitude.transform(1.0F, 0.0F, 0.0F, new Vector3f());
        Vec3 upV = new Vec3(up.x(), up.y(), up.z());
        Vec3 noseV = new Vec3(noseF.x(), noseF.y(), noseF.z());
        Vec3 rightV = new Vec3(rightF.x(), rightF.y(), rightF.z());

        Vec3 velocity = getDeltaMovement();
        double speed = velocity.length();

        double liftScale;
        if (powered) {
            liftScale = 1.0D;
        } else {
            double spent = (this.glideRun - PLANE_GLIDE_RANGE) / (PLANE_GLIDE_RANGE_HARD - PLANE_GLIDE_RANGE);
            liftScale = PLANE_GLIDE_LIFT * (1.0D - Mth.clamp(spent, 0.0D, 1.0D));
        }
        velocity = velocity.add(noseV.scale(maxThrust * this.planeThrottle * fade));
        velocity = velocity.add(upV.scale(liftCoef * speed * speed * ground * liftScale));
        velocity = velocity.subtract(0.0D, GRAVITY, 0.0D);

        double vForward = velocity.dot(noseV);
        double vSide = velocity.dot(rightV);
        double vUp = velocity.dot(upV);

        double drag = powered
                ? Mth.clamp(1.0D - PLANE_DRAG_BASE - dragPerBlock() * Math.abs(vForward), 0.90D, 0.999D)
                : PLANE_GLIDE_DRAG;
        vForward *= drag;
        vSide *= PLANE_KEEP_SIDE;
        vUp *= PLANE_KEEP_VERT;

        velocity = noseV.scale(vForward).add(rightV.scale(vSide)).add(upV.scale(vUp));

        if (grounded) {
            double rotate = cruise * PLANE_ROTATE_FRACTION;
            double vy = velocity.horizontalDistance() >= rotate ? Math.max(velocity.y, 0.0D) : 0.0D;
            velocity = new Vec3(velocity.x * PLANE_GROUND_FRICTION, vy, velocity.z * PLANE_GROUND_FRICTION);
            settleUpright();
        }

        double cap = topSpeed;
        double horizontal = velocity.horizontalDistance();
        if (horizontal > cap) {
            velocity = new Vec3(velocity.x * cap / horizontal, velocity.y, velocity.z * cap / horizontal);
        }
        velocity = new Vec3(velocity.x, Mth.clamp(velocity.y, -cap, cap), velocity.z);
        if (velocity.lengthSqr() < 1.0E-7D) {
            velocity = Vec3.ZERO;
        }
        setDeltaMovement(velocity);

        if (!powered) {
            this.glideRun += velocity.horizontalDistance();
        }

        publishAttitude();

        Vec3 approach = getDeltaMovement();
        Vec3 from = position();
        recordImpact(from, approach, 0.0F);
    }

    /**
     * A rolling aircraft rests on the ground at speed as a matter of course, so — unlike the quad — the floor
     * beneath it is not an impact.
     */
    @Override
    protected void recordImpact(Vec3 from, Vec3 approach, float touchdownSpeed) {
        this.impactFrom = from;
        super.recordImpact(from, approach, touchdownSpeed);
    }

    @Override
    protected float impactSpeed(Vec3 approach, Ram ram) {
        if (noseFused() && ram == null && (this.horizontalCollision || this.verticalCollision)
                && this.impactFrom != null) {
            Vec3 blocked = approach.subtract(position().subtract(this.impactFrom));
            Vector3f nose = this.attitude.transform(0.0F, 0.0F, -1.0F, new Vector3f());
            double intoNose = blocked.x * nose.x() + blocked.y * nose.y() + blocked.z * nose.z();
            if (intoNose >= NOSE_FUSE_SPEED) {
                return Math.nextUp((float) Math.max(approach.length(), crashSpeed()));
            }
        }
        if (fliesAsAircraft() && ram == null && !this.horizontalCollision && this.verticalCollision) {
            return (float) Math.abs(approach.y);
        }
        return super.impactSpeed(approach, ram);
    }

    /** The quadratic drag coefficient, set so thrust and drag balance at the configured top speed. */
    private double dragPerBlock() {
        double top = Math.max(1.0E-3D, topSpeed());
        double net = PLANE_THRUST_WEIGHT * GRAVITY - PLANE_DRAG_BASE * top;
        return Math.max(1.0E-4D, net / (top * top));
    }

    @Override
    public org.joml.Quaternionfc linkAttitude() {
        return this.attitude;
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_RAIL_SEATED, tag.getBoolean("RailSeated"));
        this.entityData.set(DATA_RAIL_PITCH, tag.getFloat("RailPitch"));
        this.heldThrottle = tag.getFloat("HeldThrottle");
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("RailSeated", isRailSeated());
        tag.putFloat("RailPitch", this.entityData.get(DATA_RAIL_PITCH));
        tag.putFloat("HeldThrottle", this.heldThrottle);
    }

    @Override
    public void captureFlightState(DroneFlightState out) {
        super.captureFlightState(out);
        out.airframe = true;
        out.attitudeReady = this.attitudeReady;
        out.attitude.set(this.attitude);
        out.ratePitch = this.ratePitch;
        out.rateYaw = this.rateYaw;
        out.rateRoll = this.rateRoll;
        out.demandPitch = this.demandPitch;
        out.demandYaw = this.demandYaw;
        out.demandRoll = this.demandRoll;
        out.holdX = this.holdX;
        out.holdY = this.holdY;
        out.holdZ = this.holdZ;
        out.lastVerticalDir = this.lastVerticalDir;
        out.leanRateX = this.leanRateX;
        out.leanRateZ = this.leanRateZ;
        out.power = this.power;
        out.planeThrottle = this.planeThrottle;
        out.glideRun = this.glideRun;
        out.railSeated = isRailSeated();
        out.railPitch = this.entityData.get(DATA_RAIL_PITCH);
    }

    @Override
    public void restoreFlightState(DroneFlightState state) {
        super.restoreFlightState(state);
        if (!state.airframe) {
            return;
        }
        this.attitudeReady = state.attitudeReady;
        this.attitude.set(state.attitude);
        this.ratePitch = state.ratePitch;
        this.rateYaw = state.rateYaw;
        this.rateRoll = state.rateRoll;
        this.demandPitch = state.demandPitch;
        this.demandYaw = state.demandYaw;
        this.demandRoll = state.demandRoll;
        this.holdX = state.holdX;
        this.holdY = state.holdY;
        this.holdZ = state.holdZ;
        this.lastVerticalDir = state.lastVerticalDir;
        this.leanRateX = state.leanRateX;
        this.leanRateZ = state.leanRateZ;
        this.power = state.power;
        this.planeThrottle = state.planeThrottle;
        this.glideRun = state.glideRun;
        this.entityData.set(DATA_RAIL_SEATED, state.railSeated);
        this.entityData.set(DATA_RAIL_PITCH, state.railPitch);
    }

    /**
     * Client side, once a tick on the pilot's copy: lays the predicted attitude out for the camera and the
     * model, turned by whatever is still left of the last correction.
     */
    public void presentPredictedAttitude(Quaternionfc correction, float shownYaw, float shownPitch) {
        this.clientNoseOld = this.clientNose;
        this.clientBankOld = this.clientBank;
        boolean first = !this.clientAttitudeShown;
        this.clientAttitudeOld.set(this.clientAttitude);
        this.clientAttitude.set(correction).mul(this.attitude).normalize();
        if (first) {
            this.clientAttitudeOld.set(this.clientAttitude);
            this.clientAttitudeShown = true;
        }

        float[] angles = anglesOf(this.clientAttitude);
        this.clientNose = angles[1];
        this.clientBank = angles[2];
        this.yRotO = shownYaw;
        this.xRotO = shownPitch;
        setYRot(shownYaw + Mth.wrapDegrees(angles[0] - shownYaw));
        setXRot(Mth.clamp(-angles[1], -90.0F, 90.0F));
    }

    /** The attitude too: the camera is bolted to it, and it interpolates on its own. */
    @Override
    public void settleInterpolation() {
        super.settleInterpolation();
        this.clientAttitudeOld.set(this.clientAttitude);
        this.clientNoseOld = this.clientNose;
        this.clientBankOld = this.clientBank;
    }

    /** Client side: the attitude the pilot's copy is actually flying on, as opposed to the one shown. */
    public Quaternionfc flightAttitude() {
        return this.attitude;
    }

    /** The attitude that matches the yaw the entity is carrying: level, facing that way. */
    private void attitudeFromYaw() {
        this.attitude.identity().rotateY((180.0F - getYRot()) * Mth.DEG_TO_RAD);
        this.ratePitch = 0.0D;
        this.rateYaw = 0.0D;
        this.rateRoll = 0.0D;
        this.demandPitch = 0.0D;
        this.demandYaw = 0.0D;
        this.demandRoll = 0.0D;
        this.attitudeReady = true;
    }

    /** One step of a rate closing on what it was asked for, held to what any airframe could do. */
    private static double closeRate(double current, double demand, double response, double dtSeconds) {
        double next = RateModeFlight.approach(current, demand, response, dtSeconds);
        return Mth.clamp(next, -HARD_RATE_LIMIT, HARD_RATE_LIMIT);
    }

    /**
     * Turns the attitude by the rates the airframe currently has, about its OWN axes — which is why the three
     * are combined into a single axis and angle rather than applied one after another.
     */
    private void turnAirframe(double dtSeconds) {
        double pitch = this.ratePitch * dtSeconds;
        double yaw = this.rateYaw * dtSeconds;
        double roll = this.rateRoll * dtSeconds;
        double magnitude = Math.sqrt(pitch * pitch + yaw * yaw + roll * roll);
        if (magnitude < 1.0E-7D) {
            return;
        }
        this.attitude.rotateAxis((float) (magnitude * Mth.DEG_TO_RAD),
                (float) (pitch / magnitude), (float) (yaw / magnitude), (float) (roll / magnitude));
        this.attitude.normalize();
    }

    /** On the ground it rests on its feet, whatever attitude it happened to arrive in. */
    private void settleUpright() {
        float[] angles = attitudeAngles();
        this.attitude.identity()
                .rotateY((180.0F - angles[0]) * Mth.DEG_TO_RAD)
                .rotateX(angles[1] * (1.0F - TILT_EASE) * Mth.DEG_TO_RAD)
                .rotateZ(angles[2] * (1.0F - TILT_EASE) * Mth.DEG_TO_RAD);
        this.ratePitch = 0.0D;
        this.rateRoll = 0.0D;
    }

    /**
     * How far the floor is below the airframe, in blocks, or a long way if it is not within reach of doing
     * anything.
     */
    private double heightAboveGround() {
        Vec3 from = position();
        Vec3 to = from.subtract(0.0D, RateModeFlight.GROUND_EFFECT_REACH, 0.0D);
        var hit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        if (hit.getType() == HitResult.Type.MISS) {
            return RateModeFlight.GROUND_EFFECT_REACH;
        }
        return Math.max(0.0D, from.y - hit.getLocation().y);
    }

    /**
     * The attitude as the three angles everything else in the game is built around, in the order the renderer
     * composes them: yaw, then pitch, then roll.
     */
    private float[] attitudeAngles() {
        return anglesOf(this.attitude);
    }

    /** A fixed-wing goes down as a plane; a quad tumbles. */
    @Override
    protected boolean fallsLikeAPlane() {
        return fliesAsAircraft();
    }

    /** The wreck starts from the attitude it was flying at, not from level. */
    @Override
    protected Quaternionf wreckStartAttitude() {
        return this.attitudeReady ? new Quaternionf(this.attitude) : super.wreckStartAttitude();
    }

    @Override
    protected float wreckStartRoll() {
        return this.attitudeReady ? attitudeAngles()[2] : 0.0F;
    }

    /** {@link #attitudeAngles()} for any rotation in the airframe's frame. */
    private static float[] anglesOf(Quaternionfc rotation) {
        Vector3f euler = rotation.getEulerAnglesYXZ(new Vector3f());
        return new float[] {
                Mth.wrapDegrees(180.0F - euler.y() * Mth.RAD_TO_DEG),
                euler.x() * Mth.RAD_TO_DEG,
                euler.z() * Mth.RAD_TO_DEG,
        };
    }

    /**
     * Send the attitude out, and put the same thing in the entity's own yaw and pitch so that everything
     * which has only ever known about those two — entity tracking, the arcade model's own helpers, anything
     * else that asks where the drone is facing — still gets a true answer.
     */
    private void publishAttitude() {
        float[] angles = attitudeAngles();
        setYRot(angles[0]);
        setXRot(Mth.clamp(-angles[1], -90.0F, 90.0F));
        if (level().isClientSide) {
            return;
        }
        this.entityData.set(DATA_ATTITUDE, new Quaternionf(this.attitude), this.tickCount % 20 == 0);
    }

    /**
     * Client side: takes over the attitude of the copy this one replaces, when the tracker sends the drone
     * afresh mid-flight.
     */
    public void adoptAttitudeFrom(InterceptorDroneEntity previous) {
        this.attitude.set(previous.attitude);
        this.clientAttitude.set(previous.clientAttitude);
        this.clientAttitudeOld.set(previous.clientAttitudeOld);
        this.clientNose = previous.clientNose;
        this.clientNoseOld = previous.clientNoseOld;
        this.clientBank = previous.clientBank;
        this.clientBankOld = previous.clientBankOld;
        this.clientAttitudeShown = previous.clientAttitudeShown;
        this.attitudeReady = previous.attitudeReady;
    }

    /** Client side: whether {@link #clientAttitude} has ever been laid out, so has a pose to slide from. */
    private boolean clientAttitudeShown;

    /**
     * Client side, for a drone this client is watching once it has stopped being played back from stamped
     * states: the synced attitude, laid out as the angles the renderer reads.
     */
    private void readAttitude() {
        this.attitude.set(this.entityData.get(DATA_ATTITUDE));
        shiftClientAttitude();
    }

    /** One tick's worth of attitude, laid out as the angles the camera and the renderer read. */
    private void shiftClientAttitude() {
        this.clientAttitudeOld.set(this.clientAttitudeShown ? this.clientAttitude : this.attitude);
        this.clientAttitude.set(this.attitude);
        this.clientAttitudeShown = true;

        float[] angles = attitudeAngles();
        this.clientNose = angles[1];
        this.clientBank = angles[2];

        float yaw = getYRot() + Mth.wrapDegrees(angles[0] - getYRot());
        this.yRotO = getYRot();
        this.xRotO = getXRot();
        setYRot(yaw);
        setXRot(Mth.clamp(-angles[1], -90.0F, 90.0F));
    }

    /** The attitude part of the way between the last two the server sent, as the three angles everything draws with. */
    public float[] attitudeAngles(float partialTick) {
        if (!level().isClientSide || !rateMode()) {
            return attitudeAngles();
        }
        Vector3f euler = blendedAttitude(partialTick).getEulerAnglesYXZ(new Vector3f());
        return new float[] {
                Mth.wrapDegrees(180.0F - euler.y() * Mth.RAD_TO_DEG),
                euler.x() * Mth.RAD_TO_DEG,
                euler.z() * Mth.RAD_TO_DEG,
        };
    }

    /** The airframe's own rotation, for the renderer to pose the model with directly. */
    @Override
    @Nullable
    public Quaternionf renderRotation(float partialTick) {
        if (!rateMode() || isWrecked()) {
            return null;
        }
        return new Quaternionf(level().isClientSide ? blendedAttitude(partialTick) : this.attitude);
    }

    /** Where a camera bolted on at this angle is looking. */
    public float[] cameraAngles(float partialTick, float mountTilt) {
        return RateModeFlight.cameraAnglesOf(
                level().isClientSide ? blendedAttitude(partialTick) : this.attitude, mountTilt);
    }

    private Quaternionf blendedAttitude(float partialTick) {
        this.clientAttitudeOld.slerp(this.clientAttitude, Mth.clamp(partialTick, 0.0F, 1.0F),
                this.clientBlend);
        return this.clientBlend.normalize();
    }

    /** How much of a commanded climb the motors can still back, 0..1. */
    private static float climbAuthority(float fade) {
        double best = MAX_POWER * LIFT_PER_POWER;
        double full = best - GRAVITY;
        if (full <= 0.0D) {
            return 0.0F;
        }
        return (float) Mth.clamp((best * fade - GRAVITY) / full, 0.0D, 1.0D);
    }

    /** How much of the motors this drone still has, 0..1, from the state of the pack. */
    public float packFade() {
        return dev.krutlexik.skyfront.drone.FpvPack.powerFade(
                dev.krutlexik.skyfront.drone.FpvPack.openCircuitCellVolts(getCharge()));
    }

    /**
     * Flat out, and far past anything a Mavic turns at: these are small blades on an airframe built to
     * sprint, and at full throttle they should be a blur rather than something the eye can follow round.
     */
    private static final float PROP_MAX_RATE = 100.0F;

    /**
     * Rotor speed on this airframe follows the arming switch and the throttle, not whether it happens to be
     * off the ground.
     */
    @Override
    public float propTargetRate() {
        if (motorsDead()) {
            return 0.0F;
        }
        double topSpeed = Math.max(1.0D, topSpeedKmh());
        double speedKmh = getDeltaMovement().length() * SkyfrontConfig.KMH_PER_BLOCK_TICK;
        float drive = (float) Mth.clamp(Math.max(speedKmh / topSpeed, inputLoad()), 0.0D, 1.0D);
        return Mth.lerp(drive, PROP_FLYING_RATE, PROP_MAX_RATE);
    }

    /** How far the nose is down. */
    @Override
    public float bodyPitch(float partialTick) {
        if (!level().isClientSide) {
            return rateMode() ? attitudeAngles()[1] : -tiltFromTravel(true);
        }
        return rateMode() ? attitudeAngles(partialTick)[1]
                : -Mth.lerp(partialTick, this.clientNoseOld, this.clientNose);
    }

    @Override
    public float bodyRoll(float partialTick) {
        if (!level().isClientSide) {
            return rateMode() ? attitudeAngles()[2] : tiltFromTravel(false);
        }
        return rateMode() ? attitudeAngles(partialTick)[2]
                : Mth.lerp(partialTick, this.clientBankOld, this.clientBank);
    }

    /**
     * The lean the drone's travel implies: a quadcopter only moves by leaning, so its speed along an axis of
     * its own frame says how far over it must be — full tilt at full speed, level at rest.
     */
    private float tiltFromTravel(boolean alongNose) {
        Vec3 velocity = getDeltaMovement();
        float yawRad = getYRot() * Mth.DEG_TO_RAD;
        double sin = Mth.sin(yawRad);
        double cos = Mth.cos(yawRad);
        double along = alongNose
                ? -velocity.x * sin + velocity.z * cos
                : velocity.x * cos + velocity.z * sin;
        float max = alongNose ? MAX_NOSE_TILT : MAX_TILT;
        double cap = Math.max(topSpeed(), 1.0E-3D);
        return (float) Mth.clamp(along / cap * max, -max, max);
    }

    /**
     * The pilot's self-destruct: the warhead goes off right where the drone is, exactly as it does on a
     * strike, and the airframe is gone.
     */
    @Override
    public void selfDestruct(@Nullable Entity by) {
        destroyNow(by);
    }

    /** The whole purpose of the drone. */
    @Override
    protected void goOff(ServerLevel level, @Nullable Entity attacker) {
        GrenadeAttachments.detonateWarhead(level, position(), this, attacker,
                (float) (double) SkyfrontConfig.INTERCEPTOR_BLAST.get(),
                (float) (double) SkyfrontConfig.INTERCEPTOR_VEHICLE_DAMAGE.get(),
                (float) (double) SkyfrontConfig.INTERCEPTOR_VEHICLE_RADIUS.get(), WARHEAD_EFFECT);
    }
}
