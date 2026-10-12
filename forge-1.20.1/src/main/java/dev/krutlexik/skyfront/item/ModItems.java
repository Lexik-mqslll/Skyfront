package dev.krutlexik.skyfront.item;

import dev.krutlexik.skyfront.SkyfrontMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, SkyfrontMod.MOD_ID);
    public static final RegistryObject<Item> SHAHED_136 = ITEMS.register("shahed_136", () -> new ShahedItem(new Item.Properties()));
    public static final RegistryObject<Item> LAUNCHER = ITEMS.register("launcher", () -> new LauncherItem(new Item.Properties().stacksTo(16)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
