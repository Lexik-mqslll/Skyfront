package dev.krutlexik.skyfront.drone.client;

import dev.krutlexik.skyfront.drone.entity.DroneEntity;
import dev.krutlexik.skyfront.drone.registry.DroneSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/** The propeller loop that follows one drone. */
public class DroneSoundInstance extends AbstractTickableSoundInstance {
    private static final float IDLE_VOLUME = 0.32F;
    private static final float MAX_VOLUME = 0.55F;
    private static final float VOLUME_STEP = 0.05F;

    private static final float IDLE_PITCH = 0.92F;
    private static final float MAX_PITCH = 1.08F;
    private static final float SPIN_DOWN_PITCH = 0.55F;
    private static final float WRECKED_PITCH = 0.42F;
    private static final float PITCH_STEP = 0.035F;

    /** Advances the uneven surge of a wrecked rotor. */
    private float wreckWobble;

    private final DroneEntity drone;
    private float targetVolume;

    public DroneSoundInstance(DroneEntity drone) {
        super(DroneSounds.DRONE_LOOP.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.drone = drone;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0F;
        this.pitch = IDLE_PITCH;
        this.x = drone.getX();
        this.y = drone.getY();
        this.z = drone.getZ();
    }

    /** Lets the instance start while still silent (fading in) instead of being culled immediately. */
    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        boolean airborne = !this.drone.isRemoved() && !this.drone.isGrounded();

        if (!this.drone.isRemoved()) {
            this.x = this.drone.getX();
            this.y = this.drone.getY();
            this.z = this.drone.getZ();
        }

        if (this.drone.isWrecked()) {
            this.wreckWobble += 0.5F;
            float wobble = Mth.sin(this.wreckWobble) * 0.12F;
            this.targetVolume = MAX_VOLUME;
            this.pitch = approach(this.pitch, WRECKED_PITCH + wobble, PITCH_STEP * 3.0F);
            this.volume = approach(this.volume, this.targetVolume, VOLUME_STEP);
            if (this.drone.isRemoved()) {
                this.stop();
            }
            return;
        }

        if (airborne) {
            float speed = (float) this.drone.getDeltaMovement().length();
            float throttle = Mth.clamp(speed / 0.9F, 0.0F, 1.0F);
            float ceiling = this.drone.getCharge() > 0.0F ? MAX_VOLUME : IDLE_VOLUME;
            float topPitch = this.drone.getCharge() > 0.0F ? MAX_PITCH : IDLE_PITCH;
            this.targetVolume = Mth.lerp(throttle, IDLE_VOLUME, ceiling);
            this.pitch = approach(this.pitch, Mth.lerp(throttle, IDLE_PITCH, topPitch), PITCH_STEP);
        } else {
            this.targetVolume = 0.0F;
            this.pitch = approach(this.pitch, SPIN_DOWN_PITCH, PITCH_STEP);
        }

        this.volume = approach(this.volume, this.targetVolume, VOLUME_STEP);

        if (this.drone.isRemoved() && this.volume <= 0.001F) {
            this.stop();
        }
    }

    private static float approach(float value, float goal, float step) {
        if (value < goal) {
            return Math.min(value + step, goal);
        }
        return Math.max(value - step, goal);
    }
}
