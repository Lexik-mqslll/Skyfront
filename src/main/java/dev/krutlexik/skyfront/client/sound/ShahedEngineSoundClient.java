package dev.krutlexik.skyfront.client.sound;

import dev.krutlexik.skyfront.entity.ShahedEntity;
import dev.krutlexik.skyfront.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Random;

@OnlyIn(Dist.CLIENT)
public class ShahedEngineSoundClient extends AbstractTickableSoundInstance {
    private final int entityId;
    private final Vec3 startPos;
    /** Current waypoint, refreshed from sync packets — a route target moves as legs advance. */
    private BlockPos targetPos;
    private final String diveMode;
    private boolean shouldStop = false;
    private boolean exploded = false;
    private boolean stopped = false;
    private long startTime;
    private long creationTime;
    private Vec3 explosionPos = null;
    private long explosionTime = 0L;
    private float pitchAtExplosion = 1.0f;
    private float volumeAtExplosion = 1.0f;
    private static final double CRUISE_SPEED = 1.5;
    private static final double MAX_SOUND_DISTANCE = 500.0;
    private double diveDistance;
    private double diveTransitionDistance;
    private double modeSwitchDistance;
    private float currentPitch = 1.0f;
    private float targetPitch = 1.0f;
    private float currentVolumeMultiplier = 1.0f;
    private float targetVolumeMultiplier = 1.0f;
    private int divingTicks = 0;
    private int maxDivingTicks;
    
    private boolean inModeSwitchPhase = false;
    private int modeSwitchTicks = 0;
    private static final int MODE_SWITCH_DURATION_TICKS = 30;
    private boolean modeSwitchComplete = false;
    
    private Vec3 currentDronePos;
    private Vec3 lastDirection = new Vec3(1, 0, 0);
    private float localDiveProgress = 0.0f;
    private boolean isLateJoin = false;

    private int soundTick = 0;
    private final Random damageRandom = new Random();
    private float damagePitch = 0.0f;
    private float damagePitchTarget = 0.0f;
    private int surgeRetargetIn = 0;
    private float sputterVol = 1.0f;
    private int sputterPhaseTicks = 0;
    private int sputterCooldown = 60;
    private float smoothedPitch = 1.0f;
    private float loudFactor = 1.0f;
    private int fallingTicks = 0;
    private float spool = -1.0f;
    private float packetHealthFract = 1.0f;
    private boolean packetFalling = false;
    private float packetDiveProgress = 0.0f;
    private boolean packetFinalLeg = true;

    public ShahedEngineSoundClient(int entityId, Vec3 currentPos, BlockPos targetPos, String diveMode) {
        super(ModSounds.SHAHED_ENGINE.get(), SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
        this.entityId = entityId;
        this.startPos = currentPos;
        this.currentDronePos = currentPos;
        this.targetPos = targetPos;
        this.diveMode = diveMode;
        this.looping = true;
        this.delay = 0;
        this.volume = 3.0f;
        this.pitch = 1.0f;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.creationTime = System.currentTimeMillis();
        this.startTime = this.creationTime;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            double distToStart = dev.krutlexik.skyfront.client.ClientViewpoint.position().distanceTo(currentPos);
            if (distToStart > 100) {
                this.isLateJoin = true;
                int soundDelayTicks = (int)(distToStart / 17.0);
                this.startTime = this.creationTime - (soundDelayTicks * 50L);
            }
        }
        
        if (diveMode.equals("high")) {
            this.diveDistance = 350.0;
            this.diveTransitionDistance = 500.0;
            this.modeSwitchDistance = 800.0;
            this.maxDivingTicks = 200;
        } else {
            this.diveDistance = 150.0;
            this.diveTransitionDistance = 280.0;
            this.modeSwitchDistance = 320.0;
            this.maxDivingTicks = 100;
        }
        
        this.x = currentPos.x;
        this.y = currentPos.y;
        this.z = currentPos.z;
        
        Vec3 targetVec = Vec3.atCenterOf(targetPos);
        Vec3 dir = targetVec.subtract(currentPos);
        if (dir.lengthSqr() > 0.01) {
            this.lastDirection = dir.normalize();
        }
    }
    
    public void updateDronePosition(Vec3 serverPos) {
        double dist = this.currentDronePos.distanceTo(serverPos);
        if (dist > 10.0) {
            this.currentDronePos = serverPos;
        } else if (dist > 3.0) {
            this.currentDronePos = this.currentDronePos.lerp(serverPos, 0.3);
        }
    }

    /** The drone's current waypoint (routes advance through several). */
    public void updateTarget(BlockPos targetPos) {
        this.targetPos = targetPos;
    }

    /** Flight state from the periodic sync packets (see ShahedSpawnPacket). */
    public void updateDamageState(float healthFract, boolean falling, float diveProgress, boolean finalLeg) {
        this.packetHealthFract = healthFract;
        this.packetFalling = falling;
        this.packetDiveProgress = diveProgress;
        this.packetFinalLeg = finalLeg;
    }

    public void start() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(this);
        }
    }

    public int getEntityId() {
        return this.entityId;
    }
    
    public boolean isExploded() {
        return this.exploded;
    }

    @Override
    public double getX() {
        return this.x;
    }

    @Override
    public double getY() {
        return this.y;
    }

    @Override
    public double getZ() {
        return this.z;
    }

    @Override
    public void tick() {
        if (this.stopped) {
            return;
        }
        if (this.shouldStop && !this.exploded) {
            this.volume = Math.max(0.0f, this.volume - 0.1f);
            if (this.volume <= 0.0f) {
                this.stopSoundCompletely();
            }
            return;
        }
        if (this.exploded) {
            this.handleExplosionSound();
            return;
        }
        this.updatePosition();
    }

    private void stopSoundCompletely() {
        this.stopped = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().stop(this);
        }
    }

    private void handleExplosionSound() {
        long currentTime = System.currentTimeMillis();
        long timeSinceExplosion = currentTime - this.explosionTime;
        
        long fadeTimeMs = 1500;
        
        if (timeSinceExplosion > fadeTimeMs) {
            this.forceStop();
            return;
        }
        
        float fadeProgress = (float) timeSinceExplosion / (float) fadeTimeMs;
        fadeProgress = Math.min(1.0f, Math.max(0.0f, fadeProgress));
        
        this.pitch = this.pitchAtExplosion * (1.0f - fadeProgress * 0.3f);
        this.volume = this.volumeAtExplosion * (1.0f - fadeProgress) * 0.5f;
        
        if (this.volume < 0.01f) {
            this.forceStop();
        }
    }
    
    private void forceStop() {
        this.volume = 0.0f;
        this.stopped = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().stop(this);
        }
    }

    private void updatePosition() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            this.stopSound();
            return;
        }
        LocalPlayer player = mc.player;

        float healthFract = this.packetHealthFract;
        boolean falling = this.packetFalling;
        int launchState = ShahedEntity.STATE_GUIDED;
        double actualSpeed = -1.0;
        boolean finalLeg = this.packetFinalLeg;
        float realDive = this.packetDiveProgress;
        Entity tracked = mc.level != null ? mc.level.getEntity(this.entityId) : null;
        if (tracked instanceof ShahedEntity shahed) {
            this.currentDronePos = shahed.position();
            healthFract = shahed.getHealthFraction();
            falling = shahed.getIsFalling();
            launchState = shahed.getLaunchState();
            actualSpeed = shahed.getDeltaMovement().length();
            finalLeg = shahed.isFinalLeg();
            realDive = shahed.getSyncedDiveProgress();
        }

        Vec3 targetVec = Vec3.atCenterOf(this.targetPos);
        
        double horizontalDist = Math.sqrt(
            Math.pow(targetVec.x - this.currentDronePos.x, 2.0) + 
            Math.pow(targetVec.z - this.currentDronePos.z, 2.0)
        );
        
        this.localDiveProgress = realDive;

        Vec3 toTarget = targetVec.subtract(this.currentDronePos);
        Vec3 moveDirection;
        if (toTarget.lengthSqr() < 4.0) {
            moveDirection = this.lastDirection;
        } else if (this.localDiveProgress < 0.01f) {
            Vec3 horizontalTarget = new Vec3(targetVec.x, this.currentDronePos.y, targetVec.z);
            Vec3 diff = horizontalTarget.subtract(this.currentDronePos);
            moveDirection = diff.lengthSqr() > 0.01 ? diff.normalize() : toTarget.normalize();
        } else {
            Vec3 horizontalTarget = new Vec3(targetVec.x, this.currentDronePos.y, targetVec.z);
            Vec3 horizontalDiff = horizontalTarget.subtract(this.currentDronePos);
            Vec3 horizontalDir = horizontalDiff.lengthSqr() > 0.01 ? horizontalDiff.normalize() : this.lastDirection;
            Vec3 directDir = toTarget.lengthSqr() > 0.01 ? toTarget.normalize() : this.lastDirection;
            moveDirection = new Vec3(
                horizontalDir.x + (directDir.x - horizontalDir.x) * this.localDiveProgress,
                horizontalDir.y + (directDir.y - horizontalDir.y) * this.localDiveProgress,
                horizontalDir.z + (directDir.z - horizontalDir.z) * this.localDiveProgress
            );
            if (moveDirection.lengthSqr() > 0.01) {
                moveDirection = moveDirection.normalize();
            } else {
                moveDirection = this.lastDirection;
            }
        }
        if (moveDirection.lengthSqr() > 0.01) {
            this.lastDirection = moveDirection;
        }
        
        double speed = CRUISE_SPEED + 2.1 * this.localDiveProgress;
        this.currentDronePos = this.currentDronePos.add(moveDirection.scale(speed));
        
        this.x = this.currentDronePos.x;
        this.y = this.currentDronePos.y;
        this.z = this.currentDronePos.z;

        Vec3 playerPos = dev.krutlexik.skyfront.client.ClientViewpoint.position();
        double dx = this.currentDronePos.x - playerPos.x;
        double dy = (this.currentDronePos.y - playerPos.y) * 0.3;
        double dz = this.currentDronePos.z - playerPos.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        
        float diveProgress = realDive;

        if (diveProgress > 0.02f && !this.modeSwitchComplete && !this.inModeSwitchPhase) {
            this.inModeSwitchPhase = true;
            this.modeSwitchTicks = 0;
        }

        if (!finalLeg) {
            this.divingTicks = 0;
            this.targetPitch = 1.0f;
            this.targetVolumeMultiplier = 1.0f;
        } else if (this.inModeSwitchPhase) {
            this.modeSwitchTicks++;
            float switchProgress = (float) this.modeSwitchTicks / (float) MODE_SWITCH_DURATION_TICKS;
            
            if (switchProgress < 0.5f) {
                float slowdownProgress = switchProgress * 2.0f;
                this.targetPitch = 1.0f - (slowdownProgress * 0.35f);
                this.targetVolumeMultiplier = 1.0f - (slowdownProgress * 0.25f);
            } else {
                float recoveryProgress = (switchProgress - 0.5f) * 2.0f;
                this.targetPitch = 0.65f + (recoveryProgress * 0.35f);
                this.targetVolumeMultiplier = 0.75f + (recoveryProgress * 0.25f);
            }
            
            if (this.modeSwitchTicks >= MODE_SWITCH_DURATION_TICKS) {
                this.inModeSwitchPhase = false;
                this.modeSwitchComplete = true;
            }
        } else if (this.modeSwitchComplete && diveProgress > 0.01f) {
            this.divingTicks = Math.min(this.divingTicks + 2, this.maxDivingTicks);
            float timeProgress = (float) this.divingTicks / (float) this.maxDivingTicks;
            this.targetPitch = 1.0f + timeProgress * 0.8f;
            this.targetVolumeMultiplier = 1.0f + timeProgress * 0.83f;
        } else if (!this.inModeSwitchPhase && !this.modeSwitchComplete) {
            this.divingTicks = Math.max(this.divingTicks - 1, 0);
            this.targetPitch = 1.0f;
            this.targetVolumeMultiplier = 1.0f;
        }
        
        float pitchChangeRate = this.inModeSwitchPhase ? 0.012f : 0.006f;
        if (this.currentPitch < this.targetPitch) {
            this.currentPitch = Math.min(this.currentPitch + pitchChangeRate, this.targetPitch);
        } else if (this.currentPitch > this.targetPitch) {
            this.currentPitch = Math.max(this.currentPitch - pitchChangeRate, this.targetPitch);
        }
        
        float volumeChangeRate = this.inModeSwitchPhase ? 0.02f : 0.015f;
        if (this.currentVolumeMultiplier < this.targetVolumeMultiplier) {
            this.currentVolumeMultiplier = Math.min(this.currentVolumeMultiplier + volumeChangeRate, this.targetVolumeMultiplier);
        } else if (this.currentVolumeMultiplier > this.targetVolumeMultiplier) {
            this.currentVolumeMultiplier = Math.max(this.currentVolumeMultiplier - volumeChangeRate, this.targetVolumeMultiplier);
        }
        
        this.soundTick++;
        float baseVolume = this.calculateVolume(distance);
        float pitchTarget;
        float volumeFactor = this.currentVolumeMultiplier;

        if (falling) {
            this.fallingTicks++;
            float ramp = Math.min(this.fallingTicks / 100.0f, 1.0f);
            float tremble = (float) Math.sin(this.soundTick * 0.45) * 0.03f;
            pitchTarget = 1.1f + ramp * 0.75f + tremble;
        } else if (healthFract <= 0.25f) {
            if (--this.surgeRetargetIn <= 0) {
                this.surgeRetargetIn = 15 + this.damageRandom.nextInt(20);
                this.damagePitchTarget = (this.damageRandom.nextFloat() - 0.65f) * 0.3f;
            }
            this.damagePitch = approach(this.damagePitch, this.damagePitchTarget, 0.008f);

            if (this.sputterPhaseTicks > 0) {
                this.sputterPhaseTicks--;
                if (this.sputterPhaseTicks == 0) {
                    this.sputterCooldown = 40 + this.damageRandom.nextInt(80);
                }
            } else if (--this.sputterCooldown <= 0) {
                this.sputterPhaseTicks = 5 + this.damageRandom.nextInt(8);
            }
            this.sputterVol = approach(this.sputterVol, this.sputterPhaseTicks > 0 ? 0.45f : 1.0f,
                    this.sputterPhaseTicks > 0 ? 0.1f : 0.05f);

            pitchTarget = this.currentPitch + this.damagePitch - (1.0f - this.sputterVol) * 0.2f;
            volumeFactor *= this.sputterVol;
        } else if (launchState == ShahedEntity.STATE_TAKEOFF || launchState == ShahedEntity.STATE_CLIMB
                || launchState == ShahedEntity.STATE_TURN) {
            float throttle = actualSpeed >= 0.0 ? (float) Math.min(actualSpeed / CRUISE_SPEED, 1.0) : 1.0f;
            if (this.spool < 0.0f) {
                this.spool = 0.12f;
                this.smoothedPitch = 0.55f;
            }
            this.spool = approach(this.spool, Math.max(0.25f, throttle), 0.015f);
            pitchTarget = 0.55f + 0.5f * this.spool;
            volumeFactor = 0.22f + 0.78f * this.spool;
        } else {
            pitchTarget = this.currentPitch;
        }

        this.loudFactor = approach(this.loudFactor, falling ? 1.8f + Math.min(this.fallingTicks / 100.0f, 1.0f) * 0.6f : 1.0f, 0.02f);
        this.smoothedPitch = approach(this.smoothedPitch, pitchTarget, falling ? 0.02f : 0.012f);
        this.pitch = Math.max(0.5f, Math.min(2.0f, this.smoothedPitch));
        this.volume = baseVolume * volumeFactor * this.loudFactor;

        if (finalLeg && this.currentDronePos.distanceTo(targetVec) < 2.0) {
            this.markExploded(this.currentDronePos);
        }
    }

    /** Moves {@code value} toward {@code target} by at most {@code step} — no audible jumps. */
    private static float approach(float value, float target, float step) {
        if (value < target) {
            return Math.min(value + step, target);
        }
        return Math.max(value - step, target);
    }

    private float calculateVolume(double distance) {
        if (distance >= 500.0) {
            return 0.0f;
        }
        if (distance < 30.0) {
            return 16.0f;
        }
        if (distance < 70.0) {
            float t = (float)((distance - 30.0) / 40.0);
            return 16.0f - t * 10.0f;
        }
        if (distance < 150.0) {
            float t = (float)((distance - 70.0) / 80.0);
            return 6.0f - t * 3.6f;
        }
        if (distance < 250.0) {
            float t = (float)((distance - 150.0) / 100.0);
            return 2.4f - t * 1.4f;
        }
        if (distance < 350.0) {
            float t = (float)((distance - 250.0) / 100.0);
            return 1.0f - t * 0.6f;
        }
        if (distance < 400.0) {
            float t = (float)((distance - 350.0) / 50.0);
            return 0.4f - t * 0.24f;
        }
        float t = (float)((distance - 400.0) / 100.0);
        return 0.16f * (1.0f - t);
    }

    public void markExploded(Vec3 position) {
        if (this.exploded || this.stopped) {
            return;
        }
        
        this.pitchAtExplosion = this.currentPitch;
        this.volumeAtExplosion = Math.min(this.volume, 3.0f);
        this.exploded = true;
        this.explosionPos = position;
        this.explosionTime = System.currentTimeMillis();
        
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            double dist = dev.krutlexik.skyfront.client.ClientViewpoint.position().distanceTo(position);
            if (dist < 50.0) {
                this.forceStop();
            }
        }
    }

    public void stopSound() {
        if (!this.exploded) {
            this.shouldStop = true;
        }
    }

    @Override
    public boolean isStopped() {
        return this.stopped;
    }
}
