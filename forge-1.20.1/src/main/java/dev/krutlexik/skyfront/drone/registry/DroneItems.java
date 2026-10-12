package dev.krutlexik.skyfront.drone.registry;

import dev.krutlexik.skyfront.drone.DroneMod;
import dev.krutlexik.skyfront.drone.item.DroneItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Function;

/** The drone's own items. */
public final class DroneItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, DroneMod.MODID);

    public static final RegistryObject<Item> DRONE =
            registerItem("drone", DroneItem::new, new Item.Properties().stacksTo(1));

    /** The thermal drone: the same aircraft, its own item, and a camera that can see heat. */
    public static final RegistryObject<Item> THERMAL_DRONE =
            registerItem("thermal_drone",
                    properties -> new DroneItem(properties, DroneEntities.THERMAL_DRONE),
                    new Item.Properties().stacksTo(1));

    /** The interceptor: its own airframe, its own warhead, nothing else in common with the above. */
    public static final RegistryObject<Item> INTERCEPTOR_DRONE =
            registerItem("interceptor_drone",
                    properties -> new DroneItem(properties, DroneEntities.INTERCEPTOR_DRONE),
                    new Item.Properties().stacksTo(1));

    /** The interceptor with a thermal camera: the same airframe, its own item. */
    public static final RegistryObject<Item> THERMAL_INTERCEPTOR_DRONE =
            registerItem("thermal_interceptor_drone",
                    properties -> new DroneItem(properties, DroneEntities.THERMAL_INTERCEPTOR_DRONE),
                    new Item.Properties().stacksTo(1));

    /** The FPV quad, armed with whatever RPG round is hung on it. */
    public static final RegistryObject<Item> FPV_DRONE =
            registerItem("fpv_drone",
                    properties -> new DroneItem(properties, DroneEntities.FPV_DRONE),
                    new Item.Properties().stacksTo(1));

    /** The Molniya fixed-wing strike drone. */
    public static final RegistryObject<Item> MOLNIYA_DRONE =
            registerItem("molniya_drone",
                    properties -> new DroneItem(properties, DroneEntities.MOLNIYA_DRONE, true),
                    new Item.Properties().stacksTo(1));

    /** NeoForge's DeferredRegister.Items#registerItem: the item is built from these properties when registered. */
    private static RegistryObject<Item> registerItem(String name, Function<Item.Properties, ? extends Item> factory,
                                                     Item.Properties properties) {
        return ITEMS.register(name, () -> factory.apply(properties));
    }

    private DroneItems() {
    }
}
