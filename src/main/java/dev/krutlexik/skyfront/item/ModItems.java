package dev.krutlexik.skyfront.item;

import dev.krutlexik.skyfront.SkyfrontMod;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;

import net.neoforged.neoforge.registries.DeferredItem;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyfrontMod.MOD_ID);
    public static final DeferredItem<Item> SHAHED_136 = ITEMS.register("shahed_136", () -> new ShahedItem(new Item.Properties()));
    public static final DeferredItem<Item> LAUNCHER = ITEMS.register("launcher", () -> new LauncherItem(new Item.Properties().stacksTo(16)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
