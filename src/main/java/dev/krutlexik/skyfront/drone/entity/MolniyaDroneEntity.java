package dev.krutlexik.skyfront.drone.entity;

import dev.krutlexik.skyfront.drone.registry.DroneItems;
import dev.krutlexik.skyfront.entity.DebrisEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** The Molniya ("Lightning"): a small fixed-wing strike drone with a propeller in its nose. */
public class MolniyaDroneEntity extends InterceptorDroneEntity {
    public MolniyaDroneEntity(EntityType<? extends MolniyaDroneEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean fliesAsAircraft() {
        return true;
    }

    /** Its own toughness, not the interceptor's (see {@code molniyaHitpoints}). */
    @Override
    protected double maxHitpoints() {
        return dev.krutlexik.skyfront.config.SkyfrontConfig.MOLNIYA_HITPOINTS.get();
    }

    /** Always flown on sticks, with its whole attitude simulated: a plane has no arcade mode to fall back to. */
    @Override
    public boolean rateMode() {
        return true;
    }

    /** Its own top speed, not the interceptor's: 113 km/h flat out. */
    @Override
    public double topSpeedKmh() {
        return 113.0D;
    }

    /** A plane flies level, nose on the horizon: its camera looks only a little above the airframe. */
    @Override
    public float defaultCameraTilt() {
        return 10.0F;
    }

    /** Its camera is built into the nose at that one angle: the scroll wheel and the radio's knob leave it be. */
    @Override
    public boolean tiltableCamera() {
        return false;
    }

    /** Its warhead is in the nose on a contact fuse: flown nose-first into anything, it goes off. */
    @Override
    public boolean noseFused() {
        return true;
    }

    /** Its camera sends analog video: a clean-ish picture up close, breaking up with range. */
    @Override
    public boolean analogVideo() {
        return true;
    }

    @Override
    public Osd osdStyle() {
        return Osd.INAV;
    }

    /**
     * The warhead goes off as the interceptor's does — and the airframe comes apart, its burnt pieces thrown
     * clear (all but the warhead itself, which is what went off).
     */
    @Override
    protected void goOff(ServerLevel level, @Nullable Entity attacker) {
        super.goOff(level, attacker);
        Quaternionf attitude = renderRotation(1.0F);
        if (attitude == null) {
            attitude = new Quaternionf().rotationY((180.0F - getYRot()) * Mth.DEG_TO_RAD);
        }
        DebrisEntity.burst(level, DebrisEntity.Airframe.MOLNIYA, position(), attitude, getDeltaMovement());
    }

    @Override
    public ItemStack packedItem() {
        return new ItemStack(DroneItems.MOLNIYA_DRONE.get());
    }
}
