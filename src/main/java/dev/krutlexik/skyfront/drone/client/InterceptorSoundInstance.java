package dev.krutlexik.skyfront.drone.client;

import dev.krutlexik.skyfront.config.SkyfrontConfig;
import dev.krutlexik.skyfront.drone.entity.DroneEntity;
import dev.krutlexik.skyfront.drone.registry.DroneSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * The interceptor's motor loop, which is a different animal from the Mavic's buzz: it is tied to the
 * arming switch rather than to being airborne, and it climbs in pitch as the airframe is driven, the way a
 * quad's motors whine higher the harder they are worked.
 */
public class InterceptorSoundInstance extends AbstractTickableSoundInstance {
    /** Armed and idling: clearly there, but still well back from what it sounds like driven hard. */
    private static final float IDLE_VOLUME = 0.5F;
    /**
     * Flat out, and as loud as a sound can be asked to go: {@code SoundEngine} clamps the gain it actually
     * applies to 1.0, so nothing above this buys anything at the source.
     */
    private static final float MAX_VOLUME = 1.0F;

    /**
     * The recording is a quad hanging on mid throttle, so its own pitch IS the idle note and the idle is held
     * exactly there — no drift, no wobble, nothing riding on it.
     */
    private static final float IDLE_PITCH = 1.0F;
    /** Top speed, where the motors are screaming. */
    private static final float MAX_PITCH = 1.75F;

    /** Motors spool, they do not step: how far volume and pitch close on their target each tick. */
    private static final float VOLUME_STEP = 0.06F;
    private static final float PITCH_STEP = 0.05F;
    /** Cutting the motors drops the note away faster than driving them raises it. */
    private static final float DISARM_STEP = 0.12F;

    private final DroneEntity drone;

    public InterceptorSoundInstance(DroneEntity drone) {
        super(loopFor(drone), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.drone = drone;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0F;
        this.pitch = IDLE_PITCH;
        this.x = drone.getX();
        this.y = drone.getY();
        this.z = drone.getZ();
    }

    private static net.minecraft.sounds.SoundEvent loopFor(DroneEntity drone) {
        if (drone instanceof dev.krutlexik.skyfront.drone.entity.FpvDroneEntity) {
            return DroneSounds.FPV_LOOP.get();
        }
        if (drone instanceof dev.krutlexik.skyfront.drone.entity.MolniyaDroneEntity) {
            return DroneSounds.MOLNIYA_LOOP.get();
        }
        return DroneSounds.INTERCEPTOR_LOOP.get();
    }

    /** Lets the loop exist while still silent (disarmed) instead of being culled the moment it starts. */
    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        if (!this.drone.isRemoved()) {
            this.x = this.drone.getX();
            this.y = this.drone.getY();
            this.z = this.drone.getZ();
        }

        if (this.drone.motorsDead()) {
            this.volume = approach(this.volume, 0.0F, DISARM_STEP);
            this.pitch = approach(this.pitch, IDLE_PITCH, DISARM_STEP);
            if (this.drone.isRemoved() && this.volume <= 0.001F) {
                this.stop();
            }
            return;
        }

        double topSpeed = Math.max(1.0D,
                this.drone instanceof dev.krutlexik.skyfront.drone.entity.InterceptorDroneEntity interceptor
                        ? interceptor.topSpeedKmh() : SkyfrontConfig.INTERCEPTOR_TOP_SPEED.get());
        double speedKmh = this.drone.getDeltaMovement().length() * SkyfrontConfig.KMH_PER_BLOCK_TICK;
        float drive = (float) Mth.clamp(speedKmh / topSpeed, 0.0D, 1.0D);

        this.volume = approach(this.volume, Mth.lerp(drive, IDLE_VOLUME, MAX_VOLUME), VOLUME_STEP);
        this.pitch = approach(this.pitch, Mth.lerp(drive, IDLE_PITCH, MAX_PITCH), PITCH_STEP);
    }

    private static float approach(float value, float goal, float step) {
        if (value < goal) {
            return Math.min(value + step, goal);
        }
        return Math.max(value - step, goal);
    }
}
