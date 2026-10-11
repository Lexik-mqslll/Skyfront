package dev.krutlexik.skyfront.drone.entity;

import dev.krutlexik.skyfront.drone.registry.DroneItems;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** The interceptor with a thermal camera in its nose. */
public class ThermalInterceptorDroneEntity extends InterceptorDroneEntity {
    public ThermalInterceptorDroneEntity(EntityType<? extends ThermalInterceptorDroneEntity> type,
                                         Level level) {
        super(type, level);
    }

    @Override
    public boolean hasThermalCamera() {
        return true;
    }

    @Override
    public ItemStack packedItem() {
        return new ItemStack(DroneItems.THERMAL_INTERCEPTOR_DRONE.get());
    }
}
