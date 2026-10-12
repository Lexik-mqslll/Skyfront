package dev.krutlexik.skyfront.drone;

import dev.krutlexik.skyfront.SkyfrontMod;
import dev.krutlexik.skyfront.drone.registry.DroneEntities;
import dev.krutlexik.skyfront.drone.registry.DroneItems;
import dev.krutlexik.skyfront.drone.registry.DroneSounds;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import org.slf4j.Logger;

/** Registers the drones' items, entities and sounds. */
public final class DroneMod {
    public static final String MODID = SkyfrontMod.MOD_ID;
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Called from {@link SkyfrontMod}'s constructor, on the mod event bus. */
    public static void register(IEventBus modBus) {
        DroneItems.ITEMS.register(modBus);
        DroneEntities.ENTITIES.register(modBus);
        DroneSounds.SOUNDS.register(modBus);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }

    private DroneMod() {
    }
}
