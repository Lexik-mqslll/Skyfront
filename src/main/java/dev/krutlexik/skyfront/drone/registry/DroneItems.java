package dev.krutlexik.skyfront.drone.registry;

import dev.krutlexik.skyfront.drone.DroneMod;
import dev.krutlexik.skyfront.drone.item.DroneItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The drone's own items. */
public final class DroneItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DroneMod.MODID);

    public static final DeferredItem<Item> DRONE =
            ITEMS.registerItem("drone", DroneItem::new, new Item.Properties().stacksTo(1));

    /** The thermal drone: the same aircraft, its own item, and a camera that can see heat. */
    public static final DeferredItem<Item> THERMAL_DRONE =
            ITEMS.registerItem("thermal_drone",
                    properties -> new DroneItem(properties, DroneEntities.THERMAL_DRONE),
                    new Item.Properties().stacksTo(1));

    /** The interceptor: its own airframe, its own warhead, nothing else in common with the above. */
    public static final DeferredItem<Item> INTERCEPTOR_DRONE =
            ITEMS.registerItem("interceptor_drone",
                    properties -> new DroneItem(properties, DroneEntities.INTERCEPTOR_DRONE),
                    new Item.Properties().stacksTo(1));

    /** The interceptor with a thermal camera: the same airframe, its own item. */
    public static final DeferredItem<Item> THERMAL_INTERCEPTOR_DRONE =
            ITEMS.registerItem("thermal_interceptor_drone",
                    properties -> new DroneItem(properties, DroneEntities.THERMAL_INTERCEPTOR_DRONE),
                    new Item.Properties().stacksTo(1));

    /** The FPV quad, armed with whatever RPG round is hung on it. */
    public static final DeferredItem<Item> FPV_DRONE =
            ITEMS.registerItem("fpv_drone",
                    properties -> new DroneItem(properties, DroneEntities.FPV_DRONE),
                    new Item.Properties().stacksTo(1));

    /** The Molniya fixed-wing strike drone. */
    public static final DeferredItem<Item> MOLNIYA_DRONE =
            ITEMS.registerItem("molniya_drone",
                    properties -> new DroneItem(properties, DroneEntities.MOLNIYA_DRONE, true),
                    new Item.Properties().stacksTo(1));

    private DroneItems() {
    }
}
