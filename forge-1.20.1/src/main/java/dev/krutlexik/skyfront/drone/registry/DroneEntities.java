package dev.krutlexik.skyfront.drone.registry;

import dev.krutlexik.skyfront.drone.DroneMod;
import dev.krutlexik.skyfront.drone.entity.DroneEntity;
import dev.krutlexik.skyfront.drone.entity.InterceptorDroneEntity;
import dev.krutlexik.skyfront.drone.entity.ThermalDroneEntity;
import dev.krutlexik.skyfront.drone.entity.ThermalInterceptorDroneEntity;
import dev.krutlexik.skyfront.drone.entity.MolniyaDroneEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

public final class DroneEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, DroneMod.MODID);

    public static final RegistryObject<EntityType<DroneEntity>> DRONE =
            ENTITIES.register("drone", () -> EntityType.Builder.<DroneEntity>of(DroneEntity::new, MobCategory.MISC)
                    .sized(1.0F, 0.35F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("drone"));

    /** The thermal drone. */
    public static final RegistryObject<EntityType<ThermalDroneEntity>> THERMAL_DRONE =
            ENTITIES.register("thermal_drone", () -> EntityType.Builder
                    .<ThermalDroneEntity>of(ThermalDroneEntity::new, MobCategory.MISC)
                    .sized(1.0F, 0.35F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .build("thermal_drone"));

    /** The interceptor. */
    public static final RegistryObject<EntityType<InterceptorDroneEntity>> INTERCEPTOR_DRONE =
            ENTITIES.register("interceptor_drone", () -> EntityType.Builder
                    .<InterceptorDroneEntity>of(InterceptorDroneEntity::new, MobCategory.MISC)
                    .sized(1.1F, 0.8F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .build("interceptor_drone"));

    /** The interceptor with a thermal camera. */
    public static final RegistryObject<EntityType<ThermalInterceptorDroneEntity>>
            THERMAL_INTERCEPTOR_DRONE = ENTITIES.register("thermal_interceptor_drone",
                    () -> EntityType.Builder
                            .<ThermalInterceptorDroneEntity>of(ThermalInterceptorDroneEntity::new,
                                    MobCategory.MISC)
                            .sized(1.1F, 0.8F)
                            .clientTrackingRange(16)
                            .updateInterval(1)
                            .build("thermal_interceptor_drone"));

    /** The FPV quad: the interceptor's flight on a smaller airframe, with an RPG round for a warhead. */
    public static final RegistryObject<EntityType<dev.krutlexik.skyfront.drone.entity.FpvDroneEntity>>
            FPV_DRONE = ENTITIES.register("fpv_drone", () -> EntityType.Builder
                    .<dev.krutlexik.skyfront.drone.entity.FpvDroneEntity>of(
                            dev.krutlexik.skyfront.drone.entity.FpvDroneEntity::new, MobCategory.MISC)
                    .sized(0.85F, 0.5F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .build("fpv_drone"));

    /** The bricks an FPV quad is set down on (see {@link dev.krutlexik.skyfront.drone.entity.DroneStandEntity}). */
    public static final RegistryObject<EntityType<dev.krutlexik.skyfront.drone.entity.DroneStandEntity>>
            DRONE_STAND = ENTITIES.register("drone_stand", () -> EntityType.Builder
                    .<dev.krutlexik.skyfront.drone.entity.DroneStandEntity>of(
                            dev.krutlexik.skyfront.drone.entity.DroneStandEntity::new, MobCategory.MISC)
                    .sized(0.64F, (float) dev.krutlexik.skyfront.drone.entity.FpvDroneEntity.STAND_HEIGHT)
                    .clientTrackingRange(16)
                    .updateInterval(20)
                    .build("drone_stand"));

    /** The Molniya: a fixed-wing strike drone flown exactly as the interceptor is. */
    public static final RegistryObject<EntityType<MolniyaDroneEntity>> MOLNIYA_DRONE =
            ENTITIES.register("molniya_drone", () -> EntityType.Builder
                    .<MolniyaDroneEntity>of(MolniyaDroneEntity::new, MobCategory.MISC)
                    .sized(4.2F, 1.0F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .build("molniya_drone"));

    private DroneEntities() {
    }
}
