package dev.krutlexik.skyfront.client.sound;

import dev.krutlexik.skyfront.entity.ShahedEntity;
import dev.krutlexik.skyfront.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ShahedSoundInstance extends AbstractTickableSoundInstance {
    private final Minecraft client;
    private final ShahedEntity entity;
    private double lastDistance;
    private int fade = 0;
    private boolean die = false;
    private int soundTick = 0;
    private float damagePitchOffset = 0.0f;

    public ShahedSoundInstance(ShahedEntity entity) {
        super(ModSounds.SHAHED_ENGINE.get(), SoundSource.AMBIENT, entity.level().getRandom());
        this.client = Minecraft.getInstance();
        this.entity = entity;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0f;
        this.pitch = 1.0f;
        this.attenuation = Attenuation.NONE;
    }

    @Override
    public void tick() {
        var player = this.client.player;
        if (entity.isRemoved() || player == null) {
            this.stop();
            return;
        }

        if (this.die) {
            if (this.fade > 0) this.fade--;
            else if (this.fade == 0) {
                this.stop();
                return;
            }
        } else if (this.fade < 5) {
            this.fade++;
        }

        double distanceToPlayer = this.entity.position().distanceTo(dev.krutlexik.skyfront.client.ClientViewpoint.position());
        float maxDistance = 150.0f;
        float distanceFactor = Math.max(0.0f, 1.0f - (float)(distanceToPlayer / maxDistance));
        float baseVolume = 1.0f; 
        this.volume = baseVolume * (distanceFactor * distanceFactor) * (this.fade / 5.0f);

        this.x = this.entity.getX();
        this.y = this.entity.getY();
        this.z = this.entity.getZ();

        if (player.getVehicle() != this.entity) {
            double distance = this.entity.position().subtract(dev.krutlexik.skyfront.client.ClientViewpoint.position()).length();
            
            this.pitch = 1.0f + (float) (0.16 * Math.atan(lastDistance - distance));

            this.lastDistance = distance;
        } else {
            this.lastDistance = 0;
            this.pitch = 1.0f;
        }
        
        this.soundTick++;
        float offsetTarget = 0.0f;
        int launchState = this.entity.getLaunchState();
        if (this.entity.getIsFalling()) {
            offsetTarget = 0.35f + (float) Math.sin(this.soundTick * 0.45) * 0.03f;
            this.volume = Math.min(1.0f, this.volume * 1.3f);
        } else if (this.entity.getHealthFraction() <= 0.25f) {
            offsetTarget = (float) Math.sin(this.soundTick * 0.06) * 0.05f;
        } else if (launchState == dev.krutlexik.skyfront.entity.ShahedEntity.STATE_TAKEOFF
                || launchState == dev.krutlexik.skyfront.entity.ShahedEntity.STATE_CLIMB
                || launchState == dev.krutlexik.skyfront.entity.ShahedEntity.STATE_TURN) {
            float throttle = (float) Math.min(this.entity.getDeltaMovement().length()
                    / dev.krutlexik.skyfront.entity.ShahedEntity.CRUISE_SPEED, 1.0);
            offsetTarget = -0.4f * (1.0f - Math.max(0.25f, throttle));
            this.volume *= 0.3f + 0.7f * Math.max(0.25f, throttle);
        }
        this.damagePitchOffset = this.damagePitchOffset + (offsetTarget - this.damagePitchOffset) * 0.05f;
        this.pitch += this.damagePitchOffset;

        this.pitch = Math.max(0.5f, Math.min(2.0f, this.pitch));
    }
}
