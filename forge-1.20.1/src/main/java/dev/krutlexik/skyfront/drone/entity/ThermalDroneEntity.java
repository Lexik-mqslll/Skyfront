package dev.krutlexik.skyfront.drone.entity;

import dev.krutlexik.skyfront.config.SkyfrontConfig;
import dev.krutlexik.skyfront.drone.DroneTuning;
import dev.krutlexik.skyfront.drone.registry.DroneItems;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The thermal drone: the same aircraft as {@link DroneEntity} in every respect that can be flown, shot at or
 * loaded with ordnance — it deliberately overrides nothing but its own figures, so the flight model, the
 * damage model, the stores, the wreck, the chunk corridor and the camera are literally the same code — plus
 * the one thing that makes it worth its own airframe: its camera can be switched to thermal, which is
 * entirely a client-side view mode (see {@code dev.krutlexik.skyfront.drone.client.ThermalVision}) and needs
 * nothing of the entity beyond being recognisable as this class.
 */
public class ThermalDroneEntity extends DroneEntity {
    /** How much thrust to give it beyond the bare minimum its top speed needs. */
    private static final double THRUST_MARGIN = 1.25D;

    public ThermalDroneEntity(EntityType<? extends ThermalDroneEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean hasThermalCamera() {
        return true;
    }

    @Override
    public double controlRange() {
        return SkyfrontConfig.MAVIC_3T_CONTROL_RANGE.get();
    }

    @Override
    public int fullFlightTicks() {
        return (int) DroneTuning.get(DroneTuning.Value.MAVIC_3T_FLIGHT_SECONDS) * 20;
    }

    @Override
    protected double topSpeed() {
        return DroneTuning.get(DroneTuning.Value.MAVIC_3T_TOP_SPEED) / SkyfrontConfig.KMH_PER_BLOCK_TICK;
    }

    /**
     * Thrust derived from the configured top speed rather than fixed, so the number in the config is a speed
     * the drone actually flies at instead of merely a ceiling it never reaches.
     */
    @Override
    protected float thrust() {
        double keep = AIR_FRICTION * HORIZONTAL_BOOST;
        double needed = topSpeed() * (1.0D - keep) / keep * THRUST_MARGIN;
        return (float) Math.max(needed, super.thrust());
    }

    @Override
    public ItemStack packedItem() {
        return new ItemStack(DroneItems.THERMAL_DRONE.get());
    }
}
