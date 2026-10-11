package dev.krutlexik.skyfront.drone.client;

import dev.krutlexik.skyfront.config.SkyfrontConfig;
import dev.krutlexik.skyfront.drone.DroneMod;
import dev.krutlexik.skyfront.drone.RateModeFlight;
import dev.krutlexik.skyfront.drone.GrenadeAttachments;
import dev.krutlexik.skyfront.drone.client.radio.RadioInput;
import dev.krutlexik.skyfront.drone.radio.RadioProfile;
import dev.krutlexik.skyfront.drone.entity.DroneEntity;
import dev.krutlexik.skyfront.drone.entity.DroneFlightState;
import dev.krutlexik.skyfront.drone.entity.InterceptorDroneEntity;
import dev.krutlexik.skyfront.drone.entity.PilotInput;
import dev.krutlexik.skyfront.drone.network.DroneDropPayload;
import dev.krutlexik.skyfront.drone.network.DroneInputPayload;
import dev.krutlexik.skyfront.drone.network.DroneLinkPayload;
import dev.krutlexik.skyfront.drone.network.StartControlPayload;
import dev.krutlexik.skyfront.drone.network.StopControlRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Client-side state of "I am currently flying a drone". */
public final class DroneControl {
    public static final float MIN_ZOOM = 1.0F;

    /** The largest zoom the gimbal will reach, from the config. */
    public static float maxZoom() {
        return (float) (double) SkyfrontConfig.MAVIC_MAX_ZOOM.get();
    }

    /** The drone being flown, or null when the pilot has their own eyes back. */
    @Nullable
    private static DroneEntity drone;

    /** The zoom the lens is at this frame, easing toward {@link #zoomTarget}. */
    private static float zoom = MIN_ZOOM;
    /** Where the wheel has sent the zoom: the lens travels there smoothly rather than in notches. */
    private static float zoomTarget = MIN_ZOOM;
    /** When the lens last moved, for easing it at the same pace whatever the frame rate. */
    private static long zoomClock;
    /** How quickly the lens closes on where it was sent, per second: most of the way in a fifth of one. */
    private static final double ZOOM_EASE = 12.0D;
    /**
     * The Mavic's widest view, as a zoom on top of the game's own field of view: its lens is not the
     * game's wide angle, and at 1x the picture is what 1.1x on the bare field of view was.
     */
    public static final float CAMERA_LENS = 1.1F;

    /**
     * The entity id of the drone being flown, kept separately from the entity itself: the tracker can take
     * the drone away and hand back a fresh copy under the same id, and this is what lets a link feed put the
     * right one back (see {@link #acceptLink}).
     */
    private static int flyingId = -1;
    /** Ticks the drone has been missing from the client's world. */
    private static int missingTicks;
    private static final int MISSING_GRACE_TICKS = 200;

    /** Mouse movement the gimbal has been asked for but has not caught up with yet. */
    private static double pendingYaw;
    private static double pendingPitch;
    /** Share of the outstanding movement the gimbal covers each frame. */
    private static final double GIMBAL_EASING = 0.35D;

    /**
     * The number of the last input made, for the whole session — never reset, so an input from one flight can
     * never be taken for one from the next.
     */
    private static int inputSeq;

    /** The pilot's copy of the drone, flown ahead of the server. */
    private static final DronePrediction PREDICTION = new DronePrediction();

    /** Corrections the server has made to this flight's prediction so far. */
    public static int predictionCorrections() {
        return PREDICTION.corrections();
    }

    /** Total piloted ticks this session; the OSD's "Storage" counter runs down against it. */
    private static int flightTicks;

    /** Mouse travel in one 60 Hz frame that puts the stick at its stop, in pixels. */
    private static final double STICK_FULL_SCALE = 47.0D;
    /** How much of the stick's travel is kept back for fast movement. */
    private static final double STICK_EXPO = 0.45D;
    /** The frame the mouse is measured against, whatever the actual frame rate is. */
    private static final double STICK_REFERENCE_FRAME = 1.0D / 60.0D;
    /** How fast the mouse stick follows the mouse, per second. */
    private static final double STICK_SMOOTHING = 32.0D;
    /** Where the mouse stick currently is, as opposed to where this frame's movement asks for. */
    private static double stickRollNow;
    private static double stickPitchNow;

    /**
     * The two mouse axes, accumulated across the frames of one tick as position × time rather than sampled at
     * the end of it.
     */
    private static double stickRollIntegral;
    private static double stickPitchIntegral;
    private static double stickSampleTime;
    /** The keyboard's two axes, each with a spring of its own. */
    private static double stickYaw;
    private static double stickThrottle;
    /** How fast the yaw spring moves, per second: on, and back off. */
    private static final double YAW_RISE = 4.0D;
    private static final double YAW_FALL = 5.5D;
    private static final double THROTTLE_RISE = 8.5D;
    /** And how fast it comes back off. */
    private static final double THROTTLE_FALL = 4.0D;
    /** Cutting the throttle deliberately is quicker than simply letting go of it. */
    private static final double THROTTLE_CUT = 2.5D;

    /** Where the camera is bolted, in degrees above the airframe's own level. */
    private static float cameraTilt = 25.0F;
    private static final float MAX_CAMERA_TILT = 90.0F;
    /**
     * The mount angle each kind of airframe was last flown at, so winding the quad's camera up does not leave
     * the plane's pointing at the sky, and the other way round.
     */
    private static final java.util.Map<net.minecraft.world.entity.EntityType<?>, Float> TILT_BY_TYPE =
            new java.util.HashMap<>();

    /** Whether the drone in the pilot's hands is flown on sticks rather than on keys. */
    public static boolean onSticks() {
        return drone instanceof InterceptorDroneEntity interceptor && interceptor.rateMode();
    }

    /** The camera's mounting angle above the airframe's own level, degrees. */
    public static float cameraTilt() {
        return cameraTilt;
    }

    /** The mouse, as the right-hand stick. */
    private static void feedSticks(double dx, double dy, double frameSeconds) {
        double dt = Mth.clamp(frameSeconds, 1.0D / 480.0D, 0.2D);
        double frameScale = Mth.clamp(STICK_REFERENCE_FRAME / dt, 0.1D, 8.0D);
        double gain = SkyfrontConfig.INTERCEPTOR_MOUSE_GAIN.get() / STICK_FULL_SCALE;
        double x = dx * frameScale * gain;
        double y = dy * frameScale * gain;

        double magnitude = Math.min(Math.sqrt(x * x + y * y), 1.0D);
        if (magnitude > 1.0E-6D) {
            double curved = magnitude * (1.0D - STICK_EXPO) + magnitude * magnitude * magnitude * STICK_EXPO;
            double scale = curved / magnitude;
            x *= scale;
            y *= scale;
        }

        stickRollNow = RateModeFlight.approach(stickRollNow, Mth.clamp(x, -1.0D, 1.0D), STICK_SMOOTHING, dt);
        stickPitchNow = RateModeFlight.approach(stickPitchNow, Mth.clamp(-y, -1.0D, 1.0D), STICK_SMOOTHING, dt);

        stickRollIntegral += stickRollNow * dt;
        stickPitchIntegral += stickPitchNow * dt;
        stickSampleTime += dt;
    }

    /** The two key axes, wound on and off through their springs. */
    private static void tickStickKeys(Minecraft minecraft) {
        double dt = 1.0D / 20.0D;
        boolean left = minecraft.options.keyLeft.isDown();
        boolean right = minecraft.options.keyRight.isDown();
        boolean up = minecraft.options.keyUp.isDown() || minecraft.options.keyJump.isDown();
        boolean down = minecraft.options.keyDown.isDown() || minecraft.options.keyShift.isDown();

        double yawTarget = (right ? 1.0D : 0.0D) - (left ? 1.0D : 0.0D);
        boolean yawRising = Math.abs(yawTarget) > Math.abs(stickYaw);
        stickYaw = RateModeFlight.approach(stickYaw, yawTarget, yawRising ? YAW_RISE : YAW_FALL, dt);

        double rate = up ? THROTTLE_RISE : (down ? THROTTLE_FALL * THROTTLE_CUT : THROTTLE_FALL);
        stickThrottle = Mth.clamp(
                RateModeFlight.approach(stickThrottle, up ? 1.0D : 0.0D, rate, dt), 0.0D, 1.0D);
    }

    /** Everything the sticks are holding, thrown away — a flight ending, or one not on sticks. */
    private static void clearSticks() {
        stickRollNow = 0.0D;
        stickPitchNow = 0.0D;
        stickRollIntegral = 0.0D;
        stickPitchIntegral = 0.0D;
        stickSampleTime = 0.0D;
        stickYaw = 0.0D;
        stickThrottle = 0.0D;
    }

    /** Where the pilot's own body was standing when the flight began, and where it is held for the duration. */
    @Nullable
    private static Vec3 pilotAnchor;

    private static boolean hudHiddenByUs;
    private static boolean savedHideGui;

    private static void updateHudHiding(Minecraft minecraft) {
        boolean wanted = drone != null || isFpvStatic() || DroneSignalLoss.isActive();
        if (wanted == hudHiddenByUs) {
            return;
        }
        if (wanted) {
            savedHideGui = minecraft.options.hideGui;
            minecraft.options.hideGui = true;
        } else {
            minecraft.options.hideGui = savedHideGui;
        }
        hudHiddenByUs = wanted;
    }

    /** Gives the HUD back at once, for the paths that end a flight outright. */
    private static void restoreHud() {
        if (hudHiddenByUs) {
            Minecraft.getInstance().options.hideGui = savedHideGui;
            hudHiddenByUs = false;
        }
    }

    private static int fpvStaticTicks = -1;
    /** How long the dead picture is held before the pilot is handed back their own eyes — five seconds. */
    private static final int FPV_HOLD_DURATION_TICKS = 100;

    /** Whether the feed is showing the frozen picture its link died on. */
    public static boolean isFpvStatic() {
        return fpvStaticTicks >= 0;
    }

    /**
     * Ticks since the pilot's own copy of the drone struck something hard enough to be written off, while the
     * server's word on it is awaited; -1 when nothing is pending.
     */
    private static int impactPredictedTicks = -1;
    /** The same wait, for a Mavic. */
    private static int cameraImpactTicks = -1;
    /** How long to wait for the server to agree before deciding it will not. */
    private static final int IMPACT_CONFIRM_TICKS = 20;

    /**
     * Whether the FPV picture is stopped: the link has died, or the drone has just crashed and the server is
     * about to say so.
     */
    public static boolean isPictureHeld() {
        return fpvStaticTicks >= 0 || impactPredictedTicks >= 0;
    }

    /** How far through the held picture the feed is, 0..1. */
    public static float fpvStaticProgress() {
        return fpvStaticTicks >= 0
                ? Mth.clamp((float) fpvStaticTicks / FPV_HOLD_DURATION_TICKS, 0.0F, 1.0F)
                : 0.0F;
    }

    /** Drops the held picture and gives the pilot their own eyes back. */
    public static void endHeldPicture() {
        if (fpvStaticTicks < 0) {
            return;
        }
        fpvStaticTicks = -1;
        sendStopRequest();
        stop();
    }

    public static boolean isActive() {
        return drone != null;
    }

    /** Whether the drone's feed owns the screen — flying it, or sat in front of the frozen picture its link died on. */
    public static boolean holdsTheScreen() {
        return isActive() || isFpvStatic();
    }

    private static boolean replacingEntity;

    public static boolean isProtectedEntity(int entityId) {
        return !replacingEntity && drone != null && flyingId == entityId
                && (!DroneSignalLoss.isActive() || cameraImpactTicks >= 0)
                && !isFpvStatic();
    }

    /** A removal the server asked for while the drone was protected, waiting to be carried out. */
    private static int deferredRemovalId = -1;
    @Nullable
    private static Entity.RemovalReason deferredRemovalReason;

    /** Called by the client level when it refuses to remove the flown drone. */
    public static void deferRemoval(int entityId, Entity.RemovalReason reason) {
        deferredRemovalId = entityId;
        deferredRemovalReason = reason;
    }

    /** Carries out a refused removal, if there is one and the drone is no longer protected. */
    private static void applyDeferredRemoval() {
        if (deferredRemovalId < 0 || isProtectedEntity(deferredRemovalId)) {
            return;
        }
        int id = deferredRemovalId;
        Entity.RemovalReason reason = deferredRemovalReason != null
                ? deferredRemovalReason : Entity.RemovalReason.DISCARDED;
        deferredRemovalId = -1;
        deferredRemovalReason = null;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.level.getEntity(id) != null) {
            minecraft.level.removeEntity(id, reason);
        }
    }

    /** The tracker is about to replace the flown drone with a fresh copy under the same id. */
    public static void beginTrackerReplacement(Entity incoming) {
        if (incoming.getId() == deferredRemovalId) {
            deferredRemovalId = -1;
            deferredRemovalReason = null;
        }
        DroneEntity current = drone;
        if (current == null || incoming == current || incoming.getId() != flyingId
                || !(incoming instanceof DroneEntity fresh)) {
            return;
        }
        replacingEntity = true;
        DroneFlightState carried = new DroneFlightState();
        current.captureFlightState(carried);
        fresh.restoreFlightState(carried);
        fresh.setPos(current.getX(), current.getY(), current.getZ());
        fresh.xo = current.getX();
        fresh.yo = current.getY();
        fresh.zo = current.getZ();
        fresh.setYRot(current.getYRot());
        fresh.setXRot(current.getXRot());
        fresh.yRotO = current.getYRot();
        fresh.xRotO = current.getXRot();
        fresh.setDeltaMovement(current.getDeltaMovement());
        if (fresh instanceof InterceptorDroneEntity freshAirframe
                && current instanceof InterceptorDroneEntity oldAirframe) {
            freshAirframe.adoptAttitudeFrom(oldAirframe);
        }
    }

    /** And the flight moves onto the copy, camera and all. */
    public static void finishTrackerReplacement(Entity incoming) {
        if (!replacingEntity) {
            return;
        }
        replacingEntity = false;
        if (incoming instanceof DroneEntity fresh && incoming.getId() == flyingId) {
            rebind(fresh, drone);
        }
    }

    @Nullable
    public static DroneEntity drone() {
        return drone;
    }

    public static boolean isFlying(Entity entity) {
        return drone != null && drone == entity;
    }

    public static float zoom() {
        return zoom;
    }

    public static int flightSeconds() {
        return flightTicks / 20;
    }

    /**
     * The control range this flight is actually held to — the server's figure, which is the only one that
     * decides anything.
     */
    public static double controlRange() {
        if (serverControlRange > 0.0D) {
            return serverControlRange;
        }
        DroneEntity current = drone;
        return current != null ? current.controlRange() : 1.0D;
    }

    private static double serverControlRange;

    /** The server has given this client the link: take the camera and start flying. */
    public static void start(StartControlPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        DroneEntity target = minecraft.level.getEntity(payload.entityId()) instanceof DroneEntity present
                ? present : placeFarDrone(minecraft, payload);
        if (target == null) {
            return;
        }

        stop();
        drone = target;
        flyingId = payload.entityId();
        if (target instanceof InterceptorDroneEntity interceptor) {
            cameraTilt = interceptor.tiltableCamera()
                    ? TILT_BY_TYPE.getOrDefault(target.getType(), interceptor.defaultCameraTilt())
                    : interceptor.defaultCameraTilt();
        }
        missingTicks = 0;
        target.clearPilotPrediction();
        target.setArmed(payload.armed());
        target.setCharge(payload.charge());
        target.locallyControlled = true;
        armWanted = payload.armed();
        armRequested = false;
        serverControlRange = payload.controlRange();
        zoom = MIN_ZOOM;
        zoomTarget = MIN_ZOOM;

        target.setPos(payload.x(), payload.y(), payload.z());
        target.xo = payload.x();
        target.yo = payload.y();
        target.zo = payload.z();
        target.setYRot(payload.yRot());
        target.setXRot(payload.xRot());
        target.yRotO = payload.yRot();
        target.xRotO = payload.xRot();
        PREDICTION.reset();
        PREDICTION.forceResync();

        armedFlashTicks = 0;
        wasArmed = target.isArmed();
        if (target.osdStyle().isFlightController()) {
            BetaflightOsd.setHome(target);
            if (target.isArmed()) {
                FpvBattery.arm(target);
            } else {
                FpvBattery.disarm(target);
            }
        }

        minecraft.setCameraEntity(target);
        minecraft.player.setDeltaMovement(Vec3.ZERO);
        pilotAnchor = minecraft.player.position();
        clearSticks();
        RadioInput.beginFlight();
        armRefusedTicks = 0;
    }

    /** Immediate full release: camera back to the player; the server was flying the drone all along. */
    public static void stop() {
        Minecraft minecraft = Minecraft.getInstance();
        if (drone != null) {
            drone.locallyControlled = false;
            drone.clearPilotPrediction();
            drone = null;
        }
        PREDICTION.reset();
        replacingEntity = false;
        armRequested = false;
        pendingYaw = 0.0D;
        pendingPitch = 0.0D;
        clearSticks();
        serverControlRange = 0.0D;
        fpvStaticTicks = -1;
        impactPredictedTicks = -1;
        cameraImpactTicks = -1;
        flyingId = -1;
        missingTicks = 0;
        DroneSignalLoss.clear();
        DroneFeedFreeze.release();
        ThermalVision.clear();
        FpvBattery.reset();
        BetaflightOsd.clearHome();
        zoom = MIN_ZOOM;
        zoomTarget = MIN_ZOOM;
        dropCharge = 0;
        dropFlashTicks = 0;
        releaseButtonDown = false;
        armedFlashTicks = 0;
        wasArmed = false;
        pilotAnchor = null;
        if (minecraft.player != null) {
            minecraft.setCameraEntity(minecraft.player);
        }
        restoreHud();
        applyDeferredRemoval();
    }

    /** Creates a local copy of a drone the tracker has not sent to this client, for a link-up to it. */
    @Nullable
    private static DroneEntity placeFarDrone(Minecraft minecraft, StartControlPayload payload) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.byId(payload.typeId());
        if (type == null || !(type.create(minecraft.level) instanceof DroneEntity placed)) {
            return null;
        }
        placed.setId(payload.entityId());
        placed.setPos(payload.x(), payload.y(), payload.z());
        placed.setYRot(payload.yRot());
        placed.setXRot(payload.xRot());
        minecraft.level.addEntity(placed);
        return placed;
    }

    /** Hands the camera and controls straight back to the player and tells the server the link is over right now. */
    public static void requestStop() {
        if (DroneSignalLoss.isActive()) {
            endSignalLoss();
            return;
        }
        sendStopRequest();
        stop();
    }

    private static void sendStopRequest() {
        if (flyingId >= 0 && Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new StopControlRequestPayload(flyingId));
        }
    }

    /** The link feed: the drone is still out there, and here is where it is. */
    public static void acceptLink(DroneLinkPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || flyingId != payload.entityId()) {
            return;
        }

        DroneFlightState state = payload.state();
        if (minecraft.level.getEntity(payload.entityId()) instanceof DroneEntity present) {
            if (present != drone) {
                rebind(present, drone);
                PREDICTION.forceResync();
            }
        } else {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.byId(payload.typeId());
            if (type == null || !(type.create(minecraft.level) instanceof DroneEntity restored)) {
                return;
            }
            restored.setId(payload.entityId());
            DroneEntity previous = drone;
            if (previous != null) {
                DroneFlightState carried = new DroneFlightState();
                previous.captureFlightState(carried);
                restored.restoreFlightState(carried);
                if (restored instanceof InterceptorDroneEntity freshAirframe
                        && previous instanceof InterceptorDroneEntity oldAirframe) {
                    freshAirframe.adoptAttitudeFrom(oldAirframe);
                }
            } else {
                restored.restoreFlightState(state);
            }
            restored.xo = restored.getX();
            restored.yo = restored.getY();
            restored.zo = restored.getZ();
            restored.yRotO = restored.getYRot();
            restored.xRotO = restored.getXRot();
            minecraft.level.addEntity(restored);
            rebind(restored, previous);
            PREDICTION.forceResync();
        }

        PREDICTION.acceptServerState(payload.ackSeq(), state);
    }

    /** Moves the flight onto another copy of the same drone: camera, authority and all. */
    private static void rebind(DroneEntity restored, @Nullable DroneEntity previous) {
        if (previous != null) {
            previous.locallyControlled = false;
        }
        drone = restored;
        restored.locallyControlled = true;
        missingTicks = 0;
        Minecraft.getInstance().setCameraEntity(restored);
    }

    /**
     * Fed by the MouseHandler mixin, once per render frame — deliberately not batched into the once-per-tick
     * {@link #tick()} update, or the camera would visibly lag and stutter behind the mouse (see {@link
     * DroneEntity#turnGimbal}).
     */
    public static void steer(double yawDegrees, double pitchDegrees,
                             double rawX, double rawY, double frameSeconds) {
        DroneEntity current = drone;
        if (onSticks()) {
            if (current != null && !DroneSignalLoss.isActive() && !current.isWrecked()) {
                feedSticks(rawX, rawY, frameSeconds);
            }
            return;
        }
        if (current != null && !DroneSignalLoss.isActive() && !current.isWrecked()) {
            double gearing = current.lookGearing() / zoom;
            pendingYaw += yawDegrees * gearing;
            pendingPitch += pitchDegrees * gearing;
        }
        easeGimbal(current, frameSeconds);
    }

    /** Feeds the gimbal a share of the movement it still owes, rather than the whole of it at once. */
    private static void easeGimbal(@Nullable DroneEntity current, double frameSeconds) {
        if (current == null || DroneSignalLoss.isActive() || current.isWrecked()) {
            pendingYaw = 0.0D;
            pendingPitch = 0.0D;
            return;
        }
        if (Math.abs(pendingYaw) < 1.0E-4D && Math.abs(pendingPitch) < 1.0E-4D) {
            pendingYaw = 0.0D;
            pendingPitch = 0.0D;
            return;
        }
        double lag = current.gimbalLag();
        double share = lag > 0.0D ? 1.0D - Math.exp(-Math.max(frameSeconds, 0.0D) / lag) : GIMBAL_EASING;
        double yawStep = pendingYaw * share;
        double pitchStep = pendingPitch * share;
        pendingYaw -= yawStep;
        pendingPitch -= pitchStep;
        current.turnGimbal(yawStep, pitchStep);
    }

    /** Scroll wheel: sends the zoom on, multiplicatively so a notch feels the same across the whole range. */
    public static void addZoom(double scrollDelta) {
        if (DroneSignalLoss.isActive()) {
            return;
        }
        zoomTarget = Mth.clamp(zoomTarget * (float) Math.pow(1.12D, scrollDelta), MIN_ZOOM, maxZoom());
    }

    /**
     * Moves the lens toward where the wheel sent it, once a rendered frame: an even glide in and out rather
     * than a jump at every notch.
     */
    public static void easeZoom() {
        long now = System.nanoTime();
        double dt = zoomClock == 0L ? 0.0D : Math.min(0.1D, (now - zoomClock) / 1.0E9D);
        zoomClock = now;
        double k = 1.0D - Math.exp(-dt * ZOOM_EASE);
        double eased = Mth.lerp(k, Math.log(zoom), Math.log(zoomTarget));
        zoom = Math.abs(zoomTarget - (float) Math.exp(eased)) < 1.0E-3F ? zoomTarget : (float) Math.exp(eased);
    }

    private static final int ARMED_FLASH_TICKS = 20;
    private static int armedFlashTicks;
    private static boolean wasArmed;

    /** Whether the "ARMED" confirmation is still up. */
    public static boolean isArmedFlash() {
        return armedFlashTicks > 0;
    }

    public static void toggleArm() {
        DroneEntity current = drone;
        if (current != null) {
            setArmed(current, !current.isArmed());
        }
    }

    /** Asks for the motors on or off — from the arming key, or from a switch on the radio. */
    private static void setArmed(DroneEntity current, boolean wanted) {
        if (wanted == current.isArmed() || isFpvStatic() || DroneSignalLoss.isActive()
                || current.isWrecked() || !current.requiresArming()) {
            return;
        }
        if (wanted && RadioInput.active() && !RadioInput.throttleLow()) {
            armRefusedTicks = ARM_REFUSED_TICKS;
            return;
        }
        armWanted = wanted;
        armRequested = true;
    }

    /** Where the pilot has put the arming switch this flight, and whether they have touched it at all. */
    private static boolean armWanted;
    private static boolean armRequested;

    /** How long "THROTTLE" stays up on the OSD after arming is refused for it. */
    private static final int ARM_REFUSED_TICKS = 40;
    private static int armRefusedTicks;

    /** Whether arming has just been refused because the radio's throttle was not down. */
    public static boolean isArmRefused() {
        return armRefusedTicks > 0;
    }

    /** The radio's switches, once a tick. */
    private static void tickRadioSwitches(DroneEntity current) {
        RadioProfile setup = RadioInput.profile();

        int arm = RadioInput.armEdge();
        if (arm != 0) {
            if (setup.arm.mode == RadioProfile.AuxMode.SWITCH) {
                setArmed(current, arm > 0);
            } else if (arm > 0) {
                setArmed(current, !current.isArmed());
            }
        }

        if (RadioInput.selfDestructEdge() > 0) {
            selfDestruct();
        }

        int thermal = RadioInput.thermalEdge();
        if (thermal != 0) {
            if (setup.thermal.mode == RadioProfile.AuxMode.SWITCH) {
                if ((thermal > 0) != ThermalVision.isOn()) {
                    toggleThermal();
                }
            } else if (thermal > 0) {
                toggleThermal();
            }
        }

        float level = RadioInput.cameraMove();
        if (!Float.isNaN(level) && onSticks() && tiltableCamera(drone)) {
            cameraTilt = level * MAX_CAMERA_TILT;
            if (drone != null) {
                TILT_BY_TYPE.put(drone.getType(), cameraTilt);
            }
        }
    }

    /** The thermal camera switch, for the drones that have one. */
    /** Self-destruct: asks the server to set the warhead off where the drone is. */
    public static void selfDestruct() {
        DroneEntity current = drone;
        if (!(current instanceof InterceptorDroneEntity) || current.isWrecked() || DroneSignalLoss.isActive()) {
            return;
        }
        PacketDistributor.sendToServer(new dev.krutlexik.skyfront.drone.network.DroneSelfDestructPayload(
                current.getId(), inputSeq));
        if (impactPredictedTicks < 0 && fpvStaticTicks < 0) {
            current.settleInterpolation();
            BetaflightOsd.captureSignalLoss(current);
            DroneFeedFreeze.armHold();
            impactPredictedTicks = 0;
        }
    }

    public static void toggleThermal() {
        DroneEntity current = drone;
        if (current == null || DroneSignalLoss.isActive()) {
            return;
        }
        if (!ThermalVision.isAvailable(current)) {
            DroneMod.LOGGER.info("Thermal asked for on {}, which has no thermal camera", current.getType());
            return;
        }
        ThermalVision.toggle(current);
        DroneMod.LOGGER.info("Thermal camera {}", ThermalVision.isOn() ? "on" : "off");
    }

    /**
     * Whether this airframe's bolted camera can be set to another angle at all (see
     * InterceptorDroneEntity#tiltableCamera).
     */
    private static boolean tiltableCamera(@Nullable DroneEntity current) {
        return !(current instanceof InterceptorDroneEntity interceptor) || interceptor.tiltableCamera();
    }

    /** Degrees the gimbal tilts per notch of the scroll wheel on an FPV drone. */
    private static final double CAMERA_PITCH_STEP = 8.0D;

    /**
     * Scroll wheel on an FPV drone (the interceptor) tilts the camera gimbal up and down rather than zooming
     * — it has no zoom lens, so the wheel trims where its bolted camera points instead.
     */
    public static void scrollCameraPitch(double scrollDelta) {
        DroneEntity current = drone;
        if (current == null || DroneSignalLoss.isActive() || current.isWrecked()) {
            return;
        }
        if (onSticks()) {
            if (!tiltableCamera(current)) {
                return;
            }
            cameraTilt = Mth.clamp(cameraTilt + (float) (scrollDelta * CAMERA_PITCH_STEP),
                    0.0F, MAX_CAMERA_TILT);
            TILT_BY_TYPE.put(current.getType(), cameraTilt);
            return;
        }
        pendingPitch += -scrollDelta * CAMERA_PITCH_STEP;
        easeGimbal(current, 1.0D / 60.0D);
    }

    /** One tick of the feed, run on the pilot's client after the level has ticked. */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        updateHudHiding(minecraft);
        replacingEntity = false;

        if (fpvStaticTicks >= 0) {
            tickHeldPicture(minecraft);
            return;
        }

        if (DroneSignalLoss.isActive()) {
            tickSignalLoss(minecraft);
            return;
        }

        DroneEntity current = drone;
        if (current == null) {
            return;
        }
        if (minecraft.level == null || minecraft.player == null || current.level() != minecraft.level) {
            stop();
            return;
        }
        if (current.isRemoved()) {
            if (++missingTicks > MISSING_GRACE_TICKS) {
                DroneMod.LOGGER.warn(
                        "Long-range drone {} was gone for {} ticks with no link update — ending the flight",
                        current.getId(), missingTicks);
                sendStopRequest();
                stop();
            }
            return;
        }
        missingTicks = 0;
        if (!minecraft.player.isAlive()) {
            requestStop();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }

        flightTicks++;

        boolean armed = current.isArmed();
        if (armed != wasArmed) {
            if (armed) {
                armedFlashTicks = ARMED_FLASH_TICKS;
                FpvBattery.armedNow(current);
            } else {
                FpvBattery.disarm(current);
            }
            wasArmed = armed;
        }
        if (armedFlashTicks > 0) {
            armedFlashTicks--;
        }

        if (current.osdStyle().isFlightController()) {
            if (armed) {
                FpvBattery.tick(current);
            } else {
                FpvBattery.rest(current);
            }
        }

        boolean sticks = onSticks();
        if (!sticks) {
            float wrapped = Mth.wrapDegrees(current.getYRot());
            current.yRotO += wrapped - current.getYRot();
            current.setYRot(wrapped);
        }

        byte keys = DroneInputPayload.pack(
                minecraft.options.keyUp.isDown(),
                minecraft.options.keyDown.isDown(),
                minecraft.options.keyLeft.isDown(),
                minecraft.options.keyRight.isDown(),
                minecraft.options.keyJump.isDown(),
                minecraft.options.keyShift.isDown());

        float roll = 0.0F;
        float pitch = 0.0F;
        float yaw = 0.0F;
        float throttle = 0.0F;
        if (sticks && RadioInput.active()) {
            RadioInput.latchTick();
            roll = RadioInput.roll();
            pitch = -RadioInput.pitch();
            yaw = RadioInput.yaw();
            throttle = RadioInput.throttle();
            clearSticks();
        } else if (sticks) {
            tickStickKeys(minecraft);
            if (stickSampleTime > 1.0E-6D) {
                roll = (float) Mth.clamp(stickRollIntegral / stickSampleTime, -1.0D, 1.0D);
                pitch = (float) Mth.clamp(stickPitchIntegral / stickSampleTime, -1.0D, 1.0D);
            }
            stickRollIntegral = 0.0D;
            stickPitchIntegral = 0.0D;
            stickSampleTime = 0.0D;
            yaw = (float) stickYaw;
            throttle = (float) stickThrottle;
        }
        sendAndFly(current, keys, sticks, roll, pitch, yaw, throttle);

        tickPredictedImpact(current);

        if (RadioInput.active()) {
            tickRadioSwitches(current);
        }
        if (armRefusedTicks > 0) {
            armRefusedTicks--;
        }

        tickGrenadeDrop(minecraft, current);

        pinPilot(minecraft.player);
    }

    /**
     * One numbered input: stamped with the arming switch, sent to the server, and flown on this client's own
     * copy in the same breath.
     */
    private static void sendAndFly(DroneEntity current, byte keys, boolean sticks,
                                   float roll, float pitch, float yaw, float throttle) {
        int stamped = keys;
        if (armRequested) {
            stamped |= DroneInputPayload.ARM_SET | (armWanted ? DroneInputPayload.ARM_ON : 0);
        }
        PilotInput input = new PilotInput(++inputSeq, (byte) stamped,
                sticks ? Float.NaN : current.getYRot(), sticks ? Float.NaN : current.getXRot(),
                roll, pitch, yaw, throttle);
        PacketDistributor.sendToServer(new DroneInputPayload(current.getId(), input));
        if (current.locallyControlled && !current.isWrecked()) {
            PREDICTION.step(current, input);
        }
    }

    /**
     * Freezes the picture on the frame the pilot's copy struck something in, ahead of the server's crash
     * report — and lets it go again if that report never comes.
     */
    private static void tickPredictedImpact(DroneEntity current) {
        if (current.osdStyle() == DroneEntity.Osd.CAMERA) {
            if (current.consumePredictedImpact()) {
                current.settleInterpolation();
                DroneSignalLoss.begin(DroneSignalLoss.Cause.CRASH);
                clearGrenadeDrop();
                Minecraft minecraft = Minecraft.getInstance();
                DroneFeedFreeze.arm(minecraft.player != null
                        ? DroneTelemetry.read(minecraft, current, minecraft.player) : null);
                cameraImpactTicks = 0;
                reportPredictedRam(current);
            }
            return;
        }
        if (impactPredictedTicks < 0) {
            if (current.consumePredictedImpact()) {
                current.settleInterpolation();
                BetaflightOsd.captureSignalLoss(current);
                DroneFeedFreeze.armHold();
                impactPredictedTicks = 0;
                reportPredictedRam(current);
            }
            return;
        }
        current.consumePredictedImpact();
        if (++impactPredictedTicks > IMPACT_CONFIRM_TICKS) {
            impactPredictedTicks = -1;
            DroneFeedFreeze.release();
        }
    }

    /**
     * Struck a target, not a wall: the server's copy of it is elsewhere by now, so it is told what the
     * picture froze on rather than left to find it again.
     */
    private static void reportPredictedRam(DroneEntity current) {
        if (current.predictedRamId() >= 0) {
            PacketDistributor.sendToServer(new dev.krutlexik.skyfront.drone.network
                    .DroneRamPayload(current.getId(), current.predictedRamId()));
        }
    }

    /** The frozen FPV picture, counting itself out. */
    private static void tickHeldPicture(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || !minecraft.player.isAlive()) {
            endHeldPicture();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        pinPilot(minecraft.player);
        if (DroneFeedFreeze.isFrameReady() && minecraft.getCameraEntity() != minecraft.player) {
            minecraft.setCameraEntity(minecraft.player);
        }
        letGoOfTheCopy();
        if (++fpvStaticTicks >= FPV_HOLD_DURATION_TICKS) {
            endHeldPicture();
        }
    }

    /** Holds the dead feed on screen. */
    private static void tickSignalLoss(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || !minecraft.player.isAlive()) {
            endSignalLoss();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }

        if (cameraImpactTicks >= 0 && ++cameraImpactTicks > IMPACT_CONFIRM_TICKS) {
            cameraImpactTicks = -1;
            DroneSignalLoss.clear();
            DroneFeedFreeze.release();
            DroneEntity current = drone;
            if (current != null && !current.isRemoved()) {
                minecraft.setCameraEntity(current);
                PREDICTION.forceResync();
            }
            return;
        }

        DroneEntity current = drone;
        if (cameraImpactTicks >= 0 && current != null && current.locallyControlled && !current.isRemoved()) {
            sendAndFly(current, (byte) 0, false, 0.0F, 0.0F, 0.0F, 0.0F);
            current.consumePredictedImpact();
        }

        DroneSignalLoss.tick();
        pinPilot(minecraft.player);

        if (DroneFeedFreeze.isFrameReady() && minecraft.getCameraEntity() != minecraft.player) {
            minecraft.setCameraEntity(minecraft.player);
        }
        letGoOfTheCopy();
    }

    /**
     * Once the dying feed has its last frame, the copy is no longer this client's to fly: from here the
     * server drives it again, and its updates are let through rather than discarded as echoes of a
     * prediction.
     */
    private static void letGoOfTheCopy() {
        DroneEntity current = drone;
        if (current != null && current.locallyControlled && DroneFeedFreeze.isFrameReady()
                && cameraImpactTicks < 0) {
            current.locallyControlled = false;
        }
    }

    /**
     * Kills the feed and starts whichever dying sequence this airframe has, keeping the camera on the drone
     * so the last frame can still be grabbed on the next render.
     */
    public static void beginSignalLoss(DroneSignalLoss.Cause cause) {
        DroneEntity current = drone;
        if (current == null) {
            stop();
            return;
        }
        clearGrenadeDrop();
        if (fpvStaticTicks >= 0) {
            return;
        }
        if (current.osdStyle() != DroneEntity.Osd.CAMERA) {
            if (impactPredictedTicks >= 0) {
                impactPredictedTicks = -1;
            } else {
                BetaflightOsd.captureSignalLoss(current);
                DroneFeedFreeze.armHold();
                DroneFeedFreeze.useFrameBefore();
            }
            fpvStaticTicks = 0;
            applyDeferredRemoval();
            return;
        }
        if (cameraImpactTicks >= 0 && DroneSignalLoss.isActive()) {
            cameraImpactTicks = -1;
        } else if (!DroneSignalLoss.begin(cause)) {
            return;
        } else {
            Minecraft minecraft = Minecraft.getInstance();
            DroneFeedFreeze.arm(minecraft.player != null
                    ? DroneTelemetry.read(minecraft, current, minecraft.player)
                    : null);
            DroneFeedFreeze.useFrameBefore();
        }

        applyDeferredRemoval();
    }

    /** The pilot has put the controller down: drop the frozen feed and give them their body back. */
    private static void endSignalLoss() {
        if (cameraImpactTicks >= 0) {
            sendStopRequest();
        }
        DroneSignalLoss.clear();
        DroneFeedFreeze.release();
        stop();
    }

    private static int dropCharge;
    private static int dropFlashTicks;
    private static final int DROP_CHARGE_TICKS = 20;
    private static final int DROP_FLASH_TICKS = 70;
    private static final int DROP_FLASH_IN_TICKS = 4;
    private static final int DROP_FLASH_OUT_TICKS = 12;

    /** Whether the left mouse button is down, as the window itself reported it (see {@code MouseHandlerMixin}). */
    private static boolean releaseButtonDown;

    /** Called by the mouse hook on every left-button press and release. */
    public static void setReleaseButton(boolean down) {
        releaseButtonDown = down;
    }

    /**
     * Holding the left button while a grenade is slung fills a one-second timer; when it tops out the grenade
     * is released (the server does the spawn) and a brief confirmation image flashes up.
     */
    private static void tickGrenadeDrop(Minecraft minecraft, DroneEntity current) {
        if (dropFlashTicks > 0) {
            dropFlashTicks--;
        }
        boolean held = minecraft.screen == null
                && (releaseButtonDown || minecraft.options.keyAttack.isDown());
        if (held && GrenadeAttachments.isDroppable(current.getAttachment())) {
            if (++dropCharge >= DROP_CHARGE_TICKS) {
                PacketDistributor.sendToServer(new DroneDropPayload(current.getId(), inputSeq));
                dropCharge = 0;
                dropFlashTicks = DROP_FLASH_TICKS;
            }
        } else {
            dropCharge = 0;
        }
    }

    /**
     * The feed is dying: a release half-charged, or its confirmation still up, belongs to a picture that is
     * no longer live.
     */
    private static void clearGrenadeDrop() {
        dropCharge = 0;
        dropFlashTicks = 0;
    }

    /** How far through the one-second release the slung grenade is, 0..1. */
    public static float dropProgress() {
        return dropCharge > 0 ? Math.min(1.0F, (float) dropCharge / DROP_CHARGE_TICKS) : 0.0F;
    }

    /** The release confirmation, faded in and back out. */
    public static float dropFlashAlpha() {
        if (dropFlashTicks <= 0) {
            return 0.0F;
        }
        int elapsed = DROP_FLASH_TICKS - dropFlashTicks;
        if (elapsed < DROP_FLASH_IN_TICKS) {
            return (float) elapsed / DROP_FLASH_IN_TICKS;
        }
        return dropFlashTicks < DROP_FLASH_OUT_TICKS
                ? (float) dropFlashTicks / DROP_FLASH_OUT_TICKS
                : 1.0F;
    }

    /** The pilot's body, held exactly where the flight began. */
    private static void pinPilot(LocalPlayer player) {
        player.setJumping(false);
        player.input.up = false;
        player.input.down = false;
        player.input.left = false;
        player.input.right = false;
        player.input.jumping = false;
        player.input.shiftKeyDown = false;
        player.input.forwardImpulse = 0.0F;
        player.input.leftImpulse = 0.0F;
        player.setDeltaMovement(Vec3.ZERO);
        if (pilotAnchor != null) {
            player.setPos(pilotAnchor.x, pilotAnchor.y, pilotAnchor.z);
            player.fallDistance = 0.0F;
        }
    }

    private DroneControl() {
    }
}
