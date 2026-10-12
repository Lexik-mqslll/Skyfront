package dev.krutlexik.skyfront.drone.entity;

import dev.krutlexik.skyfront.config.SkyfrontConfig;
import dev.krutlexik.skyfront.drone.DroneTuning;
import dev.krutlexik.skyfront.drone.GrenadeAttachments;
import dev.krutlexik.skyfront.drone.network.DroneInputPayload;
import dev.krutlexik.skyfront.drone.network.DroneStatePayload;
import dev.krutlexik.skyfront.drone.network.StopControlPayload;
import dev.krutlexik.skyfront.drone.registry.DroneItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import dev.krutlexik.skyfront.drone.DroneLongRange;
import dev.krutlexik.skyfront.util.ChunkLoadHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import dev.krutlexik.skyfront.port.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Optional;
import java.util.UUID;

/** Base class of every flyable drone; on its own, the Mavic 3 Pro. */
public class DroneEntity extends Entity {
    /** Blocks. */
    public double controlRange() {
        return SkyfrontConfig.MAVIC_CONTROL_RANGE.get();
    }

    /** Squared control range, for the distance checks that never need the square root. */
    public double controlRangeSqr() {
        double range = controlRange();
        return range * range;
    }

    /** Ticks of airborne time a full battery is good for. */
    public int fullFlightTicks() {
        return (int) DroneTuning.get(DroneTuning.Value.MAVIC_FLIGHT_SECONDS) * 20;
    }

    /** Thrust per tick while airborne. */
    protected float thrust() {
        return AIR_ACCEL;
    }

    /**
     * Enforced top speed in blocks/tick, level flight and climb alike, or {@code 0} for none — this drone's
     * speed being simply wherever {@link #thrust()} and the frictions below settle it.
     */
    protected double topSpeed() {
        return 0.0D;
    }

    /** Impact speed (km/h) that writes the drone off. */
    public double crashSpeedKmh() {
        return DroneTuning.get(DroneTuning.Value.MAVIC_CRASH_SPEED);
    }

    /** The same figure in blocks per tick, which is what the flight model works in. */
    public double crashSpeed() {
        return crashSpeedKmh() / SkyfrontConfig.KMH_PER_BLOCK_TICK;
    }

    /**
     * Closing speed (blocks/tick) at which the drone starts breaking itself on entities rather than slipping
     * past them.
     */
    protected double ramSpeed() {
        return crashSpeed();
    }

    /** How fast the camera follows the mouse, against the player's own look sensitivity. */
    public double lookGearing() {
        return SkyfrontConfig.MAVIC_LOOK_SENSITIVITY.get();
    }

    /** How long, in seconds, the gimbal takes to settle onto where the mouse sent it — the camera's weight. */
    public double gimbalLag() {
        return 0.08D;
    }

    /** Whether this drone has to be kept alive on the pilot's client by hand. */
    public boolean needsLongRangeLink() {
        return controlRange() > 128.0D;
    }

    /** Whether stores can be hung under this drone. */
    protected boolean carriesStores() {
        return true;
    }

    /** Whether this particular store can be hung on it — every one the mod knows, unless it says. */
    protected boolean canCarry(ItemStack stack) {
        return GrenadeAttachments.isAttachable(stack);
    }

    /** Where and how a store hangs on this airframe. */
    public GrenadeAttachments.Mount mountFor(ItemStack stack) {
        return GrenadeAttachments.mountOf(stack);
    }

    /**
     * Whether its store is drawn turning with the airframe — bolted to it — rather than hanging level under
     * it on its heading alone.
     */
    public boolean storesFollowAttitude() {
        return false;
    }

    /** What flying into a vehicle does to it, over and above any blast. */
    protected float ramDamage() {
        return (float) (double) SkyfrontConfig.DRONE_RAM_DAMAGE.get();
    }

    /** Whether this airframe carries a camera that reads heat rather than light. */
    public boolean hasThermalCamera() {
        return false;
    }

    /** Which overlay the pilot flies this drone on. */
    public enum Osd {
        /** The consumer camera-app screen: battery ring, compass, capture info. */
        CAMERA,
        /** A flight controller's own OSD, drawn in the Betaflight font. */
        BETAFLIGHT,
        /** A fixed-wing flight controller's OSD, laid out the way INAV lays it out. */
        INAV,
        /**
         * Betaflight on a quad with a GPS: the pack, link, altitude, speed, satellites, coordinates and the
         * way home, around the plain Betaflight crosshair.
         */
        BETAFLIGHT_GPS,
        /** Nothing drawn over the picture at all: the bare camera feed. */
        NONE;

        /** Whether this is a flight controller's screen, with a pack and a flight timer behind it. */
        public boolean isFlightController() {
            return this == BETAFLIGHT || this == INAV || this == BETAFLIGHT_GPS;
        }
    }

    public Osd osdStyle() {
        return Osd.CAMERA;
    }

    /** Whether the scroll wheel zooms this drone's camera. */
    public boolean hasCameraZoom() {
        return true;
    }

    /** Whether this drone runs the propeller loop. */
    public boolean hasEngineSound() {
        return true;
    }

    /** Whether the motors have to be armed before this drone will fly. */
    public boolean requiresArming() {
        return false;
    }

    /** Whether the motors are live. */
    public boolean isArmed() {
        return !requiresArming() || armedSwitch();
    }

    /** The switch itself, whatever the airframe: the pilot's copy reads its own prediction of it. */
    private boolean armedSwitch() {
        return this.locallyControlled && this.pilotArmed >= 0 ? this.pilotArmed == 1 : this.entityData.get(DATA_ARMED);
    }

    /** Throw the arming switch. */
    public void setArmed(boolean armed) {
        if (this.locallyControlled) {
            this.pilotArmed = armed ? 1 : 0;
            return;
        }
        this.entityData.set(DATA_ARMED, armed);
    }

    /**
     * Client side, on the pilot's own copy: the arming switch and the charge as its own flight has set them,
     * ahead of the server.
     */
    private int pilotArmed = -1;
    private float pilotCharge = Float.NaN;

    /** Client side: forget the copy's own predictions, for a flight starting or ending on it. */
    public void clearPilotPrediction() {
        this.pilotArmed = -1;
        this.pilotCharge = Float.NaN;
    }

    /** How hard the drone is being worked right now, 0 (hands off) to 1 (everything held down). */
    public final float inputLoad() {
        if (level().isClientSide && !this.locallyControlled) {
            return this.entityData.get(DATA_LOAD);
        }
        return computeInputLoad();
    }

    /**
     * The server-side figure behind {@link #inputLoad()}: read off the smoothed stick axes rather than the
     * keys, so it rises and falls the way the airframe actually responds.
     */
    protected float computeInputLoad() {
        double load = Math.sqrt(this.inputSideways * this.inputSideways
                + this.inputVertical * this.inputVertical
                + this.inputForward * this.inputForward) / INPUT_PEAK;
        return (float) Mth.clamp(load, 0.0D, 1.0D);
    }

    /** Load steps of 1/16th: coarse enough that the synced value only updates when it matters. */
    private void syncInputLoad() {
        float quantized = Math.round(computeInputLoad() * 16.0F) / 16.0F;
        if (this.entityData.get(DATA_LOAD) != quantized) {
            this.entityData.set(DATA_LOAD, quantized);
        }
    }

    /** Camera gimbal limits in Minecraft pitch (positive looks down); an airframe flown by tilting widens them. */
    public float minPitch() {
        return MIN_PITCH;
    }

    public float maxPitch() {
        return MAX_PITCH;
    }

    /** How the airframe is held for the renderer, in degrees, ignoring a wreck (which has its own). */
    public float bodyPitch(float partialTick) {
        return 0.0F;
    }

    public float bodyRoll(float partialTick) {
        return 0.0F;
    }

    /** Camera gimbal limits, in Minecraft pitch (positive looks down). */
    public static final float MIN_PITCH = -25.0F;
    public static final float MAX_PITCH = 90.0F;

    private static final float AIR_ACCEL = 0.026F;
    protected static final float AIR_FRICTION = 0.91F;
    private static final float VERTICAL_FRICTION = 0.98F;
    private static final float INPUT_PEAK = 1.5F;
    private static final float INPUT_DECAY = 0.3F;

    protected static final float HORIZONTAL_BOOST = 1.05F;
    private static final float HORIZONTAL_HOLD = 0.95F;
    private static final float VERTICAL_BOOST = 0.99F;
    private static final float VERTICAL_HOLD = 0.87F;

    private static final double AUTOLAND_SPEED = 0.1D;
    private static final double AUTOLAND_EASE = 0.012D;
    private static final double AUTOLAND_DRAG = 0.93D;

    /** How much of its run a landed drone keeps each tick — the skid as it settles on touchdown. */
    private static final double GROUND_FRICTION = 0.82D;
    /** Below this (blocks/tick) the skid is over and the drone is parked. */
    private static final double SKID_STOP_SPEED = 0.02D;

    private static final EntityDataAccessor<Optional<UUID>> DATA_CONTROLLER =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Float> DATA_CHARGE =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
    /** The grenade slung under the drone, or empty. */
    private static final EntityDataAccessor<ItemStack> DATA_ATTACHMENT =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.ITEM_STACK);
    /** Set once the drone is wrecked and on its way down. */
    private static final EntityDataAccessor<Boolean> DATA_WRECKED =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BOOLEAN);
    /**
     * How hard the drone is being worked, 0..1, quantized server-side so it only syncs when it meaningfully
     * changes.
     */
    private static final EntityDataAccessor<Float> DATA_LOAD =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
    /** Whether the motors are live on a drone that needs arming (see {@link #requiresArming()}). */
    private static final EntityDataAccessor<Boolean> DATA_ARMED =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BOOLEAN);

    /** Client-only: true on the client that is currently flying this drone. */
    public boolean locallyControlled;

    /** Inputs received and not yet flown, oldest first. */
    private final ArrayDeque<PilotInput> inputQueue = new ArrayDeque<>();
    /** The last input taken off the queue — what a guessed tick is flown on. */
    private PilotInput lastInput = PilotInput.NEUTRAL;
    /** Its number, or -1 before the first input of a flight. */
    private int lastInputSeq = -1;
    /** The newest number accepted into the queue; anything at or below it is refused. */
    private int receivedSeq = Integer.MIN_VALUE;
    /** When the last input landed, by the wall clock: what silence is measured from. */
    private long lastInputNanos = System.nanoTime();
    /** Ticks flown on a guess, each owed one of the pilot's inputs once they turn up. */
    private int owedInputs;
    /** Ticks of flight the pilot is entitled to, accrued by the wall clock rather than by server ticks. */
    private double inputCredit = INPUT_CREDIT_START;
    private long creditClock;
    /** Ticks since the last input packet landed. */
    private int inputSilence;
    /** Steps this drone has been simulated, its own clock for onlookers (see {@link DroneStatePayload}). */
    private int simTick;
    private boolean simulatedThisTick;

    /** After this many ticks without input the controls are treated as released. */
    private static final int INPUT_SILENCE_LIMIT = 20;
    /** Ticks of silence after which the link itself is dropped: five seconds. */
    private static final int LINK_SILENCE_TICKS = 100;
    /** How long the drone waits, frozen, for a late input before flying on a guess. */
    private static final int STALL_TOLERANCE_TICKS = 4;
    /** Most queued inputs held at once — two seconds of them. */
    private static final int MAX_QUEUED_INPUTS = 40;
    /** Most inputs flown in a single server tick, catching up after a burst or on a lagging server. */
    private static final int MAX_INPUTS_PER_TICK = 40;
    private static final double INPUT_CREDIT_START = 4.0D;
    /**
     * Ticks of flight that can be banked, the same two seconds: a server running at a few ticks a second
     * earns several per tick and has to be able to spend them.
     */
    private static final double MAX_INPUT_CREDIT = 40.0D;
    private static final double NANOS_PER_TICK = 50_000_000.0D;
    /** Most guessed ticks remembered for payback — as many as the link survives silent anyway. */
    private static final int MAX_OWED_INPUTS = LINK_SILENCE_TICKS;
    /** Most gimbal movement accepted per tick, degrees — rejects a spoofed wild spin, not a flick. */
    private static final float MAX_GIMBAL_STEP = 170.0F;

    /**
     * The four stick axes, for an airframe flown on sticks rather than on keys (see {@code
     * InterceptorDroneEntity}'s rateMode mode).
     */
    private float stickRoll;
    private float stickPitch;
    private float stickYaw;
    private float stickThrottle;

    /** Server side: one tick of controls from the pilot's client, queued to be flown in its turn. */
    public void acceptPilotInput(PilotInput input) {
        if (input.seq() <= this.receivedSeq) {
            return;
        }
        this.receivedSeq = input.seq();
        this.inputQueue.addLast(input.sanitized());
        while (this.inputQueue.size() > MAX_QUEUED_INPUTS) {
            PilotInput dropped = this.inputQueue.pollFirst();
            this.lastInput = dropped;
            this.lastInputSeq = dropped.seq();
            if (this.owedInputs > 0) {
                this.owedInputs--;
            }
        }
        this.inputSilence = 0;
        this.lastInputNanos = System.nanoTime();
    }

    /**
     * The last input the server's state includes, for the link feed — counting the guessed ticks as the
     * inputs they will be paid back with, since those are the ones the server will now skip.
     */
    public int linkAckSeq() {
        return this.lastInputSeq < 0 ? -1 : this.lastInputSeq + this.owedInputs;
    }

    /** One tick of piloted flight under one input. */
    public void flyPilotStep(PilotInput input) {
        if (input.holds(DroneInputPayload.ARM_SET) && requiresArming() && !isWrecked()) {
            boolean wanted = input.holds(DroneInputPayload.ARM_ON);
            if (armedSwitch() != wanted) {
                setArmed(wanted);
            }
        }
        if (!isGrounded()) {
            drainCharge();
        }
        setSticks(input.roll(), input.pitch(), input.yaw(), input.throttle());
        if (!flownOnSticks() && input.hasGimbal()) {
            setGimbal(input.gimbalYaw(), input.gimbalPitch());
        }
        applyControlInput(
                input.holds(DroneInputPayload.FORWARD),
                input.holds(DroneInputPayload.BACKWARD),
                input.holds(DroneInputPayload.LEFT),
                input.holds(DroneInputPayload.RIGHT),
                input.holds(DroneInputPayload.UP),
                input.holds(DroneInputPayload.DOWN));
    }

    /**
     * Whether somebody has the sticks: on the server, whether the link is up; on the pilot's client, always,
     * for the copy it is flying — which must not wait for the synced link flag to arrive before it flies like
     * a piloted drone, or its first ticks are flown on the hands-off autopilot and every one of them is
     * corrected.
     */
    protected boolean isPiloted() {
        return this.locallyControlled || isControlled();
    }

    /**
     * Whether this airframe is flown on sticks, with the camera bolted to it, rather than on keys with a
     * gimbal of its own.
     */
    public boolean flownOnSticks() {
        return false;
    }

    /** Points the camera where the pilot's input says it is pointing. */
    public void setGimbal(float yaw, float pitch) {
        float step = Mth.wrapDegrees(yaw - getYRot());
        if (Math.abs(step) <= MAX_GIMBAL_STEP) {
            setYRot(yaw);
        } else {
            setYRot(getYRot() + Math.copySign(MAX_GIMBAL_STEP, step));
        }
        setXRot(Mth.clamp(pitch, minPitch(), maxPitch()));
    }

    /** Server side, for tests: put the sticks somewhere without a packet. */
    public void setSticks(float roll, float pitch, float yaw, float throttle) {
        this.stickRoll = Mth.clamp(roll, -1.0F, 1.0F);
        this.stickPitch = Mth.clamp(pitch, -1.0F, 1.0F);
        this.stickYaw = Mth.clamp(yaw, -1.0F, 1.0F);
        this.stickThrottle = Mth.clamp(throttle, 0.0F, 1.0F);
    }

    protected float stickRoll() {
        return this.stickRoll;
    }

    protected float stickPitch() {
        return this.stickPitch;
    }

    protected float stickYaw() {
        return this.stickYaw;
    }

    protected float stickThrottle() {
        return this.stickThrottle;
    }

    /** The throttle stick as last flown, 0..1 — what the pilot's own copy of the drone was given. */
    public float throttleStick() {
        return this.stickThrottle;
    }

    /** Sticks centred and the throttle shut — what a drone with nobody on the controls is given. */
    protected void releaseSticks() {
        this.stickRoll = 0.0F;
        this.stickPitch = 0.0F;
        this.stickYaw = 0.0F;
        this.stickThrottle = 0.0F;
    }

    /** A fresh link: whatever the last pilot sent, and wherever the queue had got to, is gone. */
    private void resetPilotQueue() {
        this.inputQueue.clear();
        this.lastInput = PilotInput.NEUTRAL;
        this.lastInputSeq = -1;
        this.receivedSeq = Integer.MIN_VALUE;
        this.lastInputNanos = System.nanoTime();
        this.owedInputs = 0;
        this.inputCredit = INPUT_CREDIT_START;
        this.creditClock = 0L;
        this.inputSilence = 0;
        this.dropAtSeq = NO_ORDER;
        this.destructAtSeq = NO_ORDER;
    }

    private static final int NO_ORDER = Integer.MIN_VALUE;
    /**
     * The input the pilot let the slung store go at, and the one they set the warhead off at, while the
     * server has still to fly up to them.
     */
    private int dropAtSeq = NO_ORDER;
    private int destructAtSeq = NO_ORDER;

    /** Server side: the pilot let the slung store go at input {@code seq}. */
    public void orderDrop(ServerPlayer pilot, int seq) {
        this.dropAtSeq = orderSeq(seq);
        carryOutOrders(pilot);
    }

    /** Server side: the pilot set the warhead off at input {@code seq}. */
    public void orderSelfDestruct(ServerPlayer pilot, int seq) {
        this.destructAtSeq = orderSeq(seq);
        carryOutOrders(pilot);
    }

    /**
     * No later than the newest input heard: an order cannot belong to an input not yet sent, and one that
     * claims to would otherwise be held as long as the client cared to keep it waiting.
     */
    private int orderSeq(int seq) {
        return this.receivedSeq == Integer.MIN_VALUE ? -1 : Math.min(seq, this.receivedSeq);
    }

    /**
     * Carries out the orders the flight has now got up to — counting the inputs a guess stood in for, which
     * the server will never fly.
     */
    private boolean carryOutOrders(@Nullable ServerPlayer pilot) {
        if (this.dropAtSeq == NO_ORDER && this.destructAtSeq == NO_ORDER) {
            return true;
        }
        int reached = linkAckSeq();
        if (this.dropAtSeq != NO_ORDER && this.dropAtSeq <= reached) {
            this.dropAtSeq = NO_ORDER;
            if (pilot != null) {
                releaseStore(pilot);
            }
        }
        if (this.destructAtSeq != NO_ORDER && this.destructAtSeq <= reached) {
            this.destructAtSeq = NO_ORDER;
            selfDestruct(pilot);
            return !isRemoved();
        }
        return true;
    }

    /** Sets the warhead off where the drone is, on an airframe that has one. */
    public void selfDestruct(@Nullable Entity by) {
    }

    /** Lets the slung store go. */
    private void releaseStore(ServerPlayer pilot) {
        ItemStack attachment = getAttachment();
        if (!GrenadeAttachments.isDroppable(attachment) || !(level() instanceof ServerLevel level)) {
            return;
        }
        setAttachment(ItemStack.EMPTY);
        boolean released = false;
        try {
            released = GrenadeAttachments.release(level, pilot, attachment, grenadeDropPoint(), getDeltaMovement());
        } catch (Throwable failure) {
            dev.krutlexik.skyfront.drone.DroneMod.LOGGER.error("Could not release {} from the drone",
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(attachment.getItem()), failure);
        }
        if (!released) {
            spawnAtLocation(attachment);
        }
    }

    /** Everything this drone's flight carries from one tick to the next, written into {@code out}. */
    public void captureFlightState(DroneFlightState out) {
        out.x = getX();
        out.y = getY();
        out.z = getZ();
        Vec3 velocity = getDeltaMovement();
        out.vx = velocity.x;
        out.vy = velocity.y;
        out.vz = velocity.z;
        out.yRot = getYRot();
        out.xRot = getXRot();
        out.inputSideways = this.inputSideways;
        out.inputVertical = this.inputVertical;
        out.inputForward = this.inputForward;
        out.charge = getCharge();
        out.armed = armedSwitch();
        out.airframe = false;
    }

    /** Puts this drone's flight exactly where {@code state} says. */
    public void restoreFlightState(DroneFlightState state) {
        setPos(state.x, state.y, state.z);
        setDeltaMovement(state.vx, state.vy, state.vz);
        setYRot(state.yRot);
        setXRot(state.xRot);
        this.inputSideways = state.inputSideways;
        this.inputVertical = state.inputVertical;
        this.inputForward = state.inputForward;
        if (getCharge() != state.charge) {
            setCharge(state.charge);
        }
        if (armedSwitch() != state.armed) {
            setArmed(state.armed);
        }
    }

    /** Client side: set while the pilot's copy re-flies inputs the server has since answered. */
    private boolean replaying;

    public void setReplaying(boolean replaying) {
        this.replaying = replaying;
    }

    @Override
    protected MovementEmission getMovementEmission() {
        return this.replaying ? MovementEmission.NONE : super.getMovementEmission();
    }

    /**
     * Client side, after one live tick of the pilot's copy: whether it ended in something hard enough to
     * write the airframe off, by exactly the rule the server writes it off by (see {@link #serverTick}).
     */
    /**
     * Client side: closes the gap the renderer interpolates across, so every frame from now on shows exactly
     * where the drone is rather than part of the way from where it was a tick ago.
     */
    public void settleInterpolation() {
        this.xo = getX();
        this.yo = getY();
        this.zo = getZ();
        this.yRotO = getYRot();
        this.xRotO = getXRot();
    }

    public void notePredictedImpact() {
        if (this.lastCollisionSpeed * SkyfrontConfig.KMH_PER_BLOCK_TICK >= crashSpeedKmh()) {
            this.impactPredicted = true;
            this.predictedRamId = this.lastRammed != null ? this.lastRammed.getId() : -1;
        }
    }

    /**
     * Speed (blocks/tick) the drone was travelling at when it last hit something during {@link
     * #applyControlInput}, or 0 if that move was clear.
     */
    public float lastCollisionSpeed;

    /** The entity the drone last flew into, if any — a vehicle, an animal, a player. */
    @Nullable
    public Entity lastRammed;

    /** An entity the drone's path crosses this tick, and how far along the tick it is struck. */
    protected record Ram(Entity entity, double contact) {
    }

    /** How far beyond its own stride the drone looks for something to hit, blocks. */
    private static final double CLOSING_LEAD = 4.0D;

    /** Anything solid the drone actually flies into along {@code travel}. */
    @Nullable
    private Ram entityInTheWay(Vec3 from, Vec3 travel) {
        AABB start = getBoundingBox().move(from.x - getX(), from.y - getY(), from.z - getZ());
        double oneWay = level().isClientSide && this.locallyControlled && !this.replaying
                ? dev.krutlexik.skyfront.client.ClientLatency.packetAgeTicks() : 0.0D;
        AABB path = start.expandTowards(travel).inflate(0.1D + CLOSING_LEAD + oneWay * LEAD_REACH_PER_TICK);
        Ram closest = null;
        for (Entity other : level().getEntities(this, path, this::isSolidToDrone)) {
            Vec3 closing = travel.subtract(other.getDeltaMovement());
            AABB hull = other.getBoundingBox().inflate(0.1D);
            if (oneWay > 0.0D) {
                hull = hull.move(other.getDeltaMovement().scale(leadTicks(other, oneWay)));
            }
            double contact = sweptContact(start, closing, hull);
            Vec3 stride = new Vec3(other.getX() - other.xo, other.getY() - other.yo, other.getZ() - other.zo);
            if (other instanceof DroneEntity drone && drone.stepsLastTick > 1) {
                stride = stride.scale(1.0D / drone.stepsLastTick);
            }
            if (stride.lengthSqr() > 1.0E-6D) {
                double fromStart = sweptContact(start, travel.subtract(stride),
                        hull.move(stride.scale(-1.0D)));
                if (fromStart >= 0.0D && (contact < 0.0D || fromStart < contact)) {
                    contact = fromStart;
                }
            }
            if (contact >= 0.0D && (closest == null || contact < closest.contact)) {
                closest = new Ram(other, contact);
            }
        }
        return closest;
    }

    /** Server side: steps flown in this drone's most recent tick. */
    private int stepsLastTick = 1;

    /** Furthest a target is looked ahead per tick of lead, blocks: a Shahed in its dive and then some. */
    private static final double LEAD_REACH_PER_TICK = 8.0D;

    /**
     * Client side: how many ticks ahead of where it is drawn a target has to be looked for, for the pilot's
     * copy to meet it as the server's drone will.
     */
    private static double leadTicks(Entity other, double oneWay) {
        if (other instanceof dev.krutlexik.skyfront.entity.ShahedEntity) {
            return oneWay;
        }
        if (other instanceof DroneEntity watched && !Double.isNaN(watched.playbackTick)) {
            return 2.0D * oneWay + watched.playbackDelay;
        }
        return 2.0D * oneWay;
    }

    /** When (0..1 through this tick's travel) the moving box first overlaps {@code other}, or -1 for a clean miss. */
    private static double sweptContact(AABB self, Vec3 travel, AABB other) {
        double entry = 0.0D;
        double exit = 1.0D;
        double[][] axes = {
                {travel.x, self.minX, self.maxX, other.minX, other.maxX},
                {travel.y, self.minY, self.maxY, other.minY, other.maxY},
                {travel.z, self.minZ, self.maxZ, other.minZ, other.maxZ},
        };
        for (double[] axis : axes) {
            double speed = axis[0];
            double selfMin = axis[1];
            double selfMax = axis[2];
            double otherMin = axis[3];
            double otherMax = axis[4];
            if (speed == 0.0D) {
                if (selfMax <= otherMin || selfMin >= otherMax) {
                    return -1.0D;
                }
                continue;
            }
            double enters = (otherMin - selfMax) / speed;
            double leaves = (otherMax - selfMin) / speed;
            entry = Math.max(entry, Math.min(enters, leaves));
            exit = Math.min(exit, Math.max(enters, leaves));
        }
        return entry <= exit ? entry : -1.0D;
    }

    /** Whether the drone should break itself on this, rather than pass through it. */
    private boolean isSolidToDrone(Entity other) {
        if (other.isRemoved()) {
            return false;
        }
        if (other instanceof DroneEntity drone) {
            return drone instanceof InterceptorDroneEntity aircraft && aircraft.fliesAsAircraft()
                    && !aircraft.isWrecked();
        }
        if (other instanceof Player player && player.isSpectator()) {
            return false;
        }
        if (other instanceof dev.krutlexik.skyfront.entity.DebrisEntity) {
            return false;
        }
        if (other instanceof dev.krutlexik.skyfront.entity.LauncherEntity) {
            return false;
        }
        if (other instanceof DroneStandEntity) {
            return false;
        }
        return other.canBeCollidedWith() || other.isPickable();
    }

    /**
     * Server-only hit points. -1 until first touched, then seeded from the config; at/below 0 the drone is
     * destroyed.
     */
    private float health = -1.0F;

    /** Set the moment the drone starts being destroyed, before its warhead goes off. */
    private boolean destroyed;

    private float spin;
    private float roll;
    private float rollOld;
    /** Which way the wreck banks as it drops, so it corkscrews rather than falling dead straight. */
    private int swerve;

    protected float inputSideways;
    protected float inputVertical;
    protected float inputForward;

    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;
    private double lerpXRot;
    /** Rotation eases on its own clock: position is dead-reckoned, but angles still glide over a few ticks. */
    private int rotSteps;
    /** An onlooker's remaining pull toward the server's last stated position, bled off a share a tick. */
    private Vec3 observerCorrection = Vec3.ZERO;
    /** Ticks since the tracker last said anything about where the drone is or how it moves. */
    private int trackerSilence;

    /** One stamped state, as received. */
    private record Snapshot(int simTick, double x, double y, double z, double vx, double vy, double vz,
                            float yRot, float xRot, Quaternionf attitude) {
    }

    private final ArrayDeque<Snapshot> snapshots = new ArrayDeque<>();
    /** The moment on the drone's own timeline being shown, in its ticks; NaN until there is one. */
    private double playbackTick = Double.NaN;
    /** How far behind the newest state playback runs, in the drone's ticks. */
    private double playbackDelay = SNAPSHOT_DELAY_START;
    private int ticksSinceSnapshot = Integer.MAX_VALUE / 2;
    private int steadyTicks;
    /** Client ticks played, for timing the stream (see {@link #streamRate}). */
    private int observerClock;
    /**
     * How many of the drone's ticks the stream moves on by per client tick: 1 on a server keeping up, less on
     * one that is behind.
     */
    private double streamRate = 1.0D;
    /** Where the stream stood when the window it is being timed over opened; MIN_VALUE for none. */
    private int rateFromSim = Integer.MIN_VALUE;
    private int rateFromClock;
    /** The usual step between one state and the next, in the drone's ticks. */
    private double snapshotSpacing = 1.0D;
    /**
     * What is left of the gap between where the drone was shown and where playback put it when it started
     * over — a fresh copy, a drone let go of, a timeline resynced — bled away over a few ticks so the drone
     * slides into line rather than jumping there.
     */
    private Vec3 playbackCorrection = Vec3.ZERO;
    /**
     * The attitude played back this tick, for an airframe to pose itself with — or null when this tick was
     * not played from the stamped states and the airframe should fall back on its own.
     */
    @Nullable
    protected Quaternionf playedAttitude;

    private static final int SNAPSHOT_BUFFER = 32;
    /** Without a fresh state for this long the drone has stopped flying, and the tracker takes over again. */
    private static final int SNAPSHOT_STALE_TICKS = 10;
    private static final double SNAPSHOT_DELAY_START = 2.0D;
    private static final double SNAPSHOT_DELAY_MIN = 1.5D;
    private static final double SNAPSHOT_DELAY_MAX = 6.0D;
    /** Off by more than this and playback jumps rather than easing back into line. */
    private static final double SNAPSHOT_RESYNC_TICKS = 8.0D;
    /** How hard playback leans toward where it should be: at most this much faster or slower than real time. */
    private static final double SNAPSHOT_MAX_SKEW = 0.15D;
    private static final double SNAPSHOT_CATCH_UP = 0.1D;
    /** Ticks a stream is timed over before its pace is taken. */
    private static final int STREAM_RATE_WINDOW = 10;
    private static final double STREAM_RATE_MIN = 0.05D;
    private static final double STREAM_RATE_MAX = 1.5D;
    /** What is left of a restart's correction after each tick. */
    private static final double PLAYBACK_CORRECTION_KEEP = 0.7D;
    /** How far past the newest state playback will guess along its velocity before holding. */
    private static final double SNAPSHOT_MAX_GUESS = 3.0D;
    /** Ticks without once having to guess before the delay is trimmed back. */
    private static final int SNAPSHOT_STEADY_TICKS = 100;

    /** Client side: one stamped state of this drone, from the server. */
    public void acceptSnapshot(DroneStatePayload state) {
        if (this.locallyControlled) {
            return;
        }
        Snapshot newest = this.snapshots.peekLast();
        if (newest != null && state.simTick() <= newest.simTick()) {
            if (state.simTick() < newest.simTick() - SNAPSHOT_BUFFER) {
                forgetSnapshots();
            } else {
                return;
            }
        }
        if (this.ticksSinceSnapshot > staleTicks()) {
            forgetSnapshots();
        } else if (!Double.isNaN(this.playbackTick) && state.simTick() <= this.playbackTick) {
            this.playbackDelay = Math.min(maxPlaybackDelay(), this.playbackDelay + 0.5D);
            this.steadyTicks = 0;
        }
        timeStream(this.snapshots.peekLast(), state.simTick());
        this.snapshots.addLast(new Snapshot(state.simTick(), state.x(), state.y(), state.z(),
                state.vx(), state.vy(), state.vz(), state.yRot(), state.xRot(),
                new Quaternionf(state.qx(), state.qy(), state.qz(), state.qw()).normalize()));
        while (this.snapshots.size() > SNAPSHOT_BUFFER) {
            this.snapshots.pollFirst();
        }
        this.ticksSinceSnapshot = 0;
    }

    private void forgetSnapshots() {
        this.snapshots.clear();
        this.playbackTick = Double.NaN;
        this.ticksSinceSnapshot = Integer.MAX_VALUE / 2;
        this.playbackDelay = Math.max(SNAPSHOT_DELAY_START, minPlaybackDelay());
        this.steadyTicks = 0;
        this.rateFromSim = Integer.MIN_VALUE;
    }

    /** Takes the stream's pace and its step from a state just arrived, {@code newest} the one before it. */
    private void timeStream(@Nullable Snapshot newest, int simTick) {
        if (newest != null && simTick > newest.simTick()) {
            this.snapshotSpacing += ((simTick - newest.simTick()) - this.snapshotSpacing) * 0.2D;
        }
        if (this.rateFromSim == Integer.MIN_VALUE) {
            this.rateFromSim = simTick;
            this.rateFromClock = this.observerClock;
            return;
        }
        int elapsed = this.observerClock - this.rateFromClock;
        if (elapsed >= STREAM_RATE_WINDOW) {
            double measured = (simTick - this.rateFromSim) / (double) elapsed;
            this.streamRate += (Mth.clamp(measured, STREAM_RATE_MIN, STREAM_RATE_MAX) - this.streamRate) * 0.5D;
            this.rateFromSim = simTick;
            this.rateFromClock = this.observerClock;
        }
    }

    /** The least delay that still has a state on each side of the moment shown, at this stream's step. */
    private double minPlaybackDelay() {
        return Math.max(SNAPSHOT_DELAY_MIN, this.snapshotSpacing);
    }

    private double maxPlaybackDelay() {
        return Math.max(SNAPSHOT_DELAY_MAX, 2.0D * this.snapshotSpacing + 1.0D);
    }

    /** Client ticks without a fresh state before the drone counts as having stopped: a few of its usual gaps. */
    private int staleTicks() {
        double gap = this.snapshotSpacing / Math.max(this.streamRate, STREAM_RATE_MIN);
        return (int) Mth.clamp(Math.ceil(3.0D * gap), SNAPSHOT_STALE_TICKS, 60.0D);
    }

    /**
     * One tick of playback: moves the timeline on by one of the drone's ticks — leaning a little faster or
     * slower to hold the delay where it should be — and puts the drone where the stamped states say it was at
     * that moment.
     */
    private void playSnapshots() {
        Snapshot newest = this.snapshots.peekLast();
        Vec3 shownBefore = position();
        double target = newest.simTick() - this.playbackDelay;
        boolean restarted = false;
        if (Double.isNaN(this.playbackTick)) {
            this.playbackTick = newest.simTick();
            restarted = true;
        } else if (Math.abs(target - this.playbackTick) > Math.max(SNAPSHOT_RESYNC_TICKS, 2.0D * this.snapshotSpacing + 2.0D)) {
            this.playbackTick = target;
            restarted = true;
        } else {
            double rate = this.streamRate;
            this.playbackTick += rate + Mth.clamp((target - this.playbackTick) * SNAPSHOT_CATCH_UP,
                    -SNAPSHOT_MAX_SKEW * rate, SNAPSHOT_MAX_SKEW * rate);
        }

        if (this.playbackTick > newest.simTick()) {
            this.steadyTicks = 0;
        } else if (++this.steadyTicks > SNAPSHOT_STEADY_TICKS) {
            this.playbackDelay = Math.max(minPlaybackDelay(), this.playbackDelay - 0.25D);
            this.steadyTicks = 0;
        }

        while (this.snapshots.size() >= 2) {
            Iterator<Snapshot> walk = this.snapshots.iterator();
            walk.next();
            if (walk.next().simTick() <= this.playbackTick) {
                this.snapshots.pollFirst();
            } else {
                break;
            }
        }
        Iterator<Snapshot> walk = this.snapshots.iterator();
        Snapshot before = walk.next();
        Snapshot after = walk.hasNext() ? walk.next() : null;

        double x;
        double y;
        double z;
        Vec3 velocity;
        float yaw;
        float pitch;
        Quaternionf attitude = new Quaternionf();
        if (after == null || this.playbackTick <= before.simTick()) {
            double ahead = Mth.clamp(this.playbackTick - before.simTick(), 0.0D, SNAPSHOT_MAX_GUESS);
            x = before.x() + before.vx() * ahead;
            y = before.y() + before.vy() * ahead;
            z = before.z() + before.vz() * ahead;
            velocity = new Vec3(before.vx(), before.vy(), before.vz());
            yaw = before.yRot();
            pitch = before.xRot();
            attitude.set(before.attitude());
        } else {
            double t = (this.playbackTick - before.simTick()) / (double) (after.simTick() - before.simTick());
            x = Mth.lerp(t, before.x(), after.x());
            y = Mth.lerp(t, before.y(), after.y());
            z = Mth.lerp(t, before.z(), after.z());
            velocity = new Vec3(Mth.lerp(t, before.vx(), after.vx()), Mth.lerp(t, before.vy(), after.vy()),
                    Mth.lerp(t, before.vz(), after.vz()));
            yaw = Mth.rotLerp((float) t, before.yRot(), after.yRot());
            pitch = Mth.lerp((float) t, before.xRot(), after.xRot());
            before.attitude().slerp(after.attitude(), (float) t, attitude);
        }

        if (restarted) {
            Vec3 gap = shownBefore.subtract(x, y, z);
            this.playbackCorrection = gap.lengthSqr() < OBSERVER_SNAP_DISTANCE * OBSERVER_SNAP_DISTANCE
                    ? gap : Vec3.ZERO;
        }
        if (this.playbackCorrection != Vec3.ZERO) {
            x += this.playbackCorrection.x;
            y += this.playbackCorrection.y;
            z += this.playbackCorrection.z;
            this.playbackCorrection = this.playbackCorrection.scale(PLAYBACK_CORRECTION_KEEP);
            if (this.playbackCorrection.lengthSqr() < 1.0E-6D) {
                this.playbackCorrection = Vec3.ZERO;
            }
        }
        setPos(x, y, z);
        setDeltaMovement(velocity);
        setYRot(getYRot() + Mth.wrapDegrees(yaw - getYRot()));
        setXRot(pitch);
        this.playedAttitude = attitude.normalize();
    }
    /** Set by the pilot's copy the moment it strikes something hard enough to be written off. */
    private boolean impactPredicted;
    /** What the pilot's copy flew into, when that was an entity rather than a block; else -1. */
    private int predictedRamId = -1;

    /** Client side: the entity behind the last predicted impact, or -1 for a block (or none). */
    public int predictedRamId() {
        return this.predictedRamId;
    }

    /**
     * How far the server's copy of a target may be from the drone and a pilot's reported ram on it still be
     * believed: the ground the target covers while its position crosses the link and the report comes back —
     * about half a second of flight, which is most of a second of ping — plus a little of the drone's own,
     * plus a margin.
     */
    private static final int RAM_REPORT_LAG_TICKS = 10;
    private static final double RAM_REPORT_MARGIN = 3.0D;
    /** The fastest a target is taken to be moving when allowing for it: a Shahed in its dive. */
    private static final double RAM_REPORT_TARGET_SPEED_CAP = 3.0D;
    /**
     * The share of its ram speed the server's drone must have flown at lately for a reported ram to be
     * believed: it runs a little behind the picture the report was made from, so not quite all.
     */
    private static final double RAM_REPORT_SPEED_SHARE = 0.75D;
    /** What is left of {@link #recentApproach} after each step. */
    private static final double RECENT_APPROACH_KEEP = 0.85D;

    /** Server side: the fastest step the drone has flown lately, fading away over a second or so. */
    private double recentApproach;

    /**
     * Where on {@code target}'s hull a reported ram would put the drone, or {@code null} if the claim does
     * not hold up: not something a drone can strike, or too far from where the drone is for any lag to
     * explain.
     */
    @Nullable
    public Vec3 reportedRamContact(Entity target) {
        if (target == this || !isSolidToDrone(target) || target.level() != level()) {
            return null;
        }
        double reach = Math.min(target.getDeltaMovement().length(), RAM_REPORT_TARGET_SPEED_CAP) * RAM_REPORT_LAG_TICKS
                + getDeltaMovement().length() * 3.0D + RAM_REPORT_MARGIN;
        AABB hull = target.getBoundingBox();
        Vec3 centre = getBoundingBox().getCenter();
        Vec3 nearest = new Vec3(
                Mth.clamp(centre.x, hull.minX, hull.maxX),
                Mth.clamp(centre.y, hull.minY, hull.maxY),
                Mth.clamp(centre.z, hull.minZ, hull.maxZ));
        return nearest.distanceTo(centre) <= reach ? nearest : null;
    }

    /** Server side: the pilot's client saw this drone fly into {@code target} and froze the picture on it. */
    public boolean acceptReportedRam(ServerPlayer pilot, Entity target) {
        if (!isControlledBy(pilot) || isWrecked() || this.destroyed) {
            return false;
        }
        Vec3 nearest = reportedRamContact(target);
        if (nearest == null) {
            return false;
        }
        if (this.recentApproach < ramSpeed() * RAM_REPORT_SPEED_SHARE || !nothingSolidBetween(nearest)) {
            return false;
        }
        setPos(nearest.x, nearest.y - getBbHeight() * 0.5D, nearest.z);
        setDeltaMovement(Vec3.ZERO);
        this.lastRammed = target;
        releaseControl(StopControlPayload.Reason.CRASHED);
        ram(target, pilot);
        destroyOnImpact(pilot);
        return true;
    }

    /**
     * Whether the way from the drone to {@code contact} is open — nothing solid in the way short of the
     * target's own surroundings: the point on a hull resting on the ground touches the ground.
     */
    private boolean nothingSolidBetween(Vec3 contact) {
        Vec3 from = getBoundingBox().getCenter();
        net.minecraft.world.phys.BlockHitResult hit = level().clip(new net.minecraft.world.level.ClipContext(
                from, contact, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, this));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS
                || hit.getLocation().distanceToSqr(contact) < 1.0D;
    }

    /** Client side: whether the pilot's copy has just crashed, clearing the flag as it is read. */
    public boolean consumePredictedImpact() {
        boolean predicted = this.impactPredicted;
        this.impactPredicted = false;
        return predicted;
    }

    /** Correction beyond this is a real desync (teleport, resync), not drift: snap instead of gliding. */
    private static final double OBSERVER_SNAP_DISTANCE = 24.0;
    /** Share of the remaining correction applied per tick. */
    private static final double OBSERVER_CORRECTION_SHARE = 0.25;
    /** Ticks of flight a tracker packet is behind the server's "now" by the time it lands. */
    private static final double OBSERVER_PACKET_AGE_TICKS = 2.0;
    /** Silence after which the tracker's last velocity is no longer flown on (see {@link #trackerSilence}). */
    private static final int OBSERVER_SILENCE_TICKS = 6;

    public DroneEntity(EntityType<? extends DroneEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noCulling = true;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_CONTROLLER, Optional.empty());
        this.entityData.define(DATA_CHARGE, 1.0F);
        this.entityData.define(DATA_ATTACHMENT, ItemStack.EMPTY);
        this.entityData.define(DATA_WRECKED, false);
        this.entityData.define(DATA_LOAD, 0.0F);
        this.entityData.define(DATA_ARMED, false);
    }

    /** True once the drone has been wrecked and is tumbling; it has no control left and cannot fly. */
    public boolean isWrecked() {
        return this.entityData.get(DATA_WRECKED);
    }

    /** How far into its death spiral the wreck is, driving the roll rate and the nose-over. */
    public float getSpin() {
        return this.spin;
    }

    /** Roll angle of the tumbling wreck, interpolated for the renderer. */
    public float getRoll(float partialTick) {
        return Mth.lerp(partialTick, this.rollOld, this.roll);
    }

    /** The grenade slung under the drone, or {@link ItemStack#EMPTY}. */
    public ItemStack getAttachment() {
        return this.entityData.get(DATA_ATTACHMENT);
    }

    public boolean hasAttachment() {
        return !getAttachment().isEmpty();
    }

    public void setAttachment(ItemStack stack) {
        this.entityData.set(DATA_ATTACHMENT, stack);
    }

    /** Where a dropped grenade is released — clear of the airframe, not merely under its origin. */
    public Vec3 grenadeDropPoint() {
        return position().add(0.0D, -(getBbHeight() + 0.2D), 0.0D);
    }

    public float getCharge() {
        return this.locallyControlled && !Float.isNaN(this.pilotCharge)
                ? this.pilotCharge : this.entityData.get(DATA_CHARGE);
    }

    public void setCharge(float charge) {
        float clamped = Mth.clamp(charge, 0.0F, 1.0F);
        if (this.locallyControlled) {
            this.pilotCharge = clamped;
            return;
        }
        this.entityData.set(DATA_CHARGE, clamped);
    }

    /** One simulated tick's worth of flying off the pack. */
    protected void drainCharge() {
        setCharge(getCharge() - 1.0F / fullFlightTicks());
    }

    /**
     * Server-side battery simulation: airborne flight drains it, and hitting empty drops the link so the
     * drone sinks to the ground (see {@link #serverTick}).
     */
    public void tickBattery(boolean draining) {
        if (!draining) {
            return;
        }
        drainCharge();
        if (getCharge() <= 0.0F) {
            packRanOut();
        }
    }

    /** Server side: the pack is flat. */
    private void packRanOut() {
        String notice = autolandsWhenFlat()
                ? "message.sfw.drone.battery_empty"
                : "message.sfw.drone.battery_dead";
        getControllerId().map(id -> level().getPlayerByUUID(id)).ifPresent(pilot ->
                pilot.displayClientMessage(Component.translatable(notice), true));
        releaseControl(autolandsWhenFlat()
                ? StopControlPayload.Reason.NORMAL
                : StopControlPayload.Reason.BATTERY);
    }

    public Optional<UUID> getControllerId() {
        return this.entityData.get(DATA_CONTROLLER);
    }

    public boolean isControlledBy(Player player) {
        return getControllerId().filter(id -> id.equals(player.getUUID())).isPresent();
    }

    public boolean isControlled() {
        return getControllerId().isPresent();
    }

    public void setController(@Nullable UUID id) {
        if (id != null) {
            this.lastPilotId = id;
        }
        UUID previous = getControllerId().orElse(null);
        this.entityData.set(DATA_CONTROLLER, Optional.ofNullable(id));
        dev.krutlexik.skyfront.drone.DroneLinks.controllerChanged(this, previous, id);
        resetPilotQueue();
        releaseSticks();
    }

    /**
     * The player who last flew this drone, kept after they let go of it (and across a save): what the drone
     * does is still their doing.
     */
    @Nullable
    private UUID lastPilotId;
    /** Who brought the drone down, for the blast its wreck makes when it reaches the ground. */
    @Nullable
    private UUID downedById;

    /**
     * Server side: whom something this drone did counts for — {@code given} if there is one (the pilot flying
     * it, or whoever shot it down), else the player who last flew it, if they are still in this world.
     */
    @Nullable
    protected Entity creditFor(@Nullable Entity given) {
        if (given != null || this.lastPilotId == null) {
            return given;
        }
        return level().getPlayerByUUID(this.lastPilotId);
    }

    /** The wreck's blast is the doing of whoever brought it down; failing them, its pilot's. */
    @Nullable
    private Entity downedBy() {
        Entity downer = this.downedById != null && level() instanceof ServerLevel serverLevel
                ? serverLevel.getEntity(this.downedById) : null;
        return creditFor(downer);
    }

    /** A ram's damage, credited to {@code credit}. */
    private DamageSource ramDamage(@Nullable Entity credit, boolean vehicle) {
        var types = level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE);
        var flyIntoWall = types.getOrThrow(net.minecraft.world.damagesource.DamageTypes.FLY_INTO_WALL);
        if (vehicle) {
            return new DamageSource(flyIntoWall, this, credit);
        }
        return new DamageSource(types.get(DRONE_RAM).map(holder -> (net.minecraft.core.Holder<net.minecraft.world.damagesource.DamageType>) holder)
                .orElse(flyIntoWall), this, credit);
    }

    /** {@code sfw:drone_ram}: a drone flown into something, see data/sfw/damage_type. */
    private static final net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> DRONE_RAM =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                    dev.krutlexik.skyfront.drone.DroneMod.id("drone_ram"));

    /** Server-side: drop the link and tell the pilot's client to leave drone view, cleanly. */
    public void releaseControl(boolean outOfRange) {
        releaseControl(outOfRange
                ? StopControlPayload.Reason.OUT_OF_RANGE
                : StopControlPayload.Reason.NORMAL);
    }

    /** Server-side: drop the link and tell the pilot's client how the feed should end. */
    public void releaseControl(StopControlPayload.Reason reason) {
        if (level().isClientSide) {
            return;
        }
        getControllerId().ifPresent(id -> {
            ServerPlayer pilot = level().getPlayerByUUID(id) instanceof ServerPlayer found ? found : null;
            if (pilot != null) {
                if (PacketDistributor.hasChannel(pilot)) {
                    PacketDistributor.sendToPlayer(pilot, new StopControlPayload(reason, getX(), getY(), getZ()));
                }
                if (reason == StopControlPayload.Reason.DESTROYED
                        || reason == StopControlPayload.Reason.CRASHED) {
                    dev.krutlexik.skyfront.compat.sbw.SbwMonitor.unlinkMatching(pilot, this);
                }
            }
            dev.krutlexik.skyfront.drone.DroneLongRange.release(this, pilot);
        });
        dev.krutlexik.skyfront.drone.DroneLinks.remember(this);
        setController(null);
    }

    @Override
    public void tick() {
        this.xo = getX();
        this.yo = getY();
        this.zo = getZ();
        super.tick();

        if (level().isClientSide) {
            tickPropAnimation();
        }

        if (level().isClientSide && this.locallyControlled) {
            this.playedAttitude = null;
            forgetSnapshots();
            this.lerpSteps = 0;
            this.rotSteps = 0;
            this.wreckSteps = 0;
            this.observerCorrection = Vec3.ZERO;
            return;
        }
        if (level().isClientSide) {
            clearPilotPrediction();
        }

        if (isWrecked()) {
            tickFalling();
            return;
        }

        if (level().isClientSide) {
            tickObserver();
            return;
        }
        int simBefore = this.simTick;
        serverTick();
        this.stepsLastTick = Math.max(1, this.simTick - simBefore);
    }

    /** How much faster than the baked clip's own rate the propellers spin once airborne. */
    static final float PROP_FLYING_RATE = 28.0F;
    /** Rotors on a flat battery: still turning enough to let it down, but plainly winding down. */
    private static final float PROP_AUTOLAND_RATE = 11.0F;
    /** Ticks to reach full speed on takeoff: quick, like motors spinning up under power. */
    private static final float PROP_SPIN_UP_STEP = PROP_FLYING_RATE / 6.0F;
    /** Ticks to coast to a stop on landing: slower, like blades winding down under their own drag. */
    private static final float PROP_SPIN_DOWN_STEP = PROP_FLYING_RATE / 20.0F;

    /**
     * Current propeller speed, in multiples of the baked clip's own rate; eases toward 0 or {@link
     * #PROP_FLYING_RATE}.
     */
    private float propRate;
    private float propPhase;
    private float propPhaseOld;

    /**
     * Advances how far into the baked propeller loop this drone is, at a rate that ramps up on takeoff and
     * coasts down on landing instead of snapping — rather than feeding the clip real time directly, which
     * would spin it at its own (slow) authored rate constantly, even while parked.
     */
    private void tickPropAnimation() {
        this.propPhaseOld = this.propPhase;

        float targetRate = propTargetRate();
        if (targetRate > this.propRate) {
            this.propRate = Math.min(this.propRate + PROP_SPIN_UP_STEP, targetRate);
        } else {
            this.propRate = Math.max(this.propRate - PROP_SPIN_DOWN_STEP, targetRate);
        }

        this.propPhase += this.propRate / 20.0F;
    }

    /**
     * The speed the rotors are trying to turn at, which {@link #tickPropAnimation} then ramps toward rather
     * than snapping to.
     */
    public float propTargetRate() {
        boolean airborne = !isRemoved() && !isGrounded();
        return !airborne ? 0.0F : getCharge() > 0.0F ? PROP_FLYING_RATE : PROP_AUTOLAND_RATE;
    }

    /**
     * Client-only: the propeller animation's current playback time, for {@link
     * dev.krutlexik.skyfront.drone.client.render.DroneRenderer}.
     */
    public float getPropPhase(float partialTick) {
        return Mth.lerp(partialTick, this.propPhaseOld, this.propPhase);
    }

    /** Client-side motion for a drone this client is watching rather than flying. */
    private void tickObserver() {
        this.playedAttitude = null;
        this.observerClock++;
        if (!this.snapshots.isEmpty()) {
            if (this.ticksSinceSnapshot++ < staleTicks()) {
                playSnapshots();
                this.lerpSteps = 0;
                this.observerCorrection = Vec3.ZERO;
                this.rotSteps = 0;
                return;
            }
            forgetSnapshots();
        }

        Vec3 velocity = ++this.trackerSilence > OBSERVER_SILENCE_TICKS ? Vec3.ZERO : getDeltaMovement();
        if (this.lerpSteps > 0) {
            this.lerpSteps = 0;
            Vec3 target = new Vec3(this.lerpX, this.lerpY, this.lerpZ)
                    .add(velocity.scale(OBSERVER_PACKET_AGE_TICKS));
            this.observerCorrection = target.subtract(position());
            if (this.observerCorrection.lengthSqr() > OBSERVER_SNAP_DISTANCE * OBSERVER_SNAP_DISTANCE) {
                setPos(target.x, target.y, target.z);
                this.observerCorrection = Vec3.ZERO;
            }
        }

        if (velocity.lengthSqr() > 1.0E-8D || this.observerCorrection.lengthSqr() > 1.0E-6D) {
            Vec3 step = velocity.add(this.observerCorrection.scale(OBSERVER_CORRECTION_SHARE));
            this.observerCorrection = this.observerCorrection.scale(1.0D - OBSERVER_CORRECTION_SHARE);
            setPos(getX() + step.x, getY() + step.y, getZ() + step.z);
        }

        if (this.rotSteps > 0) {
            float yRot = getYRot() + (float) Mth.wrapDegrees(this.lerpYRot - getYRot()) / this.rotSteps;
            float xRot = getXRot() + (float) (this.lerpXRot - getXRot()) / this.rotSteps;
            this.rotSteps--;
            setRot(yRot, xRot);
        }
    }

    /**
     * Keeps a continuous, forward-reaching corridor of chunks loaded along the drone's path, copied from
     * {@code ShahedEntity} because that scheme is what holds up on the hybrid Bukkit/Paper cores this mod is
     * run on (the async pre-warm goes through Paper's own loader; see {@link ChunkLoadHelper}).
     */
    private void maintainFlightChunks() {
        if (this.tickCount % 4 != 0) {
            return;
        }
        loadFlightChunks();
    }

    /**
     * The same corridor, laid without waiting for the refresh interval — and, crucially, callable from
     * outside this entity's own tick.
     */
    public void loadFlightChunks() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        ChunkPos current = this.chunkPosition();
        ChunkLoadHelper.keepTicking(serverLevel, current);

        Vec3 motion = this.getDeltaMovement();
        double speed = motion.horizontalDistance();

        if (needsLongRangeLink()) {
            double lead = DroneLongRange.streamRadius() * 16.0 * 0.6;
            double leadX = speed > 1.0E-3 ? motion.x / speed * lead : 0.0;
            double leadZ = speed > 1.0E-3 ? motion.z / speed * lead : 0.0;
            ChunkPos centre = ChunkLoadHelper.chunkAt(this.getX() + leadX, this.getZ() + leadZ);
            int radius = DroneLongRange.loadedRadius();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    ChunkLoadHelper.keepLoaded(serverLevel, new ChunkPos(centre.x + dx, centre.z + dz));
                }
            }
            if (speed > 1.0E-3) {
                double dirX = motion.x / speed;
                double dirZ = motion.z / speed;
                double preload = Math.max((radius + 1) * 16.0 * 2.0, speed * 20.0 * 4.0);
                for (double ahead = (radius + 1) * 16.0; ahead <= preload; ahead += 16.0) {
                    ChunkLoadHelper.preloadAhead(serverLevel,
                            ChunkLoadHelper.chunkAt(this.getX() + dirX * ahead, this.getZ() + dirZ * ahead));
                }
            }
            return;
        }

        if (speed < 1.0E-3) {
            return;
        }
        double dirX = motion.x / speed;
        double dirZ = motion.z / speed;

        double ticking = Math.max(48.0, speed * 20.0 * 2.0);
        ChunkPos last = current;
        for (double ahead = 16.0; ahead <= ticking; ahead += 16.0) {
            ChunkPos next = ChunkLoadHelper.chunkAt(this.getX() + dirX * ahead, this.getZ() + dirZ * ahead);
            if (!next.equals(last)) {
                ChunkLoadHelper.keepTicking(serverLevel, next);
                last = next;
            }
        }
        double preload = Math.max(160.0, speed * 20.0 * 6.0);
        for (double ahead = ticking + 64.0; ahead <= preload; ahead += 64.0) {
            ChunkPos far = ChunkLoadHelper.chunkAt(this.getX() + dirX * ahead, this.getZ() + dirZ * ahead);
            ChunkLoadHelper.preloadAhead(serverLevel, far);
        }
    }

    private void serverTick() {
        this.simulatedThisTick = false;
        Optional<UUID> controller = getControllerId();
        ServerPlayer pilot = controller.map(id -> level().getPlayerByUUID(id))
                .filter(ServerPlayer.class::isInstance)
                .map(ServerPlayer.class::cast)
                .orElse(null);
        if (controller.isPresent()) {
            if (pilot == null || pilot.isRemoved() || !pilot.isAlive() || pilot.level() != level()
                    || distanceToSqr(pilot) > controlRangeSqr()) {
                releaseControl(true);
                pilot = null;
            } else if (this.inputSilence > LINK_SILENCE_TICKS) {
                releaseControl(false);
                pilot = null;
            }
        }
        if (this.tickCount % 10 == 0) {
            dev.krutlexik.skyfront.drone.DroneLinks.remember(this);
        }

        if (getCharge() <= 0.0F && isControlled()) {
            packRanOut();
        }
        boolean piloted = isControlled() && pilot != null;

        if (autolandsWhenFlat() && getCharge() <= 0.0F && !isGrounded()) {
            Vec3 velocity = getDeltaMovement();
            double descent = Math.max(velocity.y - AUTOLAND_EASE, -AUTOLAND_SPEED);
            setDeltaMovement(velocity.x * AUTOLAND_DRAG, descent, velocity.z * AUTOLAND_DRAG);
            move(MoverType.SELF, getDeltaMovement());
            this.simTick++;
            this.simulatedThisTick = true;
            broadcastState();
            return;
        }

        if (piloted) {
            this.inputSilence++;
            if (!flyQueuedInputs(pilot)) {
                return;
            }
        } else if (isGrounded() && getDeltaMovement().lengthSqr() < 1.0E-6D) {
            if (getDeltaMovement() != Vec3.ZERO) {
                setDeltaMovement(Vec3.ZERO);
            }
            syncInputLoad();
            return;
        } else if (!flyOneStep(PilotInput.NEUTRAL, null)) {
            return;
        }
        syncInputLoad();

        if (piloted && isControlled()) {
            maintainFlightChunks();
            dev.krutlexik.skyfront.drone.DroneLongRange.tick(this, pilot);
        }
        broadcastState();
    }

    /** This tick's share of the pilot's queued inputs, one simulated tick each. */
    private boolean flyQueuedInputs(@Nullable ServerPlayer pilot) {
        boolean heard = !this.inputQueue.isEmpty();

        while (this.owedInputs > 0 && !this.inputQueue.isEmpty()) {
            PilotInput owed = this.inputQueue.pollFirst();
            this.lastInput = owed;
            this.lastInputSeq = owed.seq();
            this.owedInputs--;
        }
        if (!carryOutOrders(pilot)) {
            return false;
        }

        refillInputCredit();
        int flown = 0;
        while (!this.inputQueue.isEmpty() && flown < MAX_INPUTS_PER_TICK && this.inputCredit >= 1.0D) {
            PilotInput input = this.inputQueue.pollFirst();
            this.inputCredit -= 1.0D;
            this.lastInput = input;
            this.lastInputSeq = input.seq();
            flown++;
            if (!flyOneStep(input, pilot) || !carryOutOrders(pilot)) {
                return false;
            }
            if (!keepStepping()) {
                break;
            }
        }
        if (heard) {
            return true;
        }

        double silent = (System.nanoTime() - this.lastInputNanos) / NANOS_PER_TICK;
        if (this.lastInputSeq < 0) {
            if (silent <= INPUT_SILENCE_LIMIT) {
                return true;
            }
            if (!flyOneStep(PilotInput.NEUTRAL, pilot)) {
                return false;
            }
            keepStepping();
            return true;
        }
        if (silent <= STALL_TOLERANCE_TICKS) {
            return true;
        }
        if (this.inputCredit < 1.0D) {
            return true;
        }
        PilotInput held = silent > INPUT_SILENCE_LIMIT ? this.lastInput.released() : this.lastInput;
        PilotInput guess = held.withSeq(this.lastInputSeq + this.owedInputs + 1);
        this.inputCredit -= 1.0D;
        if (this.owedInputs < MAX_OWED_INPUTS) {
            this.owedInputs++;
        }
        if (!flyOneStep(guess, pilot) || !carryOutOrders(pilot)) {
            return false;
        }
        keepStepping();
        return true;
    }

    /** The chunk last given a ticking ticket from inside a run of steps; see keepStepping. */
    private long steppedChunk = Long.MIN_VALUE;

    /**
     * Server side, after each simulated step: keeps the drone's own chunk entity-ticking as it goes, and says
     * whether it can go on being simulated this tick.
     */
    private boolean keepStepping() {
        if (!(level() instanceof ServerLevel serverLevel) || isRemoved()) {
            return true;
        }
        ChunkPos chunk = chunkPosition();
        if (chunk.toLong() != this.steppedChunk) {
            this.steppedChunk = chunk.toLong();
            ChunkLoadHelper.keepTicking(serverLevel, chunk);
        }
        return serverLevel.isPositionEntityTicking(blockPosition());
    }

    private void refillInputCredit() {
        long now = System.nanoTime();
        if (this.creditClock != 0L) {
            this.inputCredit = Math.min(MAX_INPUT_CREDIT,
                    this.inputCredit + (now - this.creditClock) / NANOS_PER_TICK);
        }
        this.creditClock = now;
    }

    /** One simulated tick, and the crash check that goes with it. */
    private boolean flyOneStep(PilotInput input, @Nullable ServerPlayer pilot) {
        flyPilotStep(input);
        this.simTick++;
        this.simulatedThisTick = true;

        if (this.lastCollisionSpeed * SkyfrontConfig.KMH_PER_BLOCK_TICK >= crashSpeedKmh()) {
            Entity credit = creditFor(pilot);
            Entity struck = this.lastRammed;
            releaseControl(StopControlPayload.Reason.CRASHED);
            if (struck != null && !struck.isRemoved()) {
                ram(struck, credit);
            }
            destroyOnImpact(credit);
            return false;
        }
        return true;
    }

    /**
     * This tick of the drone, stamped with its own tick number, to everyone watching it — the pilot ignores
     * it, flying a copy of their own.
     */
    private void broadcastState() {
        if (!this.simulatedThisTick) {
            return;
        }
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 velocity = getDeltaMovement();
        Quaternionfc attitude = linkAttitude();
        DroneStatePayload state = new DroneStatePayload(getId(), this.simTick,
                getX(), getY(), getZ(), velocity.x, velocity.y, velocity.z, getYRot(), getXRot(),
                attitude.x(), attitude.y(), attitude.z(), attitude.w());
        dev.krutlexik.skyfront.drone.TrackerAccess.Entry tracked =
                dev.krutlexik.skyfront.drone.TrackerAccess.tracked(serverLevel, getId());
        if (tracked == null) {
            return;
        }
        for (net.minecraft.server.network.ServerPlayerConnection connection : tracked.sfw$seenBy()) {
            ServerPlayer watcher = connection.getPlayer();
            if (!isControlledBy(watcher) && PacketDistributor.hasChannel(watcher)) {
                PacketDistributor.sendToPlayer(watcher, state);
            }
        }
    }

    /** Whether a flat battery puts this drone into a controlled descent of its own. */
    public boolean autolandsWhenFlat() {
        return true;
    }

    /** Whether the motors have stopped turning — written off, or switched off. */
    public boolean motorsDead() {
        return isRemoved() || isWrecked();
    }

    /**
     * Whether the drone is currently resting on a block, checked directly from its position rather than
     * vanilla's {@link #onGround()} flag.
     */
    public boolean isGrounded() {
        AABB box = getBoundingBox();
        AABB probe = new AABB(box.minX + 0.1, box.minY - 0.06, box.minZ + 0.1,
                box.maxX - 0.1, box.minY + 0.01, box.maxZ - 0.1);
        return !level().noCollision(this, probe);
    }

    /** Mouse-look for the camera gimbal. */
    public void turnGimbal(double yawDelta, double pitchDelta) {
        float yaw = getYRot() + (float) yawDelta;
        float pitch = Mth.clamp(getXRot() + (float) pitchDelta, minPitch(), maxPitch());
        setYRot(yaw);
        setXRot(pitch);
        this.yRotO = yaw;
        this.xRotO = pitch;
    }

    /** One tick of flight, run on the pilot's client. */
    public void applyControlInput(boolean forward, boolean backward, boolean left, boolean right,
                                  boolean up, boolean down) {
        float wrapped = Mth.wrapDegrees(getYRot());
        this.yRotO += wrapped - getYRot();
        setYRot(wrapped);

        this.inputSideways = decay(this.inputSideways, left, right);
        this.inputVertical = decay(this.inputVertical, up, down);
        this.inputForward = decay(this.inputForward, forward, backward);

        boolean grounded = isGrounded();
        float touchdownSpeed = grounded ? settleOnGround() : 0.0F;

        boolean throttled = up || (!grounded && (forward || backward || left || right || down));

        if (throttled && getDeltaMovement().horizontalDistanceSqr() < 1.0D) {
            setDeltaMovement(getDeltaMovement().multiply(1.04D, 1.0D, 1.04D));
        }

        float friction = AIR_FRICTION;

        moveRelative(thrust(), new Vec3(2.0D * this.inputSideways, 2.0D * this.inputVertical, 2.0D * this.inputForward));

        Vec3 approach = getDeltaMovement();
        Vec3 from = position();
        recordImpact(from, approach, touchdownSpeed);

        double yawRad = getYRot() * Mth.DEG_TO_RAD;
        double sin = Mth.sin((float) yawRad);
        double cos = Mth.cos((float) yawRad);
        Vec3 velocity = getDeltaMovement();
        double localSide = velocity.x * cos + velocity.z * sin;
        double localForward = -velocity.x * sin + velocity.z * cos;

        localSide = axisFriction(localSide, this.inputSideways, friction, HORIZONTAL_BOOST, HORIZONTAL_HOLD);
        localForward = axisFriction(localForward, this.inputForward, friction, HORIZONTAL_BOOST, HORIZONTAL_HOLD);
        double localUp = axisFriction(velocity.y, this.inputVertical, VERTICAL_FRICTION, VERTICAL_BOOST, VERTICAL_HOLD);
        double maxDescent = DroneTuning.get(DroneTuning.Value.MAVIC_MAX_DESCENT_SPEED) / SkyfrontConfig.KMH_PER_BLOCK_TICK;
        if (localUp < -maxDescent) {
            localUp = -maxDescent;
        }

        double cap = topSpeed();
        if (cap > 0.0D) {
            double horizontal = Math.sqrt(localSide * localSide + localForward * localForward);
            if (horizontal > cap) {
                double scale = cap / horizontal;
                localSide *= scale;
                localForward *= scale;
            }
            if (localUp > cap) {
                localUp = cap;
            }
        }

        Vec3 newVelocity = new Vec3(localSide * cos - localForward * sin, localUp, localForward * cos + localSide * sin);
        if (newVelocity.lengthSqr() < 1.0E-7D) {
            newVelocity = Vec3.ZERO;
        }
        setDeltaMovement(newVelocity);
    }

    /** One tick of a drone sitting on a block. */
    protected float settleOnGround() {
        float touchdownSpeed = (float) getDeltaMovement().length();

        this.inputSideways = 0.0F;
        this.inputForward = 0.0F;
        if (this.inputVertical < 0.0F) {
            this.inputVertical = 0.0F;
        }

        Vec3 landing = getDeltaMovement();
        double skidX = landing.x * GROUND_FRICTION;
        double skidZ = landing.z * GROUND_FRICTION;
        if (skidX * skidX + skidZ * skidZ < SKID_STOP_SPEED * SKID_STOP_SPEED) {
            skidX = 0.0D;
            skidZ = 0.0D;
        }
        setDeltaMovement(skidX, Math.max(landing.y, 0.0D), skidZ);
        return touchdownSpeed;
    }

    /**
     * Moves the drone along {@code approach} and records what it ran into, so the pilot's client can tell a
     * hard impact from a graze (see {@link #lastCollisionSpeed}).
     */
    protected void recordImpact(Vec3 from, Vec3 approach, float touchdownSpeed) {
        move(MoverType.SELF, approach);

        if (!level().isClientSide) {
            this.recentApproach = Math.max(this.recentApproach * RECENT_APPROACH_KEEP, approach.length());
        }
        Ram ram = !this.replaying && approach.length() >= ramSpeed() ? entityInTheWay(from, approach) : null;
        float struck = impactSpeed(approach, ram);
        this.lastCollisionSpeed = Math.max(struck, touchdownSpeed);
        this.lastRammed = ram != null ? ram.entity() : null;
        if (ram != null) {
            Vec3 contact = from.add(approach.scale(ram.contact()));
            setPos(contact.x, contact.y, contact.z);
            setDeltaMovement(Vec3.ZERO);
        }
    }

    /**
     * How hard this tick's move counted as a hit: a ram is the end of the airframe whatever the closing
     * speed, a collision with anything solid is at the speed it was flying, and a clean tick is nothing.
     */
    protected float impactSpeed(Vec3 approach, @Nullable Ram ram) {
        if (ram != null) {
            return Math.nextUp((float) Math.max(approach.length(), crashSpeed()));
        }
        if (this.horizontalCollision || this.verticalCollision) {
            return (float) approach.length();
        }
        return 0.0F;
    }

    /** An axis snaps to full deflection while its key is held, and eases back to neutral once released. */
    protected static float decay(float axis, boolean positive, boolean negative) {
        if (positive) {
            return INPUT_PEAK;
        }
        if (negative) {
            return -INPUT_PEAK;
        }
        return axis >= 0.0F
                ? Mth.clamp(axis - INPUT_DECAY, 0.0F, 1.0F)
                : Mth.clamp(axis + INPUT_DECAY, -1.0F, 0.0F);
    }

    protected static double axisFriction(double localVelocity, float axisInput, float baseFriction, float boost, float hold) {
        boolean boosting = axisInput != 0.0F
                && !(Math.signum(axisInput) == -Math.signum(localVelocity) && Math.abs(localVelocity) > 1.0E-3D);
        return localVelocity * baseFriction * (boosting ? boost : hold);
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        if (this.locallyControlled) {
            return;
        }
        // 1.20.1's move packet fills the half it leaves out from where the drone is shown rather than from the
        // lerpTarget* below, which it never calls: put the target already pending this tick back in its place.
        if (!teleport) {
            if (x == getX() && y == getY() && z == getZ()) {
                x = lerpTargetX();
                y = lerpTargetY();
                z = lerpTargetZ();
            }
            if (yRot == getYRot() && xRot == getXRot()) {
                yRot = lerpTargetYRot();
                xRot = lerpTargetXRot();
            }
        }
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yRot;
        this.lerpXRot = xRot;
        this.lerpSteps = 1;
        this.trackerSilence = 0;
        this.rotSteps = Math.max(steps, 1);
    }

    /** The tracker's velocity, likewise a round trip stale for the pilot's copy, which has its own. */
    @Override
    public void lerpMotion(double x, double y, double z) {
        if (this.locallyControlled) {
            return;
        }
        super.lerpMotion(x, y, z);
        this.trackerSilence = 0;
    }

    /** The attitude to send to onlookers. */
    public org.joml.Quaternionfc linkAttitude() {
        return LEVEL;
    }

    private static final org.joml.Quaternionf LEVEL = new org.joml.Quaternionf();

    /**
     * How to turn the model, as one rotation, or null for a drone whose attitude the renderer can build out
     * of angles on its own.
     */
    @org.jetbrains.annotations.Nullable
    public org.joml.Quaternionf renderRotation(float partialTick) {
        return null;
    }

    public double lerpTargetX() {
        return this.lerpSteps > 0 ? this.lerpX : getX();
    }

    public double lerpTargetY() {
        return this.lerpSteps > 0 ? this.lerpY : getY();
    }

    public double lerpTargetZ() {
        return this.lerpSteps > 0 ? this.lerpZ : getZ();
    }

    public float lerpTargetXRot() {
        return this.lerpSteps > 0 && !this.locallyControlled ? (float) this.lerpXRot : getXRot();
    }

    public float lerpTargetYRot() {
        return this.lerpSteps > 0 && !this.locallyControlled ? (float) this.lerpYRot : getYRot();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);

        if (player.isShiftKeyDown()
                && dev.krutlexik.skyfront.compat.sbw.SbwMonitor.isLinkedTo(held, this)) {
            if (!level().isClientSide) {
                dev.krutlexik.skyfront.compat.sbw.SbwMonitor.unlink(held, player);
                releaseControl(false);
                player.displayClientMessage(Component.translatable("message.sfw.drone.unlinked"), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        if (player.isShiftKeyDown()) {
            if (!level().isClientSide) {
                packUpFor(player, !player.getAbilities().instabuild);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        if (dev.krutlexik.skyfront.compat.sbw.SbwMonitor.isMonitor(held)) {
            if (!level().isClientSide) {
                if (dev.krutlexik.skyfront.compat.sbw.SbwMonitor.isLinked(held)) {
                    player.displayClientMessage(dev.krutlexik.skyfront.compat.sbw.SbwMonitor
                            .isLinkedTo(held, this)
                            ? Component.translatable("message.sfw.drone.linked")
                            : Component.translatable("message.sfw.drone.monitor_busy"), true);
                } else {
                    dev.krutlexik.skyfront.compat.sbw.SbwMonitor.link(held, this);
                    player.displayClientMessage(Component.translatable("message.sfw.drone.linked"), true);
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        if (carriesStores() && !hasAttachment() && canCarry(held)) {
            if (!level().isClientSide) {
                setAttachment(held.copyWithCount(1));
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                player.displayClientMessage(
                        Component.translatable("message.sfw.drone.grenade_attached"), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        return InteractionResult.PASS;
    }

    /**
     * Packs the drone up into {@code player}'s hands: the link let go, the item handed over (with {@code
     * give}) along with whatever store was slung under it, and the airframe gone.
     */
    public void packUpFor(Player player, boolean give) {
        releaseControl(false);
        dev.krutlexik.skyfront.compat.sbw.SbwMonitor.unlinkMatching(player, this);
        ItemStack store = getAttachment();
        setAttachment(ItemStack.EMPTY);
        if (give) {
            giveOrDrop(player, packedItem());
            if (!store.isEmpty()) {
                giveOrDrop(player, store);
            }
        }
        discard();
    }

    /** Leaves the world as its item, dropped where it stood, with any store it carried. */
    public void dropAsItem() {
        ItemStack store = getAttachment();
        setAttachment(ItemStack.EMPTY);
        spawnAtLocation(packedItem());
        if (!store.isEmpty()) {
            spawnAtLocation(store);
        }
        discard();
    }

    /** Hands an item to the player, or leaves it where the drone stood if there is no room for it. */
    private void giveOrDrop(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            spawnAtLocation(stack);
        }
    }

    /** How much damage this drone takes before it is destroyed, from its own config figure. */
    protected double maxHitpoints() {
        return SkyfrontConfig.MAVIC_HITPOINTS.get();
    }

    /** Current hit points, seeded from the config the first time it is asked for. */
    public float getHealth() {
        if (this.health < 0.0F) {
            this.health = (float) maxHitpoints();
        }
        return this.health;
    }

    /**
     * Any damage — a fist, an arrow, a modded gun (TACZ, SuperbWarfare) — chips the drone's hit points, and
     * running them out destroys it: the feed cuts out with the signal-loss freeze for whoever was piloting,
     * and the drone leaves the world for good (no wreck, no item, like a crash).
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved() || isWrecked() || this.destroyed || amount <= 0.0F) {
            return false;
        }
        if (source.getEntity() == this || source.getDirectEntity() == this) {
            return false;
        }
        this.health = getHealth() - amount;
        if (this.health > 0.0F) {
            return true;
        }

        releaseControl(StopControlPayload.Reason.DESTROYED);

        Entity attacker = creditFor(source.getEntity());
        if (isGrounded()) {
            destroyNow(attacker);
            return true;
        }

        if (GrenadeAttachments.isExplosive(getAttachment())
                && this.random.nextInt(100) < SkyfrontConfig.DRONE_AIRBURST_CHANCE.get()) {
            destroyNow(attacker);
            return true;
        }

        this.downedById = attacker != null ? attacker.getUUID() : null;
        startFalling();
        return true;
    }

    /** Cuts the motors and lets the drone go. */
    private void startFalling() {
        this.entityData.set(DATA_WRECKED, true);
        this.swerve = this.random.nextBoolean() ? 1 : -1;
        setNoGravity(true);
        setDeltaMovement(getDeltaMovement().add(0.0D, -0.05D, 0.0D));
    }

    /** Ticks the client takes to reach each position the server reports for a falling wreck. */
    private static final int WRECK_LERP_STEPS = 3;
    /** Ticks left in reaching the last of those reports. */
    private int wreckSteps;

    /** Client side: the wreck's fall is the server's alone, followed rather than flown a second time. */
    private void followWreck() {
        if (this.lerpSteps > 0) {
            this.lerpSteps = 0;
            this.wreckSteps = WRECK_LERP_STEPS;
        }
        if (this.wreckSteps > 0) {
            double share = 1.0D / this.wreckSteps;
            setPos(getX() + (this.lerpX - getX()) * share,
                    getY() + (this.lerpY - getY()) * share,
                    getZ() + (this.lerpZ - getZ()) * share);
            setRot(getYRot() + (float) (Mth.wrapDegrees(this.lerpYRot - getYRot()) * share),
                    getXRot() + (float) ((this.lerpXRot - getXRot()) * share));
            this.wreckSteps--;
        } else {
            Vec3 velocity = getDeltaMovement();
            setPos(getX() + velocity.x, getY() + velocity.y, getZ() + velocity.z);
        }
    }

    /**
     * Whether this airframe goes down as a crippled aeroplane — nosing over into a tightening spiral dive,
     * rolling about its own length, as the Shahed does — rather than as a quad that has lost its motors.
     */
    protected boolean fallsLikeAPlane() {
        return false;
    }

    /** The rotation the model was drawn at when it was shot down, which a tumbling wreck starts from. */
    protected org.joml.Quaternionf wreckStartAttitude() {
        return new org.joml.Quaternionf().rotationY((180.0F - getYRot()) * Mth.DEG_TO_RAD);
    }

    /** The bank, in the renderer's degrees, a falling plane starts its roll from. */
    protected float wreckStartRoll() {
        return 0.0F;
    }

    private final org.joml.Quaternionf tumble = new org.joml.Quaternionf();
    private final org.joml.Quaternionf tumbleOld = new org.joml.Quaternionf();
    private final org.joml.Vector3f tumbleAxis = new org.joml.Vector3f();
    private float tumbleRate;
    private boolean tumbleReady;
    /** Ticks since it was shot down, on whichever side this is. */
    private int wreckTicks;

    /** How long a quad takes to wind up to its full tumble once its motors are gone, in ticks. */
    private static final float TUMBLE_WIND_UP = 12.0F;
    private static final double QUAD_GRAVITY = 0.06D;
    private static final double QUAD_DRAG = 0.975D;
    private static final double QUAD_DRAG_VERTICAL = 0.962D;

    /**
     * The rotation to draw a tumbling quad's wreck at, or null for a plane, which is drawn from its heading,
     * dive and roll instead.
     */
    @Nullable
    public org.joml.Quaternionf wreckRotation(float partialTick) {
        if (fallsLikeAPlane() || !this.tumbleReady) {
            return null;
        }
        return this.tumbleOld.slerp(this.tumble, Mth.clamp(partialTick, 0.0F, 1.0F), new org.joml.Quaternionf());
    }

    /** One tick of the fall. */
    private void tickFalling() {
        this.wreckTicks++;
        boolean plane = fallsLikeAPlane();
        if (plane) {
            this.spin += 0.12F;
            if (this.wreckTicks == 1) {
                this.roll = wreckStartRoll();
            }
            this.rollOld = this.roll;
            this.roll -= Math.min(this.spin * 1.5F, 9.0F);
        }

        if (level().isClientSide) {
            if (!plane) {
                tumbleStep();
            }
            followWreck();
            return;
        }

        Vec3 falling = plane ? planeFall() : quadFall();
        setDeltaMovement(falling);
        if (plane) {
            setYRot((float) (Mth.atan2(-falling.x, falling.z) * (180.0F / (float) Math.PI)));
            setXRot((float) (Mth.atan2(-falling.y, falling.horizontalDistance()) * (180.0F / (float) Math.PI)));
        }

        move(MoverType.SELF, falling);
        if (this.horizontalCollision || this.verticalCollision || isGrounded() || isInWall()) {
            destroyNow(downedBy());
            return;
        }
        if (getY() < level().getMinBuildHeight() - 16) {
            destroyNow(downedBy());
        }
    }

    /** The Shahed's own crash, step for step: see {@code ShahedEntity#tickFalling}. */
    private Vec3 planeFall() {
        Vec3 motion = getDeltaMovement();
        double speed = motion.length();
        if (speed < 1.0E-4D) {
            motion = new Vec3(0.0D, -0.1D, 0.0D);
            speed = 0.1D;
        }
        double blend = Math.min(0.0012D * this.spin, 0.06D);
        Vec3 direction = motion.scale(1.0D / speed);
        Vec3 down = new Vec3(0.0D, -1.0D, 0.0D);
        Vec3 aim = direction.add(down.subtract(direction).scale(blend));
        aim = aim.lengthSqr() > 1.0E-6D ? aim.normalize() : down;
        double newSpeed = Math.min(speed + 0.02D * Math.max(0.0D, -aim.y), 1.8D);
        Vec3 falling = aim.scale(newSpeed).add(0.0D, -Math.min(0.002D * this.spin, 0.06D), 0.0D);
        if (this.swerve != 0) {
            double curve = this.swerve * Math.min(this.spin * 0.004D, 0.045D);
            double cos = Math.cos(curve);
            double sin = Math.sin(curve);
            falling = new Vec3(falling.x * cos - falling.z * sin, falling.y, falling.x * sin + falling.z * cos);
        }
        return falling;
    }

    /** A quad with its motors gone: dropping under gravity, the air holding it back. */
    private Vec3 quadFall() {
        Vec3 motion = getDeltaMovement().add(0.0D, -QUAD_GRAVITY, 0.0D);
        return new Vec3(motion.x * QUAD_DRAG, motion.y * QUAD_DRAG_VERTICAL, motion.z * QUAD_DRAG);
    }

    /** Client side: a quad's tumble. */
    private void tumbleStep() {
        if (!this.tumbleReady) {
            this.tumble.set(wreckStartAttitude());
            this.tumbleOld.set(this.tumble);
            RandomSource dice = RandomSource.create(getId() * 341873128712L + 132897987541L);
            this.tumbleAxis.set((float) dice.nextGaussian() * 0.6F, (float) dice.nextGaussian() * 0.3F,
                    (float) dice.nextGaussian());
            if (this.tumbleAxis.lengthSquared() < 1.0E-4F) {
                this.tumbleAxis.set(1.0F, 0.0F, 0.0F);
            }
            this.tumbleAxis.normalize();
            this.tumbleRate = (14.0F + dice.nextFloat() * 14.0F) * Mth.DEG_TO_RAD;
            this.tumbleReady = true;
        }
        this.tumbleOld.set(this.tumble);
        float windUp = Math.min(1.0F, this.wreckTicks / TUMBLE_WIND_UP);
        float angle = this.tumbleRate * windUp * windUp;
        if (angle > 1.0E-5F) {
            new org.joml.Quaternionf().fromAxisAngleRad(this.tumbleAxis, angle).mul(this.tumble, this.tumble);
        }
    }

    /** The drone flew into something at speed. */
    public void destroyOnImpact(@Nullable Entity pilot) {
        destroyNow(pilot);
    }

    /** Puts the impact of a ram into whatever the drone flew into. */
    public void ram(Entity struck, @Nullable Entity pilot) {
        if (level().isClientSide) {
            return;
        }
        float damage = ramDamage();
        if (damage <= 0.0F) {
            return;
        }
        Entity credit = creditFor(pilot);
        boolean vehicle = dev.krutlexik.skyfront.compat.sbw.SbwCompat.isLoaded()
                && dev.krutlexik.skyfront.compat.sbw.SbwVehicles.isVehicle(struck);
        if (vehicle) {
            dev.krutlexik.skyfront.compat.sbw.SbwVehicles.damageVehicleDirectHit(struck, this, credit, damage);
        }
        struck.hurt(ramDamage(credit, vehicle), damage);
    }

    /** The end of the drone: anything it is carrying goes off, and the airframe leaves the world. */
    protected void destroyNow(@Nullable Entity attacker) {
        if (this.destroyed) {
            return;
        }
        this.destroyed = true;
        releaseControl(StopControlPayload.Reason.DESTROYED);
        if (level() instanceof ServerLevel serverLevel) {
            goOff(serverLevel, attacker);
        }
        discard();
    }

    /** What the end of this drone looks and sounds like. */
    protected void goOff(ServerLevel level, @Nullable Entity attacker) {
        goOffWithStore(level, attacker);
    }

    /** The end as a drone carrying a store meets it: the store goes off, or the airframe just breaks up. */
    protected final void goOffWithStore(ServerLevel level, @Nullable Entity attacker) {
        ItemStack store = getAttachment();
        setAttachment(ItemStack.EMPTY);

        if (GrenadeAttachments.isExplosive(store)) {
            GrenadeAttachments.detonate(level, store, position(), this, attacker);
        } else if (!store.isEmpty()) {
            ServerPlayer owner = attacker instanceof ServerPlayer player ? player : null;
            GrenadeAttachments.release(level, owner, store, position(), getDeltaMovement());
            GrenadeAttachments.breakUp(level, position(), this, attacker);
        } else {
            GrenadeAttachments.breakUp(level, position(), this, attacker);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) {
            releaseControl(false);
            if (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED) {
                dev.krutlexik.skyfront.compat.sbw.SbwMonitor.unlinkEverywhere(this);
                dev.krutlexik.skyfront.drone.DroneLinks.forget(this);
            }
        }
        super.remove(reason);
    }

    /** The item this drone packs back into when crouch-clicked — each variant answers with its own. */
    public ItemStack packedItem() {
        return new ItemStack(DroneItems.DRONE.get());
    }

    @Override
    public ItemStack getPickResult() {
        return packedItem();
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    /**
     * The airframe's hitbox is small (about a block across, a third of one tall), and vanilla scales an
     * entity's render distance to its box — so a drone would be culled at roughly fifty blocks and simply
     * vanish for anyone not standing next to it, however far away it is actually tracked.
     */
    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        double range = Math.max(controlRange(), 256.0);
        return distanceSqr < range * range;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("Charge")) {
            setCharge(tag.getFloat("Charge"));
        }
        if (tag.contains("Health")) {
            this.health = tag.getFloat("Health");
        }
        if (tag.contains("Armed")) {
            this.entityData.set(DATA_ARMED, tag.getBoolean("Armed"));
        }
        if (tag.hasUUID("LastPilot")) {
            this.lastPilotId = tag.getUUID("LastPilot");
        }
        if (tag.contains("Attachment", CompoundTag.TAG_COMPOUND)) {
            setAttachment(ItemStack.of(tag.getCompound("Attachment")));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Charge", getCharge());
        tag.putFloat("Health", getHealth());
        tag.putBoolean("Armed", this.entityData.get(DATA_ARMED));
        if (this.lastPilotId != null) {
            tag.putUUID("LastPilot", this.lastPilotId);
        }
        if (hasAttachment()) {
            tag.put("Attachment", getAttachment().save(new CompoundTag()));
        }
    }
}
