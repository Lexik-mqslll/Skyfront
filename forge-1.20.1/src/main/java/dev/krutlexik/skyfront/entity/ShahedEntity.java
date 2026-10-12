package dev.krutlexik.skyfront.entity;

import dev.krutlexik.skyfront.config.SkyfrontConfig;
import dev.krutlexik.skyfront.item.ModItems;
import dev.krutlexik.skyfront.item.ShahedItem;
import dev.krutlexik.skyfront.network.NetworkHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;

import javax.annotation.Nullable;
import dev.krutlexik.skyfront.network.ShahedExplosionPacket;
import dev.krutlexik.skyfront.network.ShahedRemovePacket;
import dev.krutlexik.skyfront.network.ShahedSpawnPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.core.particles.ParticleOptions;
import dev.krutlexik.skyfront.particle.ModParticles;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.animation.AnimationState;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

public class ShahedEntity extends Projectile implements GeoEntity {
    private static final EntityDataAccessor<BlockPos> TARGET_POS = SynchedEntityData.defineId(ShahedEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Boolean> IS_DIVING = SynchedEntityData.defineId(ShahedEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_FALLING = SynchedEntityData.defineId(ShahedEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> DIVE_MODE = SynchedEntityData.defineId(ShahedEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> HEALTH_FRACT = SynchedEntityData.defineId(ShahedEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> SWERVE_DIR = SynchedEntityData.defineId(ShahedEntity.class, EntityDataSerializers.INT);
    public static final int STATE_GUIDED = 0;
    public static final int STATE_PARKED = 1;
    public static final int STATE_TAKEOFF = 2;
    public static final int STATE_CLIMB = 3;
    public static final int STATE_TURN = 4;
    /** Launched from a rail: running up it, before the climb takes over (see {@link #seatOnRail}). */
    public static final int STATE_RAIL = 5;
    private static final EntityDataAccessor<Integer> LAUNCH_STATE = SynchedEntityData.defineId(ShahedEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CRUISE_ALT = SynchedEntityData.defineId(ShahedEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FINAL_LEG = SynchedEntityData.defineId(ShahedEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DIVE_PROGRESS = SynchedEntityData.defineId(ShahedEntity.class, EntityDataSerializers.FLOAT);
    public static final double CRUISE_SPEED = 1.5;
    public static final double DIVE_SPEED = 3.6;
    private static final float EXPLOSION_POWER = 12.0f;
    private static final float EXPLOSION_RADIUS = 25.0f;
    private static final float SBW_VEHICLE_DIRECT_HIT_DAMAGE = 1200.0f;
    private static final float SBW_VEHICLE_EXPLOSION_DAMAGE = 900.0f;
    /** NB: SBW's falloff (which we mirror) fades over 2x this — so the blast actually reaches 32 blocks. */
    private static final float SBW_VEHICLE_EXPLOSION_RADIUS = 16.0f;
    private boolean isDiving = false;
    private float diveProgress = 0.0f;
    private double diveDistance = 150.0;
    private double diveTransitionDistance = 180.0;
    private Vec3 lastDirection = new Vec3(0, -1, 0);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    
    private int syncTickCounter = 0;
    private static final int SYNC_INTERVAL = 40;
    private boolean initialPacketSent = false;
    
    private Vec3 serverTargetPos = null;
    /** Error beyond which the client stops correcting and simply snaps — a real desync, not drift. */
    private static final double CORRECTION_SNAP_DISTANCE = 30.0;
    /** Share of the remaining error closed per tick. */
    private static final double CORRECTION_SHARE = 0.12;
    private boolean soundStarted = false;

    private float droneHealth = -1.0f;
    private boolean isFalling = false;
    private float postFallDamage = 0.0f;
    private boolean pendingSelfDiscard = false;
    private Vec3 takeoffOrigin = null;
    private Vec3 launchOrigin = null;
    private int[] route = new int[0];
    private int routeIndex = 0;
    /** True once the strike height has been read from the terrain at the target column. */
    private boolean targetGroundResolved = false;
    /** Used until the target chunk loads and the real ground height can be read. */
    private static final int FALLBACK_TARGET_Y = 64;
    private BlockPos boundTerminal = null;
    private float destroyRot = 0.0f;
    private float rollDeg = 0.0f;
    private float prevRollDeg = 0.0f;

    public ShahedEntity(EntityType<? extends ShahedEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    public ShahedEntity(Level level, Vec3 startPos, BlockPos targetPos, String diveMode) {
        this(ModEntities.SHAHED.get(), level);
        this.setPos(startPos);
        this.launchOrigin = startPos;
        this.entityData.set(TARGET_POS, targetPos);
        this.entityData.set(DIVE_MODE, diveMode);
        if (diveMode.equals("high")) {
            this.diveDistance = 350.0;
            this.diveTransitionDistance = 500.0;
        } else {
            this.diveDistance = 150.0;
            this.diveTransitionDistance = 280.0;
        }
        Vec3 targetVec = Vec3.atCenterOf(targetPos);
        Vec3 dir = targetVec.subtract(startPos);
        if (dir.lengthSqr() > 1.0E-4) {
            this.lastDirection = dir.normalize();
        }
    }

    /** Sets this drone up as a parked (placed) unit standing on the ground, engine off. */

    /** Speed gained per tick running up the rail, and the speed it leaves the rail at most. */
    private static final double RAIL_ACCELERATION = 0.16;
    private static final double RAIL_EXIT_SPEED = 0.9;

    private boolean onRail;
    private float railPitch;
    private double railRun;
    @javax.annotation.Nullable
    private Vec3 railStart;

    /**
     * Parks the drone on a launch rail: at {@code pos} on its top edge, heading {@code yawDeg} up it, nose
     * raised {@code pitchDeg} with it, with {@code run} blocks of rail ahead to run up.
     */
    public void seatOnRail(Vec3 pos, float yawDeg, float pitchDeg, double run) {
        this.initParked(pos, yawDeg);
        this.onRail = true;
        this.railPitch = pitchDeg;
        this.railRun = run;
        this.setXRot(-pitchDeg);
        this.xRotO = -pitchDeg;
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public boolean isOnRail() {
        return this.onRail;
    }

    public void initParked(Vec3 pos, float yawDeg) {
        this.setPos(pos);
        this.setYRot(yawDeg);
        this.yRotO = yawDeg;
        this.setXRot(0.0f);
        this.entityData.set(LAUNCH_STATE, STATE_PARKED);
        this.noPhysics = false;
        this.setNoGravity(false);
        this.setDeltaMovement(Vec3.ZERO);
    }

    /**
     * Ground launch from the control terminal: takeoff run straight ahead (the way the nose points), lift
     * off, climb to {@code altitude}, then turn toward the target mid-air.
     */
    /** Ground launch along a planned route: flattened (x,z) waypoint pairs, the LAST pair being the strike target. */
    public void launchFromGround(int[] routePoints, int altitude, String diveMode) {
        this.route = routePoints.clone();
        this.routeIndex = 0;
        this.entityData.set(CRUISE_ALT, altitude);
        this.setWaypointTarget(this.route[0], this.route[1], this.route.length <= 2);
        this.entityData.set(FINAL_LEG, this.route.length <= 2);
        this.entityData.set(DIVE_MODE, diveMode);
        this.entityData.set(LAUNCH_STATE, STATE_TAKEOFF);
        this.takeoffOrigin = this.position();
        this.launchOrigin = this.position();
        this.noPhysics = false;
        this.setNoGravity(true);
        if (this.onRail) {
            this.entityData.set(LAUNCH_STATE, STATE_RAIL);
            this.railStart = this.position();
            this.noPhysics = true;
        }
    }

    public int getLaunchState() {
        return this.entityData.get(LAUNCH_STATE);
    }

    public boolean isParked() {
        return this.entityData.get(LAUNCH_STATE) == STATE_PARKED;
    }

    /** False while cruising between route waypoints — the attack dive only happens on the last leg. */
    public boolean isFinalLeg() {
        return this.entityData.get(FINAL_LEG);
    }

    /** The real attack-dive progress (0..1), synched for the engine sound. */
    public float getSyncedDiveProgress() {
        return this.entityData.get(DIVE_PROGRESS);
    }

    /** Where this drone took off from (null until launched). */
    @Nullable
    public Vec3 getLaunchOrigin() {
        return this.launchOrigin;
    }

    /** Direct HP restore when re-placing a damaged drone item (&lt; 0 = undamaged). */
    public void setDroneHealthDirect(float health) {
        this.droneHealth = health;
        this.entityData.set(HEALTH_FRACT, health < 0.0f ? 1.0f : Math.max(0.0f, health / maxDroneHealth()));
    }

    public float getDroneHealth() {
        return this.droneHealth;
    }

    public void setBoundTerminal(@Nullable BlockPos pos) {
        this.boundTerminal = pos;
    }

    @Nullable
    public BlockPos getBoundTerminal() {
        return this.boundTerminal;
    }

    @Override
    public boolean canBeCollidedWith() {
        return this.isParked();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.isParked() && player.isShiftKeyDown()) {
            if (!this.level().isClientSide) {
                ItemStack stack = this.toItem();
                if (!player.addItem(stack)) {
                    player.drop(stack, false);
                }
                this.discardAsItem();
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return super.interact(player, hand);
    }

    /** This drone as the item it packs into, carrying its identity, its health and its binding. */
    public ItemStack toItem() {
        ItemStack stack = new ItemStack(ModItems.SHAHED_136.get());
        ShahedItem.writeData(stack, this.getUUID(), this.droneHealth, this.boundTerminal,
                this.level().dimension().location().toString());
        return stack;
    }

    /** Leaves the world quietly, having gone back into an item — not destroyed, and not a wreck. */
    public void discardAsItem() {
        this.pendingSelfDiscard = true;
        this.discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 490000.0;
    }
    
    /** A fresh statement of where the server has this Shahed. */
    public void setServerPosition(Vec3 pos) {
        if (!this.level().isClientSide) {
            return;
        }
        Vec3 target = pos.add(this.getDeltaMovement().scale(
                dev.krutlexik.skyfront.client.ClientLatency.packetAgeTicks()));
        if (target.distanceToSqr(this.position()) > CORRECTION_SNAP_DISTANCE * CORRECTION_SNAP_DISTANCE) {
            this.setPos(target.x, target.y, target.z);
            this.serverTargetPos = null;
            return;
        }
        this.serverTargetPos = target;
    }

    /** One tick of binding the client's dead-reckoned flight to the server's. */
    private void tickClientCorrection() {
        if (this.serverTargetPos == null) {
            return;
        }
        this.serverTargetPos = this.serverTargetPos.add(this.getDeltaMovement());
        Vec3 error = this.serverTargetPos.subtract(this.position());
        if (error.lengthSqr() < 1.0E-6) {
            return;
        }
        this.setPos(this.getX() + error.x * CORRECTION_SHARE,
                this.getY() + error.y * CORRECTION_SHARE,
                this.getZ() + error.z * CORRECTION_SHARE);
    }

    /**
     * The vanilla tracker's move and teleport packets land here — and the base {@link Entity} implementation
     * is an instant {@code setPos}.
     */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        if (!this.level().isClientSide) {
            super.lerpTo(x, y, z, yRot, xRot, steps, teleport);
            return;
        }
        this.setServerPosition(new Vec3(x, y, z));
        this.setRot(yRot, xRot);
    }

    /** Just the velocity. */
    @Override
    public void lerpMotion(double x, double y, double z) {
        this.setDeltaMovement(x, y, z);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(TARGET_POS, BlockPos.ZERO);
        this.entityData.define(IS_DIVING, false);
        this.entityData.define(IS_FALLING, false);
        this.entityData.define(DIVE_MODE, "low");
        this.entityData.define(HEALTH_FRACT, 1.0f);
        this.entityData.define(SWERVE_DIR, 0);
        this.entityData.define(LAUNCH_STATE, STATE_GUIDED);
        this.entityData.define(CRUISE_ALT, 150);
        this.entityData.define(FINAL_LEG, true);
        this.entityData.define(DIVE_PROGRESS, 0.0f);
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (entity instanceof ShahedEntity || entity instanceof Projectile) {
            return false;
        }
        return super.canHitEntity(entity);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.entityData.get(IS_FALLING)) {
            this.tickFalling();
            return;
        }

        int launchState = this.entityData.get(LAUNCH_STATE);
        if (launchState == STATE_PARKED) {
            this.tickParked();
            return;
        }
        if (launchState != STATE_GUIDED) {
            this.tickLaunchSequence(launchState);
            return;
        }

        if (this.level().isClientSide) {
            if (!this.soundStarted) {
                dev.krutlexik.skyfront.client.sound.ClientSoundHelper.startShahedSound(this);
                this.soundStarted = true;
            }
            this.tickClientCorrection();
            Vec3 synced = this.getDeltaMovement();
            this.setPos(this.getX() + synced.x, this.getY() + synced.y, this.getZ() + synced.z);
            return;
        }

        BlockPos target = this.entityData.get(TARGET_POS);
        Vec3 targetVec = Vec3.atCenterOf(target);
        
        String mode = this.entityData.get(DIVE_MODE);
        if (mode.equals("high")) {
            this.diveDistance = 350.0;
            this.diveTransitionDistance = 500.0;
        } else {
            this.diveDistance = 150.0;
            this.diveTransitionDistance = 280.0;
        }
        
        Vec3 currentPos = this.position();
        double horizontalDist = Math.sqrt(Math.pow(targetVec.x - currentPos.x, 2.0) + Math.pow(targetVec.z - currentPos.z, 2.0));
        
        boolean finalLeg = this.entityData.get(FINAL_LEG);
        boolean passed = horizontalDist < 40.0
                && new Vec3(targetVec.x - currentPos.x, 0.0, targetVec.z - currentPos.z)
                .dot(new Vec3(this.lastDirection.x, 0.0, this.lastDirection.z)) < 0.0;
        if (!finalLeg && !this.level().isClientSide && (horizontalDist < 24.0 || passed)) {
            this.routeIndex++;
            int base = this.routeIndex * 2;
            if (base + 1 < this.route.length) {
                boolean nowFinal = base + 2 >= this.route.length;
                this.setWaypointTarget(this.route[base], this.route[base + 1], nowFinal);
                this.entityData.set(FINAL_LEG, nowFinal);
                finalLeg = nowFinal;
            } else {
                this.entityData.set(FINAL_LEG, true);
                finalLeg = true;
            }
            target = this.entityData.get(TARGET_POS);
            targetVec = Vec3.atCenterOf(target);
            horizontalDist = Math.sqrt(Math.pow(targetVec.x - currentPos.x, 2.0) + Math.pow(targetVec.z - currentPos.z, 2.0));
        }

        if (finalLeg && !this.level().isClientSide) {
            this.resolveTargetGround(target);
            target = this.entityData.get(TARGET_POS);
            targetVec = Vec3.atCenterOf(target);
        }

        Vec3 toTargetFlat = new Vec3(targetVec.x - currentPos.x, 0.0, targetVec.z - currentPos.z);
        Vec3 headingFlat = new Vec3(this.lastDirection.x, 0.0, this.lastDirection.z);
        boolean alignedWithTarget = toTargetFlat.lengthSqr() < 1.0E-4 || headingFlat.lengthSqr() < 1.0E-4
                || toTargetFlat.normalize().dot(headingFlat.normalize()) > 0.94;

        float targetProgress = 0.0f;
        boolean wasNotDiving = !this.isDiving;
        if (finalLeg && alignedWithTarget && horizontalDist < this.diveTransitionDistance) {
            if (horizontalDist < this.diveDistance) {
                this.isDiving = true;
                targetProgress = 1.0f;
            } else {
                targetProgress = 1.0f - (float) ((horizontalDist - this.diveDistance) / (this.diveTransitionDistance - this.diveDistance));
            }
        }
        
        this.diveProgress += (targetProgress - this.diveProgress) * 0.035f;
        this.diveProgress = Math.max(0.0f, Math.min(1.0f, this.diveProgress));
        if (Math.abs(this.diveProgress - this.entityData.get(DIVE_PROGRESS)) > 0.02f) {
            this.entityData.set(DIVE_PROGRESS, this.diveProgress);
        }

        Vec3 moveDirection;
        Vec3 toTarget = targetVec.subtract(currentPos);
        if (toTarget.lengthSqr() < 4.0) {
            moveDirection = this.lastDirection;
        } else if (this.diveProgress < 0.01f) {
            Vec3 horizontalTarget = new Vec3(targetVec.x, currentPos.y, targetVec.z);
            Vec3 diff = horizontalTarget.subtract(currentPos);
            if (diff.lengthSqr() > 1.0E-4) {
                moveDirection = diff.normalize();
            } else {
                moveDirection = toTarget.normalize();
            }
        } else {
            Vec3 horizontalTarget = new Vec3(targetVec.x, currentPos.y, targetVec.z);
            Vec3 horizontalDiff = horizontalTarget.subtract(currentPos);
            Vec3 directDiff = toTarget;
            Vec3 horizontalDir = horizontalDiff.lengthSqr() > 1.0E-4 ? horizontalDiff.normalize() : this.lastDirection;
            Vec3 directDir = directDiff.lengthSqr() > 1.0E-4 ? directDiff.normalize() : this.lastDirection;
            moveDirection = new Vec3(
                    horizontalDir.x + (directDir.x - horizontalDir.x) * this.diveProgress,
                    horizontalDir.y + (directDir.y - horizontalDir.y) * this.diveProgress,
                    horizontalDir.z + (directDir.z - horizontalDir.z) * this.diveProgress
            );
            if (moveDirection.lengthSqr() > 1.0E-4) {
                moveDirection = moveDirection.normalize();
            } else {
                moveDirection = this.lastDirection;
            }
        }
        
        if (moveDirection.lengthSqr() > 1.0E-4 && this.lastDirection.lengthSqr() > 1.0E-4) {
            double maxTurn = Math.toRadians(2.5 + 2.5 * this.diveProgress);
            moveDirection = turnToward(this.lastDirection, moveDirection, maxTurn);
        }

        if (moveDirection.lengthSqr() > 1.0E-4) {
            this.lastDirection = moveDirection;
        }

        double speed = CRUISE_SPEED + 2.1 * this.diveProgress;
        Vec3 motion = moveDirection.scale(speed);
        this.setDeltaMovement(motion);
        
        if (motion.lengthSqr() > 1.0E-4) {
            this.setYRot((float) (Math.atan2(motion.x, motion.z) * 57.29577951308232));
            this.setXRot((float) (Math.atan2(motion.y, Math.sqrt(motion.x * motion.x + motion.z * motion.z)) * 57.29577951308232));
        }
        
        if (wasNotDiving && this.isDiving) {
            this.entityData.set(IS_DIVING, true);
        }
        
        HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hitResult.getType() != HitResult.Type.MISS) {
            this.onHit(hitResult);
            return;
        }
        
        if (this.isDiving && this.getY() <= targetVec.y) {
            this.explode();
            return;
        }

        if (this.checkFlightRange()) {
            return;
        }
        
        this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);

        if (this.entityData.get(HEALTH_FRACT) <= 0.25f) {
            this.tickDamagedEngineEffects();
        }

        this.sendFlightSync();
    }

    /** Rolling chunk window for autonomous flight. */
    private void maintainFlightChunks() {
        if (!(this.level() instanceof ServerLevel serverLevel) || this.tickCount % 4 != 0) {
            return;
        }
        ChunkPos current = this.chunkPosition();
        dev.krutlexik.skyfront.util.ChunkLoadHelper.keepTicking(serverLevel, current);

        Vec3 motion = this.getDeltaMovement();
        double speed = motion.horizontalDistance();
        if (speed < 1.0E-3) {
            return;
        }
        double dirX = motion.x / speed;
        double dirZ = motion.z / speed;

        ChunkPos last = current;
        for (double ahead = 16.0; ahead <= 48.0; ahead += 16.0) {
            ChunkPos next = dev.krutlexik.skyfront.util.ChunkLoadHelper.chunkAt(
                    this.getX() + dirX * ahead, this.getZ() + dirZ * ahead);
            if (!next.equals(last)) {
                dev.krutlexik.skyfront.util.ChunkLoadHelper.keepTicking(serverLevel, next);
                last = next;
            }
        }
        for (double ahead = 96.0; ahead <= 160.0; ahead += 64.0) {
            ChunkPos far = dev.krutlexik.skyfront.util.ChunkLoadHelper.chunkAt(
                    this.getX() + dirX * ahead, this.getZ() + dirZ * ahead);
            dev.krutlexik.skyfront.util.ChunkLoadHelper.preloadAhead(serverLevel, far);
        }
    }

    /**
     * Out-of-range failsafe: a drone that strays past {@code maxFlightRange} from its launch point loses
     * control and crashes, so nothing can wander across the whole server.
     */
    private boolean checkFlightRange() {
        if (this.level().isClientSide || this.launchOrigin == null || this.isFalling) {
            return false;
        }
        int max = SkyfrontConfig.MAX_FLIGHT_RANGE.get();
        double dx = this.getX() - this.launchOrigin.x;
        double dz = this.getZ() - this.launchOrigin.z;
        if (dx * dx + dz * dz > (double) max * max) {
            this.startFalling();
            return true;
        }
        return false;
    }

    /** Points TARGET_POS at a waypoint. */
    private void setWaypointTarget(int x, int z, boolean isFinal) {
        int y = isFinal ? FALLBACK_TARGET_Y : this.entityData.get(CRUISE_ALT);
        this.entityData.set(TARGET_POS, new BlockPos(x, y, z));
        this.targetGroundResolved = false;
    }

    /** Sets the strike height to the actual ground at the target column. */
    private void resolveTargetGround(BlockPos target) {
        if (this.targetGroundResolved || !(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (serverLevel.hasChunkAt(target)) {
            int groundY = serverLevel.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                    target.getX(), target.getZ());
            this.entityData.set(TARGET_POS, new BlockPos(target.getX(), groundY, target.getZ()));
            this.targetGroundResolved = true;
        } else if (this.tickCount % 20 == 0) {
            dev.krutlexik.skyfront.util.ChunkLoadHelper.preloadAhead(serverLevel, new ChunkPos(target));
        }
    }

    /** Rotates {@code from} toward {@code to} by at most {@code maxAngleRad}. */
    private static Vec3 turnToward(Vec3 from, Vec3 to, double maxAngleRad) {
        Vec3 a = from.normalize();
        Vec3 b = to.normalize();
        double dot = Math.max(-1.0, Math.min(1.0, a.dot(b)));
        double angle = Math.acos(dot);
        if (angle <= maxAngleRad || angle < 1.0E-4) {
            return b;
        }
        if (angle > Math.PI - 1.0E-3) {
            b = b.add(0.01, 0.0, 0.01).normalize();
        }
        Vec3 result = a.add(b.subtract(a).scale(maxAngleRad / angle));
        return result.lengthSqr() > 1.0E-8 ? result.normalize() : b;
    }

    /** Streams position/state to all players (engine sound + far-range sync). */
    private void sendFlightSync() {
        this.maintainFlightChunks();
        BlockPos target = this.entityData.get(TARGET_POS);
        String mode = this.entityData.get(DIVE_MODE);
        if (!this.initialPacketSent) {
            this.initialPacketSent = true;
            NetworkHandler.sendToAllPlayers(new ShahedSpawnPacket(this.getId(), this.position(), target, mode,
                    this.entityData.get(HEALTH_FRACT), this.entityData.get(IS_FALLING),
                    this.entityData.get(DIVE_PROGRESS), this.entityData.get(FINAL_LEG)));
        }
        this.syncTickCounter++;
        if (this.syncTickCounter >= SYNC_INTERVAL) {
            this.syncTickCounter = 0;
            NetworkHandler.sendToAllPlayers(new ShahedSpawnPacket(this.getId(), this.position(), target, mode,
                    this.entityData.get(HEALTH_FRACT), this.entityData.get(IS_FALLING),
                    this.entityData.get(DIVE_PROGRESS), this.entityData.get(FINAL_LEG)));
        }
    }

    /** Parked on the ground: engine off, no guidance — just settle onto the ground and sit still. */
    private void tickParked() {
        if (this.level().isClientSide) {
            return;
        }
        if (this.onRail) {
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (!this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.06, 0.0).multiply(0.98, 0.98, 0.98));
            this.move(MoverType.SELF, this.getDeltaMovement());
        } else {
            this.setDeltaMovement(Vec3.ZERO);
        }
    }

    /**
     * The ground-launch sequence, in the direction the nose points regardless of where the target is: a
     * ~15-block accelerating takeoff run, a smooth climb to the ordered altitude, then a mid-air turn onto
     * the target bearing, after which normal guided flight takes over.
     */
    private void tickLaunchSequence(int state) {
        if (this.level().isClientSide) {
            if (!this.soundStarted) {
                dev.krutlexik.skyfront.client.sound.ClientSoundHelper.startShahedSound(this);
                this.soundStarted = true;
            }
            this.tickClientCorrection();
            Vec3 m = this.getDeltaMovement();
            if (state == STATE_TAKEOFF) {
                this.setPos(this.getX() + m.x, this.getY(), this.getZ() + m.z);
            } else {
                this.setPos(this.getX() + m.x, this.getY() + m.y, this.getZ() + m.z);
            }
            return;
        }

        double yawRad = Math.toRadians(this.getYRot());
        Vec3 forward = new Vec3(Math.sin(yawRad), 0.0, Math.cos(yawRad));
        BlockPos target = this.entityData.get(TARGET_POS);
        int cruiseAlt = this.entityData.get(CRUISE_ALT);

        switch (state) {
            case STATE_RAIL -> {
                double pitch = Math.toRadians(this.railPitch);
                Vec3 along = new Vec3(forward.x * Math.cos(pitch), Math.sin(pitch), forward.z * Math.cos(pitch));
                double speed = Math.min(this.getDeltaMovement().length() + RAIL_ACCELERATION, RAIL_EXIT_SPEED);
                Vec3 motion = along.scale(speed);
                this.setDeltaMovement(motion);
                this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);
                if (this.railStart == null) {
                    this.railStart = this.position();
                }
                if (this.position().distanceTo(this.railStart) >= this.railRun) {
                    this.onRail = false;
                    this.entityData.set(LAUNCH_STATE, STATE_CLIMB);
                }
            }
            case STATE_TAKEOFF -> {
                if (this.takeoffOrigin == null) {
                    this.takeoffOrigin = this.position();
                }
                double run = this.position().multiply(1, 0, 1).distanceTo(this.takeoffOrigin.multiply(1, 0, 1));
                double speed = Math.min(0.12 + run * 0.032, 0.9);
                Vec3 motion = forward.scale(speed).add(0.0, -0.08, 0.0);
                this.setDeltaMovement(motion);
                this.move(MoverType.SELF, motion);
                this.setXRot(0.0f);
                if (this.horizontalCollision) {
                    this.explode();
                    return;
                }
                if (run >= 24.0) {
                    this.entityData.set(LAUNCH_STATE, STATE_CLIMB);
                    this.noPhysics = true;
                }
            }
            case STATE_CLIMB -> {
                Vec3 current = this.getDeltaMovement();
                double fwdSpeed = Math.min(current.horizontalDistance() + 0.008, CRUISE_SPEED);
                double remaining = cruiseAlt - this.getY();
                double targetVy = Math.max(0.03, Math.min(remaining * 0.04, 0.3));
                double vy = current.y + (targetVy - current.y) * 0.045;
                Vec3 motion = forward.scale(fwdSpeed).add(0.0, vy, 0.0);
                this.setDeltaMovement(motion);
                if (this.checkFlightCollision()) {
                    return;
                }
                this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);
                this.setXRot((float) (Math.atan2(motion.y, motion.horizontalDistance()) * 57.29577951308232));
                if (this.getY() >= cruiseAlt - 0.5) {
                    this.entityData.set(LAUNCH_STATE, STATE_TURN);
                }
            }
            case STATE_TURN -> {
                Vec3 targetVec = Vec3.atCenterOf(target);
                float bearing = (float) (Math.atan2(targetVec.x - this.getX(), targetVec.z - this.getZ()) * 57.29577951308232);
                float delta = net.minecraft.util.Mth.wrapDegrees(bearing - this.getYRot());
                float step = net.minecraft.util.Mth.clamp(delta, -2.2f, 2.2f);
                this.setYRot(this.getYRot() + step);
                double newYawRad = Math.toRadians(this.getYRot());
                Vec3 dir = new Vec3(Math.sin(newYawRad), 0.0, Math.cos(newYawRad));
                double vy = net.minecraft.util.Mth.clamp((cruiseAlt - this.getY()) * 0.1, -0.1, 0.1);
                Vec3 motion = dir.scale(CRUISE_SPEED).add(0.0, vy, 0.0);
                this.setDeltaMovement(motion);
                if (this.checkFlightCollision()) {
                    return;
                }
                this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);
                this.setXRot(0.0f);
                if (Math.abs(delta) < 4.0f) {
                    this.lastDirection = dir;
                    this.entityData.set(LAUNCH_STATE, STATE_GUIDED);
                }
            }
            default -> {
            }
        }

        this.sendFlightSync();
    }

    /** Mid-air obstacle check for the launch phases; explodes on impact. @return true if hit. */
    private boolean checkFlightCollision() {
        HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hitResult.getType() != HitResult.Type.MISS) {
            this.onHit(hitResult);
            return true;
        }
        return false;
    }

    /** The drone's hidden HP pool. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || this.isRemoved() || amount <= 0.0f) {
            return false;
        }
        if (this.isInvulnerableTo(source)) {
            return false;
        }
        if (source.getEntity() == this || source.getDirectEntity() == this) {
            return false;
        }

        Vec3 origin = null;
        Entity direct = source.getDirectEntity();
        if (direct != null && direct != source.getEntity()) {
            origin = direct.position();
        } else if (source.getEntity() instanceof LivingEntity attacker) {
            origin = attacker.getEyePosition();
        } else if (source.getSourcePosition() != null) {
            origin = source.getSourcePosition();
        }
        Vec3 hitPos;
        if (origin != null) {
            net.minecraft.world.phys.AABB box = this.getBoundingBox();
            hitPos = new Vec3(
                    net.minecraft.util.Mth.clamp(origin.x, box.minX, box.maxX),
                    net.minecraft.util.Mth.clamp(origin.y, box.minY, box.maxY),
                    net.minecraft.util.Mth.clamp(origin.z, box.minZ, box.maxZ));
        } else {
            hitPos = this.position().add(0.0, this.getBbHeight() * 0.5, 0.0);
        }
        if (this.level() instanceof ServerLevel serverLevel) {
            spawnBulletImpact(serverLevel, hitPos);
        }
        this.level().playSound(null, hitPos.x, hitPos.y, hitPos.z,
                SoundEvents.NETHERITE_BLOCK_HIT, SoundSource.HOSTILE, 1.4f, 0.7f + this.random.nextFloat() * 0.4f);

        if (amount >= maxDroneHealth() * 2.0f) {
            this.explode();
            return true;
        }

        if (this.isFalling) {
            this.postFallDamage += amount;
            if (this.postFallDamage >= maxDroneHealth()) {
                this.explode();
            }
            return true;
        }

        if (this.droneHealth < 0.0f) {
            this.droneHealth = (float) (double) SkyfrontConfig.SHAHED_HEALTH.get();
        }
        this.droneHealth -= amount;
        this.entityData.set(HEALTH_FRACT, Math.max(0.0f, this.droneHealth / maxDroneHealth()));
        if (this.droneHealth <= 0.0f) {
            if (this.isParked()) {
                this.explode();
            } else {
                this.startFalling();
            }
        }
        return true;
    }

    private static float maxDroneHealth() {
        return (float) Math.max(1.0, SkyfrontConfig.SHAHED_HEALTH.get());
    }

    private void startFalling() {
        this.isFalling = true;
        this.entityData.set(IS_FALLING, true);
        this.entityData.set(HEALTH_FRACT, 0.0f);
        this.noPhysics = true;
        if (this.entityData.get(SWERVE_DIR) == 0) {
            this.entityData.set(SWERVE_DIR, this.random.nextBoolean() ? 1 : -1);
        }
        this.setNoGravity(true);
        Vec3 m = this.getDeltaMovement();
        this.setDeltaMovement(m.x, m.y - 0.05, m.z);
    }

    /**
     * Server-side effects for a struggling engine (≤25% HP, still flying): a thin smoke trail and random
     * backfires — a muffled bang, flame puff and sparks, like a real damaged engine hunting for thrust.
     */
    private void tickDamagedEngineEffects() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        sendParticlesForced(serverLevel, ParticleTypes.SMOKE, this.getX(), this.getY() + 0.3, this.getZ(), 2, 0.15, 0.1, 0.15, 0.01);
        if (this.random.nextInt(4) == 0) {
            sendParticlesForced(serverLevel, ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.3, this.getZ(), 1, 0.1, 0.1, 0.1, 0.01);
        }
        if (this.random.nextInt(35) == 0) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.HOSTILE, 4.0f, 0.5f + this.random.nextFloat() * 0.2f);
            sendParticlesForced(serverLevel, ParticleTypes.FLAME, this.getX(), this.getY() + 0.2, this.getZ(), 8, 0.3, 0.2, 0.3, 0.06);
            sendParticlesForced(serverLevel, ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY() + 0.3, this.getZ(), 5, 0.2, 0.2, 0.2, 0.15);
            sendParticlesForced(serverLevel, ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.3, this.getZ(), 4, 0.2, 0.2, 0.2, 0.02);
        }
    }

    private void spawnBulletImpact(ServerLevel level, Vec3 pos) {
        sendParticlesForced(level, ParticleTypes.CRIT, pos.x, pos.y, pos.z, 6, 0.1, 0.1, 0.1, 0.15);
        sendParticlesForced(level, ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 8, 0.1, 0.1, 0.1, 0.2);
        sendParticlesForced(level, ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 3, 0.05, 0.05, 0.05, 0.02);
    }

    /**
     * Physics for a downed drone: it keeps its forward momentum, the nose eases toward straight down, the roll
     * spin accelerates and the sink rate ramps up.
     */
    private void tickFalling() {
        this.destroyRot += 0.12f;

        Vec3 motion = this.getDeltaMovement();
        double speed = motion.length();
        if (speed < 1.0E-4) {
            motion = new Vec3(0, -0.1, 0);
            speed = 0.1;
        }

        double blend = Math.min(0.0012 * this.destroyRot, 0.06);
        Vec3 dir = motion.scale(1.0 / speed);
        Vec3 down = new Vec3(0.0, -1.0, 0.0);
        Vec3 newDir = dir.add(down.subtract(dir).scale(blend));
        if (newDir.lengthSqr() > 1.0E-6) {
            newDir = newDir.normalize();
        } else {
            newDir = down;
        }

        double newSpeed = Math.min(speed + 0.02 * Math.max(0.0, -newDir.y), DIVE_SPEED + 1.0);
        Vec3 newMotion = newDir.scale(newSpeed).add(0.0, -Math.min(0.002 * this.destroyRot, 0.06), 0.0);

        int fallCurve = this.entityData.get(SWERVE_DIR);
        if (fallCurve != 0) {
            double curve = fallCurve * Math.min(this.destroyRot * 0.004, 0.045);
            double cos = Math.cos(curve);
            double sin = Math.sin(curve);
            newMotion = new Vec3(newMotion.x * cos - newMotion.z * sin, newMotion.y,
                    newMotion.x * sin + newMotion.z * cos);
        }
        this.setDeltaMovement(newMotion);

        this.prevRollDeg = this.rollDeg;
        this.rollDeg -= Math.min(this.destroyRot * 1.5f, 9.0f);

        if (newMotion.lengthSqr() > 1.0E-4) {
            this.setYRot((float) (Math.atan2(newMotion.x, newMotion.z) * 57.29577951308232));
            this.setXRot((float) (Math.atan2(newMotion.y, Math.sqrt(newMotion.x * newMotion.x + newMotion.z * newMotion.z)) * 57.29577951308232));
        }

        Vec3 before = this.position();

        if (this.level().isClientSide) {
            if (!this.soundStarted) {
                dev.krutlexik.skyfront.client.sound.ClientSoundHelper.startShahedSound(this);
                this.soundStarted = true;
            }
            this.tickClientCorrection();
            this.setPos(this.getX() + newMotion.x, this.getY() + newMotion.y, this.getZ() + newMotion.z);
            return;
        }

        HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hitResult.getType() != HitResult.Type.MISS) {
            this.onHit(hitResult);
            return;
        }

        this.setPos(this.getX() + newMotion.x, this.getY() + newMotion.y, this.getZ() + newMotion.z);

        if (this.level() instanceof ServerLevel serverLevel) {
            Vec3 after = this.position();
            int steps = 3;
            for (int i = 0; i < steps; i++) {
                double t = (i + 1) / (double) steps;
                double px = before.x + (after.x - before.x) * t;
                double py = before.y + (after.y - before.y) * t + 0.3;
                double pz = before.z + (after.z - before.z) * t;
                sendParticlesForced(serverLevel, ParticleTypes.LARGE_SMOKE, px, py, pz, 2, 0.2, 0.2, 0.2, 0.01);
                if (i == 0) {
                    sendParticlesForced(serverLevel, ParticleTypes.CAMPFIRE_COSY_SMOKE, px, py, pz, 1, 0.1, 0.1, 0.1, 0.005);
                }
                if (this.random.nextInt(3) == 0) {
                    sendParticlesForced(serverLevel, ParticleTypes.FLAME, px, py - 0.1, pz, 1, 0.1, 0.05, 0.1, 0.02);
                }
                if (this.random.nextInt(6) == 0) {
                    sendParticlesForced(serverLevel, ParticleTypes.LAVA, px, py, pz, 1, 0.05, 0.05, 0.05, 0.05);
                }
            }
            if (this.random.nextInt(22) == 0) {
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.HOSTILE, 3.5f, 0.4f + this.random.nextFloat() * 0.15f);
            }
        }

        if (this.getY() < this.level().getMinBuildHeight() - 8) {
            this.explode();
            return;
        }

        this.maintainFlightChunks();

        this.syncTickCounter++;
        if (this.syncTickCounter >= 4) {
            this.syncTickCounter = 0;
            NetworkHandler.sendToAllPlayers(new ShahedSpawnPacket(this.getId(), this.position(),
                    this.entityData.get(TARGET_POS), this.entityData.get(DIVE_MODE),
                    this.entityData.get(HEALTH_FRACT), this.entityData.get(IS_FALLING),
                    this.entityData.get(DIVE_PROGRESS), this.entityData.get(FINAL_LEG)));
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!this.level().isClientSide) {
            this.explode();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!this.level().isClientSide) {
            if (dev.krutlexik.skyfront.compat.sbw.SbwCompat.isLoaded()) {
                dev.krutlexik.skyfront.compat.sbw.SbwVehicles.damageVehicleDirectHit(
                        result.getEntity(), this, this.getOwner(), SBW_VEHICLE_DIRECT_HIT_DAMAGE);
            }
            this.explode();
        }
    }

    private void explode() {
        if (!this.level().isClientSide) {
            Vec3 pos = this.position();
            float particleMult = SkyfrontConfig.areExplosionParticlesEnabled()
                    ? SkyfrontConfig.getParticleMultiplier() / 100.0f : 0.0f;
            NetworkHandler.sendToAllPlayers(new ShahedExplosionPacket(pos, particleMult,
                    SkyfrontConfig.areCustomExplosionSoundsEnabled()));

            Entity owner = this.getOwner();
            
            AABB explosionBox = new AABB(pos.x - EXPLOSION_RADIUS, pos.y - EXPLOSION_RADIUS, pos.z - EXPLOSION_RADIUS, pos.x + EXPLOSION_RADIUS, pos.y + EXPLOSION_RADIUS, pos.z + EXPLOSION_RADIUS);
            List<Entity> entities = this.level().getEntities(this, explosionBox);
            for (Entity entity : entities) {
                if (entity instanceof Player targetPlayer) {
                    double distance = targetPlayer.position().distanceTo(pos);
                    float damage = 0.0f;
                    if (distance <= 17.0) {
                        damage = 40.0f;
                    } else if (distance <= 20.0) {
                        damage = 30.0f;
                    } else if (distance <= 22.0) {
                        damage = 20.0f;
                    } else if (distance <= 24.0) {
                        damage = 12.0f;
                    } else if (distance <= 25.0) {
                        damage = 6.0f;
                    }
                    if (damage > 0.0f) {
                        DamageSource damageSource;
                        if (owner instanceof LivingEntity livingOwner) {
                            damageSource = targetPlayer.damageSources().explosion(this, livingOwner);
                        } else {
                            damageSource = targetPlayer.damageSources().explosion(this, null);
                        }
                        targetPlayer.hurt(damageSource, damage);
                        Vec3 knockback = targetPlayer.position().subtract(pos).normalize();
                        double knockbackStrength = Math.max(0.0, 1.0 - distance / 25.0) * 2.5;
                        targetPlayer.push(knockback.x * knockbackStrength, knockback.y * knockbackStrength * 0.6, knockback.z * knockbackStrength);
                        targetPlayer.hurtMarked = true;
                    }
                } else if (entity instanceof LivingEntity livingEntity) {
                    double distance = livingEntity.position().distanceTo(pos);
                    if (distance <= EXPLOSION_RADIUS) {
                        double damageMultiplier = 1.0 - distance / EXPLOSION_RADIUS;
                        float damage = (float) (EXPLOSION_POWER * damageMultiplier * 3.0);
                        DamageSource damageSource;
                        if (owner instanceof LivingEntity livingOwner) {
                            damageSource = livingEntity.damageSources().explosion(this, livingOwner);
                        } else {
                            damageSource = livingEntity.damageSources().explosion(this, null);
                        }
                        livingEntity.hurt(damageSource, damage);
                        Vec3 knockback = livingEntity.position().subtract(pos).normalize();
                        double knockbackStrength = damageMultiplier * 2.0;
                        livingEntity.push(knockback.x * knockbackStrength, knockback.y * knockbackStrength * 0.6, knockback.z * knockbackStrength);
                        livingEntity.hurtMarked = true;
                    }
                }
            }
            
            if (this.level() instanceof ServerLevel serverLevel
                    && dev.krutlexik.skyfront.compat.sbw.SbwCompat.isLoaded()) {
                dev.krutlexik.skyfront.compat.sbw.SbwVehicles.damageVehiclesAround(
                        serverLevel, this, owner, pos, SBW_VEHICLE_EXPLOSION_DAMAGE, SBW_VEHICLE_EXPLOSION_RADIUS);
            }

            LivingEntity explosionOwner = (owner instanceof LivingEntity) ? (LivingEntity) owner : null;
            if (SkyfrontConfig.doExplosionsBreakBlocks()) {
                this.level().explode(this, this.damageSources().explosion(this, explosionOwner), null, pos.x, pos.y, pos.z, EXPLOSION_POWER, false, Level.ExplosionInteraction.TNT);
            } else {
                this.level().explode(this, this.damageSources().explosion(this, explosionOwner), null, pos.x, pos.y, pos.z, EXPLOSION_POWER, false, Level.ExplosionInteraction.NONE);
            }

            if (this.level() instanceof ServerLevel serverLevel) {
                Vec3 motion = this.getDeltaMovement();
                float yaw;
                float pitch;
                if (!this.isParked() && motion.lengthSqr() > 1.0E-4) {
                    yaw = (float) Math.toDegrees(Math.atan2(motion.x, motion.z));
                    pitch = (float) Math.toDegrees(Math.atan2(-motion.y, motion.horizontalDistance()));
                } else {
                    yaw = this.getYRot();
                    pitch = this.getXRot();
                }
                DebrisEntity.burst(serverLevel, DebrisEntity.Airframe.SHAHED, pos,
                        DebrisEntity.shahedAttitude(yaw, pitch), motion);
            }
        }
        this.pendingSelfDiscard = true;
        this.discard();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason == RemovalReason.DISCARDED && !this.pendingSelfDiscard && !this.level().isClientSide) {
            return;
        }
        if (!this.level().isClientSide) {
            NetworkHandler.sendToAllPlayers(new ShahedRemovePacket(this.getId()));
        }
        super.remove(reason);
    }

    public boolean getIsDiving() {
        return this.entityData.get(IS_DIVING);
    }

    public boolean getIsFalling() {
        return this.entityData.get(IS_FALLING);
    }

    /** Remaining health as a 0..1 fraction (synched; 1.0 = untouched). */
    public float getHealthFraction() {
        return this.entityData.get(HEALTH_FRACT);
    }

    /** Interpolated crash-roll angle (degrees) for the renderer. */
    public float getRenderRoll(float partialTick) {
        return this.prevRollDeg + (this.rollDeg - this.prevRollDeg) * partialTick;
    }

    public String getDiveMode() {
        return this.entityData.get(DIVE_MODE);
    }

    public double getDiveDistance() {
        return this.diveDistance;
    }

    public double getDiveTransitionDistance() {
        return this.diveTransitionDistance;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        BlockPos target = this.entityData.get(TARGET_POS);
        tag.putInt("TargetX", target.getX());
        tag.putInt("TargetY", target.getY());
        tag.putInt("TargetZ", target.getZ());
        tag.putBoolean("IsDiving", this.isDiving);
        tag.putFloat("DiveProgress", this.diveProgress);
        tag.putString("DiveMode", this.entityData.get(DIVE_MODE));
        tag.putDouble("LastDirX", this.lastDirection.x);
        tag.putDouble("LastDirY", this.lastDirection.y);
        tag.putDouble("LastDirZ", this.lastDirection.z);
        tag.putFloat("DroneHealth", this.droneHealth);
        tag.putBoolean("IsFalling", this.isFalling);
        tag.putFloat("DestroyRot", this.destroyRot);
        tag.putFloat("PostFallDamage", this.postFallDamage);
        tag.putInt("SwerveDir", this.entityData.get(SWERVE_DIR));
        tag.putInt("LaunchState", this.entityData.get(LAUNCH_STATE));
        tag.putInt("CruiseAlt", this.entityData.get(CRUISE_ALT));
        if (this.takeoffOrigin != null) {
            tag.putDouble("TakeoffX", this.takeoffOrigin.x);
            tag.putDouble("TakeoffY", this.takeoffOrigin.y);
            tag.putDouble("TakeoffZ", this.takeoffOrigin.z);
        }
        if (this.boundTerminal != null) {
            tag.putIntArray("BoundTerminal", new int[]{this.boundTerminal.getX(), this.boundTerminal.getY(), this.boundTerminal.getZ()});
        }
        if (this.launchOrigin != null) {
            tag.putDouble("LaunchOriginX", this.launchOrigin.x);
            tag.putDouble("LaunchOriginY", this.launchOrigin.y);
            tag.putDouble("LaunchOriginZ", this.launchOrigin.z);
        }
        tag.putIntArray("Route", this.route);
        tag.putInt("RouteIndex", this.routeIndex);
        tag.putBoolean("TargetGroundResolved", this.targetGroundResolved);
        tag.putBoolean("FinalLeg", this.entityData.get(FINAL_LEG));
        tag.putBoolean("OnRail", this.onRail);
        tag.putFloat("RailPitch", this.railPitch);
        tag.putDouble("RailRun", this.railRun);
        if (this.railStart != null) {
            tag.putDouble("RailStartX", this.railStart.x);
            tag.putDouble("RailStartY", this.railStart.y);
            tag.putDouble("RailStartZ", this.railStart.z);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        BlockPos target = new BlockPos(tag.getInt("TargetX"), tag.getInt("TargetY"), tag.getInt("TargetZ"));
        this.entityData.set(TARGET_POS, target);
        this.isDiving = tag.getBoolean("IsDiving");
        this.diveProgress = tag.getFloat("DiveProgress");
        String mode = tag.getString("DiveMode");
        this.entityData.set(DIVE_MODE, mode);
        if (mode.equals("high")) {
            this.diveDistance = 350.0;
            this.diveTransitionDistance = 500.0;
        } else {
            this.diveDistance = 150.0;
            this.diveTransitionDistance = 280.0;
        }
        this.entityData.set(IS_DIVING, this.isDiving);
        if (tag.contains("LastDirX")) {
            this.lastDirection = new Vec3(tag.getDouble("LastDirX"), tag.getDouble("LastDirY"), tag.getDouble("LastDirZ"));
        }
        this.droneHealth = tag.contains("DroneHealth") ? tag.getFloat("DroneHealth") : -1.0f;
        this.isFalling = tag.getBoolean("IsFalling");
        this.destroyRot = tag.getFloat("DestroyRot");
        this.postFallDamage = tag.getFloat("PostFallDamage");
        this.entityData.set(IS_FALLING, this.isFalling);
        this.entityData.set(HEALTH_FRACT, this.droneHealth < 0.0f ? 1.0f : Math.max(0.0f, this.droneHealth / maxDroneHealth()));
        this.entityData.set(SWERVE_DIR, tag.getInt("SwerveDir"));
        int launchState = tag.getInt("LaunchState");
        this.entityData.set(LAUNCH_STATE, launchState);
        if (tag.contains("CruiseAlt")) {
            this.entityData.set(CRUISE_ALT, tag.getInt("CruiseAlt"));
        }
        if (tag.contains("TakeoffX")) {
            this.takeoffOrigin = new Vec3(tag.getDouble("TakeoffX"), tag.getDouble("TakeoffY"), tag.getDouble("TakeoffZ"));
        }
        if (tag.contains("LaunchOriginX")) {
            this.launchOrigin = new Vec3(tag.getDouble("LaunchOriginX"), tag.getDouble("LaunchOriginY"), tag.getDouble("LaunchOriginZ"));
        }
        this.route = tag.getIntArray("Route");
        this.routeIndex = tag.getInt("RouteIndex");
        this.targetGroundResolved = tag.getBoolean("TargetGroundResolved");
        if (tag.contains("FinalLeg")) {
            this.entityData.set(FINAL_LEG, tag.getBoolean("FinalLeg"));
        }
        if (tag.contains("BoundTerminal")) {
            int[] p = tag.getIntArray("BoundTerminal");
            if (p.length == 3) {
                this.boundTerminal = new BlockPos(p[0], p[1], p[2]);
            }
        }
        this.noPhysics = launchState != STATE_PARKED && launchState != STATE_TAKEOFF;
        this.setNoGravity(launchState != STATE_PARKED);
        this.onRail = tag.getBoolean("OnRail");
        this.railPitch = tag.getFloat("RailPitch");
        this.railRun = tag.getDouble("RailRun");
        if (tag.contains("RailStartX")) {
            this.railStart = new Vec3(tag.getDouble("RailStartX"), tag.getDouble("RailStartY"), tag.getDouble("RailStartZ"));
        }
        if (this.onRail && launchState == STATE_PARKED) {
            this.noPhysics = true;
            this.setNoGravity(true);
        }
    }

    public float getDiveProgress() {
        return this.diveProgress;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    private PlayState predicate(AnimationState<ShahedEntity> animationState) {
        if (this.isParked()) {
            return PlayState.STOP;
        }
        if (this.entityData.get(IS_FALLING) || this.entityData.get(IS_DIVING)) {
            animationState.getController().setAnimation(RawAnimation.begin().thenLoop("animation.shahed.dive"));
        } else {
            animationState.getController().setAnimation(RawAnimation.begin().thenLoop("animation.shahed.fly"));
        }
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
    
    private <T extends ParticleOptions> void sendParticlesForced(ServerLevel level, T particle, double x, double y, double z, int count, double xOffset, double yOffset, double zOffset, double speed) {
        for (ServerPlayer player : level.players()) {
            level.sendParticles(player, particle, true, x, y, z, count, xOffset, yOffset, zOffset, speed);
        }
    }
}
