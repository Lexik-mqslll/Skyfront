package dev.krutlexik.skyfront.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * One burnt piece of a drone that has blown up — a Shahed's wing or motor, a Molniya's spar or tail fin —
 * thrown clear by the blast, up to 40 blocks from one on the ground and 100 from one high up, tumbling,
 * bouncing, and coming to rest on the ground, where it lies for three minutes before it is gone.
 */
public class DebrisEntity extends Entity {
    /**
     * How each airframe's model is drawn on its entity, which is where its pieces start from: the scale it is
     * drawn at, and the point of the model (in its sixteenths) that is kept over the entity's position — the
     * middle of its bounds across, and its lowest point down — or the model's own origin for one drawn
     * straight off it.
     */
    public enum Airframe {
        /** GeckoLib draws the Shahed off its model's origin, at full size. */
        SHAHED(1.0F, 0.0F, 0.0F, 0.0F),
        /** The Molniya's glb, centred on its bounds and stood on its lowest point (see DroneRenderer). */
        MOLNIYA(1.4F, 0.0F, 0.615F, -0.8F);

        public final float scale;
        final float centreX;
        final float centreZ;
        final float minY;

        Airframe(float scale, float centreX, float centreZ, float minY) {
            this.scale = scale;
            this.centreX = centreX;
            this.centreZ = centreZ;
            this.minY = minY;
        }

        /** Where a point of the model, in its sixteenths, is in the world. */
        Vec3 world(Vec3 position, Quaternionfc attitude, Vector3f point) {
            Vector3f p = new Vector3f(point).sub(this.centreX, 0.0F, this.centreZ).mul(this.scale / 16.0F);
            attitude.transform(p);
            return position.add(p.x, p.y - this.minY * this.scale / 16.0D, p.z);
        }
    }

    /** How a piece comes to rest: as it sat, or knocked over across its thin way (about X or about Z). */
    public enum Lie { NONE, X, Z }

    /**
     * What each piece is, where it sat on its airframe and how big it is — the cutter's figures, in the
     * model's sixteenths — how it comes to rest, and the share of the blast's full throw it can be carried
     * (the heavy fuselage and motor go less far).
     */
    public enum Piece {
        SHAHED_MOTOR(Airframe.SHAHED, "shahed_debris_motor", 0.00F, 1.90F, -28.50F, 6.00F, 4.80F, 9.00F, Lie.NONE, 0.50F),
        SHAHED_WINGS_LEFT(Airframe.SHAHED, "shahed_debris_wings_left", -11.61F, -2.22F, -9.92F, 23.23F, 10.23F, 38.16F, Lie.NONE, 0.80F),
        SHAHED_WINGS_RIGHT(Airframe.SHAHED, "shahed_debris_wings_right", 11.61F, -2.22F, -9.92F, 23.23F, 10.23F, 38.16F, Lie.NONE, 0.80F),
        SHAHED_MAIN(Airframe.SHAHED, "shahed_debris_main", 0.00F, 0.00F, 0.50F, 6.00F, 6.00F, 49.00F, Lie.NONE, 0.50F),
        SHAHED_ELERONS_LEFT(Airframe.SHAHED, "shahed_debris_elerons_left", -12.00F, 1.90F, -25.50F, 16.00F, 2.20F, 5.00F, Lie.NONE, 1.00F),
        SHAHED_ELERONS_RIGHT(Airframe.SHAHED, "shahed_debris_elerons_right", 12.00F, 1.90F, -25.50F, 16.00F, 2.20F, 5.00F, Lie.NONE, 1.00F),
        SHAHED_PROP(Airframe.SHAHED, "shahed_debris_prop", 0.00F, 2.50F, -32.00F, 18.00F, 1.20F, 0.00F, Lie.X, 1.00F),
        MOLNIYA_STICKS_1(Airframe.MOLNIYA, "molniya_debris_sticks_1", -4.50F, 0.00F, -2.19F, 1.00F, 1.00F, 42.02F, Lie.NONE, 1.00F),
        MOLNIYA_STICKS_2(Airframe.MOLNIYA, "molniya_debris_sticks_2", 4.50F, 0.00F, -2.19F, 1.00F, 1.00F, 42.02F, Lie.NONE, 1.00F),
        MOLNIYA_MOTOR_PROP(Airframe.MOLNIYA, "molniya_debris_motor_prop", 0.00F, -0.10F, -24.70F, 12.40F, 1.20F, 2.60F, Lie.NONE, 0.50F),
        MOLNIYA_GUIDES_1(Airframe.MOLNIYA, "molniya_debris_guides_1", 0.00F, -0.30F, -23.10F, 11.00F, 1.60F, 0.60F, Lie.X, 1.00F),
        MOLNIYA_GUIDES_2(Airframe.MOLNIYA, "molniya_debris_guides_2", 0.00F, -0.30F, -11.70F, 11.00F, 1.60F, 0.60F, Lie.X, 1.00F),
        MOLNIYA_GUIDES_3(Airframe.MOLNIYA, "molniya_debris_guides_3", 0.00F, -0.30F, -0.20F, 11.00F, 1.60F, 0.60F, Lie.X, 1.00F),
        MOLNIYA_GUIDES_4(Airframe.MOLNIYA, "molniya_debris_guides_4", 0.00F, -0.30F, 17.30F, 11.00F, 1.60F, 0.60F, Lie.X, 1.00F),
        MOLNIYA_WING0(Airframe.MOLNIYA, "molniya_debris_wing0", 0.00F, -0.30F, 22.10F, 26.00F, 1.60F, 8.20F, Lie.NONE, 0.80F),
        MOLNIYA_MAIN(Airframe.MOLNIYA, "molniya_debris_main", 0.00F, -0.30F, -5.95F, 13.00F, 1.60F, 10.10F, Lie.NONE, 0.50F),
        MOLNIYA_WING1(Airframe.MOLNIYA, "molniya_debris_wing1", -13.90F, -0.30F, -5.95F, 20.20F, 1.60F, 10.10F, Lie.NONE, 0.80F),
        MOLNIYA_WING4(Airframe.MOLNIYA, "molniya_debris_wing4", 0.00F, -0.15F, 23.00F, 1.00F, 10.02F, 8.11F, Lie.Z, 0.80F),
        MOLNIYA_SERVO(Airframe.MOLNIYA, "molniya_debris_servo", -1.00F, 0.80F, 24.12F, 1.00F, 5.83F, 6.23F, Lie.Z, 1.00F),
        MOLNIYA_ELERONS_MIDDLE(Airframe.MOLNIYA, "molniya_debris_elerons_middle", 0.00F, 0.20F, 25.60F, 23.60F, 0.60F, 1.20F, Lie.NONE, 1.00F),
        MOLNIYA_ELERONS_LEFT(Airframe.MOLNIYA, "molniya_debris_elerons_left", -14.40F, -0.10F, -1.95F, 15.20F, 0.80F, 2.10F, Lie.NONE, 1.00F),
        MOLNIYA_ELERONS_RIGHT(Airframe.MOLNIYA, "molniya_debris_elerons_right", 14.40F, -0.10F, -1.95F, 15.20F, 0.80F, 2.10F, Lie.NONE, 1.00F),
        MOLNIYA_BATTERY_1(Airframe.MOLNIYA, "molniya_debris_battery_1", -1.30F, 1.10F, -5.70F, 2.20F, 1.90F, 4.60F, Lie.NONE, 0.50F),
        MOLNIYA_BATTERY_2(Airframe.MOLNIYA, "molniya_debris_battery_2", 1.30F, 1.10F, -5.70F, 2.20F, 1.90F, 4.60F, Lie.NONE, 0.50F),
        MOLNIYA_WING3(Airframe.MOLNIYA, "molniya_debris_wing3", 13.90F, -0.30F, -5.95F, 20.20F, 1.60F, 10.10F, Lie.NONE, 0.80F);

        public final Airframe airframe;
        public final String model;
        /** Its bottom-centre on the airframe, in the model's sixteenths. */
        public final Vector3f base;
        /** Its size, in the model's sixteenths. */
        public final Vector3f size;
        public final Lie lie;
        public final float reach;

        Piece(Airframe airframe, String model, float x, float y, float z, float sx, float sy, float sz, Lie lie,
              float reach) {
            this.airframe = airframe;
            this.model = model;
            this.base = new Vector3f(x, y, z);
            this.size = new Vector3f(sx, sy, sz);
            this.lie = lie;
            this.reach = reach;
        }

        /** Its height in blocks as it is drawn, standing as it sat on the airframe. */
        public float visualHeight() {
            return this.size.y * this.airframe.scale / 16.0F;
        }

        /**
         * The height, in blocks, of the point it tumbles about and comes to rest on: the middle of it, and
         * for one knocked over, the middle of its thin way, which is then its height.
         */
        public float pivotHeight() {
            float thick = switch (this.lie) {
                case NONE -> this.size.y;
                case X -> this.size.z;
                case Z -> this.size.x;
            };
            return thick * this.airframe.scale / 32.0F;
        }

        EntityDimensions dimensions() {
            float s = this.airframe.scale / 16.0F;
            float width = Mth.clamp(Math.max(this.size.x, this.size.z) * s, 0.3F, 1.2F);
            float height = Mth.clamp(pivotHeight() * 2.0F, 0.1F, 0.5F);
            return EntityDimensions.scalable(width, height);
        }

        public static Piece of(int index) {
            Piece[] all = values();
            return all[Mth.clamp(index, 0, all.length - 1)];
        }

        static List<Piece> of(Airframe airframe) {
            List<Piece> out = new ArrayList<>();
            for (Piece piece : values()) {
                if (piece.airframe == airframe) {
                    out.add(piece);
                }
            }
            return out;
        }
    }

    /** How long a piece lies there: three minutes. */
    public static final int LIFETIME = 3 * 60 * 20;

    private static final EntityDataAccessor<Integer> DATA_PIECE =
            SynchedEntityData.defineId(DebrisEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Quaternionf> DATA_ATTITUDE =
            SynchedEntityData.defineId(DebrisEntity.class, EntityDataSerializers.QUATERNION);
    /** The tumble the blast gave it: an axis, its length the turn in radians per tick. */
    private static final EntityDataAccessor<Vector3f> DATA_SPIN =
            SynchedEntityData.defineId(DebrisEntity.class, EntityDataSerializers.VECTOR3);
    /** In the air, sliding along the ground, or at rest: what the client's tumble does with it. */
    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(DebrisEntity.class, EntityDataSerializers.INT);
    /** How many times it has struck the ground; each strike knocks its tumble about. */
    private static final EntityDataAccessor<Integer> DATA_BOUNCES =
            SynchedEntityData.defineId(DebrisEntity.class, EntityDataSerializers.INT);

    private static final int IN_THE_AIR = 0;
    private static final int SLIDING = 1;
    private static final int AT_REST = 2;

    private static final double GRAVITY = 0.04D;
    /** Landing faster than this it bounces, keeping a share of the speed it came down at. */
    private static final double BOUNCE_SPEED = 0.15D;
    /** What it keeps of its speed each tick it slides along the ground. */
    private static final double GROUND_FRICTION = 0.8D;

    private int age;
    /** A piece this side flies itself, which nobody else knows of: never synced, never saved. */
    private boolean local;

    private final Quaternionf attitude = new Quaternionf();
    private final Quaternionf attitudeO = new Quaternionf();
    private final Vector3f spinAxis = new Vector3f();
    private float spinRate;
    private boolean attitudeReady;
    private int bouncesSeen;
    /** How far it has got with falling over onto its rest, 0 until it lands. */
    private float settling;

    private double targetX;
    private double targetY;
    private double targetZ;
    private int targetSteps;

    public DebrisEntity(EntityType<? extends DebrisEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_PIECE, 0);
        builder.define(DATA_ATTITUDE, new Quaternionf());
        builder.define(DATA_SPIN, new Vector3f());
        builder.define(DATA_STATE, IN_THE_AIR);
        builder.define(DATA_BOUNCES, 0);
    }

    public Piece piece() {
        return Piece.of(this.entityData.get(DATA_PIECE));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_PIECE.equals(key)) {
            refreshDimensions();
        }
        if (DATA_ATTITUDE.equals(key)) {
            this.attitudeReady = false;
        }
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return piece().dimensions();
    }

    /**
     * How a piece meets the air, by how heavy it is for its size (its reach, which already says that: 0.5 the
     * heavy fuselage and motor, 1 the light flat elevons and blades).
     */
    private static float lightness(Piece piece) {
        return Mth.clamp((piece.reach - 0.5F) / 0.5F, 0.0F, 1.0F);
    }

    /** What it keeps of its speed across the ground each tick in the air. */
    static double dragAcross(Piece piece) {
        return 1.0D - Mth.lerp(lightness(piece), 0.012D, 0.03D);
    }

    /** What it keeps of its fall each tick: a flat piece falls far slower than a lump. */
    static double dragDown(Piece piece) {
        return 1.0D - Mth.lerp(lightness(piece), 0.02D, 0.065D);
    }

    /** The share of its fall it bounces back up with. */
    private static double bounceKeep(Piece piece) {
        return Mth.lerp(lightness(piece), 0.18D, 0.32D);
    }

    /** How far the pieces fly from a blast at or near the ground, and from one high up, in blocks. */
    public static final double GROUND_RADIUS = 40.0D;
    public static final double HIGH_RADIUS = 100.0D;
    /** Below this height the blast counts as on the ground; from the other one up, as high. */
    private static final double LOW_BLAST = 8.0D;
    private static final double HIGH_BLAST = 60.0D;
    /** The share of the radius a piece is aimed at, leaving room for its bounce and slide after landing. */
    private static final double BOUNCE_ALLOWANCE = 0.8D;
    /** Longest flight the launch speeds are worked out for, in ticks. */
    private static final int MAX_FLIGHT = 600;

    /** A Shahed's attitude from how its renderer points it: its heading, and pitch positive nose-down. */
    public static Quaternionf shahedAttitude(float yawDeg, float pitchDeg) {
        return new Quaternionf().rotationY(yawDeg * Mth.DEG_TO_RAD).rotateX(pitchDeg * Mth.DEG_TO_RAD);
    }

    /** Breaks an airframe into its pieces where it blew up. */
    public static void burst(ServerLevel level, Airframe airframe, Vec3 position, Quaternionfc attitude, Vec3 motion) {
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(position.x), Mth.floor(position.z));
        double height = Math.max(0.0D, position.y - ground);
        double high = Mth.clamp((height - LOW_BLAST) / (HIGH_BLAST - LOW_BLAST), 0.0D, 1.0D);
        burst(level, airframe, position, attitude, motion, height, Mth.lerp(high, GROUND_RADIUS, HIGH_RADIUS));
    }

    /**
     * One piece as the blast throws it: where it starts, how fast it goes, the way it faces, and the tumble
     * it is given.
     */
    public record Spawn(Piece piece, Vec3 position, Vec3 velocity, float yRot, Quaternionf attitude, Vector3f spin) {
    }

    /** The same, with the blast's height over the ground and the farthest a piece may land given outright. */
    public static void burst(ServerLevel level, Airframe airframe, Vec3 position, Quaternionfc attitude, Vec3 motion,
                             double height, double radius) {
        List<Spawn> pieces = plan(level.getRandom(), airframe, position, attitude, motion, height, radius);
        var packet = new dev.krutlexik.skyfront.network.DebrisBurstPacket(pieces);
        for (net.minecraft.server.level.ServerPlayer player : level.players()) {
            if (player.connection.hasChannel(dev.krutlexik.skyfront.network.DebrisBurstPacket.TYPE)) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, packet);
            }
        }
    }

    /** How the blast throws each piece. */
    public static List<Spawn> plan(RandomSource random, Airframe airframe, Vec3 position, Quaternionfc attitude,
                                   Vec3 motion, double height, double radius) {
        List<Spawn> out = new ArrayList<>();
        double high = Mth.clamp((height - LOW_BLAST) / (HIGH_BLAST - LOW_BLAST), 0.0D, 1.0D);
        double groundY = position.y - height;
        Vec3 heading = motion.horizontalDistanceSqr() > 1.0E-4D
                ? new Vec3(motion.x, 0.0D, motion.z).normalize() : Vec3.ZERO;
        Vec3 blast = airframe.world(position, attitude, new Vector3f(airframe.centreX, 0.0F, airframe.centreZ));
        for (Piece piece : Piece.of(airframe)) {
            Vector3f middleOfPiece = new Vector3f(piece.base).add(0.0F, piece.size.y / 2.0F, 0.0F);
            Vec3 middle = airframe.world(position, attitude, middleOfPiece);
            double startY = Math.max(middle.y - piece.pivotHeight(), groundY) + 0.3D;
            Vector3f nose = attitude.transform(new Vector3f(0.0F, 0.0F, 1.0F));
            float yRot = (float) Math.toDegrees(Math.atan2(nose.x, nose.z));

            Vec3 dir = new Vec3(middle.x - blast.x + random.nextGaussian() * 0.4D, 0.0D,
                    middle.z - blast.z + random.nextGaussian() * 0.4D);
            if (dir.lengthSqr() < 1.0E-4D) {
                dir = new Vec3(random.nextGaussian(), 0.0D, random.nextGaussian());
            }
            dir = dir.normalize().add(heading.scale(0.6D));
            if (dir.lengthSqr() < 1.0E-4D) {
                dir = new Vec3(1.0D, 0.0D, 0.0D);
            }
            dir = dir.normalize();

            double reach = radius * BOUNCE_ALLOWANCE * piece.reach * (0.35D + 0.65D * random.nextDouble());
            double up = Mth.lerp(high, 0.55D + random.nextDouble() * 0.45D, 0.05D + random.nextDouble() * 0.3D);
            int flight = flightTicks(piece, up, Math.max(0.0D, startY - groundY));
            double drag = dragAcross(piece);
            double speed = Math.min(6.0D, reach * (1.0D - drag) / (1.0D - Math.pow(drag, flight)));

            Vector3f axis = new Vector3f((float) random.nextGaussian(), (float) random.nextGaussian(),
                    (float) random.nextGaussian());
            if (axis.lengthSquared() < 1.0E-4F) {
                axis.set(1.0F, 0.0F, 0.0F);
            }
            float rate = Mth.lerp(lightness(piece), 6.0F, 16.0F) * (0.7F + random.nextFloat() * 0.6F) * Mth.DEG_TO_RAD;
            out.add(new Spawn(piece, new Vec3(middle.x, startY, middle.z), new Vec3(dir.x * speed, up, dir.z * speed),
                    yRot, new Quaternionf(attitude), axis.normalize().mul(rate)));
        }
        return out;
    }

    /** Ids for pieces that exist on one side only, counting down well clear of the server's own. */
    private static int nextLocalId = -1_000_000;

    /** Puts the pieces into a level to fly there on their own - each client's copy, never sent anywhere or saved. */
    public static List<DebrisEntity> spawnLocally(Level level, List<Spawn> pieces,
                                                  java.util.function.Consumer<DebrisEntity> add) {
        List<DebrisEntity> made = new ArrayList<>();
        for (Spawn spawn : pieces) {
            DebrisEntity debris = ModEntities.DEBRIS.get().create(level);
            if (debris == null) {
                continue;
            }
            debris.local = true;
            debris.setId(nextLocalId--);
            debris.setInvisible(true);
            debris.entityData.set(DATA_PIECE, spawn.piece().ordinal());
            debris.refreshDimensions();
            debris.setPos(spawn.position());
            debris.setYRot(spawn.yRot());
            debris.setDeltaMovement(spawn.velocity());
            debris.entityData.set(DATA_ATTITUDE, new Quaternionf(spawn.attitude()));
            debris.entityData.set(DATA_SPIN, new Vector3f(spawn.spin()));
            add.accept(debris);
            made.add(debris);
        }
        return made;
    }

    /**
     * Ticks a piece launched upward at {@code up} flies before it has come down {@code drop} below where it
     * started.
     */
    public static int flightTicks(Piece piece, double up, double drop) {
        double y = 0.0D;
        double vy = up;
        double drag = dragDown(piece);
        for (int tick = 1; tick <= MAX_FLIGHT; tick++) {
            vy -= GRAVITY;
            y += vy;
            vy *= drag;
            if (y <= -drop) {
                return tick;
            }
        }
        return MAX_FLIGHT;
    }

    /** How far across the ground a piece launched at {@code velocity} lands, {@code drop} below where it started. */
    public static double flightDistance(Piece piece, Vec3 velocity, double drop) {
        int ticks = flightTicks(piece, velocity.y, drop);
        double drag = dragAcross(piece);
        return velocity.horizontalDistance() * (1.0D - Math.pow(drag, ticks)) / (1.0D - drag);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && !this.local) {
            followServer();
            tumble();
            return;
        }
        fly();
        if (level().isClientSide && !isRemoved()) {
            tumble();
        }
    }

    /** The fall itself: run by whichever side owns the piece - the client, for every new one. */
    private void fly() {
        if (++this.age >= LIFETIME) {
            discard();
            return;
        }

        if (!level().noCollision(this, getBoundingBox().deflate(1.0E-7D))) {
            moveTowardsClosestSpace(getX(), (getBoundingBox().minY + getBoundingBox().maxY) / 2.0D, getZ());
        }

        Piece piece = piece();
        Vec3 before = getDeltaMovement().add(0.0D, -GRAVITY, 0.0D);
        if (!onGround()) {
            before = before.add(flutter(piece, before));
        }
        move(MoverType.SELF, before);
        Vec3 after;
        int state;
        if (onGround()) {
            if (before.y < -BOUNCE_SPEED) {
                after = new Vec3(before.x * 0.65D, -before.y * bounceKeep(piece), before.z * 0.65D);
                this.entityData.set(DATA_BOUNCES, this.entityData.get(DATA_BOUNCES) + 1);
                state = IN_THE_AIR;
            } else {
                after = new Vec3(before.x * GROUND_FRICTION, 0.0D, before.z * GROUND_FRICTION);
                state = after.horizontalDistanceSqr() < 4.0E-4D ? AT_REST : SLIDING;
            }
        } else {
            after = new Vec3(before.x * dragAcross(piece), before.y * dragDown(piece), before.z * dragAcross(piece));
            state = IN_THE_AIR;
        }
        if (state == AT_REST) {
            after = Vec3.ZERO;
        }
        setDeltaMovement(after);
        if (this.entityData.get(DATA_STATE) != state) {
            this.entityData.set(DATA_STATE, state);
        }
    }

    /**
     * A light, flat piece does not fall straight: it catches the air and sideslips one way and then the other
     * as it tips.
     */
    private Vec3 flutter(Piece piece, Vec3 velocity) {
        float light = lightness(piece);
        if (light <= 0.0F || velocity.horizontalDistanceSqr() < 1.0E-6D) {
            return Vec3.ZERO;
        }
        Vec3 across = new Vec3(-velocity.z, 0.0D, velocity.x).normalize();
        double phase = getId() * 1.618D;
        double swing = Math.sin(this.age * (0.25D + Math.floorMod(getId(), 7) * 0.03D) + phase);
        return across.scale(0.018D * light * swing);
    }

    /** Where the server says it is. */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
        this.targetSteps = 3;
        setRot(yRot, xRot);
    }

    private void followServer() {
        if (this.targetSteps > 0) {
            double share = 1.0D / this.targetSteps;
            setPos(getX() + (this.targetX - getX()) * share,
                    getY() + (this.targetY - getY()) * share,
                    getZ() + (this.targetZ - getZ()) * share);
            this.targetSteps--;
        } else if (this.entityData.get(DATA_STATE) != AT_REST) {
            Vec3 velocity = getDeltaMovement();
            setPos(getX() + velocity.x, getY() + velocity.y, getZ() + velocity.z);
        }
    }

    /** Client side: how the piece turns. */
    private void tumble() {
        if (!this.attitudeReady) {
            this.attitude.set(this.entityData.get(DATA_ATTITUDE));
            this.attitudeO.set(this.attitude);
            Vector3f spin = this.entityData.get(DATA_SPIN);
            this.spinRate = spin.length();
            if (this.spinRate > 1.0E-6F) {
                this.spinAxis.set(spin).div(this.spinRate);
            } else {
                this.spinAxis.set(1.0F, 0.0F, 0.0F);
            }
            this.bouncesSeen = this.entityData.get(DATA_BOUNCES);
            this.attitudeReady = true;
        }
        this.attitudeO.set(this.attitude);

        int bounces = this.entityData.get(DATA_BOUNCES);
        if (bounces != this.bouncesSeen) {
            this.bouncesSeen = bounces;
            RandomSource knock = RandomSource.create(getId() * 31L + bounces * 1013L);
            this.spinAxis.add((float) knock.nextGaussian() * 0.8F, (float) knock.nextGaussian() * 0.8F,
                    (float) knock.nextGaussian() * 0.8F);
            if (this.spinAxis.lengthSquared() < 1.0E-4F) {
                this.spinAxis.set(0.0F, 0.0F, 1.0F);
            }
            this.spinAxis.normalize();
            this.spinRate *= 0.5F;
        }

        int state = this.entityData.get(DATA_STATE);
        if (state == IN_THE_AIR) {
            this.settling = 0.0F;
            turn(this.spinAxis, this.spinRate);
            this.spinRate *= 0.996F;
            return;
        }

        Vec3 velocity = getDeltaMovement();
        if (state == SLIDING && velocity.horizontalDistanceSqr() > 1.0E-6D) {
            Vector3f axis = new Vector3f((float) velocity.z, 0.0F, (float) -velocity.x).normalize();
            float radius = Math.max(0.05F, piece().pivotHeight());
            turn(axis, (float) velocity.horizontalDistance() / radius * 0.35F);
        }
        this.settling = Math.min(1.0F, this.settling + 0.06F);
        this.attitude.slerp(nearestRest(), 0.04F + this.settling * this.settling * 0.4F);
    }

    private void turn(Vector3f axis, float angle) {
        if (angle > 1.0E-5F) {
            new Quaternionf().fromAxisAngleRad(axis, angle).mul(this.attitude, this.attitude);
        }
    }

    /** The resting pose closest to how it is lying now: face up or face down, or on either side. */
    private Quaternionf nearestRest() {
        Vector3f forward = this.attitude.transform(new Vector3f(0.0F, 0.0F, 1.0F));
        float heading;
        if (forward.x * forward.x + forward.z * forward.z > 0.1F) {
            heading = (float) Math.atan2(forward.x, forward.z);
        } else {
            Vector3f side = this.attitude.transform(new Vector3f(1.0F, 0.0F, 0.0F));
            heading = (float) Math.atan2(side.x, side.z) - Mth.HALF_PI;
        }
        Quaternionf best = null;
        float bestDot = -1.0F;
        for (Quaternionf pose : restPoses(piece().lie)) {
            Quaternionf rest = new Quaternionf().rotationY(heading).mul(pose);
            float dot = Math.abs(rest.dot(this.attitude));
            if (dot > bestDot) {
                bestDot = dot;
                best = rest;
            }
        }
        return best;
    }

    private static Quaternionf[] restPoses(Lie lie) {
        return switch (lie) {
            case NONE -> new Quaternionf[] {new Quaternionf(), new Quaternionf().rotationZ(Mth.PI)};
            case X -> new Quaternionf[] {new Quaternionf().rotationX(Mth.HALF_PI), new Quaternionf().rotationX(-Mth.HALF_PI)};
            case Z -> new Quaternionf[] {new Quaternionf().rotationZ(Mth.HALF_PI), new Quaternionf().rotationZ(-Mth.HALF_PI)};
        };
    }

    /** The attitude to draw it at, between the last tick and this one. */
    public Quaternionf renderAttitude(float partialTick, Quaternionf out) {
        if (!this.attitudeReady) {
            return out.set(this.entityData.get(DATA_ATTITUDE));
        }
        return this.attitudeO.slerp(this.attitude, partialTick, out);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(DATA_PIECE, tag.getInt("Piece"));
        this.age = tag.getInt("Age");
        this.entityData.set(DATA_SPIN, new Vector3f());
        this.entityData.set(DATA_STATE, AT_REST);
        this.entityData.set(DATA_ATTITUDE, new Quaternionf().rotationY(getYRot() * Mth.DEG_TO_RAD));
        refreshDimensions();
    }

    /** A local piece is not part of the world to keep. */
    @Override
    public boolean shouldBeSaved() {
        return !this.local && super.shouldBeSaved();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Piece", this.entityData.get(DATA_PIECE));
        tag.putInt("Age", this.age);
    }
}
