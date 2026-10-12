package dev.krutlexik.skyfront.entity;

import dev.krutlexik.skyfront.SkyfrontMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, SkyfrontMod.MOD_ID);
    public static final RegistryObject<EntityType<ShahedEntity>> SHAHED = ENTITY_TYPES.register("shahed", 
            () -> EntityType.Builder.<ShahedEntity>of(ShahedEntity::new, MobCategory.MISC)
                    .sized(3.0f, 1.5f)
                    .clientTrackingRange(600)
                    .updateInterval(1)
                    .setShouldReceiveVelocityUpdates(true)
                    .build("shahed"));
    /** A burnt piece of a Shahed or a Molniya that blew up, lying where it came down (sized per piece). */
    public static final RegistryObject<EntityType<DebrisEntity>> DEBRIS = ENTITY_TYPES.register("debris",
            () -> EntityType.Builder.<DebrisEntity>of(DebrisEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.3f)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .setShouldReceiveVelocityUpdates(true)
                    .build("debris"));
    /** The Shahed / Molniya launch rail: a frame about three blocks long, chest high. */
    public static final RegistryObject<EntityType<LauncherEntity>> LAUNCHER = ENTITY_TYPES.register("launcher",
            () -> EntityType.Builder.<LauncherEntity>of(LauncherEntity::new, MobCategory.MISC)
                    .sized(3.1f, 1.7f)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .build("launcher"));

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
