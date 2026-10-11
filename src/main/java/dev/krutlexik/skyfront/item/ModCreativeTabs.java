package dev.krutlexik.skyfront.item;

import dev.krutlexik.skyfront.SkyfrontMod;
import dev.krutlexik.skyfront.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = 
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SkyfrontMod.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SKYFRONT_TAB = CREATIVE_MODE_TABS.register("skyfront_tab",
            () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(ModItems.SHAHED_136.get()))
                    .title(Component.translatable("itemGroup.sfw"))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.SHAHED_136.get());
                        output.accept(ModItems.LAUNCHER.get());
                        output.accept(ModBlocks.SHAHED_TERMINAL.get());
                        output.accept(ModBlocks.SHAHED_RADAR.get());
                        output.accept(ModBlocks.SIREN.get());
                        output.accept(dev.krutlexik.skyfront.drone.registry.DroneItems.DRONE.get());
                        output.accept(dev.krutlexik.skyfront.drone.registry.DroneItems.THERMAL_DRONE.get());
                        output.accept(dev.krutlexik.skyfront.drone.registry.DroneItems.INTERCEPTOR_DRONE.get());
                        output.accept(dev.krutlexik.skyfront.drone.registry.DroneItems.THERMAL_INTERCEPTOR_DRONE.get());
                        output.accept(dev.krutlexik.skyfront.drone.registry.DroneItems.FPV_DRONE.get());
                        output.accept(dev.krutlexik.skyfront.drone.registry.DroneItems.MOLNIYA_DRONE.get());
                        net.minecraft.world.item.ItemStack monitor = dev.krutlexik.skyfront.compat.sbw.SbwMonitor.newMonitor();
                        if (!monitor.isEmpty()) {
                            output.accept(monitor);
                        }
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
