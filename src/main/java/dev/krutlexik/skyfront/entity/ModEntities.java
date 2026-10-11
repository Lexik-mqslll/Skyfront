package dev.krutlexik.skyfront.entity;

import dev.krutlexik.skyfront.SkyfrontMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, SkyfrontMod.MOD_ID);
    public static final DeferredHolder<EntityType<?>, EntityType<ShahedEntity>> SHAHED = ENTITY_TYPES.register("shahed", 
            () -> EntityType.Builder.<ShahedEntity>of(ShahedEntity::new, MobCategory.MISC)
                    .sized(3.0f, 1.5f)
                    .clientTrackingRange(600)
                    .updateInterval(1)
                    .setShouldReceiveVelocityUpdates(true)
                    .build("shahed"));
    /** A burnt piece of a Shahed or a Molniya that blew up, lying where it came down (sized per piece). */
    public static final DeferredHolder<EntityType<?>, EntityType<DebrisEntity>> DEBRIS = ENTITY_TYPES.register("debris",
            () -> EntityType.Builder.<DebrisEntity>of(DebrisEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.3f)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .setShouldReceiveVelocityUpdates(true)
                    .build("debris"));
    /** The Shahed / Molniya launch rail: a frame about three blocks long, chest high. */
    public static final DeferredHolder<EntityType<?>, EntityType<LauncherEntity>> LAUNCHER = ENTITY_TYPES.register("launcher",
            () -> EntityType.Builder.<LauncherEntity>of(LauncherEntity::new, MobCategory.MISC)
                    .sized(3.1f, 1.7f)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .build("launcher"));

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
