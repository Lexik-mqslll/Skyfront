package dev.krutlexik.skyfront.drone;

import dev.krutlexik.skyfront.config.SkyfrontConfig;
import dev.krutlexik.skyfront.drone.client.DroneSignalLoss;
import dev.krutlexik.skyfront.drone.client.ThermalVision;
import dev.krutlexik.skyfront.drone.entity.DroneEntity;
import dev.krutlexik.skyfront.drone.radio.RadioProfile;
import dev.krutlexik.skyfront.drone.entity.InterceptorDroneEntity;
import dev.krutlexik.skyfront.drone.entity.ThermalDroneEntity;
import dev.krutlexik.skyfront.drone.registry.DroneEntities;
import dev.krutlexik.skyfront.drone.registry.DroneItems;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Flight-model checks. */
@GameTestHolder(DroneMod.MODID)
@PrefixGameTestTemplate(false)
public class DroneGameTests {
    /** Dead centre of the 48x10x48 test arena, with room in every direction for the drone to drift. */
    private static DroneEntity hovering(GameTestHelper helper, float yaw) {
        DroneEntity drone = helper.spawn(DroneEntities.DRONE.get(), 24, 3, 24);
        drone.setYRot(yaw);
        drone.setXRot(0.0F);
        drone.setDeltaMovement(0.0D, 0.0D, 0.0D);
        return drone;
    }

    private static void hold(DroneEntity drone, boolean forward, boolean backward, boolean left,
                            boolean right, boolean up, boolean down, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            drone.applyControlInput(forward, backward, left, right, up, down);
        }
    }

    private static void hold(DroneEntity drone, boolean forward, boolean backward, boolean left,
                            boolean right, boolean up, boolean down) {
        hold(drone, forward, backward, left, right, up, down, 15);
    }

    /** Facing south (yaw 0), forward is +Z. */
    @GameTest(template = "empty9")
    public static void wFliesForwardAndSFliesBack(GameTestHelper helper) {
        DroneEntity drone = hovering(helper, 0.0F);
        double start = drone.getZ();

        hold(drone, true, false, false, false, false, false);
        double forward = drone.getZ();
        helper.assertTrue(forward > start + 0.2D, "W should fly forward (+Z facing south), Z went " + start + " -> " + forward);

        drone.setDeltaMovement(0.0D, 0.0D, 0.0D);
        hold(drone, false, true, false, false, false, false);
        helper.assertTrue(drone.getZ() < forward - 0.2D, "S should fly backward");

        helper.succeed();
    }

    /** Facing south, the drone's left is east (+X) — vanilla's strafe convention. */
    @GameTest(template = "empty9")
    public static void aStrafesLeftAndDStrafesRight(GameTestHelper helper) {
        DroneEntity drone = hovering(helper, 0.0F);
        double start = drone.getX();

        hold(drone, false, false, true, false, false, false);
        double left = drone.getX();
        helper.assertTrue(left > start + 0.2D, "A should strafe left (+X facing south), X went " + start + " -> " + left);

        drone.setDeltaMovement(0.0D, 0.0D, 0.0D);
        hold(drone, false, false, false, true, false, false);
        helper.assertTrue(drone.getX() < left - 0.2D, "D should strafe right");

        helper.succeed();
    }

    @GameTest(template = "empty9")
    public static void spaceClimbsAndShiftDrops(GameTestHelper helper) {
        DroneEntity drone = hovering(helper, 0.0F);
        double start = drone.getY();

        hold(drone, false, false, false, false, true, false);
        double climbed = drone.getY();
        helper.assertTrue(climbed > start + 0.2D, "space should climb, Y went " + start + " -> " + climbed);

        drone.setDeltaMovement(0.0D, 0.0D, 0.0D);
        hold(drone, false, false, false, false, false, true);
        helper.assertTrue(drone.getY() < climbed - 0.2D, "shift should descend");

        helper.succeed();
    }

    /** The flight direction rides on the yaw, and the mouse aims the drone within the gimbal limits. */
    @GameTest(template = "empty9")
    public static void aimFollowsMouseAndSteersFlight(GameTestHelper helper) {
        DroneEntity drone = hovering(helper, 90.0F);
        double startX = drone.getX();

        hold(drone, true, false, false, false, false, false);
        helper.assertTrue(drone.getX() < startX - 0.2D,
                "after yawing 90 degrees, W should fly toward -X, X went " + startX + " -> " + drone.getX());

        drone.turnGimbal(45.0D, 0.0D);
        helper.assertTrue(Math.abs(drone.getYRot() - 135.0F) < 0.01F,
                "mouse should yaw the drone, expected 135 but got " + drone.getYRot());

        for (int i = 0; i < 20; i++) {
            drone.turnGimbal(0.0D, 20.0D);
        }
        helper.assertTrue(Math.abs(drone.getXRot() - DroneEntity.MAX_PITCH) < 0.01F,
                "pitch should clamp at " + DroneEntity.MAX_PITCH + " but was " + drone.getXRot());
        for (int i = 0; i < 20; i++) {
            drone.turnGimbal(0.0D, -20.0D);
        }
        helper.assertTrue(Math.abs(drone.getXRot() - DroneEntity.MIN_PITCH) < 0.01F,
                "pitch should clamp at " + DroneEntity.MIN_PITCH + " but was " + drone.getXRot());

        helper.succeed();
    }

    /** Pitching the camera down must not turn the drone into a dive: flight stays level. */
    @GameTest(template = "empty9")
    public static void pitchDoesNotTiltTheFlightPath(GameTestHelper helper) {
        DroneEntity drone = hovering(helper, 0.0F);
        drone.setXRot(DroneEntity.MAX_PITCH);
        double startY = drone.getY();

        hold(drone, true, false, false, false, false, false);
        helper.assertTrue(Math.abs(drone.getY() - startY) < 0.05D,
                "flying forward with the camera aimed down should hold altitude, Y went " + startY + " -> " + drone.getY());

        helper.succeed();
    }

    /**
     * The battery drains under control, clamps at zero (dropping the link), an empty drone sinks to the
     * ground on its own, and it never charges back up — the drone is a one-shot.
     */
    @GameTest(template = "empty9")
    public static void batteryDrainsAndAutolands(GameTestHelper helper) {
        helper.setBlock(24, 2, 24, Blocks.STONE);
        DroneEntity drone = hovering(helper, 0.0F);

        drone.setCharge(0.5F);
        drone.tickBattery(true);
        helper.assertTrue(drone.getCharge() < 0.5F, "piloted flight should drain the battery");

        drone.setCharge(1.0E-6F);
        drone.tickBattery(true);
        helper.assertTrue(drone.getCharge() == 0.0F, "battery should clamp at zero");

        drone.tickBattery(false);
        helper.assertTrue(drone.getCharge() == 0.0F, "an idle drone must not gain charge");

        helper.succeedWhen(() -> {
            helper.assertTrue(drone.isGrounded(), "empty drone should autoland");
            drone.tickBattery(false);
            helper.assertTrue(drone.getCharge() == 0.0F, "a grounded drone must not recharge");
        });
    }

    /** The battery is spent by being airborne, not by being flown. */
    @GameTest(template = "empty9", timeoutTicks = 100)
    public static void hoveringUnpilotedStillDrainsTheBattery(GameTestHelper helper) {
        DroneEntity drone = helper.spawn(DroneEntities.DRONE.get(), 24, 6, 24);
        drone.setDeltaMovement(0.0D, 0.0D, 0.0D);
        drone.setCharge(1.0F);

        helper.assertFalse(drone.isControlled(), "precondition: nobody is flying it");
        helper.assertFalse(drone.isGrounded(), "precondition: it is airborne");

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(drone.getCharge() < 1.0F,
                    "an airborne drone should drain even with no pilot, still at " + drone.getCharge());
            helper.succeed();
        });
    }

    /**
     * Flying the drone straight down onto the ground sets it down rather than smashing it: the sink rate
     * under power is capped below the crash speed, so a plain vertical landing can never reach it however
     * long descent is held.
     */
    @GameTest(template = "empty9")
    public static void descentIsCappedBelowCrashSpeed(GameTestHelper helper) {
        DroneEntity drone = helper.spawn(DroneEntities.DRONE.get(), 24, 8, 24);
        drone.setDeltaMovement(0.0D, 0.0D, 0.0D);

        double worst = 0.0D;
        for (int tick = 0; tick < 40; tick++) {
            drone.applyControlInput(false, false, false, false, false, true);
            worst = Math.min(worst, drone.getDeltaMovement().y);
        }

        double worstKmh = -worst * 72.0D;
        helper.assertTrue(worstKmh <= 43.0D + 0.5D,
                "a straight-down descent should stay at or under the 43 km/h cap, peaked at " + worstKmh);
        helper.assertTrue(worstKmh > 20.0D,
                "precondition: it should actually be descending at a fair clip, only reached " + worstKmh);

        helper.succeed();
    }

    /**
     * {@link DroneEntity#isGrounded} must recognise a drone the instant it's placed at rest — not only once
     * it has actually moved — since vanilla's own {@code onGround()} flag needs a downward movement attempt
     * to update and never gets one for a motionless, gravity-free drone.
     */
    @GameTest(template = "empty9")
    public static void groundedIsDetectedWithoutMoving(GameTestHelper helper) {
        helper.setBlock(24, 2, 24, Blocks.STONE);
        DroneEntity drone = helper.spawn(DroneEntities.DRONE.get(), new net.minecraft.world.phys.Vec3(24.5, 3.0, 24.5));
        drone.setDeltaMovement(0.0D, 0.0D, 0.0D);

        helper.assertTrue(drone.isGrounded(), "a drone resting on a block, never having moved, should read as grounded");

        Vec3 openAir = helper.absoluteVec(new Vec3(24.5, 8.0, 24.5));
        drone.teleportTo(openAir.x, openAir.y, openAir.z);
        helper.assertTrue(!drone.isGrounded(), "a drone hanging in open air should not read as grounded");

        helper.succeed();
    }

    /** A grounded drone is not a car: horizontal input must not taxi it across the floor. */
    @GameTest(template = "empty9")
    public static void groundedDroneCannotTaxiButCanTakeOff(GameTestHelper helper) {
        helper.setBlock(24, 2, 24, Blocks.STONE);
        DroneEntity drone = helper.spawn(DroneEntities.DRONE.get(), new net.minecraft.world.phys.Vec3(24.5, 3.0, 24.5));
        drone.setYRot(0.0F);
        drone.setXRot(0.0F);
        drone.setDeltaMovement(0.0D, 0.0D, 0.0D);
        helper.assertTrue(drone.isGrounded(), "precondition: the drone should be resting on the pad");

        double startX = drone.getX();
        double startZ = drone.getZ();
        for (int i = 0; i < 20; i++) {
            drone.applyControlInput(true, false, false, true, false, false);
        }
        helper.assertTrue(Math.abs(drone.getX() - startX) < 0.05D && Math.abs(drone.getZ() - startZ) < 0.05D,
                "a grounded drone must not move horizontally, but it slid to "
                        + drone.getX() + ", " + drone.getZ());

        double startY = drone.getY();
        for (int i = 0; i < 20; i++) {
            drone.applyControlInput(false, false, false, false, true, false);
        }
        helper.assertTrue(drone.getY() > startY + 0.3D,
                "space should lift the grounded drone off, but Y went " + startY + " -> " + drone.getY());

        helper.succeed();
    }

    /** Released controls: the drone must brake to a hover instead of drifting off. */
    @GameTest(template = "empty9")
    public static void hoversWhenControlsReleased(GameTestHelper helper) {
        DroneEntity drone = hovering(helper, 0.0F);
        hold(drone, true, false, false, true, true, false);

        hold(drone, false, false, false, false, false, false, 100);
        helper.assertTrue(drone.getDeltaMovement().lengthSqr() < 1.0E-4D,
                "drone should settle into a hover, still moving at " + drone.getDeltaMovement());

        helper.succeed();
    }

    /**
     * The camera interpolates between {@code yRotO} and {@code yRot} every frame, so those two must never
     * disagree — least of all when the yaw sweeps past due south and gets wrapped back into range, which used
     * to leave them a full revolution apart and jerk the view backwards.
     */
    @GameTest(template = "empty9")
    public static void yawStaysContinuousAcrossTheWrapPoint(GameTestHelper helper) {
        DroneEntity drone = hovering(helper, 170.0F);

        for (int frame = 0; frame < 12; frame++) {
            drone.turnGimbal(5.0D, 0.0D);
            helper.assertTrue(Math.abs(drone.getYRot() - drone.yRotO) < 0.01F,
                    "yaw and its interpolation partner drifted apart: yRot=" + drone.getYRot()
                            + " yRotO=" + drone.yRotO);
        }

        drone.applyControlInput(false, false, false, false, false, false);
        helper.assertTrue(Math.abs(drone.getYRot()) <= 180.0F,
                "yaw should be wrapped back into range, got " + drone.getYRot());
        helper.assertTrue(Math.abs(drone.getYRot() - drone.yRotO) < 0.01F,
                "wrapping must not tear yaw away from yRotO: yRot=" + drone.getYRot()
                        + " yRotO=" + drone.yRotO);

        helper.succeed();
    }

    /**
     * The bug being fixed here: flying forward at speed and then snapping the stick straight to backward used
     * to blindly boost the *whole* velocity vector (since some key was still held), amplifying the leftover
     * forward momentum for many ticks before it could turn around.
     */
    @GameTest(template = "empty9")
    public static void reversingDoesNotOvershootMomentum(GameTestHelper helper) {
        DroneEntity drone = hovering(helper, 0.0F);
        hold(drone, true, false, false, false, false, false, 40);
        double forwardSpeed = drone.getDeltaMovement().z;
        helper.assertTrue(forwardSpeed > 0.3D, "expected meaningful forward speed, got " + forwardSpeed);

        double worstOvershoot = 0.0D;
        for (int tick = 0; tick < 40; tick++) {
            drone.applyControlInput(false, true, false, false, false, false);
            worstOvershoot = Math.max(worstOvershoot, drone.getDeltaMovement().z);
        }
        helper.assertTrue(worstOvershoot < forwardSpeed + 0.05D,
                "reversing should not amplify the existing forward momentum: had " + forwardSpeed
                        + " forward, saw it climb to " + worstOvershoot + " before turning around");

        helper.succeed();
    }

    /**
     * The impact speed the video link drops out at is read off {@code lastCollisionSpeed}, which has to be
     * sampled across the move: a collision is precisely what zeroes the velocity, so reading it afterwards
     * would report every crash as a gentle one. 45 km/h is the threshold, and the OSD's own 72x factor
     * converts it.
     */
    @GameTest(template = "empty9")
    public static void hardImpactRecordsItsSpeed(GameTestHelper helper) {
        DroneEntity drone = hovering(helper, 0.0F);

        for (int y = 2; y <= 6; y++) {
            for (int x = 22; x <= 26; x++) {
                helper.setBlock(x, y, 34, Blocks.STONE);
            }
        }

        float impact = 0.0F;
        for (int tick = 0; tick < 60 && impact == 0.0F; tick++) {
            drone.applyControlInput(true, false, false, false, false, false);
            impact = drone.lastCollisionSpeed;
        }

        helper.assertTrue(impact > 0.0F, "flying into a wall should have recorded an impact speed");
        helper.assertTrue(impact * 72.0F >= 45.0F,
                "a full-throttle run into a wall should clear the 45 km/h signal-loss threshold, got "
                        + impact * 72.0F + " km/h");

        helper.succeed();
    }

    /** Coming down onto the ground hard is an impact like any other. */
    @GameTest(template = "empty9")
    public static void fastLandingCountsAsAnImpact(GameTestHelper helper) {
        for (int x = 20; x <= 28; x++) {
            for (int z = 20; z <= 44; z++) {
                helper.setBlock(x, 2, z, Blocks.STONE);
            }
        }

        DroneEntity drone = helper.spawn(DroneEntities.DRONE.get(), 24, 6, 22);
        drone.setYRot(0.0F);
        drone.setXRot(0.0F);
        drone.setDeltaMovement(0.0D, 0.0D, 0.0D);
        hold(drone, true, false, false, false, false, false, 30);

        Vec3 approach = drone.getDeltaMovement();
        helper.assertTrue(approach.horizontalDistance() * 72.0D >= 45.0D,
                "precondition: should be travelling fast, was " + approach.horizontalDistance() * 72.0D + " km/h");
        drone.setPos(drone.getX(), helper.absolutePos(new BlockPos(24, 3, 24)).getY() + 0.03D, drone.getZ());
        drone.setDeltaMovement(approach);
        helper.assertTrue(drone.isGrounded(), "precondition: the drone should read as touching down");

        drone.applyControlInput(true, false, false, false, false, false);

        helper.assertTrue(drone.lastCollisionSpeed * 72.0F >= 45.0F,
                "touching down at speed should register as a hard landing, got "
                        + drone.lastCollisionSpeed * 72.0F + " km/h");

        helper.succeed();
    }

    /** Letting go of the controller does not call the impact off. */
    @GameTest(template = "empty9")
    public static void aDroneLetGoOfStillCrashes(GameTestHelper helper) {
        for (int y = 2; y <= 6; y++) {
            for (int x = 22; x <= 26; x++) {
                helper.setBlock(x, y, 34, Blocks.STONE);
            }
        }

        DroneEntity drone = hovering(helper, 0.0F);
        drone.setPos(drone.getX(), drone.getY(), helper.absolutePos(new BlockPos(24, 3, 32)).getZ() + 0.5D);
        drone.setDeltaMovement(0.0D, 0.0D, 0.97D);

        float impact = 0.0F;
        for (int tick = 0; tick < 10 && impact == 0.0F; tick++) {
            drone.applyControlInput(false, false, false, false, false, false);
            impact = drone.lastCollisionSpeed;
        }

        helper.assertTrue(impact * 72.0F >= 45.0F,
                "a drone coasting into a wall after the pilot let go should still register a crash, got "
                        + impact * 72.0F + " km/h");

        helper.succeed();
    }

    /**
     * A drained battery has to bring the drone down from wherever it is, not just from a hover, and it has to
     * do it gently: a drone that runs flat mid-cruise carries its momentum and settles into the descent
     * rather than stopping dead in the air and dropping straight down.
     */
    @GameTest(template = "empty9", timeoutTicks = 400)
    public static void emptyBatteryAutolands(GameTestHelper helper) {
        for (int x = 20; x <= 28; x++) {
            for (int z = 20; z <= 28; z++) {
                helper.setBlock(x, 2, z, Blocks.STONE);
            }
        }
        DroneEntity drone = helper.spawn(DroneEntities.DRONE.get(), 24, 9, 22);
        drone.setDeltaMovement(0.6D, 0.0D, 0.0D);
        drone.setCharge(0.0F);

        double startY = drone.getY();
        double startX = drone.getX();

        helper.onEachTick(() -> helper.assertTrue(drone.getDeltaMovement().y >= -0.11D,
                "the autolanding should stay gentle, was descending at " + drone.getDeltaMovement().y));

        helper.succeedWhen(() -> {
            helper.assertTrue(drone.isGrounded(),
                    "an empty drone should autoland, still at " + drone.getY() + " from " + startY);
            helper.assertTrue(drone.getX() > startX + 0.5D,
                    "and should have carried its momentum out rather than dropping on the spot");
        });
    }

    /** Open air must never look like an impact, or the feed would drop out mid-flight. */
    @GameTest(template = "empty9")
    public static void clearFlightRecordsNoImpact(GameTestHelper helper) {
        DroneEntity drone = hovering(helper, 0.0F);

        for (int tick = 0; tick < 25; tick++) {
            drone.applyControlInput(true, false, false, false, false, false);
            helper.assertTrue(drone.lastCollisionSpeed == 0.0F,
                    "clear air reported an impact at " + drone.lastCollisionSpeed + " blocks/tick"
                            + " @" + drone.position() + " on tick " + tick);
        }

        helper.succeed();
    }

    /**
     * The signal-loss sequence's timings, which are the visible spec: the picture holds its colour for 0.6s,
     * drains to grey with the readings dashed out, and 0.4s after that owns up to it with the "No signal"
     * badge.
     */
    @GameTest(template = "empty9")
    public static void signalLossDrainsThenAdmitsItself(GameTestHelper helper) {
        DroneSignalLoss.clear();
        try {
            helper.assertTrue(DroneSignalLoss.begin(DroneSignalLoss.Cause.CRASH),
                    "a fresh drop should start the sequence");
            helper.assertFalse(DroneSignalLoss.begin(DroneSignalLoss.Cause.OUT_OF_RANGE),
                    "a second drop must not restart a sequence already under way");
            helper.assertTrue(DroneSignalLoss.isActive(), "the sequence should be running once begun");
            helper.assertFalse(DroneSignalLoss.isBlanked(), "the frame keeps its colour at the moment of impact");
            helper.assertFalse(DroneSignalLoss.showNoSignal(), "no badge while the picture is still colour");

            step(11);
            helper.assertFalse(DroneSignalLoss.isBlanked(), "colour should hold for the full 0.6s");

            step(1);
            helper.assertTrue(DroneSignalLoss.isBlanked(), "the frame should drain to grey at 0.6s");
            helper.assertFalse(DroneSignalLoss.showNoSignal(), "the badge waits a further 0.4s");

            step(7);
            helper.assertFalse(DroneSignalLoss.showNoSignal(), "the badge waits a further 0.4s");

            step(1);
            helper.assertTrue(DroneSignalLoss.showNoSignal(), "the badge should be up 0.4s after the grey");

            helper.assertTrue(DroneSignalLoss.cause() == DroneSignalLoss.Cause.CRASH,
                    "the cause decides how the link is torn down, so it must survive the sequence");

            step(20 * 60);
            helper.assertTrue(DroneSignalLoss.isActive(),
                    "the frozen feed should still be up a minute later, waiting on the pilot");
            helper.assertTrue(DroneSignalLoss.showNoSignal(), "and still showing the badge");
        } finally {
            DroneSignalLoss.clear();
        }

        helper.succeed();
    }

    private static void step(int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            DroneSignalLoss.tick();
        }
    }

    /**
     * The drone soaks damage up to its hit points and only then breaks — a fragile thing, but not one that a
     * single graze finishes.
     */
    @GameTest(template = "empty9")
    public static void droneBreaksOnlyWhenHitPointsRunOut(GameTestHelper helper) {
        DroneEntity drone = hovering(helper, 0.0F);
        float hp = drone.getHealth();
        helper.assertTrue(hp >= 2.0F, "precondition: the drone should have a few hit points, had " + hp);

        var source = drone.damageSources().generic();

        drone.hurt(source, 1.0F);
        helper.assertFalse(drone.isWrecked(), "a single point of damage should not wreck a " + hp + "-HP drone");

        drone.hurt(source, hp);
        helper.assertTrue(drone.isWrecked(), "running the hit points out should wreck the drone");
        helper.assertFalse(drone.isControlled(), "and the link must be gone with it");

        helper.succeed();
    }

    /**
     * A drone shot out of the sky does not simply vanish: it loses its rotors, tumbles down under its own
     * momentum, and is only destroyed once it reaches the ground — the same two-stage end a downed Shahed
     * comes to.
     */
    @GameTest(template = "empty9", timeoutTicks = 300)
    public static void droneShotDownFallsAndBreaksOnImpact(GameTestHelper helper) {
        for (int x = 20; x <= 28; x++) {
            for (int z = 20; z <= 28; z++) {
                helper.setBlock(x, 2, z, Blocks.STONE);
            }
        }
        DroneEntity drone = helper.spawn(DroneEntities.DRONE.get(), 24, 9, 24);
        drone.setDeltaMovement(0.0D, 0.0D, 0.0D);

        drone.hurt(drone.damageSources().generic(), drone.getHealth());
        helper.assertTrue(drone.isWrecked(), "precondition: the drone should have been wrecked");
        helper.assertFalse(drone.isRemoved(), "a wreck must stay in the world long enough to fall");

        double startY = drone.getY();
        helper.succeedWhen(() -> {
            helper.assertTrue(drone.getY() < startY - 1.0D,
                    "the wreck should be dropping, still at " + drone.getY() + " from " + startY);
            helper.assertTrue(drone.isRemoved(), "and should be destroyed once it reaches the ground");
        });
    }

    /**
     * The slung grenade is kept as synced, saved entity data, so it rides along on the drone and survives the
     * world unloading.
     */
    @GameTest(template = "empty9")
    public static void attachmentSurvivesSaveAndLoad(GameTestHelper helper) {
        DroneEntity drone = hovering(helper, 0.0F);
        helper.assertFalse(drone.hasAttachment(), "a fresh drone carries nothing");

        drone.setAttachment(new ItemStack(net.minecraft.world.item.Items.STONE));
        helper.assertTrue(drone.hasAttachment(), "attaching should register");

        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        drone.saveWithoutId(tag);
        DroneEntity reloaded = DroneEntities.DRONE.get().create(helper.getLevel());
        helper.assertTrue(reloaded != null, "should have created a drone to reload into");
        reloaded.load(tag);

        helper.assertTrue(reloaded.hasAttachment(), "the attachment should survive save and load");
        helper.assertTrue(reloaded.getAttachment().getItem() == net.minecraft.world.item.Items.STONE,
                "and it should be the same item that was slung");
        reloaded.discard();

        helper.succeed();
    }

    /** A grenade must be released clear of the drone's own hitbox. */
    @GameTest(template = "empty9")
    public static void grenadeIsReleasedClearOfTheAirframe(GameTestHelper helper) {
        DroneEntity drone = hovering(helper, 0.0F);
        Vec3 drop = drone.grenadeDropPoint();

        double grenadeSize = 0.3D;
        net.minecraft.world.phys.AABB grenadeBox = new net.minecraft.world.phys.AABB(
                drop.x - grenadeSize / 2, drop.y, drop.z - grenadeSize / 2,
                drop.x + grenadeSize / 2, drop.y + grenadeSize, drop.z + grenadeSize / 2);

        helper.assertFalse(grenadeBox.intersects(drone.getBoundingBox()),
                "a grenade released at " + drop + " overlaps the drone's own hitbox "
                        + drone.getBoundingBox() + " — the drone would strike it immediately");

        helper.succeed();
    }

    /** A drone that touches down with speed on carries it out in a short skid. */
    @GameTest(template = "empty9")
    public static void landingSkidsOutItsMomentum(GameTestHelper helper) {
        for (int x = 20; x <= 30; x++) {
            for (int z = 20; z <= 30; z++) {
                helper.setBlock(x, 2, z, Blocks.STONE);
            }
        }
        DroneEntity drone = helper.spawn(DroneEntities.DRONE.get(), 24, 3, 24);
        drone.setYRot(0.0F);
        drone.setXRot(0.0F);
        helper.assertTrue(drone.isGrounded(), "precondition: the drone should be sitting on the pad");

        double startZ = drone.getZ();
        drone.setDeltaMovement(0.0D, 0.0D, 0.35D);
        drone.applyControlInput(false, false, false, false, false, false);
        helper.assertTrue(drone.getDeltaMovement().horizontalDistance() > 0.0D,
                "a landing drone should keep some of its run, not stop dead on contact");

        for (int tick = 0; tick < 60; tick++) {
            drone.applyControlInput(false, false, false, false, false, false);
        }
        helper.assertTrue(drone.getDeltaMovement().horizontalDistance() == 0.0D,
                "the skid should come to a stop, still moving at " + drone.getDeltaMovement());
        double slid = drone.getZ() - startZ;
        helper.assertTrue(slid > 0.05D && slid < 3.0D,
                "the drone should slide a short way and stop, slid " + slid + " blocks");

        helper.succeed();
    }

    /** The release itself, exercised end to end. */
    @GameTest(template = "empty9")
    public static void releasingAStoreSpawnsItOwnedByThePilot(GameTestHelper helper) {
        DroneEntity drone = hovering(helper, 0.0F);
        var pilot = helper.makeMockServerPlayerInLevel();
        Vec3 drop = drone.grenadeDropPoint();

        boolean released = dev.krutlexik.skyfront.drone.GrenadeAttachments.release(
                helper.getLevel(), pilot, new ItemStack(net.minecraft.world.item.Items.SNOWBALL),
                drop, Vec3.ZERO);
        helper.assertTrue(released, "the store should have been released");

        var spawned = helper.getLevel().getEntitiesOfClass(
                net.minecraft.world.entity.projectile.Snowball.class,
                new net.minecraft.world.phys.AABB(drop, drop).inflate(1.5D));
        helper.assertTrue(spawned.size() == 1,
                "exactly one store should have been spawned at the drop point, found " + spawned.size());

        var store = spawned.get(0);
        helper.assertTrue(store.getOwner() == pilot,
                "the released store must be owned by the pilot, so kills are credited to them");
        helper.assertTrue(store.getDeltaMovement().y < 0.0D,
                "it should be leaving downward, away from the drone");

        store.discard();
        helper.succeed();
    }

    /** A wreck carries its ordnance to the ground and lets go of it there. */
    @GameTest(template = "empty9", timeoutTicks = 300)
    public static void aWreckDoesNotLeaveItsOrdnanceBehind(GameTestHelper helper) {
        for (int x = 20; x <= 28; x++) {
            for (int z = 20; z <= 28; z++) {
                helper.setBlock(x, 2, z, Blocks.STONE);
            }
        }
        DroneEntity drone = helper.spawn(DroneEntities.DRONE.get(), 24, 9, 24);
        drone.setDeltaMovement(0.0D, 0.0D, 0.0D);
        drone.setAttachment(new ItemStack(net.minecraft.world.item.Items.STONE));

        drone.hurt(drone.damageSources().generic(), drone.getHealth());
        helper.assertTrue(drone.isWrecked(), "precondition: the drone should have been wrecked");
        helper.assertTrue(drone.hasAttachment(), "the store rides the wreck down rather than vanishing with the hit");

        helper.succeedWhen(() -> {
            helper.assertTrue(drone.isRemoved(), "the wreck should be destroyed on reaching the ground");
            helper.assertFalse(drone.hasAttachment(), "and its store should have gone with it");
        });
    }

    /** The four stores split two ways. */
    @GameTest(template = "empty9")
    public static void onlyFusedStoresCanBeLetGoOf(GameTestHelper helper) {
        record Store(String id, boolean droppable, boolean explodes) {
        }
        var stores = new Store[]{
                new Store("superbwarfare:rgo_grenade", true, true),
                new Store("superbwarfare:m18_smoke_grenade", true, false),
                new Store("superbwarfare:rpg_rocket_tbg", false, true),
                new Store("superbwarfare:c4_bomb", false, true),
        };

        for (Store store : stores) {
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .get(net.minecraft.resources.ResourceLocation.parse(store.id()));
            if (item == net.minecraft.world.item.Items.AIR) {
                continue;
            }
            ItemStack stack = new ItemStack(item);
            helper.assertTrue(dev.krutlexik.skyfront.drone.GrenadeAttachments.isAttachable(stack),
                    store.id() + " should hang under the drone");
            helper.assertTrue(
                    dev.krutlexik.skyfront.drone.GrenadeAttachments.isDroppable(stack) == store.droppable(),
                    store.id() + " droppability should be " + store.droppable());
            helper.assertTrue(
                    dev.krutlexik.skyfront.drone.GrenadeAttachments.isExplosive(stack) == store.explodes(),
                    store.id() + " explosiveness should be " + store.explodes());
        }

        helper.succeed();
    }

    /** An entity in the drone's path is something to break against, not something to fly through. */
    @GameTest(template = "empty9")
    public static void rammingAnEntityCountsAsAnImpact(GameTestHelper helper) {
        DroneEntity drone = hovering(helper, 0.0F);
        hold(drone, true, false, false, false, false, false, 30);

        var target = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.COW,
                new Vec3(24.5, 3.0, drone.getZ() - helper.absolutePos(BlockPos.ZERO).getZ() + 2.0));

        float impact = 0.0F;
        for (int tick = 0; tick < 20 && impact == 0.0F; tick++) {
            drone.applyControlInput(true, false, false, false, false, false);
            impact = drone.lastCollisionSpeed;
        }

        helper.assertTrue(impact > 0.0F, "flying into an entity at speed should register an impact");
        helper.assertTrue(drone.lastRammed == target,
                "and should name what was struck, so the ram can be made to hurt it");

        helper.succeed();
    }

    private static ThermalDroneEntity hoveringThermal(GameTestHelper helper, float yaw) {
        ThermalDroneEntity drone = helper.spawn(DroneEntities.THERMAL_DRONE.get(), 24, 5, 24);
        drone.setYRot(yaw);
        drone.setXRot(0.0F);
        drone.setDeltaMovement(0.0D, 0.0D, 0.0D);
        return drone;
    }

    /** Runs a drone up to its terminal speed and reports the fastest it got, in km/h. */
    private static double topSpeedKmh(GameTestHelper helper, DroneEntity drone, int ticks) {
        Vec3 centre = helper.absoluteVec(new Vec3(24.5, 5.0, 24.5));
        double peak = 0.0D;
        for (int tick = 0; tick < ticks; tick++) {
            drone.applyControlInput(true, false, false, false, false, false);
            peak = Math.max(peak, drone.getDeltaMovement().horizontalDistance());
            drone.teleportTo(centre.x, centre.y, centre.z);
        }
        return peak * 72.0D;
    }

    /** Each variant reads its flight time and control range from its own config section. */
    @GameTest(template = "empty9")
    public static void theThermalDroneFliesOnItsOwnFigures(GameTestHelper helper) {
        DroneEntity recon = hovering(helper, 0.0F);
        ThermalDroneEntity thermal = hoveringThermal(helper, 0.0F);

        helper.assertTrue(thermal.controlRange() == (double) (int) SkyfrontConfig.MAVIC_3T_CONTROL_RANGE.get(),
                "the thermal drone's range should come from thermalControlRange, was " + thermal.controlRange());
        helper.assertTrue(thermal.fullFlightTicks() == SkyfrontConfig.MAVIC_3T_FLIGHT_SECONDS.get() * 20,
                "its battery should come from thermalFlightSeconds, was " + thermal.fullFlightTicks() + " ticks");

        helper.assertTrue(recon.controlRange() == (double) (int) SkyfrontConfig.MAVIC_CONTROL_RANGE.get(),
                "and the recon drone must be left on its own droneControlRange, was " + recon.controlRange());
        helper.assertTrue(recon.fullFlightTicks() == SkyfrontConfig.MAVIC_FLIGHT_SECONDS.get() * 20,
                "likewise its battery, was " + recon.fullFlightTicks() + " ticks");

        helper.succeed();
    }

    /**
     * The thermal drone's configured top speed is a speed it actually flies at, not merely a ceiling: it is
     * given enough thrust to arrive there and then held to it.
     */
    @GameTest(template = "empty9", timeoutTicks = 200)
    public static void theThermalDroneIsHeldToItsConfiguredTopSpeed(GameTestHelper helper) {
        double cap = SkyfrontConfig.MAVIC_3T_TOP_SPEED.get();

        ThermalDroneEntity thermal = hoveringThermal(helper, 0.0F);
        double thermalPeak = topSpeedKmh(helper, thermal, 160);
        thermal.discard();

        helper.assertTrue(thermalPeak <= cap + 0.5D,
                "the thermal drone must not exceed " + cap + " km/h, reached " + thermalPeak);
        helper.assertTrue(thermalPeak >= cap - 1.0D,
                "and it should actually get there, only reached " + thermalPeak + " km/h");

        DroneEntity recon = hovering(helper, 0.0F);
        double reconPeak = topSpeedKmh(helper, recon, 160);
        helper.assertTrue(reconPeak < thermalPeak - 5.0D,
                "the recon drone's own speed must be left as it was, well under the thermal drone's — "
                        + reconPeak + " km/h against " + thermalPeak);

        helper.succeed();
    }

    /** The flight model itself is shared, not copied: the same controls do the same things. */
    @GameTest(template = "empty9")
    public static void theThermalDroneFliesLikeTheReconOne(GameTestHelper helper) {
        ThermalDroneEntity drone = hoveringThermal(helper, 0.0F);
        double startZ = drone.getZ();
        double startY = drone.getY();

        drone.setXRot(DroneEntity.MAX_PITCH);
        hold(drone, true, false, false, false, false, false);

        helper.assertTrue(drone.getZ() > startZ + 0.2D,
                "W should fly it forward (+Z facing south), Z went " + startZ + " -> " + drone.getZ());
        helper.assertTrue(Math.abs(drone.getY() - startY) < 0.05D,
                "and flight should stay level with the camera aimed down, Y went " + startY + " -> " + drone.getY());

        helper.succeed();
    }

    /** Packing one up gives a thermal drone back — a variant that handed out recon drones would be a duplicator. */
    @GameTest(template = "empty9")
    public static void theThermalDronePacksIntoItsOwnItem(GameTestHelper helper) {
        ThermalDroneEntity drone = hoveringThermal(helper, 0.0F);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);

        drone.interact(player, InteractionHand.MAIN_HAND);

        helper.assertTrue(drone.isRemoved(), "the drone should be gone from the world once packed up");
        helper.assertTrue(player.getInventory().contains(new ItemStack(DroneItems.THERMAL_DRONE.get())),
                "crouch-clicking a thermal drone must give a thermal drone back");
        helper.assertFalse(player.getInventory().contains(new ItemStack(DroneItems.DRONE.get())),
                "and certainly not a recon one");

        helper.succeed();
    }

    /** Only the thermal drone's camera has the switch; the recon drone's left click must not find one. */
    @GameTest(template = "empty9")
    public static void onlyTheThermalDroneHasAThermalCamera(GameTestHelper helper) {
        DroneEntity recon = hovering(helper, 0.0F);
        ThermalDroneEntity thermal = hoveringThermal(helper, 0.0F);
        ThermalVision.clear();

        ThermalVision.toggle(recon);
        helper.assertFalse(ThermalVision.isOn(), "a recon drone has no thermal mode to switch on");

        ThermalVision.toggle(thermal);
        helper.assertTrue(ThermalVision.isOn(), "the thermal drone's camera should switch to heat");
        ThermalVision.toggle(thermal);
        helper.assertFalse(ThermalVision.isOn(), "and switch back off again");

        ThermalVision.toggle(thermal);
        ThermalVision.clear();
        helper.assertFalse(ThermalVision.isOn(), "putting the controller down should leave the mode off");

        helper.succeed();
    }

    /** Packing up a loaded drone gives back what was on the pylon as well as the airframe. */
    @GameTest(template = "empty9")
    public static void packingUpALoadedDroneReturnsItsStoreToo(GameTestHelper helper) {
        DroneEntity drone = hovering(helper, 0.0F);
        drone.setAttachment(new ItemStack(net.minecraft.world.item.Items.STONE));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);

        drone.interact(player, InteractionHand.MAIN_HAND);

        helper.assertTrue(drone.isRemoved(), "the drone should be gone from the world once packed up");
        helper.assertTrue(player.getInventory().contains(new ItemStack(DroneItems.DRONE.get())),
                "the drone itself should be in the inventory");
        helper.assertTrue(player.getInventory().contains(new ItemStack(net.minecraft.world.item.Items.STONE)),
                "and so should the store that was slung under it, rather than being destroyed with the drone");
        helper.assertFalse(drone.hasAttachment(), "the pylon should have been emptied on the way");

        helper.succeed();
    }

    private static InterceptorDroneEntity interceptor(GameTestHelper helper, float yaw, float pitch) {
        InterceptorDroneEntity drone = helper.spawn(DroneEntities.INTERCEPTOR_DRONE.get(), 24, 5, 24);
        drone.setYRot(yaw);
        drone.setXRot(pitch);
        drone.forceRateMode(false);
        drone.setDeltaMovement(0.0D, 0.0D, 0.0D);
        drone.setArmed(true);
        return drone;
    }

    /**
     * The airframe is flown by leaning it, the way SuperbWarfare's own drone is: holding forward tilts the
     * nose down and that lean is what accelerates it, while the camera — which the mouse moves independently
     * — has no say in where it goes.
     */
    @GameTest(template = "empty9")
    public static void theInterceptorFliesOnLeanNotOnAim(GameTestHelper helper) {
        InterceptorDroneEntity level = interceptor(helper, 0.0F, 0.0F);
        double startZ = level.getZ();
        hold(level, true, false, false, false, false, false, 10);
        double flown = level.getZ() - startZ;
        helper.assertTrue(flown > 0.2D,
                "holding forward should lean the drone and fly it forward, Z moved " + flown);
        level.discard();

        InterceptorDroneEntity aimed = interceptor(helper, 0.0F, 60.0F);
        double aimedStartY = aimed.getY();
        double aimedStartZ = aimed.getZ();
        hold(aimed, true, false, false, false, false, false, 10);

        helper.assertTrue(Math.abs((aimed.getZ() - aimedStartZ) - flown) < 0.1D,
                "where the camera points must not change the flight path, flew "
                        + (aimed.getZ() - aimedStartZ) + " against " + flown);
        helper.assertTrue(aimed.getY() > aimedStartY - 1.0D,
                "and it certainly should not dive because the camera is aimed down, Y went "
                        + aimedStartY + " -> " + aimed.getY());

        helper.succeed();
    }

    /**
     * Left and right roll it, and rolling is what moves it sideways — the same lean-to-move model as forward
     * flight, on the other axis.
     */
    @GameTest(template = "empty9")
    public static void theInterceptorRollsToStrafe(GameTestHelper helper) {
        InterceptorDroneEntity drone = interceptor(helper, 0.0F, 0.0F);
        double startX = drone.getX();

        hold(drone, false, false, true, false, false, false, 12);
        double left = drone.getX();
        helper.assertTrue(left > startX + 0.2D,
                "A should roll it left and carry it there (+X facing south), X went " + startX + " -> " + left);
        helper.assertTrue(drone.bodyRoll(1.0F) != 0.0F, "and the airframe should be visibly banked");

        helper.succeed();
    }

    /** The arming switch is what stands between the sticks and the motors. */
    @GameTest(template = "empty9")
    public static void theInterceptorFliesOnlyOnceArmed(GameTestHelper helper) {
        InterceptorDroneEntity drone = helper.spawn(DroneEntities.INTERCEPTOR_DRONE.get(), 24, 8, 24);
        drone.setYRot(0.0F);
        drone.forceRateMode(false);
        drone.setDeltaMovement(0.0D, 0.0D, 0.0D);

        helper.assertFalse(drone.isArmed(), "a fresh interceptor must start disarmed");

        double startY = drone.getY();
        double startZ = drone.getZ();
        hold(drone, true, false, false, false, true, false, 10);
        helper.assertTrue(drone.getY() < startY,
                "disarmed, it should be falling, Y went " + startY + " -> " + drone.getY());
        helper.assertTrue(Math.abs(drone.getZ() - startZ) < 0.1D,
                "and the sticks should move it nowhere, Z drifted " + (drone.getZ() - startZ));

        drone.setArmed(true);
        drone.setDeltaMovement(0.0D, 0.0D, 0.0D);
        double armedStartY = drone.getY();
        hold(drone, false, false, false, false, true, false, 6);
        helper.assertTrue(drone.getY() > armedStartY,
                "armed, space should lift it, Y went " + armedStartY + " -> " + drone.getY());

        helper.succeed();
    }

    /** A drone destroyed with nobody flying it must still free the remotes paired with it. */
    @GameTest(template = "empty9")
    public static void aDroneDestroyedWithNoPilotFreesItsRemotes(GameTestHelper helper) {
        InterceptorDroneEntity drone = interceptor(helper, 0.0F, 0.0F);

        drone.releaseControl(false);
        helper.assertFalse(drone.isControlled(), "precondition: nothing is holding the link");

        drone.destroyOnImpact(null);
        helper.assertTrue(drone.isRemoved(), "the drone should be gone");
        helper.assertTrue(drone.getRemovalReason() == net.minecraft.world.entity.Entity.RemovalReason.DISCARDED,
                "and gone for good, not merely unloaded — that is what frees the remotes, and an "
                        + "unloaded drone must keep its pairing instead");

        helper.succeed();
    }

    /**
     * The pack glides evenly from full to the "land now" mark across nearly the whole flight, rather than
     * sitting on a plateau and falling off a cliff at the end.
     */
    @GameTest(template = "empty9")
    public static void theInterceptorPackDischargesAcrossTheWholeFlight(GameTestHelper helper) {
        helper.assertTrue(FpvPack.openCircuitCellVolts(1.0F) == FpvPack.CELL_FULL,
                "a full cell should read " + FpvPack.CELL_FULL);

        double previous = FpvPack.openCircuitCellVolts(0.0F);
        for (int step = 1; step <= 100; step++) {
            double volts = FpvPack.openCircuitCellVolts(step / 100.0F);
            helper.assertTrue(volts >= previous - 1.0E-6D,
                    "the curve must not rise as the pack drains, " + previous + " -> " + volts);
            previous = volts;
        }

        float lowAt = chargeAtVolts(FpvPack.CELL_LOW);
        helper.assertTrue(lowAt > 0.35F && lowAt < 0.60F,
                "LOW BATTERY should arrive around half a flight in, but at " + lowAt + " charge");

        float landAt = chargeAtVolts(FpvPack.CELL_LAND);
        helper.assertTrue(landAt > 0.05F && landAt < 0.20F,
                "LAND NOW should leave a short reserve, but arrives at " + landAt + " charge");
        helper.assertTrue(landAt < lowAt, "and must come after LOW BATTERY");

        helper.succeed();
    }

    /** The charge at which a resting cell first falls to {@code volts}. */
    private static float chargeAtVolts(double volts) {
        for (int step = 100; step >= 0; step--) {
            if (FpvPack.openCircuitCellVolts(step / 100.0F) <= volts) {
                return step / 100.0F;
            }
        }
        return 0.0F;
    }

    /** At the cutoff the motors can no longer carry the airframe. */
    @GameTest(template = "empty9")
    public static void aFlatPackCannotHoldTheInterceptorUp(GameTestHelper helper) {
        InterceptorDroneEntity healthy = interceptor(helper, 0.0F, 0.0F);
        healthy.setCharge(1.0F);
        helper.assertTrue(healthy.packFade() == 1.0F, "a full pack must give the motors everything");

        InterceptorDroneEntity flat = helper.spawn(DroneEntities.INTERCEPTOR_DRONE.get(), 24, 8, 22);
        flat.setDeltaMovement(0.0D, 0.0D, 0.0D);
        flat.forceRateMode(false);
        flat.setArmed(true);
        flat.setCharge(chargeAtVolts(FpvPack.CELL_CUTOFF));
        helper.assertTrue(flat.packFade() < 1.0F, "precondition: the pack is past its cutoff");

        double startY = flat.getY();
        hold(flat, false, false, false, false, true, false, 40);
        helper.assertTrue(flat.getY() < startY,
                "at the cutoff, holding climb should still lose height, Y went "
                        + startY + " -> " + flat.getY());

        helper.succeed();
    }

    /** A flat interceptor does not let itself down. */
    @GameTest(template = "empty9")
    public static void aFlatInterceptorFallsRatherThanLanding(GameTestHelper helper) {
        DroneEntity recon = helper.spawn(DroneEntities.DRONE.get(), 24, 8, 22);
        helper.assertTrue(recon.autolandsWhenFlat(), "a Mavic should still bring itself down");

        InterceptorDroneEntity drone = interceptor(helper, 0.0F, 0.0F);
        helper.assertFalse(drone.autolandsWhenFlat(),
                "the interceptor must have no landing sequence at all");

        drone.setCharge(0.0F);
        drone.setDeltaMovement(0.0D, 0.0D, 0.0D);
        helper.assertTrue(drone.motorsDead(), "a flat pack must leave the motors dead");

        double startY = drone.getY();
        hold(drone, false, false, false, false, true, false, 6);

        double sink = -drone.getDeltaMovement().y;
        helper.assertTrue(drone.getY() < startY,
                "a flat interceptor should be coming down, Y went " + startY + " -> " + drone.getY());
        helper.assertTrue(sink > 0.25D,
                "and should be falling, not letting itself down: sinking at " + sink + " b/t");

        helper.succeed();
    }

    /** The rotors answer to the arming switch and the throttle, not to being off the ground. */
    @GameTest(template = "empty9")
    public static void theInterceptorRotorsFollowArmingAndThrottle(GameTestHelper helper) {
        InterceptorDroneEntity drone = helper.spawn(DroneEntities.INTERCEPTOR_DRONE.get(), 24, 5, 24);
        drone.setDeltaMovement(0.0D, 0.0D, 0.0D);
        drone.forceRateMode(false);

        helper.assertTrue(drone.propTargetRate() == 0.0F,
                "disarmed, the rotors must be dead, was " + drone.propTargetRate());

        drone.setArmed(true);
        float idle = drone.propTargetRate();
        DroneEntity recon = helper.spawn(DroneEntities.DRONE.get(), 24, 5, 22);
        recon.setDeltaMovement(0.0D, 0.0D, 0.0D);
        helper.assertFalse(recon.isGrounded(), "precondition: the recon drone is airborne and spinning");
        helper.assertTrue(idle == recon.propTargetRate(),
                "armed and idle should turn at the recon drone's rate (" + recon.propTargetRate()
                        + "), was " + idle);

        drone.setDeltaMovement(0.0D, 0.0D, drone.crashSpeed() * 4.0D);
        float driven = drone.propTargetRate();
        helper.assertTrue(driven > idle * 2.5F,
                "at speed the rotors should be a blur, only reached " + driven + " against idle " + idle);

        helper.succeed();
    }

    /** Putting the remote down does not cut the motors. */
    @GameTest(template = "empty9")
    public static void lettingGoOfTheLinkLeavesItArmedAndHovering(GameTestHelper helper) {
        InterceptorDroneEntity drone = interceptor(helper, 0.0F, 0.0F);
        helper.assertTrue(drone.isArmed(), "precondition: armed");

        drone.releaseControl(false);
        helper.assertTrue(drone.isArmed(), "releasing the link must not disarm the motors");

        double startY = drone.getY();
        hold(drone, false, false, false, false, false, false, 40);
        helper.assertTrue(Math.abs(drone.getY() - startY) < 1.5D,
                "an abandoned armed drone should hover, Y went " + startY + " -> " + drone.getY());

        helper.succeed();
    }

    /** Driving forward stands the airframe steeply on its nose — far past the thirty degrees a turn banks it. */
    @GameTest(template = "empty9", timeoutTicks = 200)
    public static void theInterceptorNosesOverHardAtSpeed(GameTestHelper helper) {
        Vec3 centre = helper.absoluteVec(new Vec3(24.5, 5.0, 24.5));
        InterceptorDroneEntity drone = interceptor(helper, 0.0F, 0.0F);

        float steepest = 0.0F;
        for (int tick = 0; tick < 160; tick++) {
            drone.applyControlInput(true, false, false, false, false, false);
            steepest = Math.min(steepest, drone.bodyPitch(1.0F));
            drone.teleportTo(centre.x, centre.y, centre.z);
        }

        helper.assertTrue(steepest < -45.0F,
                "flying flat out should pitch the nose well past a bank, only reached " + steepest + " degrees");
        helper.assertTrue(steepest >= -85.0F,
                "but it must not tip past its own limit, reached " + steepest + " degrees");

        helper.succeed();
    }

    /** It has to be able to run a Shahed down. */
    @GameTest(template = "empty9", timeoutTicks = 200)
    public static void theInterceptorOutrunsACruisingShahed(GameTestHelper helper) {
        double cap = SkyfrontConfig.INTERCEPTOR_TOP_SPEED.get();
        InterceptorDroneEntity drone = interceptor(helper, 0.0F, 0.0F);
        double peak = topSpeedKmh(helper, drone, 160);

        helper.assertTrue(peak <= cap + 0.5D,
                "the interceptor must not exceed " + cap + " km/h, reached " + peak);
        helper.assertTrue(peak >= cap - 1.0D,
                "and should actually reach it, only got " + peak + " km/h");

        double shahedCruiseKmh = dev.krutlexik.skyfront.entity.ShahedEntity.CRUISE_SPEED * 72.0D;
        helper.assertTrue(peak > shahedCruiseKmh,
                "it has to be faster than a cruising Shahed (" + shahedCruiseKmh + " km/h) to intercept one, was " + peak);

        helper.succeed();
    }

    /** The contact fuse. */
    @GameTest(template = "empty9")
    public static void theInterceptorDetonatesOnTouchingItsTarget(GameTestHelper helper) {
        InterceptorDroneEntity drone = interceptor(helper, 0.0F, 0.0F);
        var target = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.COW,
                new Vec3(24.5, 5.0, drone.getZ() - helper.absolutePos(BlockPos.ZERO).getZ() + 1.5));

        drone.setDeltaMovement(0.0D, 0.0D, 0.25D);
        float impact = 0.0F;
        for (int tick = 0; tick < 20 && impact == 0.0F; tick++) {
            drone.applyControlInput(false, false, false, false, false, false);
            impact = drone.lastCollisionSpeed;
        }

        helper.assertTrue(drone.lastRammed == target, "it should have found the target in its path");
        helper.assertTrue(impact * SkyfrontConfig.KMH_PER_BLOCK_TICK >= drone.crashSpeedKmh(),
                "touching a target should register as writing the airframe off, reported "
                        + impact * 72.0F + " km/h against a crash speed of " + drone.crashSpeedKmh());

        helper.succeed();
    }

    /** A target flying AT the drone must be struck, not passed through. */
    @GameTest(template = "empty9")
    public static void theInterceptorHitsATargetClosingHeadOn(GameTestHelper helper) {
        InterceptorDroneEntity drone = interceptor(helper, 0.0F, 0.0F);
        double droneZ = drone.getZ() - helper.absolutePos(BlockPos.ZERO).getZ();

        double gap = 3.0D;
        var target = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.COW,
                new Vec3(24.5, 5.0, droneZ + gap));
        double closing = dev.krutlexik.skyfront.entity.ShahedEntity.CRUISE_SPEED;
        target.setDeltaMovement(0.0D, 0.0D, -closing);

        double stride = drone.crashSpeed();
        drone.setDeltaMovement(0.0D, 0.0D, stride);
        helper.assertTrue(stride < gap, "precondition: the drone alone cannot cover the gap");

        drone.applyControlInput(false, false, false, false, false, false);

        helper.assertTrue(drone.lastRammed == target,
                "a target closing head-on must be struck, not passed through (rammed "
                        + drone.lastRammed + ")");

        helper.succeed();
    }

    /** The drone must come to rest ON what it hit, not short of it. */
    @GameTest(template = "empty9")
    public static void theInterceptorStopsOnTheHullNotShortOfIt(GameTestHelper helper) {
        InterceptorDroneEntity drone = interceptor(helper, 0.0F, 0.0F);

        var target = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.COW,
                new Vec3(24.5, 5.0, drone.getZ() - helper.absolutePos(BlockPos.ZERO).getZ() + 4.0));

        double closing = 2.0D;
        drone.setDeltaMovement(0.0D, 0.0D, closing);
        for (int tick = 0; tick < 10 && drone.lastRammed == null; tick++) {
            drone.applyControlInput(false, false, false, false, false, false);
            drone.setDeltaMovement(0.0D, 0.0D, closing);
        }

        helper.assertTrue(drone.lastRammed == target, "it should have found the target in its path");

        double gap = target.getBoundingBox().minZ - drone.getBoundingBox().maxZ;
        helper.assertTrue(gap < 0.5D,
                "it should stop on the hull, but stopped " + gap + " blocks clear of it");

        helper.succeed();
    }

    /** Running a target down from astern when the target has already taken its step this tick. */
    @GameTest(template = "empty9")
    public static void theInterceptorCatchesATargetThatHasAlreadyMoved(GameTestHelper helper) {
        InterceptorDroneEntity drone = interceptor(helper, 0.0F, 0.0F);
        double droneZ = drone.getZ() - helper.absolutePos(BlockPos.ZERO).getZ();
        double half = drone.getBbWidth() * 0.5D;

        double speed = dev.krutlexik.skyfront.entity.ShahedEntity.CRUISE_SPEED;
        double gap = half + 1.4D;
        var target = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.COW,
                new Vec3(24.5, 5.0, droneZ + gap + target(0.0D)));
        target.setDeltaMovement(0.0D, 0.0D, speed);
        target.xo = target.getX();
        target.yo = target.getY();
        target.zo = target.getZ() - speed;

        drone.setDeltaMovement(0.0D, 0.0D, speed + 0.1D);
        drone.applyControlInput(false, false, false, false, false, false);

        helper.assertTrue(drone.lastRammed == target,
                "a target run down from astern must be struck even when it has moved first (rammed "
                        + drone.lastRammed + ")");
        helper.succeed();
    }

    /** Keeps the spawn line above readable: the cow's half-width, which puts its tail at the gap. */
    private static double target(double extra) {
        return net.minecraft.world.entity.EntityType.COW.getWidth() * 0.5D + extra;
    }

    /**
     * A ram the pilot's client saw is carried out on the server, against the target itself — so the picture
     * that froze on the contact is never thawed by a server that saw a miss.
     */
    @GameTest(template = "empty9")
    public static void aReportedRamIsHeldToIfTheTargetIsNearby(GameTestHelper helper) {
        InterceptorDroneEntity drone = interceptor(helper, 0.0F, 0.0F);

        var distant = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.COW,
                new Vec3(4.5, 5.0, 4.5));
        distant.setPos(drone.getX() + 40.0D, drone.getY(), drone.getZ());
        helper.assertTrue(drone.reportedRamContact(distant) == null,
                "a ram on something forty blocks off must be refused");

        var close = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.COW,
                new Vec3(4.5, 5.0, 4.5));
        close.setPos(drone.getX(), drone.getY(), drone.getZ() + 6.0D);
        close.setDeltaMovement(0.0D, 0.0D, dev.krutlexik.skyfront.entity.ShahedEntity.CRUISE_SPEED);
        Vec3 contact = drone.reportedRamContact(close);
        helper.assertTrue(contact != null, "a ram on a fast target a few blocks ahead must be held to");
        helper.assertTrue(close.getBoundingBox().inflate(0.01D).contains(contact),
                "the strike must be put on the target's hull, not where the drone had fallen behind to");

        helper.succeed();
    }

    /**
     * The link-up carries the drone's type, so a client that was never sent the drone — it is out past the
     * pilot's own loaded area — can still put one in place and fly it.
     */
    @GameTest(template = "empty9")
    public static void theLinkUpCarriesTheDronesType(GameTestHelper helper) {
        int type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getId(DroneEntities.INTERCEPTOR_DRONE.get());
        var sent = new dev.krutlexik.skyfront.drone.network.StartControlPayload(
                42, 700.0D, 1.0D, 2.0D, 3.0D, 10.0F, -5.0F, type, true, 0.37F);
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        dev.krutlexik.skyfront.drone.network.StartControlPayload.STREAM_CODEC.encode(buffer, sent);
        var received = dev.krutlexik.skyfront.drone.network.StartControlPayload.STREAM_CODEC.decode(buffer);
        helper.assertTrue(received.equals(sent), "the link-up must arrive as it was sent, got " + received);
        helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.byId(received.typeId())
                        == DroneEntities.INTERCEPTOR_DRONE.get(),
                "the type it names must be the drone's own");
        helper.succeed();
    }

    /** Flicking climb and descend in turn must not fling the drone up and down at full speed. */
    @GameTest(template = "empty9")
    public static void tappingClimbAndDescendStaysCalm(GameTestHelper helper) {
        InterceptorDroneEntity drone = helper.spawn(DroneEntities.INTERCEPTOR_DRONE.get(), 24, 6, 24);
        drone.setDeltaMovement(0.0D, 0.0D, 0.0D);
        drone.forceRateMode(false);
        drone.setArmed(true);
        helper.assertFalse(drone.isGrounded(), "precondition: airborne");

        double worst = 0.0D;
        for (int tick = 0; tick < 40; tick++) {
            boolean up = tick % 2 == 0;
            drone.applyControlInput(false, false, false, false, up, !up);
            worst = Math.max(worst, Math.abs(drone.getDeltaMovement().y));
        }
        double worstKmh = worst * 72.0D;
        helper.assertTrue(worstKmh < 40.0D,
                "alternating climb/descend should stay gentle, peaked at " + worstKmh + " km/h");

        InterceptorDroneEntity held = helper.spawn(DroneEntities.INTERCEPTOR_DRONE.get(), 24, 3, 22);
        held.setDeltaMovement(0.0D, 0.0D, 0.0D);
        held.forceRateMode(false);
        held.setArmed(true);
        double climbPeak = 0.0D;
        for (int tick = 0; tick < 6; tick++) {
            held.applyControlInput(false, false, false, false, true, false);
            climbPeak = Math.max(climbPeak, held.getDeltaMovement().y);
        }
        helper.assertTrue(climbPeak > 0.15D,
                "a held climb should still build real vertical speed, only reached " + climbPeak);

        helper.succeed();
    }

    /** It is the warhead: nothing can be hung under it, and there is no store to give back. */
    @GameTest(template = "empty9")
    public static void theInterceptorCarriesNoStores(GameTestHelper helper) {
        InterceptorDroneEntity drone = interceptor(helper, 0.0F, 0.0F);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);

        for (String id : new String[]{"superbwarfare:rgo_grenade", "superbwarfare:c4_bomb"}) {
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .get(net.minecraft.resources.ResourceLocation.parse(id));
            if (item == net.minecraft.world.item.Items.AIR) {
                continue;
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
            drone.interact(player, InteractionHand.MAIN_HAND);
            helper.assertFalse(drone.hasAttachment(), id + " must not hang under an interceptor");
        }

        helper.succeed();
    }

    /**
     * Shot down, it behaves like the Mavic does — control gone, tumbling, and finished by the ground —
     * rather than detonating where it was hit.
     */
    @GameTest(template = "empty9", timeoutTicks = 300)
    public static void theInterceptorFallsWhenShotDownAndGoesOffOnTheGround(GameTestHelper helper) {
        for (int x = 20; x <= 28; x++) {
            for (int z = 20; z <= 28; z++) {
                helper.setBlock(x, 2, z, Blocks.STONE);
            }
        }
        InterceptorDroneEntity drone = helper.spawn(DroneEntities.INTERCEPTOR_DRONE.get(), 24, 9, 24);
        drone.setDeltaMovement(0.0D, 0.0D, 0.0D);

        drone.hurt(drone.damageSources().generic(), drone.getHealth());
        helper.assertTrue(drone.isWrecked(), "running its hit points out should wreck it");
        helper.assertFalse(drone.isRemoved(), "and it must stay in the world long enough to fall");

        double startY = drone.getY();
        helper.succeedWhen(() -> {
            helper.assertTrue(drone.getY() < startY - 1.0D,
                    "the wreck should be dropping, still at " + drone.getY() + " from " + startY);
            helper.assertTrue(drone.isRemoved(), "and should be destroyed once it reaches the ground");
        });
    }

    /** Only the drones that can outrun the server's own tracking carry the long-range link. */
    @GameTest(template = "empty9")
    public static void onlyFarFlyingDronesCarryTheLongRangeLink(GameTestHelper helper) {
        DroneEntity recon = hovering(helper, 0.0F);
        helper.assertTrue(recon.needsLongRangeLink() == recon.controlRange() > 128.0D,
                "the recon drone, flown to " + recon.controlRange() + " blocks, should carry the link exactly"
                        + " when that is past the tracker's reach");

        InterceptorDroneEntity interceptor = interceptor(helper, 0.0F, 0.0F);
        helper.assertTrue(interceptor.needsLongRangeLink(),
                "the interceptor is flown to " + interceptor.controlRange()
                        + " blocks, far past anything the server sends by itself");

        dev.krutlexik.skyfront.drone.DroneLongRange.release(interceptor, null);

        helper.succeed();
    }

    /**
     * {@code /skyfront config reload}: a setting changed in memory goes back to what the file says, and the
     * reload counts it as changed.
     */
    @GameTest(template = "empty9")
    public static void theConfigReloadsFromItsFile(GameTestHelper helper) {
        java.nio.file.Path path = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve(SkyfrontConfig.FILE_NAME);
        helper.assertTrue(java.nio.file.Files.exists(path), "the config file should be there at " + path);
        int inFile;
        try (var file = com.electronwill.nightconfig.core.file.CommentedFileConfig.builder(path).sync().build()) {
            file.load();
            inFile = ((Number) file.get(SkyfrontConfig.MAVIC_CONTROL_RANGE.getPath())).intValue();
        }
        int before = SkyfrontConfig.MAVIC_CONTROL_RANGE.get();
        SkyfrontConfig.MAVIC_CONTROL_RANGE.set(inFile == 77 ? 78 : 77);
        SkyfrontConfig.ReloadResult result = SkyfrontConfig.reloadFromFile();
        int after = SkyfrontConfig.MAVIC_CONTROL_RANGE.get();
        SkyfrontConfig.MAVIC_CONTROL_RANGE.set(before);
        helper.assertTrue(result.fileFound(), "the reload should find the file");
        helper.assertTrue(after == inFile, "the range should be back to the file's " + inFile + ", is " + after);
        helper.assertTrue(result.changed() >= 1, "and the reload should count it as changed");
        helper.succeed();
    }

    /** The Molniya is as tough as its own config figure says — 30 out of the box — not the interceptor's 4. */
    @GameTest(template = "empty9")
    public static void theMolniyaTakesItsOwnHitpoints(GameTestHelper helper) {
        helper.assertTrue(SkyfrontConfig.MOLNIYA_HITPOINTS.getDefault() == 30.0D,
                "the Molniya should ship with 30 hit points, ships with " + SkyfrontConfig.MOLNIYA_HITPOINTS.getDefault());
        var molniya = helper.spawn(DroneEntities.MOLNIYA_DRONE.get(), 24, 4, 24);
        helper.assertTrue(molniya.getHealth() == (float) (double) SkyfrontConfig.MOLNIYA_HITPOINTS.get(),
                "a fresh Molniya should have its configured " + SkyfrontConfig.MOLNIYA_HITPOINTS.get()
                        + " hit points, has " + molniya.getHealth());
        helper.succeed();
    }

    /**
     * A player new to the mod gets the Mavics' own ranges without touching the config: the Mavic 3 Pro flies
     * to 500 blocks, the Mavic 3T to 1000 — both on the long-range link.
     */
    @GameTest(template = "empty9")
    public static void theMavicsShipWithTheirLongRanges(GameTestHelper helper) {
        helper.assertTrue(SkyfrontConfig.MAVIC_CONTROL_RANGE.getDefault() == 500,
                "the Mavic 3 Pro should ship flying to 500 blocks, ships to " + SkyfrontConfig.MAVIC_CONTROL_RANGE.getDefault());
        helper.assertTrue(SkyfrontConfig.MAVIC_3T_CONTROL_RANGE.getDefault() == 1000,
                "the Mavic 3T should ship flying to 1000 blocks, ships to " + SkyfrontConfig.MAVIC_3T_CONTROL_RANGE.getDefault());
        helper.succeed();
    }

    /** A destroyed drone's own warhead must not wound the drone that set it off. */
    @GameTest(template = "empty9")
    public static void aWarheadDoesNotDetonateItselfForever(GameTestHelper helper) {
        for (int x = 22; x <= 26; x++) {
            for (int z = 22; z <= 26; z++) {
                helper.setBlock(x, 2, z, Blocks.STONE);
            }
        }
        InterceptorDroneEntity a = helper.spawn(DroneEntities.INTERCEPTOR_DRONE.get(), 24, 3, 24);
        InterceptorDroneEntity b = helper.spawn(DroneEntities.INTERCEPTOR_DRONE.get(), 24, 3, 25);
        a.setDeltaMovement(0.0D, 0.0D, 0.0D);
        b.setDeltaMovement(0.0D, 0.0D, 0.0D);

        a.hurt(a.damageSources().generic(), a.getHealth() + 1.0F);

        helper.assertTrue(a.isRemoved(), "the destroyed interceptor should be gone");
        helper.assertFalse(a.hurt(a.damageSources().generic(), 100.0F),
                "a drone already going off must refuse further damage");

        helper.succeed();
    }

    /** Crouch-clicking a drone packs it away, and the item has to land in the bag, not on the floor. */
    @GameTest(template = "empty9")
    public static void shiftClickPacksTheDroneIntoTheInventory(GameTestHelper helper) {
        DroneEntity drone = hovering(helper, 0.0F);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);

        drone.interact(player, InteractionHand.MAIN_HAND);

        helper.assertTrue(drone.isRemoved(), "the drone should be gone from the world once packed up");
        helper.assertTrue(player.getInventory().contains(new ItemStack(DroneItems.DRONE.get())),
                "the drone item should have gone straight into the player's inventory");

        helper.succeed();
    }

    /** Armed, on the sticks, and with somebody at the controls. */
    private static InterceptorDroneEntity rateModeInterceptor(GameTestHelper helper, float yaw) {
        return rateModeInterceptor(helper, yaw, 8);
    }

    private static InterceptorDroneEntity rateModeInterceptor(GameTestHelper helper, float yaw, int height) {
        return rateMode(helper, helper.spawn(DroneEntities.INTERCEPTOR_DRONE.get(), 24, height, 24), yaw);
    }

    private static <T extends InterceptorDroneEntity> T rateMode(GameTestHelper helper, T drone, float yaw) {
        drone.setYRot(yaw);
        drone.setXRot(0.0F);
        drone.forceRateMode(true);
        drone.setDeltaMovement(0.0D, 0.0D, 0.0D);
        drone.setArmed(true);
        drone.setController(java.util.UUID.randomUUID());
        return drone;
    }

    /** Where the airframe's own up axis points, vertically: 1 level, 0 on its side, -1 on its back. */
    private static double upwards(InterceptorDroneEntity drone) {
        return Math.cos(drone.bodyRoll(1.0F) * Mth.DEG_TO_RAD)
                * Math.cos(drone.bodyPitch(1.0F) * Mth.DEG_TO_RAD);
    }

    /** Holds the four sticks for a number of ticks. */
    private static void sticks(InterceptorDroneEntity drone, double roll, double pitch,
                               double yaw, double throttle, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            drone.setSticks((float) roll, (float) pitch, (float) yaw, (float) throttle);
            drone.applyControlInput(false, false, false, false, false, false);
        }
    }

    /** The curve every rate goes through, on its own. */
    @GameTest(template = "empty9")
    public static void theRateCurveIsSoftInTheMiddleAndFullAtTheStop(GameTestHelper helper) {
        helper.assertTrue(RateModeFlight.actualRate(0.0D, 150.0D, 650.0D, 0.2D) == 0.0D,
                "a centred stick asks for nothing");
        double full = RateModeFlight.actualRate(1.0D, 150.0D, 650.0D, 0.2D);
        helper.assertTrue(Math.abs(full - 650.0D) < 0.001D,
                "the stop should be exactly the configured rate, was " + full);

        double half = RateModeFlight.actualRate(0.5D, 150.0D, 650.0D, 0.2D);
        helper.assertTrue(half < 325.0D,
                "half stick must be gentler than half the rate (that is what the curve is for), was " + half);
        helper.assertTrue(half > 75.0D,
                "but not so gentle that the middle of the stick is dead, was " + half);
        helper.assertTrue(RateModeFlight.actualRate(-1.0D, 150.0D, 650.0D, 0.2D) == -full,
                "and it must be symmetrical");

        helper.assertTrue(RateModeFlight.thrustAtRest() > 0.7D && RateModeFlight.thrustAtRest() < 0.9D,
                "standing still, the rotors make about four fifths of their best");
        helper.assertTrue(RateModeFlight.thrustAtAirspeed(160.0D, 200.0D) < RateModeFlight.thrustAtAirspeed(60.0D, 200.0D),
                "and less and less of it the faster air is coming through them");
        helper.assertTrue(RateModeFlight.thrustAtAirspeed(400.0D, 200.0D) <= 0.0D,
                "down to nothing once the airframe is well past its own limit");
        helper.assertTrue(RateModeFlight.thrustAtAirspeed(160.0D, 400.0D) > RateModeFlight.thrustAtAirspeed(160.0D, 200.0D),
                "a faster airframe must still be pulling at a speed that has finished a slower one");
        helper.assertTrue(RateModeFlight.groundEffect(0.1D) > 1.5D,
                "a hand's breadth off the floor the downwash should be carrying it");
        helper.assertTrue(Math.abs(RateModeFlight.groundEffect(RateModeFlight.GROUND_EFFECT_REACH) - 1.0D) < 0.05D,
                "and by a block up there should be none of it left");

        helper.succeed();
    }

    /** What the interceptor ships as. */
    @GameTest(template = "empty9")
    public static void theInterceptorShipsFlyingLikeARealQuad(GameTestHelper helper) {
        helper.assertTrue(SkyfrontConfig.INTERCEPTOR_RATE_MODE.get(),
                "rateMode is meant to be what the interceptor ships with");
        InterceptorDroneEntity drone = helper.spawn(DroneEntities.INTERCEPTOR_DRONE.get(), 24, 5, 24);
        helper.assertTrue(drone.rateMode(), "and a drone straight out of the config should agree");
        helper.succeed();
    }

    /** The whole of rateMode mode in one test. */
    @GameTest(template = "empty9")
    public static void rateModeSticksAskForARateNotAnAngle(GameTestHelper helper) {
        InterceptorDroneEntity drone = rateModeInterceptor(helper, 0.0F);
        helper.assertTrue(Math.abs(drone.bodyRoll(1.0F)) < 0.5F, "precondition: it starts level");

        sticks(drone, 0.35D, 0.0D, 0.0D, 0.6D, 6);
        float banked = drone.bodyRoll(1.0F);
        helper.assertTrue(Math.abs(banked) > 8.0F,
                "holding roll should bank it, only reached " + banked + " degrees");

        sticks(drone, 0.35D, 0.0D, 0.0D, 0.6D, 6);
        float further = drone.bodyRoll(1.0F);
        helper.assertTrue(Math.abs(further) > Math.abs(banked) + 8.0F,
                "and holding it longer should keep rolling — a rate, not an angle. Went "
                        + banked + " -> " + further);

        sticks(drone, 0.0D, 0.0D, 0.0D, 0.6D, 10);
        float left = drone.bodyRoll(1.0F);
        helper.assertTrue(Math.abs(left) >= Math.abs(further) - 1.0F,
                "letting go must not level it: " + further + " -> " + left);
        helper.assertTrue(Math.abs(left - further) < 60.0F,
                "and it must stop turning soon after, not carry on: " + further + " -> " + left);
        helper.assertTrue(Math.abs(left) > 20.0F,
                "twenty ticks later it should still be banked right over, was " + left);

        helper.succeed();
    }

    /**
     * The rotors push along the airframe's own up axis and nowhere else, so a level drone under throttle goes
     * straight up and nowhere sideways, and one with the throttle shut simply falls.
     */
    @GameTest(template = "empty9")
    public static void rateModeOnlyMovesOnThrottle(GameTestHelper helper) {
        InterceptorDroneEntity drone = rateModeInterceptor(helper, 0.0F, 3);
        double startX = drone.getX();
        double startZ = drone.getZ();

        sticks(drone, 0.0D, 0.0D, 0.0D, 1.0D, 10);
        helper.assertTrue(drone.getDeltaMovement().y > 0.1D,
                "level and on full throttle it should be climbing, was " + drone.getDeltaMovement().y);
        helper.assertTrue(Math.abs(drone.getX() - startX) < 0.1D
                        && Math.abs(drone.getZ() - startZ) < 0.1D,
                "and going nowhere sideways: thrust follows the airframe's up axis and nothing else");

        InterceptorDroneEntity idle = rateModeInterceptor(helper, 0.0F, 8);
        sticks(idle, 0.0D, 0.0D, 0.0D, 0.0D, 10);
        helper.assertTrue(idle.getDeltaMovement().y < -0.1D,
                "with the throttle shut it should be dropping, was " + idle.getDeltaMovement().y);

        helper.succeed();
    }

    /** Point it and open the throttle — the only way there is to go anywhere. */
    @GameTest(template = "empty9")
    public static void rateModeFliesWhereTheAirframeIsPointing(GameTestHelper helper) {
        InterceptorDroneEntity south = rateModeInterceptor(helper, 0.0F);
        double startZ = south.getZ();
        sticks(south, 0.0D, -0.6D, 0.0D, 0.0D, 5);
        float nose = south.bodyPitch(1.0F);
        helper.assertTrue(nose < -15.0F,
                "pulling the pitch stick forward should drop the nose, reached " + nose);

        sticks(south, 0.0D, 0.0D, 0.0D, 1.0D, 15);
        helper.assertTrue(south.getZ() > startZ + 0.5D,
                "nose down and on the throttle should fly it forward (+Z facing south), Z moved "
                        + (south.getZ() - startZ));

        InterceptorDroneEntity west = rateModeInterceptor(helper, 90.0F);
        double startX = west.getX();
        double westZ = west.getZ();
        sticks(west, 0.0D, -0.6D, 0.0D, 0.0D, 5);
        sticks(west, 0.0D, 0.0D, 0.0D, 1.0D, 15);
        helper.assertTrue(west.getX() < startX - 0.5D,
                "and the same sticks at a different heading fly a different way, X moved "
                        + (west.getX() - startX));
        helper.assertTrue(Math.abs(west.getZ() - westZ) < Math.abs(west.getX() - startX),
                "with the travel along the nose rather than across it");

        helper.succeed();
    }

    /** The yaw stick turns the nose, and turns it the way the stick is pushed. */
    @GameTest(template = "empty9")
    public static void rateModeYawTurnsTheNose(GameTestHelper helper) {
        InterceptorDroneEntity drone = rateModeInterceptor(helper, 0.0F);
        sticks(drone, 0.0D, 0.0D, 0.5D, 0.3D, 12);
        float turned = Mth.wrapDegrees(drone.getYRot());
        helper.assertTrue(turned > 15.0F,
                "yaw right should swing the nose right (increasing yaw), reached " + turned);

        InterceptorDroneEntity other = rateModeInterceptor(helper, 0.0F);
        sticks(other, 0.0D, 0.0D, -0.5D, 0.3D, 12);
        helper.assertTrue(Mth.wrapDegrees(other.getYRot()) < -15.0F,
                "and yaw left the other way, reached " + Mth.wrapDegrees(other.getYRot()));

        helper.succeed();
    }

    /** Letting go of the link mid-flight does not hand the drone to an autopilot. */
    @GameTest(template = "empty9")
    public static void anAbandonedRateModeDroneFliesOnAsItWasLeft(GameTestHelper helper) {
        InterceptorDroneEntity left = rateModeInterceptor(helper, 0.0F);
        InterceptorDroneEntity twin = rateModeInterceptor(helper, 0.0F);
        twin.setPos(twin.getX() + 6.0D, twin.getY(), twin.getZ());
        sticks(left, 0.6D, 0.0D, 0.0D, 0.4D, 6);
        sticks(twin, 0.6D, 0.0D, 0.0D, 0.4D, 6);
        double banked = left.bodyRoll(1.0F);
        helper.assertTrue(Math.abs(banked) > 15.0F, "precondition: it has been left well banked over");

        left.setController(null);
        double leftStart = left.getY();
        double twinStart = twin.getY();
        for (int tick = 0; tick < 20; tick++) {
            left.applyControlInput(false, false, false, false, false, false);
            twin.setSticks(0.0F, 0.0F, 0.0F, 0.4F);
            twin.applyControlInput(false, false, false, false, false, false);
        }

        helper.assertTrue(Math.abs(left.bodyRoll(1.0F)) > Math.abs(banked) * 0.8D,
                "an abandoned drone must not level itself, bank went " + banked + " -> " + left.bodyRoll(1.0F));
        helper.assertTrue(Math.abs(left.bodyRoll(1.0F) - twin.bodyRoll(1.0F)) < 0.5F,
                "it should hold its attitude exactly as a pilot centring the sticks does: "
                        + left.bodyRoll(1.0F) + " vs " + twin.bodyRoll(1.0F));
        helper.assertTrue(Math.abs((left.getY() - leftStart) - (twin.getY() - twinStart)) < 0.05D,
                "and fly on at the throttle it was left at, climbing just as the twin does: "
                        + (left.getY() - leftStart) + " vs " + (twin.getY() - twinStart));
        helper.assertTrue(left.getDeltaMovement().subtract(twin.getDeltaMovement()).length() < 0.01D,
                "the two should be flying the same flight, velocities "
                        + left.getDeltaMovement() + " vs " + twin.getDeltaMovement());

        helper.succeed();
    }

    /** Rate mode changes how the interceptor is flown and nothing else about it. */
    @GameTest(template = "empty9")
    public static void rateModeStillAnswersToTheArmingSwitch(GameTestHelper helper) {
        InterceptorDroneEntity drone = rateModeInterceptor(helper, 0.0F);
        drone.setArmed(false);
        double startY = drone.getY();

        sticks(drone, 1.0D, 1.0D, 1.0D, 1.0D, 10);
        helper.assertTrue(drone.getY() < startY,
                "disarmed, full throttle must do nothing and it should be falling, Y went "
                        + startY + " -> " + drone.getY());
        helper.assertTrue(Math.abs(drone.bodyRoll(1.0F)) < 1.0F,
                "and the sticks should not be turning it either, banked " + drone.bodyRoll(1.0F));

        helper.succeed();
    }

    /** The pitch stick, both ways. */
    @GameTest(template = "empty9")
    public static void rateModePitchPutsTheNoseWhereTheControlsSay(GameTestHelper helper) {
        InterceptorDroneEntity up = rateModeInterceptor(helper, 0.0F, 3);
        sticks(up, 0.0D, 0.6D, 0.0D, 0.0D, 5);
        helper.assertTrue(up.bodyPitch(1.0F) > 15.0F,
                "mouse forward should raise the nose, reached " + up.bodyPitch(1.0F));

        double startZ = up.getZ();
        sticks(up, 0.0D, 0.0D, 0.0D, 1.0D, 12);
        helper.assertTrue(up.getZ() < startZ - 0.5D,
                "and nose up on the throttle should fly it BACKWARDS, Z moved " + (up.getZ() - startZ));

        InterceptorDroneEntity down = rateModeInterceptor(helper, 0.0F, 3);
        sticks(down, 0.0D, -0.6D, 0.0D, 0.0D, 5);
        helper.assertTrue(down.bodyPitch(1.0F) < -15.0F,
                "mouse back should drop the nose, reached " + down.bodyPitch(1.0F));

        double forwardZ = down.getZ();
        sticks(down, 0.0D, 0.0D, 0.0D, 1.0D, 12);
        helper.assertTrue(down.getZ() > forwardZ + 0.5D,
                "and nose down on the throttle flies it forwards, Z moved " + (down.getZ() - forwardZ));

        helper.succeed();
    }

    /** The roll stick, measured by where the thrust ends up taking it. */
    @GameTest(template = "empty9")
    public static void rateModeRollBanksTheWayTheControlsSay(GameTestHelper helper) {
        InterceptorDroneEntity right = rateModeInterceptor(helper, 0.0F, 3);
        sticks(right, 0.5D, 0.0D, 0.0D, 0.0D, 5);
        double startX = right.getX();
        double startZ = right.getZ();
        sticks(right, 0.0D, 0.0D, 0.0D, 1.0D, 12);

        helper.assertTrue(right.getX() < startX - 0.5D,
                "mouse right should bank right and carry it to the drone's right (-X facing south), "
                        + "X moved " + (right.getX() - startX));
        helper.assertTrue(Math.abs(right.getZ() - startZ) < Math.abs(right.getX() - startX),
                "sideways rather than along the nose");

        InterceptorDroneEntity left = rateModeInterceptor(helper, 0.0F, 3);
        sticks(left, -0.5D, 0.0D, 0.0D, 0.0D, 5);
        double leftX = left.getX();
        sticks(left, 0.0D, 0.0D, 0.0D, 1.0D, 12);
        helper.assertTrue(left.getX() > leftX + 0.5D,
                "and mouse left the other way, X moved " + (left.getX() - leftX));

        helper.succeed();
    }

    /**
     * Full rateMode, stated as plainly as it can be: nothing rights the airframe, so a drone rolled onto its back
     * stays on its back — and opening the throttle there drives it at the GROUND, because the rotors only
     * ever push one way and that way is now down.
     */
    @GameTest(template = "empty9")
    public static void rateModeWillFlyItUpsideDown(GameTestHelper helper) {
        InterceptorDroneEntity drone = rateModeInterceptor(helper, 0.0F, 5);

        for (int tick = 0; tick < 25 && upwards(drone) > -0.95D; tick++) {
            sticks(drone, 1.0D, 0.0D, 0.0D, 1.0D, 1);
        }
        helper.assertTrue(upwards(drone) < -0.95D,
                "it should roll all the way onto its back, its up axis reached " + upwards(drone));

        drone.setDeltaMovement(0.0D, 0.0D, 0.0D);
        sticks(drone, 0.0D, 0.0D, 0.0D, 1.0D, 3);

        helper.assertTrue(upwards(drone) < -0.4D,
                "and nothing may turn it back over, its up axis went to " + upwards(drone));
        helper.assertTrue(drone.getDeltaMovement().y < -0.3D,
                "inverted, the throttle should drive it DOWN, was " + drone.getDeltaMovement().y);

        helper.succeed();
    }

    /**
     * The camera is bolted to the airframe, which means its orientation and the model's have to be the same
     * rotation — and they are built by two different pieces of the game, in two different sign conventions,
     * so the only way to know they agree is to build both and compare.
     */
    @GameTest(template = "empty9")
    public static void theCameraIsBoltedToTheAirframe(GameTestHelper helper) {
        for (float bodyPitch : new float[] {-23.0F, 51.0F, 88.5F, -88.5F}) {
            for (float mount : new float[] {0.0F, 25.0F}) {
                assertCameraMatchesAirframe(helper, 37.0F, bodyPitch, 41.0F, mount);
            }
        }
        helper.succeed();
    }

    /**
     * Builds the airframe's rotation and the camera's, from the two different pieces of the game that build
     * them, and insists they are the same rotation — with the camera's mounting angle turned in about the
     * airframe's own right-hand axis, which is what a bracket does.
     */
    private static void assertCameraMatchesAirframe(GameTestHelper helper, float yaw,
                                                    float bodyPitch, float bodyRoll, float mount) {
        org.joml.Quaternionf wanted = new org.joml.Quaternionf()
                .rotateY((180.0F - yaw) * Mth.DEG_TO_RAD)
                .rotateX(bodyPitch * Mth.DEG_TO_RAD)
                .rotateZ(bodyRoll * Mth.DEG_TO_RAD)
                .rotateX(mount * Mth.DEG_TO_RAD);

        float[] angles = RateModeFlight.cameraAnglesOf(new org.joml.Quaternionf()
                .rotateY((180.0F - yaw) * Mth.DEG_TO_RAD)
                .rotateX(bodyPitch * Mth.DEG_TO_RAD)
                .rotateZ(bodyRoll * Mth.DEG_TO_RAD), mount);
        org.joml.Quaternionf camera = new org.joml.Quaternionf().rotationYXZ(
                (float) Math.PI - angles[0] * Mth.DEG_TO_RAD,
                -angles[1] * Mth.DEG_TO_RAD,
                -angles[2] * Mth.DEG_TO_RAD);

        for (float[] axis : new float[][] {{0.0F, 0.0F, -1.0F}, {0.0F, 1.0F, 0.0F}, {1.0F, 0.0F, 0.0F}}) {
            org.joml.Vector3f want = wanted.transform(new org.joml.Vector3f(axis[0], axis[1], axis[2]));
            org.joml.Vector3f got = camera.transform(new org.joml.Vector3f(axis[0], axis[1], axis[2]));
            helper.assertTrue(want.distance(got) < 1.0E-3F,
                    "the camera must point exactly where the airframe does (pitch " + bodyPitch
                            + ", mount " + mount + "): wanted " + want + " got " + got);
        }
    }

    /** The attitude travels in the link feed. */
    @GameTest(template = "empty9")
    public static void theAttitudeTravelsInTheLinkFeed(GameTestHelper helper) {
        InterceptorDroneEntity flying = rateModeInterceptor(helper, 0.0F, 8);
        sticks(flying, 0.7D, -0.4D, 0.0D, 0.3D, 6);
        helper.assertTrue(Math.abs(flying.bodyRoll(1.0F)) > 10.0F
                        && Math.abs(flying.bodyPitch(1.0F)) > 5.0F,
                "precondition: it is banked and pitched to something worth sending");

        var sent = flying.linkAttitude();
        helper.assertFalse(sent.x() == 0.0F && sent.y() == 0.0F && sent.z() == 0.0F,
                "a banked airframe must not be sending a level attitude");

        float[] here = RateModeFlight.cameraAnglesOf(sent, 0.0F);
        float[] there = RateModeFlight.cameraAnglesOf(
                new org.joml.Quaternionf(sent.x(), sent.y(), sent.z(), sent.w()), 0.0F);
        for (int axis = 0; axis < 3; axis++) {
            helper.assertTrue(Math.abs(Mth.wrapDegrees(here[axis] - there[axis])) < 0.1F,
                    "the attitude must survive the wire: " + here[axis] + " -> " + there[axis]);
        }
        helper.assertTrue(Math.abs(Mth.wrapDegrees(there[2] - RateModeFlight.cameraRoll(flying.bodyRoll(1.0F)))) < 0.5F,
                "and the bank has to be in there: drone at " + flying.bodyRoll(1.0F)
                        + ", packet says " + there[2]);

        DroneEntity recon = helper.spawn(DroneEntities.DRONE.get(), 24, 5, 26);
        var level = recon.linkAttitude();
        helper.assertTrue(level.x() == 0.0F && level.y() == 0.0F && level.z() == 0.0F
                        && level.w() == 1.0F,
                "a drone with no attitude of its own should send the level one");

        helper.succeed();
    }

    /** Stood on its nose with the throttle open, the drone reaches the speed in its own config. */
    @GameTest(template = "empty9", timeoutTicks = 400)
    public static void rateModeReachesTheSpeedInTheConfig(GameTestHelper helper) {
        InterceptorDroneEntity drone = rateModeInterceptor(helper, 0.0F, 5);
        double reached = flatOutInRateMode(helper, drone);
        double wanted = SkyfrontConfig.INTERCEPTOR_TOP_SPEED.get();
        helper.assertTrue(reached > wanted * 0.9D,
                "flat out it should reach the configured " + wanted + " km/h, only made " + reached);

        helper.succeed();
    }

    /**
     * The FPV quad flies on its own figures, not the interceptor's: 111 km/h flat out, and lighter — more
     * thrust over its weight, so it hovers lower on the stick.
     */
    @GameTest(template = "empty9", timeoutTicks = 400)
    public static void theFpvFliesAtItsOwnTopSpeed(GameTestHelper helper) {
        var fpv = rateMode(helper, helper.spawn(DroneEntities.FPV_DRONE.get(), 24, 5, 24), 0.0F);
        double reached = flatOutInRateMode(helper, fpv);
        helper.assertTrue(reached > 111.0D * 0.9D && reached < 111.0D * 1.05D,
                "flat out it should make its own 111 km/h, made " + reached);
        helper.assertTrue(fpv.hoverThrottle() < rateModeInterceptor(helper, 0.0F, 5).hoverThrottle(),
                "and it should hover lower on the stick than the interceptor, being lighter");

        helper.succeed();
    }

    /** Pitched right over with the throttle open until the speed settles; the speed reached across the ground, km/h. */
    private static double flatOutInRateMode(GameTestHelper helper, InterceptorDroneEntity drone) {
        net.minecraft.world.phys.Vec3 held = drone.position();

        for (int tick = 0; tick < 60 && upwards(drone) > 0.42D; tick++) {
            sticks(drone, 0.0D, -0.25D, 0.0D, 0.0D, 1);
            drone.setPos(held.x, held.y, held.z);
        }
        helper.assertTrue(upwards(drone) < 0.5D,
                "it must be able to pitch right over at all, its up axis only reached "
                        + upwards(drone));

        for (int tick = 0; tick < 200; tick++) {
            sticks(drone, 0.0D, 0.0D, 0.0D, 1.0D, 1);
            drone.setPos(held.x, held.y, held.z);
        }

        return drone.getDeltaMovement().horizontalDistance() * SkyfrontConfig.KMH_PER_BLOCK_TICK;
    }

    /** The drawn attitude is the real one. */
    @GameTest(template = "empty9")
    public static void theModelIsDrawnAtTheAttitudeItIsFlying(GameTestHelper helper) {
        InterceptorDroneEntity drone = rateModeInterceptor(helper, 0.0F, 5);
        net.minecraft.world.phys.Vec3 held = drone.position();

        for (int tick = 0; tick < 40 && drone.bodyPitch(1.0F) > -60.0F; tick++) {
            sticks(drone, 0.0D, -1.0D, 0.0D, 0.3D, 1);
            drone.setPos(held.x, held.y, held.z);
        }

        org.joml.Quaternionf posed = drone.renderRotation(1.0F);
        helper.assertTrue(posed != null, "an rateMode airframe must hand the renderer its whole rotation");
        float[] fromModel = RateModeFlight.cameraAnglesOf(posed, 0.0F);
        float drawn = drone.bodyPitch(1.0F);
        helper.assertTrue(Math.abs(fromModel[1] + drawn) < 0.5F,
                "and that rotation must be the attitude it is flying: " + drawn + " vs " + fromModel[1]);
        helper.assertTrue(drawn < -55.0F,
                "the model should be pitched right over with it, was " + drawn);
        float[] camera = drone.cameraAngles(1.0F, 0.0F);
        helper.assertTrue(Math.abs(camera[1] + drawn) < 0.5F,
                "and a camera bolted flat to it looks the same way down: model " + drawn
                        + ", camera " + camera[1]);

        float[] levelled = drone.cameraAngles(1.0F, -drawn);
        helper.assertTrue(Math.abs(levelled[1]) < 1.0F,
                "with the camera wound up to match, the view should be level, was " + levelled[1]);

        helper.succeed();
    }

    /** Shutting the throttle is not the same as switching the drone off. */
    @GameTest(template = "empty9")
    public static void cuttingTheThrottleIsASettleNotADrop(GameTestHelper helper) {
        InterceptorDroneEntity drone = rateModeInterceptor(helper, 0.0F, 5);
        net.minecraft.world.phys.Vec3 held = drone.position();

        sticks(drone, 0.0D, 0.0D, 0.0D, 1.0D, 10);
        helper.assertTrue(drone.getDeltaMovement().y > 0.3D,
                "precondition: it is climbing, at " + drone.getDeltaMovement().y);

        double before = drone.getY();
        sticks(drone, 0.0D, 0.0D, 0.0D, 0.0D, 2);
        helper.assertTrue(drone.getY() > before,
                "it should carry on up on its own momentum, went " + before + " -> " + drone.getY());

        for (int tick = 0; tick < 40; tick++) {
            sticks(drone, 0.0D, 0.0D, 0.0D, 0.0D, 1);
            drone.setPos(held.x, held.y, held.z);
        }
        double sink = -drone.getDeltaMovement().y;
        helper.assertTrue(sink > 0.15D,
                "with nothing driving them it does have to come down, was sinking at " + sink);
        helper.assertTrue(sink < 0.9D,
                "but gently — the disc is braking it, not a free fall. Sinking at " + sink
                        + " blocks a tick (" + sink * SkyfrontConfig.KMH_PER_BLOCK_TICK + " km/h)");

        helper.succeed();
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1.0E-3F;
    }

    /**
     * A stick is read through its own three points, each half scaled on its own - so a gimbal that rests off
     * the middle still reads nothing at rest and still reaches a full one at both stops.
     */
    @GameTest(template = "empty9")
    public static void aRadioStickIsReadThroughItsOwnCalibration(GameTestHelper helper) {
        RadioProfile.Calibration identity = new RadioProfile.Calibration();
        helper.assertTrue(near(RadioProfile.centred(0.4F, identity), 0.4F), "an untouched axis reads as it comes");

        RadioProfile.Calibration worn = new RadioProfile.Calibration(-0.9F, 0.1F, 0.8F);
        helper.assertTrue(near(RadioProfile.centred(0.1F, worn), 0.0F), "at rest it must read nothing");
        helper.assertTrue(near(RadioProfile.centred(0.8F, worn), 1.0F), "and reach a full one at the top");
        helper.assertTrue(near(RadioProfile.centred(-0.9F, worn), -1.0F), "and at the bottom");
        helper.assertTrue(near(RadioProfile.centred(0.45F, worn), 0.5F), "half way up is half");

        helper.assertTrue(near(RadioProfile.span(-0.9F, worn), 0.0F), "throttle at its bottom is nothing");
        helper.assertTrue(near(RadioProfile.span(0.8F, worn), 1.0F), "and at its top is everything");

        helper.assertTrue(RadioProfile.deadband(0.02F, 0.05F) == 0.0F, "inside the deadband reads nothing");
        helper.assertTrue(near(RadioProfile.deadband(1.0F, 0.05F), 1.0F), "the stop is still a full stick");
        helper.assertTrue(near(RadioProfile.deadband(-1.0F, 0.05F), -1.0F), "either way");
        helper.succeed();
    }

    /** A transmitter's throttle runs from its bottom stop; a gamepad's springs to the middle and runs from there. */
    @GameTest(template = "empty9")
    public static void aGamepadThrottleRunsFromTheMiddle(GameTestHelper helper) {
        RadioProfile radio = new RadioProfile();
        helper.assertFalse(radio.gamepad, "a transmitter is the default");
        float[] axes = new float[8];
        axes[radio.throttle.axis] = -1.0F;
        helper.assertTrue(near(radio.throttleOf(axes), 0.0F), "a transmitter's throttle at the bottom is nothing");
        axes[radio.throttle.axis] = 0.0F;
        helper.assertTrue(near(radio.throttleOf(axes), 0.5F), "and in the middle is half");

        RadioProfile pad = new RadioProfile();
        pad.gamepad = true;
        pad.deadband = 0.0F;
        helper.assertTrue(near(pad.throttleOf(axes), 0.0F), "a gamepad's stick at rest in the middle is no throttle");
        axes[pad.throttle.axis] = 0.5F;
        helper.assertTrue(near(pad.throttleOf(axes), 0.5F), "half way up is half");
        axes[pad.throttle.axis] = 1.0F;
        helper.assertTrue(near(pad.throttleOf(axes), 1.0F), "full up is full");
        axes[pad.throttle.axis] = -1.0F;
        helper.assertTrue(near(pad.throttleOf(axes), 0.0F), "and pulled below the middle it stays at nothing");
        helper.succeed();
    }

    /** The four sticks and a reversed channel, end to end through a profile. */
    @GameTest(template = "empty9")
    public static void aRadioProfileTurnsAxesIntoSticks(GameTestHelper helper) {
        RadioProfile profile = new RadioProfile();
        profile.deadband = 0.0F;
        profile.pitch.reverse = true;
        float[] axes = {0.5F, 0.6F, 1.0F, -0.25F};

        helper.assertTrue(near(profile.stick(profile.roll, axes), 0.5F), "roll off axis one");
        helper.assertTrue(near(profile.stick(profile.pitch, axes), -0.6F), "pitch off axis two, reversed");
        helper.assertTrue(near(profile.stick(profile.yaw, axes), -0.25F), "yaw off axis four");
        helper.assertTrue(near(profile.throttleOf(axes), 1.0F), "throttle off axis three, full");

        profile.throttle.reverse = true;
        helper.assertTrue(near(profile.throttleOf(axes), 0.0F), "a reversed throttle at the same place is shut");

        profile.roll.axis = 12;
        helper.assertTrue(profile.stick(profile.roll, axes) == 0.0F, "a missing axis must read nothing");
        helper.succeed();
    }

    /** Switches, on axes and on buttons, the right way up and reversed - and a three-position one. */
    @GameTest(template = "empty9")
    public static void aRadioSwitchReadsOnWhereItShould(GameTestHelper helper) {
        RadioProfile profile = new RadioProfile();
        float[] axes = {0.0F, 0.0F, 0.0F, 0.0F, 1.0F, -1.0F, 0.0F};
        boolean[] buttons = {false, true};

        RadioProfile.Aux onAxis = new RadioProfile.Aux();
        onAxis.source = RadioProfile.Source.AXIS;
        onAxis.index = 4;
        helper.assertTrue(profile.auxOn(onAxis, axes, buttons), "a switch axis at the top is on");
        onAxis.reverse = true;
        helper.assertFalse(profile.auxOn(onAxis, axes, buttons), "and reversed, the top is off");

        RadioProfile.Aux middle = new RadioProfile.Aux();
        middle.source = RadioProfile.Source.AXIS;
        middle.index = 6;
        helper.assertFalse(profile.auxOn(middle, axes, buttons), "a three-position switch in the middle is off");

        RadioProfile.Aux onButton = new RadioProfile.Aux();
        onButton.source = RadioProfile.Source.BUTTON;
        onButton.index = 1;
        helper.assertTrue(profile.auxOn(onButton, axes, buttons), "a pressed button is on");
        onButton.reverse = true;
        helper.assertFalse(profile.auxOn(onButton, axes, buttons), "and reversed, pressed is off");

        helper.assertFalse(profile.auxOn(new RadioProfile.Aux(), axes, buttons), "an unassigned switch is never on");

        RadioProfile.Aux knob = new RadioProfile.Aux();
        knob.source = RadioProfile.Source.AXIS;
        knob.index = 5;
        helper.assertTrue(near(profile.auxLevel(knob, axes), 0.0F), "a knob at its bottom reads nothing");
        knob.reverse = true;
        helper.assertTrue(near(profile.auxLevel(knob, axes), 1.0F), "and reversed, everything");
        helper.succeed();
    }

    /**
     * Finding a control by moving it: whatever moved furthest is the answer, and which way it moved decides
     * the reversal.
     */
    @GameTest(template = "empty9")
    public static void aRadioControlIsFoundByMovingIt(GameTestHelper helper) {
        float[] rest = {0.0F, 0.0F, -1.0F, 0.0F, -1.0F};
        boolean[] none = {false, false, false};

        RadioProfile.Detection roll = new RadioProfile.Detection(rest, none, false);
        helper.assertTrue(roll.poll(new float[] {0.1F, 0.0F, -1.0F, -0.2F, -1.0F}, none) == null,
                "a small movement is not an answer yet");
        RadioProfile.Found found = roll.poll(new float[] {0.15F, 0.0F, -1.0F, -0.95F, -1.0F}, none);
        helper.assertTrue(found != null && found.source() == RadioProfile.Source.AXIS && found.index() == 3,
                "the axis that moved furthest should be found, got " + found);
        helper.assertTrue(found.reverse(), "and one that moved the wrong way is reversed");

        RadioProfile.Detection flip = new RadioProfile.Detection(rest, none, true);
        RadioProfile.Found button = flip.poll(rest, new boolean[] {false, false, true});
        helper.assertTrue(button != null && button.source() == RadioProfile.Source.BUTTON && button.index() == 2
                && !button.reverse(), "a button flipped to pressed is found, pressed meaning on: " + button);

        RadioProfile.Detection sticksOnly = new RadioProfile.Detection(rest, none, false);
        helper.assertTrue(sticksOnly.poll(rest, new boolean[] {true, false, false}) == null,
                "a stick is never found on a button");
        helper.succeed();
    }

    /** A calibration taken by sweeping: the ends are what was reached, the centre where it rests. */
    @GameTest(template = "empty9")
    public static void aRadioCalibrationIsTakenBySweeping(GameTestHelper helper) {
        RadioProfile.Capture capture = new RadioProfile.Capture();
        capture.accumulate(new float[] {0.05F, 0.0F});
        capture.accumulate(new float[] {-0.92F, 0.01F});
        capture.accumulate(new float[] {0.87F, 0.0F});
        java.util.List<RadioProfile.Calibration> result = capture.finish(new float[] {0.05F, 0.0F});

        RadioProfile.Calibration swept = result.get(0);
        helper.assertTrue(near(swept.min, -0.92F) && near(swept.max, 0.87F) && near(swept.centre, 0.05F),
                "the swept axis should keep its ends and its resting point");
        RadioProfile.Calibration still = result.get(1);
        helper.assertTrue(near(still.min, -1.0F) && near(still.max, 1.0F) && near(still.centre, 0.0F),
                "an axis never moved must be left alone, not given a range made of noise");
        helper.succeed();
    }

    /** The setup survives being written out and read back, switches and calibration included. */
    @GameTest(template = "empty9")
    public static void aRadioProfileSurvivesSaving(GameTestHelper helper) {
        RadioProfile profile = new RadioProfile();
        profile.enabled = true;
        profile.deviceName = "RadioMaster Pocket";
        profile.yaw.axis = 5;
        profile.yaw.reverse = true;
        profile.arm.source = RadioProfile.Source.BUTTON;
        profile.arm.index = 7;
        profile.arm.mode = RadioProfile.AuxMode.BUTTON;
        profile.calibration.add(new RadioProfile.Calibration(-0.9F, 0.1F, 0.8F));

        com.google.gson.Gson gson = new com.google.gson.Gson();
        RadioProfile back = gson.fromJson(gson.toJson(profile), RadioProfile.class);
        helper.assertTrue(back.enabled && back.deviceName.equals("RadioMaster Pocket"), "device lost");
        helper.assertTrue(back.yaw.axis == 5 && back.yaw.reverse, "a stick mapping was lost");
        helper.assertTrue(back.arm.source == RadioProfile.Source.BUTTON && back.arm.index == 7
                && back.arm.mode == RadioProfile.AuxMode.BUTTON, "a switch mapping was lost");
        helper.assertTrue(back.calibration.size() == 1 && near(back.calibration.get(0).centre, 0.1F),
                "the calibration was lost");
        helper.succeed();
    }

    /** Flown into a wall at speed, the drone is written off on the tick it arrives — not after scraping along it. */
    @GameTest(template = "empty9")
    public static void aFastDroneIsWrittenOffTheTickItHitsAWall(GameTestHelper helper) {
        for (int y = 1; y <= 9; y++) {
            for (int z = 16; z <= 32; z++) {
                helper.setBlock(30, y, z, Blocks.STONE);
            }
        }
        for (float heading : new float[] {-90.0F, -60.0F}) {
            InterceptorDroneEntity drone = rateModeInterceptor(helper, heading, 5);
            drone.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(22.5D, 5.0D, 24.5D)));
            double crash = drone.crashSpeedKmh() / SkyfrontConfig.KMH_PER_BLOCK_TICK;
            double rad = Math.toRadians(heading);
            drone.setDeltaMovement(-Math.sin(rad) * 2.0D, 0.0D, Math.cos(rad) * 2.0D);

            StringBuilder trace = new StringBuilder();
            int hitTick = -1;
            int contactTick = -1;
            for (int tick = 0; tick < 12; tick++) {
                double before = drone.getX();
                sticks(drone, 0.0D, 0.0D, 0.0D, 0.6D, 1);
                trace.append(String.format(java.util.Locale.ROOT, "[t%d x=%.2f v=%.2f hit=%.2f]",
                        tick, drone.getX(), drone.getDeltaMovement().length(), drone.lastCollisionSpeed));
                if (contactTick < 0 && drone.horizontalCollision) {
                    contactTick = tick;
                }
                if (hitTick < 0 && drone.lastCollisionSpeed >= crash) {
                    hitTick = tick;
                }
            }
            helper.assertTrue(contactTick >= 0, "the drone never reached the wall: " + trace);
            helper.assertTrue(hitTick == contactTick,
                    "heading " + heading + ": first touched the wall on tick " + contactTick
                            + " but was only written off on tick " + hitTick + ": " + trace);
        }
        helper.succeed();
    }

    /**
     * The Molniya is the interceptor in a different airframe: every behaviour it is meant to share is checked
     * here against the interceptor's own, and what is meant to differ — no OSD, its own item — differs.
     */
    @GameTest(template = "empty9")
    public static void theMolniyaFliesExactlyAsTheInterceptorDoes(GameTestHelper helper) {
        dev.krutlexik.skyfront.drone.entity.MolniyaDroneEntity molniya =
                helper.spawn(DroneEntities.MOLNIYA_DRONE.get(), 24, 5, 24);

        helper.assertTrue(molniya instanceof InterceptorDroneEntity,
                "it must BE an interceptor, not a copy of one, so the warhead and pack are the same code");
        helper.assertTrue(molniya.requiresArming(), "it has an arming switch like the interceptor");
        helper.assertTrue(molniya.fliesAsAircraft(), "but it flies as a fixed-wing aircraft, not a quad");
        helper.assertTrue(molniya.osdStyle() == DroneEntity.Osd.INAV, "and flies on the fixed-wing screen");
        helper.assertFalse(molniya.hasThermalCamera(), "and no thermal camera");
        helper.assertTrue(molniya.packedItem().is(DroneItems.MOLNIYA_DRONE.get()),
                "and packs up into its own item, not the interceptor's");
        helper.succeed();
    }

    /** And it actually flies: armed and on the throttle it climbs, and the sticks turn it. */
    @GameTest(template = "empty9")
    public static void theMolniyaAnswersTheSticks(GameTestHelper helper) {
        dev.krutlexik.skyfront.drone.entity.MolniyaDroneEntity molniya =
                helper.spawn(DroneEntities.MOLNIYA_DRONE.get(), 24, 3, 24);
        molniya.forceRateMode(true);
        molniya.setDeltaMovement(0.0D, 0.0D, 0.0D);
        molniya.setArmed(true);
        molniya.setController(java.util.UUID.randomUUID());

        double startSpeed = molniya.getDeltaMovement().length();
        for (int tick = 0; tick < 15; tick++) {
            molniya.setSticks(0.0F, 0.0F, 0.0F, 1.0F);
            molniya.applyControlInput(false, false, false, false, false, false);
        }
        helper.assertTrue(molniya.getDeltaMovement().horizontalDistance() > startSpeed + 0.1D,
                "on the throttle it should gather airspeed down its nose, reached "
                        + molniya.getDeltaMovement().horizontalDistance());

        for (int tick = 0; tick < 8; tick++) {
            molniya.setSticks(0.6F, 0.0F, 0.0F, 0.8F);
            molniya.applyControlInput(false, false, false, false, false, false);
        }
        helper.assertTrue(Math.abs(molniya.bodyRoll(1.0F)) > 8.0F,
                "and with airspeed the roll stick should bank it, reached " + molniya.bodyRoll(1.0F));
        helper.succeed();
    }

    /** From a standing start on the ground, throttle open, it rolls forward and lifts off on its own. */
    @GameTest(template = "empty9")
    public static void theMolniyaTakesOffFromTheGround(GameTestHelper helper) {
        for (int x = 20; x <= 28; x++) {
            for (int z = 2; z <= 46; z++) {
                helper.setBlock(x, 2, z, Blocks.STONE);
            }
        }
        var molniya = helper.spawn(DroneEntities.MOLNIYA_DRONE.get(), 24, 3, 4);
        molniya.setYRot(0.0F);
        molniya.setXRot(0.0F);
        molniya.setArmed(true);
        molniya.setController(java.util.UUID.randomUUID());
        molniya.setDeltaMovement(0.0D, 0.0D, 0.0D);

        double startY = molniya.getY();
        double highest = startY;
        boolean rolled = false;
        for (int tick = 0; tick < 120 && !molniya.horizontalCollision; tick++) {
            molniya.setSticks(0.0F, 0.0F, 0.0F, 1.0F);
            molniya.applyControlInput(false, false, false, false, false, false);
            if (molniya.getDeltaMovement().horizontalDistance() > 0.4D) {
                rolled = true;
            }
            highest = Math.max(highest, molniya.getY());
        }
        helper.assertTrue(rolled, "it must gather speed down the runway on the throttle");
        helper.assertTrue(highest > startY + 1.0D,
                "and lift off from level ground on its own, climbed only " + (highest - startY));
        helper.succeed();
    }

    /** Rolling down the runway at speed is taxiing, not a crash: it must not blow itself up. */
    @GameTest(template = "empty9")
    public static void theMolniyaDoesNotCrashTaxiingAtSpeed(GameTestHelper helper) {
        for (int x = 20; x <= 28; x++) {
            for (int z = 20; z <= 44; z++) {
                helper.setBlock(x, 2, z, Blocks.STONE);
            }
        }
        var molniya = helper.spawn(DroneEntities.MOLNIYA_DRONE.get(), 24, 3, 24);
        molniya.setYRot(0.0F);
        molniya.setXRot(0.0F);
        molniya.setArmed(true);
        molniya.setController(java.util.UUID.randomUUID());

        boolean taxiedOnGround = false;
        for (int tick = 0; tick < 20; tick++) {
            molniya.setDeltaMovement(molniya.getDeltaMovement().x, Math.min(molniya.getDeltaMovement().y, 0.0D), 1.2D);
            molniya.setSticks(0.0F, 0.0F, 0.0F, 0.5F);
            molniya.applyControlInput(false, false, false, false, false, false);
            if (molniya.isGrounded()) {
                taxiedOnGround = true;
                helper.assertTrue(molniya.lastCollisionSpeed * 72.0F < 10.0F,
                        "taxiing fast must not read as a hard impact, got "
                                + molniya.lastCollisionSpeed * 72.0F + " km/h");
            }
            helper.assertFalse(molniya.isWrecked(), "it must not be written off just for rolling on the ground");
        }
        helper.assertTrue(taxiedOnGround, "precondition: it should have been on the ground at speed");
        helper.succeed();
    }

    /** Its warhead is nose-fused: flown nose-first into a wall even at a crawl, it goes off. */
    @GameTest(template = "empty9")
    public static void theMolniyaGoesOffOnTheSlightestNoseHit(GameTestHelper helper) {
        for (int x = 18; x <= 30; x++) {
            for (int y = 2; y <= 14; y++) {
                helper.setBlock(x, y, 29, Blocks.STONE);
            }
        }
        var molniya = molniyaInFlight(helper, 0.12D);
        boolean hit = false;
        for (int tick = 0; tick < 80 && !hit; tick++) {
            molniya.setDeltaMovement(0.0D, 0.0D, 0.12D);
            molniya.setSticks(0.0F, 0.0F, 0.0F, 0.0F);
            molniya.applyControlInput(false, false, false, false, false, false);
            hit = molniya.horizontalCollision;
        }
        helper.assertTrue(hit, "precondition: it should have reached the wall");
        helper.assertTrue(molniya.lastCollisionSpeed * 72.0F >= molniya.crashSpeedKmh(),
                "a nose-first touch must set the warhead off, read only "
                        + molniya.lastCollisionSpeed * 72.0F + " km/h");
        helper.succeed();
    }

    /** Set down on its belly, nose level, it is a landing: the nose fuse must not take it for a hit. */
    @GameTest(template = "empty9")
    public static void theMolniyaNoseFuseIgnoresABellyLanding(GameTestHelper helper) {
        for (int x = 18; x <= 30; x++) {
            for (int z = 18; z <= 44; z++) {
                helper.setBlock(x, 5, z, Blocks.STONE);
            }
        }
        var molniya = molniyaInFlight(helper, 0.3D);
        boolean landed = false;
        for (int tick = 0; tick < 40 && !landed; tick++) {
            molniya.setDeltaMovement(0.0D, -0.15D, 0.3D);
            molniya.setSticks(0.0F, 0.0F, 0.0F, 0.0F);
            molniya.applyControlInput(false, false, false, false, false, false);
            landed = molniya.verticalCollision;
        }
        helper.assertTrue(landed, "precondition: it should have come down on the floor");
        helper.assertTrue(molniya.lastCollisionSpeed * 72.0F < molniya.crashSpeedKmh(),
                "a gentle belly landing must not read as a hit, got "
                        + molniya.lastCollisionSpeed * 72.0F + " km/h");
        helper.succeed();
    }

    /** A plane that has the throttle open flies where its nose points, gathering speed along it. */
    @GameTest(template = "empty9")
    public static void theMolniyaFliesWhereItsNoseAimsAndNeedsSpeedToStayUp(GameTestHelper helper) {
        var slow = molniyaInFlight(helper, 0.3D);
        for (int tick = 0; tick < 10; tick++) {
            slow.setSticks(0.0F, 0.0F, 0.0F, 0.0F);
            slow.applyControlInput(false, false, false, false, false, false);
        }
        helper.assertTrue(slow.getDeltaMovement().y < 0.0D,
                "a slow plane with no throttle must sink, y was " + slow.getDeltaMovement().y);

        var fast = molniyaInFlight(helper, 1.6D);
        double sink = 0.0D;
        for (int tick = 0; tick < 10; tick++) {
            fast.setSticks(0.0F, 0.0F, 0.0F, 0.5F);
            fast.applyControlInput(false, false, false, false, false, false);
            sink = fast.getDeltaMovement().y;
        }
        helper.assertTrue(sink > slow.getDeltaMovement().y,
                "with airspeed the wings should hold it up far better than at a crawl");
        helper.succeed();
    }

    /** Mouse forward pitches the nose up; mouse right rolls right. */
    @GameTest(template = "empty9")
    public static void theMolniyaPitchAndRollFollowTheStick(GameTestHelper helper) {
        var pitch = molniyaInFlight(helper, 1.6D);
        for (int tick = 0; tick < 8; tick++) {
            pitch.setSticks(0.0F, 0.6F, 0.0F, 0.7F);
            pitch.applyControlInput(false, false, false, false, false, false);
        }
        helper.assertTrue(pitch.bodyPitch(1.0F) > 8.0F,
                "mouse forward must raise the nose, pitch reached " + pitch.bodyPitch(1.0F));

        var roll = molniyaInFlight(helper, 1.6D);
        for (int tick = 0; tick < 8; tick++) {
            roll.setSticks(0.6F, 0.0F, 0.0F, 0.7F);
            roll.applyControlInput(false, false, false, false, false, false);
        }
        helper.assertTrue(roll.bodyRoll(1.0F) < -8.0F,
                "mouse right must bank right, roll reached " + roll.bodyRoll(1.0F));
        helper.succeed();
    }

    /** Banking turns the aircraft: hold a bank and the heading comes round with it. */
    @GameTest(template = "empty9")
    public static void theMolniyaBanksIntoATurn(GameTestHelper helper) {
        var molniya = molniyaInFlight(helper, 1.8D);
        float startYaw = molniya.getYRot();
        for (int tick = 0; tick < 30; tick++) {
            molniya.setSticks(0.35F, 0.0F, 0.0F, 0.8F);
            molniya.applyControlInput(false, false, false, false, false, false);
        }
        float turned = Mth.degreesDifference(startYaw, molniya.getYRot());
        helper.assertTrue(molniya.bodyRoll(1.0F) < -10.0F, "it should be banked to the right, roll "
                + molniya.bodyRoll(1.0F));
        helper.assertTrue(turned > 20.0F,
                "and a held right bank should swing the heading right, but it moved " + turned + " degrees");
        helper.succeed();
    }

    /**
     * The P1-SUN can bring a Molniya down: drones pass through one another, but an aircraft is a target, and
     * flying into one is a strike — the interceptor's fuse goes and the Molniya is written off.
     */
    @GameTest(template = "empty9")
    public static void theInterceptorBringsDownAMolniya(GameTestHelper helper) {
        InterceptorDroneEntity interceptor = interceptor(helper, 0.0F, 0.0F);
        double z = interceptor.getZ() - helper.absolutePos(BlockPos.ZERO).getZ();
        var molniya = helper.spawn(DroneEntities.MOLNIYA_DRONE.get(), new Vec3(24.5, 5.0, z + 4.0));
        molniya.setArmed(true);

        interceptor.setDeltaMovement(0.0D, 0.0D, 0.5D);
        for (int tick = 0; tick < 20 && interceptor.lastRammed == null; tick++) {
            interceptor.applyControlInput(false, false, false, false, false, false);
            interceptor.setDeltaMovement(0.0D, 0.0D, 0.5D);
        }

        helper.assertTrue(interceptor.lastRammed == molniya,
                "the interceptor should strike the Molniya in its path, struck " + interceptor.lastRammed);
        interceptor.ram(molniya, null);
        helper.assertTrue(molniya.isRemoved() || molniya.isWrecked(),
                "and a direct strike should write the Molniya off");

        InterceptorDroneEntity other = interceptor(helper, 0.0F, 0.0F);
        double oz = other.getZ() - helper.absolutePos(BlockPos.ZERO).getZ();
        var quad = helper.spawn(DroneEntities.INTERCEPTOR_DRONE.get(), new Vec3(24.5, 5.0, oz + 2.0));
        other.setDeltaMovement(0.0D, 0.0D, 0.5D);
        for (int tick = 0; tick < 10; tick++) {
            other.applyControlInput(false, false, false, false, false, false);
            other.setDeltaMovement(0.0D, 0.0D, 0.5D);
        }
        helper.assertTrue(other.lastRammed != quad, "drones that are not aircraft still pass through");
        helper.succeed();
    }

    /**
     * /skyfront config blockDamage covers every drone's warhead, not just the Shahed's: switched off, a drone
     * going off leaves the blocks around it standing.
     */
    @GameTest(template = "empty9")
    public static void blockDamageOffSparesTheGroundUnderADroneWarhead(GameTestHelper helper) {
        boolean before = dev.krutlexik.skyfront.config.SkyfrontConfig.doExplosionsBreakBlocks();
        try {
            BlockPos off = new BlockPos(10, 3, 10);
            helper.setBlock(off, Blocks.STONE);
            dev.krutlexik.skyfront.config.SkyfrontConfig.setExplosionsBreakBlocks(false);
            GrenadeAttachments.detonateWarhead(helper.getLevel(),
                    Vec3.atCenterOf(helper.absolutePos(off)).add(0.0D, 1.0D, 0.0D), null, null, 4.0F, 0.0F, 0.0F, 0);
            helper.assertBlockPresent(Blocks.STONE, off);

            BlockPos on = new BlockPos(30, 3, 30);
            helper.setBlock(on, Blocks.STONE);
            dev.krutlexik.skyfront.config.SkyfrontConfig.setExplosionsBreakBlocks(true);
            GrenadeAttachments.detonateWarhead(helper.getLevel(),
                    Vec3.atCenterOf(helper.absolutePos(on)).add(0.0D, 1.0D, 0.0D), null, null, 4.0F, 0.0F, 0.0F, 0);
            helper.assertBlockNotPresent(Blocks.STONE, on);
        } finally {
            dev.krutlexik.skyfront.config.SkyfrontConfig.setExplosionsBreakBlocks(before);
        }
        helper.succeed();
    }

    /** The Molniya has a top speed of its own — 113 km/h — whatever the interceptor's config says. */
    @GameTest(template = "empty9")
    public static void theMolniyaIsHeldTo113Kmh(GameTestHelper helper) {
        var molniya = molniyaInFlight(helper, 3.0D);
        helper.assertTrue(Math.abs(molniya.topSpeedKmh() - 113.0D) < 1.0E-6D,
                "its own top speed should be 113 km/h, was " + molniya.topSpeedKmh());
        for (int tick = 0; tick < 20; tick++) {
            molniya.setSticks(0.0F, 0.0F, 0.0F, 1.0F);
            molniya.applyControlInput(false, false, false, false, false, false);
            double kmh = molniya.getDeltaMovement().horizontalDistance() * 72.0D;
            helper.assertTrue(kmh <= 113.0D + 0.5D, "flat out it must not pass 113 km/h, was " + kmh);
        }
        helper.succeed();
    }

    /**
     * Disarmed in the air, the Molniya is a glider, not a brick: the wings still fly it, so it keeps its
     * forward run and comes down on a long, gentle slope rather than dropping like the motor-dead quad does.
     */
    @GameTest(template = "empty9")
    public static void theMolniyaGlidesWhenDisarmedInTheAir(GameTestHelper helper) {
        var fast = helper.spawn(DroneEntities.MOLNIYA_DRONE.get(), 24, 8, 4);
        fast.setYRot(0.0F);
        fast.setXRot(0.0F);
        fast.setArmed(true);
        fast.setController(java.util.UUID.randomUUID());
        fast.setDeltaMovement(0.0D, 0.0D, 1.57D);
        double entrySpeed = fast.getDeltaMovement().horizontalDistance();
        fast.setArmed(false);
        helper.assertTrue(fast.motorsDead(), "precondition: disarming must kill the motor");
        for (int tick = 0; tick < 15; tick++) {
            fast.setSticks(0.0F, 0.0F, 0.0F, 0.0F);
            fast.applyControlInput(false, false, false, false, false, false);
        }
        helper.assertFalse(fast.isWrecked(), "a glide must not wreck the airframe");
        helper.assertTrue(fast.getDeltaMovement().horizontalDistance() > entrySpeed * 0.7D,
                "flat out, a disarmed glider should carry its speed and bleed it slowly: "
                        + entrySpeed * 72.0D + " -> " + fast.getDeltaMovement().horizontalDistance() * 72.0D + " km/h");
        helper.assertTrue(fast.glideRun() > 1.0D, "and the glide should clock up the distance it flew");

        var free = helper.spawn(DroneEntities.MOLNIYA_DRONE.get(), 24, 9, 8);
        free.setYRot(0.0F);
        free.setXRot(0.0F);
        free.setArmed(true);
        free.setController(java.util.UUID.randomUUID());
        free.setDeltaMovement(0.0D, 0.0D, 1.3D);
        free.setArmed(false);

        for (int tick = 0; tick < 10; tick++) {
            free.setSticks(0.0F, 0.7F, 0.0F, 0.0F);
            free.applyControlInput(false, false, false, false, false, false);
        }
        double nosedUp = free.bodyPitch(1.0F);
        helper.assertTrue(nosedUp > 8.0F, "the pitch stick must raise the nose, reached " + nosedUp);
        for (int tick = 0; tick < 10; tick++) {
            free.setSticks(0.0F, 0.0F, 0.0F, 0.0F);
            free.applyControlInput(false, false, false, false, false, false);
        }
        helper.assertTrue(free.bodyPitch(1.0F) > nosedUp * 0.7D,
                "a disarmed glider must not level its own nose, pitch went " + nosedUp
                        + " -> " + free.bodyPitch(1.0F));

        for (int tick = 0; tick < 6; tick++) {
            free.setSticks(0.8F, 0.0F, 0.0F, 0.0F);
            free.applyControlInput(false, false, false, false, false, false);
        }
        double banked = free.bodyRoll(1.0F);
        helper.assertTrue(Math.abs(banked) > 10.0F,
                "the roll stick must still bank it on its elevons, reached " + banked);
        for (int tick = 0; tick < 10; tick++) {
            free.setSticks(0.0F, 0.0F, 0.0F, 0.0F);
            free.applyControlInput(false, false, false, false, false, false);
        }
        helper.assertTrue(Math.abs(free.bodyRoll(1.0F)) > Math.abs(banked) * 0.7D,
                "a disarmed glider must not level its own wings, bank went " + banked
                        + " -> " + free.bodyRoll(1.0F));

        double sinkInRange = glideSinkAt(helper, 100.0D);
        double sinkPastRange = glideSinkAt(helper, PLANE_GLIDE_RANGE_HARD + 20.0D);
        helper.assertTrue(sinkPastRange < -0.1D,
                "past its range a dead-stick glider must be coming down hard, sink was " + sinkPastRange);
        helper.assertTrue(sinkInRange > sinkPastRange + 0.15D,
                "within range it should still glide far gentler than past it: " + sinkInRange
                        + " vs " + sinkPastRange);
        helper.succeed();
    }

    /** The distance by which a dead-stick glide's wings have fully given out (mirrors the entity's own). */
    private static final double PLANE_GLIDE_RANGE_HARD = 280.0D;

    /** The worst sink a level, disarmed Molniya shows over a few ticks with this much glide already flown. */
    private static double glideSinkAt(GameTestHelper helper, double glideAlready) {
        var g = helper.spawn(DroneEntities.MOLNIYA_DRONE.get(), 24, 8, 24);
        g.setYRot(0.0F);
        g.setXRot(0.0F);
        g.setArmed(true);
        g.setController(java.util.UUID.randomUUID());
        g.setDeltaMovement(0.0D, 0.0D, 1.0D);
        g.setArmed(false);
        g.forceGlideRun(glideAlready);
        double worst = 0.0D;
        for (int tick = 0; tick < 6; tick++) {
            g.setSticks(0.0F, 0.0F, 0.0F, 0.0F);
            g.applyControlInput(false, false, false, false, false, false);
            worst = Math.min(worst, g.getDeltaMovement().y);
        }
        g.discard();
        return worst;
    }

    /**
     * The self-destruct: the warhead goes off where the drone is, in the air, and the airframe is gone — on
     * the interceptor and on the Molniya alike.
     */
    @GameTest(template = "empty9")
    public static void selfDestructBlowsTheDroneUpInTheAir(GameTestHelper helper) {
        InterceptorDroneEntity interceptor = rateModeInterceptor(helper, 0.0F);
        var molniya = molniyaInFlight(helper, 1.0D);
        helper.assertFalse(interceptor.isGrounded(), "precondition: the interceptor is in the air");

        interceptor.selfDestruct(null);
        molniya.selfDestruct(null);

        helper.assertTrue(interceptor.isRemoved(), "the interceptor should be gone after its self-destruct");
        helper.assertTrue(molniya.isRemoved(), "and so should the Molniya");
        helper.succeed();
    }

    /**
     * A Shahed laid on the launch rail: on the rails' top edge, nose up them, parked; and launched, it is set
     * to run up them rather than take a run along the ground.
     */
    @GameTest(template = "empty9")
    public static void aShahedIsLaidOnTheLaunchRailAndLaunchedUpIt(GameTestHelper helper) {
        var launcher = helper.spawn(dev.krutlexik.skyfront.entity.ModEntities.LAUNCHER.get(), 24, 2, 6);
        launcher.setYRot(0.0F);
        Vec3 foot = launcher.railPoint(0.0D);
        Vec3 head = launcher.railPoint(1.0D);
        helper.assertTrue(head.z > foot.z + 2.8D && head.y > foot.y + 0.5D,
                "the rails should rise about three blocks forward, foot " + foot + " head " + head);

        var shahed = new dev.krutlexik.skyfront.entity.ShahedEntity(
                dev.krutlexik.skyfront.entity.ModEntities.SHAHED.get(), helper.getLevel());
        Vec3 seat = launcher.railPoint(0.62D);
        shahed.seatOnRail(seat, 0.0F, dev.krutlexik.skyfront.entity.LauncherEntity.RAIL_PITCH, launcher.railAhead(0.62D));
        helper.assertTrue(shahed.isOnRail() && shahed.isParked(), "laid on the rail it is parked on it");
        helper.assertTrue(Math.abs(shahed.getXRot() + dev.krutlexik.skyfront.entity.LauncherEntity.RAIL_PITCH) < 1.0E-3F,
                "nose up the rails, pitch " + shahed.getXRot());

        shahed.launchFromGround(new int[] {(int) seat.x, (int) seat.z + 600}, (int) seat.y + 30, "low");
        helper.assertTrue(shahed.getLaunchState() == dev.krutlexik.skyfront.entity.ShahedEntity.STATE_RAIL,
                "launched from a rail it should run up it, not take a ground run");
        helper.succeed();
    }

    /**
     * A Shahed that blows up comes apart into its pieces, which fly clear and come to rest on the ground —
     * flown here as each client flies them, on their own.
     */
    @GameTest(template = "empty9", timeoutTicks = 200)
    public static void aShahedComesApartIntoPiecesThatSettle(GameTestHelper helper) {
        for (int x = 8; x <= 40; x++) {
            for (int z = 8; z <= 40; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
            }
        }
        Vec3 at = helper.absoluteVec(new Vec3(24.5D, 3.0D, 24.5D));
        var plan = dev.krutlexik.skyfront.entity.DebrisEntity.plan(helper.getLevel().getRandom(),
                dev.krutlexik.skyfront.entity.DebrisEntity.Airframe.SHAHED, at,
                dev.krutlexik.skyfront.entity.DebrisEntity.shahedAttitude(30.0F, 20.0F), new Vec3(0.0D, -1.0D, 1.0D), 1.0D, 6.0D);
        var pieces = dev.krutlexik.skyfront.entity.DebrisEntity.spawnLocally(helper.getLevel(), plan,
                helper.getLevel()::addFreshEntity);
        helper.assertTrue(pieces.size() == 7,
                "every piece of the airframe should be thrown, got " + pieces.size());
        helper.assertTrue(pieces.stream().map(dev.krutlexik.skyfront.entity.DebrisEntity::piece).distinct().count() == pieces.size(),
                "one of each piece");
        helper.succeedWhen(() -> {
            for (var piece : pieces) {
                helper.assertFalse(piece.isRemoved(), "a piece must lie there, not vanish at once");
                helper.assertTrue(piece.onGround() && piece.getDeltaMovement().lengthSqr() < 1.0E-4D,
                        piece.piece() + " should have come to rest on the ground");
                helper.assertTrue(piece.getY() > at.y - 3.0D, piece.piece() + " fell through the floor");
            }
            pieces.forEach(net.minecraft.world.entity.Entity::discard);
        });
    }

    /**
     * A Molniya that goes off comes apart into its own burnt pieces, all but the warhead — and the server
     * keeps none of them: they are each client's to throw and to see.
     */
    @GameTest(template = "empty9")
    public static void aMolniyaComesApartWhenItGoesOff(GameTestHelper helper) {
        var plan = dev.krutlexik.skyfront.entity.DebrisEntity.plan(helper.getLevel().getRandom(),
                dev.krutlexik.skyfront.entity.DebrisEntity.Airframe.MOLNIYA, helper.absoluteVec(new Vec3(24.5D, 10.0D, 24.5D)),
                new org.joml.Quaternionf(), Vec3.ZERO, 8.0D, 40.0D);
        helper.assertTrue(plan.size() == 18, "into its eighteen pieces, got " + plan.size());
        helper.assertTrue(plan.stream().allMatch(p -> p.piece().airframe == dev.krutlexik.skyfront.entity.DebrisEntity.Airframe.MOLNIYA),
                "and all of them its own");

        var molniya = molniyaInFlight(helper, 0.5D);
        molniya.selfDestruct(null);
        helper.assertTrue(molniya.isRemoved(), "the Molniya should be gone");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(dev.krutlexik.skyfront.entity.DebrisEntity.class,
                        molniya.getBoundingBox().inflate(8.0D)).isEmpty(),
                "and the server should keep no pieces of it: they are only for the clients to see");
        helper.succeed();
    }

    /** The pieces fly up to 40 blocks from a blast on the ground and up to 100 from one high up — and no further. */
    @GameTest(template = "empty9")
    public static void shahedDebrisFliesFartherFromAHighBlast(GameTestHelper helper) {
        double[][] cases = {{1.0D, dev.krutlexik.skyfront.entity.DebrisEntity.GROUND_RADIUS},
                {120.0D, dev.krutlexik.skyfront.entity.DebrisEntity.HIGH_RADIUS}};
        double[] farthest = new double[2];
        for (int c = 0; c < 2; c++) {
            double height = cases[c][0];
            double radius = cases[c][1];
            for (int round = 0; round < 6; round++) {
                Vec3 at = helper.absoluteVec(new Vec3(24.5D, 4.0D, 24.5D));
                var plan = dev.krutlexik.skyfront.entity.DebrisEntity.plan(helper.getLevel().getRandom(),
                        round % 2 == 0 ? dev.krutlexik.skyfront.entity.DebrisEntity.Airframe.SHAHED
                                : dev.krutlexik.skyfront.entity.DebrisEntity.Airframe.MOLNIYA,
                        at, new org.joml.Quaternionf(), Vec3.ZERO, height, radius);
                for (var piece : plan) {
                    double drop = piece.position().y - (at.y - height);
                    double lands = dev.krutlexik.skyfront.entity.DebrisEntity.flightDistance(piece.piece(), piece.velocity(), drop);
                    helper.assertTrue(lands <= radius + 1.0D,
                            piece.piece() + " would land " + lands + " blocks out, past " + radius);
                    farthest[c] = Math.max(farthest[c], lands);
                }
            }
        }
        helper.assertTrue(farthest[0] > 20.0D, "from the ground the pieces should fly well out, farthest " + farthest[0]);
        helper.assertTrue(farthest[1] > 60.0D, "from high up much farther, farthest " + farthest[1]);
        helper.succeed();
    }

    /** An FPV quad set down on its bricks, as the item puts it down, in the middle of the pad. */
    private static dev.krutlexik.skyfront.drone.entity.DroneStandEntity bricksUnder(
            GameTestHelper helper, DroneEntity[] quadOut) {
        helper.setBlock(24, 2, 24, Blocks.STONE);
        var stand = helper.spawn(DroneEntities.DRONE_STAND.get(), new Vec3(24.5, 3.0, 24.5));
        var fpv = helper.spawn(DroneEntities.FPV_DRONE.get(),
                new Vec3(24.5, 3.0 + dev.krutlexik.skyfront.drone.entity.FpvDroneEntity.STAND_HEIGHT, 24.5));
        fpv.setDeltaMovement(Vec3.ZERO);
        stand.holding(fpv);
        quadOut[0] = fpv;
        return stand;
    }

    /**
     * Set down on its bricks, the quad stands on them — it does not sink through to the ground — and counts
     * as on the ground there.
     */
    @GameTest(template = "empty9")
    public static void theFpvStandsOnItsBricks(GameTestHelper helper) {
        DroneEntity[] quad = new DroneEntity[1];
        var stand = bricksUnder(helper, quad);
        double onTop = helper.absoluteVec(new Vec3(0, 3.0 + dev.krutlexik.skyfront.drone.entity.FpvDroneEntity.STAND_HEIGHT, 0)).y;
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(Math.abs(quad[0].getY() - onTop) < 0.02D,
                    "the quad should be standing on the bricks, at " + onTop + ", was at " + quad[0].getY());
            helper.assertTrue(quad[0].isGrounded(), "and count as on the ground");
            helper.assertFalse(stand.isRemoved(), "with the bricks still there under it");
            helper.succeed();
        });
    }

    /** Once the quad has flown off its bricks they lie there five seconds, and then they are gone. */
    @GameTest(template = "empty9", timeoutTicks = 200)
    public static void theBricksGoFiveSecondsAfterTheQuadFliesOff(GameTestHelper helper) {
        DroneEntity[] quad = new DroneEntity[1];
        var stand = bricksUnder(helper, quad);
        helper.runAfterDelay(10, () -> {
            Vec3 away = helper.absoluteVec(new Vec3(30.5, 8.0, 30.5));
            quad[0].teleportTo(away.x, away.y, away.z);
        });
        helper.runAfterDelay(10 + dev.krutlexik.skyfront.drone.entity.DroneStandEntity.LINGER_TICKS / 2,
                () -> helper.assertFalse(stand.isRemoved(), "the bricks should still be there halfway through"));
        helper.runAfterDelay(10 + dev.krutlexik.skyfront.drone.entity.DroneStandEntity.LINGER_TICKS + 10, () -> {
            helper.assertTrue(stand.isRemoved(), "five seconds after the quad left, the bricks should be gone");
            helper.assertFalse(quad[0].isRemoved(), "and the quad itself flies on");
            helper.succeed();
        });
    }

    /** Pack the quad up off its bricks and the bricks go with it: there is nothing left for them to hold. */
    @GameTest(template = "empty9")
    public static void theBricksGoWithTheirQuad(GameTestHelper helper) {
        DroneEntity[] quad = new DroneEntity[1];
        var stand = bricksUnder(helper, quad);
        helper.runAfterDelay(60, () -> quad[0].discard());
        helper.runAfterDelay(63, () -> {
            helper.assertTrue(stand.isRemoved(), "with the quad gone, the bricks should go too");
            helper.succeed();
        });
    }

    /** Puts a drone down off its item onto the top of a bottom slab, the way a player does. */
    private static void placeOnSlab(GameTestHelper helper, net.minecraft.world.item.Item item, int x) {
        helper.setBlock(x, 2, 24, Blocks.STONE_SLAB);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        net.minecraft.core.BlockPos slab = helper.absolutePos(new net.minecraft.core.BlockPos(x, 2, 24));
        Vec3 top = Vec3.atBottomCenterOf(slab).add(0.0D, 0.5D, 0.0D);
        item.useOn(new net.minecraft.world.item.context.UseOnContext(player, InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(top, net.minecraft.core.Direction.UP, slab, false)));
    }

    /**
     * Put down on a slab, a drone stands on the slab's top — not half a block over it, at the foot of the
     * block space above — and the FPV quad's bricks stand there too, with the quad on them.
     */
    @GameTest(template = "empty9")
    public static void aDroneIsPutDownOnTheTopOfASlab(GameTestHelper helper) {
        placeOnSlab(helper, DroneItems.DRONE.get(), 20);
        placeOnSlab(helper, DroneItems.FPV_DRONE.get(), 28);
        double slabTop = helper.absoluteVec(new Vec3(0, 2.5, 0)).y;
        var all = helper.getLevel().getEntities((net.minecraft.world.entity.Entity) null,
                new net.minecraft.world.phys.AABB(helper.absolutePos(new net.minecraft.core.BlockPos(16, 0, 20))).inflate(16));
        DroneEntity recon = null, fpv = null;
        net.minecraft.world.entity.Entity stand = null;
        for (net.minecraft.world.entity.Entity e : all) {
            if (e.getType() == DroneEntities.DRONE.get()) recon = (DroneEntity) e;
            if (e.getType() == DroneEntities.FPV_DRONE.get()) fpv = (DroneEntity) e;
            if (e.getType() == DroneEntities.DRONE_STAND.get()) stand = e;
        }
        helper.assertTrue(recon != null && fpv != null && stand != null, "both drones and the bricks should be down");
        helper.assertTrue(Math.abs(recon.getY() - slabTop) < 1.0E-3D,
                "the drone should stand on the slab's top, " + slabTop + ", stands at " + recon.getY());
        helper.assertTrue(Math.abs(stand.getY() - slabTop) < 1.0E-3D,
                "the bricks likewise, stand at " + stand.getY());
        helper.assertTrue(Math.abs(fpv.getY() - slabTop - dev.krutlexik.skyfront.drone.entity.FpvDroneEntity.STAND_HEIGHT) < 1.0E-3D,
                "and the FPV quad on top of them, stands at " + fpv.getY());
        helper.succeed();
    }

    /**
     * Without a round the FPV quad is no weapon: what it flies into is not hurt, and when it comes apart it
     * does so without a blast that hurts anything near.
     */
    @GameTest(template = "empty9")
    public static void aBareFpvHurtsNothing(GameTestHelper helper) {
        helper.setBlock(24, 2, 24, Blocks.STONE);
        var pig = helper.spawn(net.minecraft.world.entity.EntityType.PIG, new Vec3(24.5, 3.0, 25.5));
        float health = pig.getHealth();
        var fpv = helper.spawn(DroneEntities.FPV_DRONE.get(), new Vec3(24.5, 3.0, 24.5));
        helper.assertFalse(fpv.hasAttachment(), "precondition: nothing hung on it");

        fpv.ram(pig, null);
        helper.assertTrue(pig.getHealth() == health, "rammed by a bare quad, the pig should be unhurt, has "
                + pig.getHealth() + " of " + health);

        fpv.selfDestruct(null);
        helper.assertTrue(fpv.isRemoved(), "the quad should be gone");
        helper.assertTrue(pig.getHealth() == health, "and coming apart right beside it should not hurt the pig, has "
                + pig.getHealth() + " of " + health);
        helper.succeed();
    }

    /**
     * The FPV quad flies as the interceptor does but carries an RPG round for its warhead: no screen over its
     * feed, a round hung on it by right-clicking, and nothing else will go on.
     */
    @GameTest(template = "empty9")
    public static void theFpvCarriesOnlyAnRpgRound(GameTestHelper helper) {
        var fpv = helper.spawn(DroneEntities.FPV_DRONE.get(), 24, 4, 24);
        helper.assertTrue(fpv instanceof InterceptorDroneEntity, "it flies as the interceptor does");
        helper.assertTrue(fpv.osdStyle() == DroneEntity.Osd.BETAFLIGHT_GPS, "and flies on Betaflight with a GPS");
        helper.assertTrue(fpv.storesFollowAttitude(), "and its round turns with the airframe");
        helper.assertTrue(fpv.packedItem().is(DroneItems.FPV_DRONE.get()), "packing it gives the FPV back");

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.APPLE));
        fpv.interact(player, InteractionHand.MAIN_HAND);
        helper.assertFalse(fpv.hasAttachment(), "anything that is not an RPG round stays in the hand");

        for (String round : new String[] {"rpg_rocket_standard", "rpg_rocket_tbg", "mortar_shell"}) {
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("superbwarfare", round));
            if (item.isEmpty()) {
                continue;
            }
            var drone = helper.spawn(DroneEntities.FPV_DRONE.get(), 24, 4, 24);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item.get()));
            drone.interact(player, InteractionHand.MAIN_HAND);
            helper.assertTrue(drone.hasAttachment() && drone.getAttachment().is(item.get()),
                    round + " should hang on the FPV");
        }
        helper.succeed();
    }

    /** Neither the Shahed nor the Molniya can be set down on the ground: only on a launch rail. */
    @GameTest(template = "empty9")
    public static void theShahedAndTheMolniyaGoOnlyOnALaunchRail(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos floor = helper.absolutePos(new BlockPos(24, 1, 24));
        for (ItemStack stack : new ItemStack[] {
                new ItemStack(dev.krutlexik.skyfront.item.ModItems.SHAHED_136.get()),
                new ItemStack(DroneItems.MOLNIYA_DRONE.get())}) {
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(floor).add(0.0D, 0.5D, 0.0D),
                    net.minecraft.core.Direction.UP, floor, false);
            net.minecraft.world.InteractionResult result = stack.useOn(new net.minecraft.world.item.context.UseOnContext(
                    player, InteractionHand.MAIN_HAND, hit));
            helper.assertFalse(result.consumesAction(), stack + " must not be set down on the ground");
            helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1,
                    "and stays in the hand");
        }
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Entity.class,
                new net.minecraft.world.phys.AABB(floor).inflate(4.0D),
                e -> e instanceof dev.krutlexik.skyfront.entity.ShahedEntity || e instanceof DroneEntity).isEmpty(),
                "nothing should have been put in the world");
        helper.succeed();
    }

    /** Crouch-clicked, the rail gives back the drone on it first, and itself on the next click. */
    @GameTest(template = "empty9")
    public static void aLaunchRailPacksUpWithItsDrone(GameTestHelper helper) {
        var launcher = helper.spawn(dev.krutlexik.skyfront.entity.ModEntities.LAUNCHER.get(), 24, 2, 6);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DroneItems.MOLNIYA_DRONE.get()));
        launcher.interact(player, InteractionHand.MAIN_HAND);
        var molniya = helper.getLevel().getEntitiesOfClass(InterceptorDroneEntity.class,
                launcher.getBoundingBox().inflate(4.0D), InterceptorDroneEntity::isRailSeated);
        helper.assertTrue(molniya.size() == 1, "precondition: the Molniya should be on the rail");

        player.setShiftKeyDown(true);
        launcher.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(molniya.get(0).isRemoved(), "the first click takes the Molniya off");
        helper.assertTrue(player.getInventory().countItem(DroneItems.MOLNIYA_DRONE.get()) == 1,
                "into the inventory");
        helper.assertFalse(launcher.isRemoved(), "and leaves the rail standing");

        launcher.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(launcher.isRemoved(), "the second click takes the rail");
        helper.assertTrue(player.getInventory().countItem(dev.krutlexik.skyfront.item.ModItems.LAUNCHER.get()) == 1,
                "into the inventory too");
        helper.succeed();
    }

    /** Broken, the rail drops as its item, and the drone on it falls off as its own. */
    @GameTest(template = "empty9")
    public static void aBrokenLaunchRailDropsItsDroneAsAnItem(GameTestHelper helper) {
        var launcher = helper.spawn(dev.krutlexik.skyfront.entity.ModEntities.LAUNCHER.get(), 24, 2, 6);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DroneItems.MOLNIYA_DRONE.get()));
        launcher.interact(player, InteractionHand.MAIN_HAND);
        var molniya = helper.getLevel().getEntitiesOfClass(InterceptorDroneEntity.class,
                launcher.getBoundingBox().inflate(4.0D), InterceptorDroneEntity::isRailSeated);
        helper.assertTrue(molniya.size() == 1, "precondition: the Molniya should be on the rail");

        launcher.hurt(helper.getLevel().damageSources().generic(), 20.0F);
        helper.assertTrue(launcher.isRemoved() && molniya.get(0).isRemoved(), "both should leave the world");
        var drops = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                launcher.getBoundingBox().inflate(6.0D));
        helper.assertTrue(drops.stream().anyMatch(d -> d.getItem().is(DroneItems.MOLNIYA_DRONE.get())),
                "the Molniya should drop as its item");
        helper.assertTrue(drops.stream().anyMatch(d -> d.getItem().is(dev.krutlexik.skyfront.item.ModItems.LAUNCHER.get())),
                "and the rail as its own");
        helper.succeed();
    }

    /**
     * A Molniya on the launch rail waits there — armed or not — until its pilot opens the throttle, then
     * leaves the rails at flying speed along them, nose up.
     */
    @GameTest(template = "empty9")
    public static void aMolniyaLeavesTheLaunchRailOnTheThrottle(GameTestHelper helper) {
        var launcher = helper.spawn(dev.krutlexik.skyfront.entity.ModEntities.LAUNCHER.get(), 24, 2, 8);
        launcher.setYRot(0.0F);
        var molniya = helper.spawn(DroneEntities.MOLNIYA_DRONE.get(), 24, 4, 8);
        molniya.seatOnRail(launcher.railPoint(0.5D), 0.0F, dev.krutlexik.skyfront.entity.LauncherEntity.RAIL_PITCH);
        molniya.setArmed(true);
        molniya.setController(java.util.UUID.randomUUID());
        Vec3 seat = molniya.position();

        for (int tick = 0; tick < 20; tick++) {
            molniya.setSticks(0.0F, 0.0F, 0.0F, 0.0F);
            molniya.applyControlInput(false, false, false, false, false, false);
        }
        helper.assertTrue(molniya.isRailSeated() && molniya.position().distanceTo(seat) < 1.0E-6D,
                "with the throttle shut it should wait on the rail, moved " + molniya.position().distanceTo(seat));
        helper.assertTrue(Math.abs(molniya.bodyPitch(1.0F) - dev.krutlexik.skyfront.entity.LauncherEntity.RAIL_PITCH) < 1.0F,
                "lying at the rails' angle, pitch " + molniya.bodyPitch(1.0F));

        molniya.setSticks(0.0F, 0.0F, 0.0F, 1.0F);
        molniya.applyControlInput(false, false, false, false, false, false);
        Vec3 v = molniya.getDeltaMovement();
        helper.assertFalse(molniya.isRailSeated(), "on the throttle it should leave the rail");
        helper.assertTrue(v.length() > molniya.trimSpeed() * 0.9D && v.y > 0.0D && v.z > 0.0D,
                "leaving at flying speed, forward and up the rails, velocity " + v + " grounded=" + molniya.isGrounded()
                        + " dead=" + molniya.motorsDead() + " wrecked=" + molniya.isWrecked() + " removed=" + molniya.isRemoved()
                        + " y=" + molniya.getY());

        var second = helper.spawn(DroneEntities.MOLNIYA_DRONE.get(), 24, 4, 8);
        second.seatOnRail(launcher.railPoint(0.5D), 0.0F, dev.krutlexik.skyfront.entity.LauncherEntity.RAIL_PITCH);
        launcher.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);
        helper.assertTrue(second.isRailSeated(), "precondition: the rail did not know it was carrying this one");
        helper.succeed();
    }

    /** A Molniya up to speed, armed, on the sticks, at the given airspeed down its nose (+Z at yaw 0). */
    private static dev.krutlexik.skyfront.drone.entity.MolniyaDroneEntity molniyaInFlight(
            GameTestHelper helper, double speed) {
        var molniya = helper.spawn(DroneEntities.MOLNIYA_DRONE.get(), 24, 8, 24);
        molniya.setYRot(0.0F);
        molniya.setXRot(0.0F);
        molniya.setArmed(true);
        molniya.setController(java.util.UUID.randomUUID());
        molniya.setDeltaMovement(0.0D, 0.0D, speed);
        return molniya;
    }
}
