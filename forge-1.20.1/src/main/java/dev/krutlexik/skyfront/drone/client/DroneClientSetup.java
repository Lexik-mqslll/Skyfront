package dev.krutlexik.skyfront.drone.client;

import dev.krutlexik.skyfront.drone.DroneMod;
import dev.krutlexik.skyfront.drone.client.render.DroneRenderer;
import dev.krutlexik.skyfront.drone.client.render.ThermalBadge;
import dev.krutlexik.skyfront.drone.registry.DroneEntities;
import dev.krutlexik.skyfront.drone.registry.DroneItems;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import dev.krutlexik.skyfront.drone.client.radio.RadioSettingsScreen;

@Mod.EventBusSubscriber(modid = DroneMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DroneClientSetup {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DroneEntities.DRONE.get(), DroneRenderer::new);
        event.registerEntityRenderer(DroneEntities.THERMAL_DRONE.get(), DroneRenderer::new);
        event.registerEntityRenderer(DroneEntities.INTERCEPTOR_DRONE.get(),
                context -> new DroneRenderer<>(context, DroneRenderer.FPV_MODEL, DroneRenderer.FPV_SCALE, true,
                        DroneRenderer.tailSitter(DroneRenderer.FPV_LEVEL_AT_PITCH)));
        event.registerEntityRenderer(DroneEntities.THERMAL_INTERCEPTOR_DRONE.get(),
                context -> new DroneRenderer<>(context, DroneRenderer.FPV_MODEL, DroneRenderer.FPV_SCALE, true,
                        DroneRenderer.tailSitter(DroneRenderer.FPV_LEVEL_AT_PITCH)));
        event.registerEntityRenderer(DroneEntities.FPV_DRONE.get(),
                context -> new DroneRenderer<>(context, DroneRenderer.FPV_QUAD_MODEL, DroneRenderer.FPV_QUAD_SCALE, true,
                        DroneRenderer.gentlerLean(0.45F)));
        event.registerEntityRenderer(DroneEntities.DRONE_STAND.get(),
                dev.krutlexik.skyfront.drone.client.render.DroneStandRenderer::new);
        event.registerEntityRenderer(DroneEntities.MOLNIYA_DRONE.get(),
                context -> new DroneRenderer<>(context, DroneRenderer.MOLNIYA_MODEL, DroneRenderer.MOLNIYA_SCALE, true,
                        DroneRenderer.fastCruise(DroneRenderer.MOLNIYA_LEVEL_AT_PITCH)));
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources -> {
            DroneRenderer.reload(resources);
            dev.krutlexik.skyfront.drone.client.render.ThermalScreenPass.unload();
            dev.krutlexik.skyfront.drone.client.render.ThermalPackPass.unload();
            dev.krutlexik.skyfront.drone.client.render.AnalogFeedPass.unload();
        });
    }

    /** The arming switch. */
    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(DroneKeys.ARM);
        event.register(DroneKeys.THERMAL);
        event.register(DroneKeys.RADIO_SETTINGS);
        event.register(DroneKeys.SELF_DESTRUCT);
    }

    /**
     * The radio setup doubles as the mod's own config screen, so it can be reached from the Mods list without
     * binding anything — the key for it starts out unbound (see {@code DroneKeys}).
     */
    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new RadioSettingsScreen(parent))));
    }

    /*
     * Every drone item is drawn as its own 3D airframe — the very glb the entity renderer flies — instead of a
     * hand-painted sprite: on Forge the item classes hand their renderer over themselves, through
     * Item#initializeClient (see dev.krutlexik.skyfront.client.ItemRenderers).
     */

    /** The thermal airframes carry a small "THERMAL" tag in the corner of their slot (see {@link ThermalBadge}). */
    @SubscribeEvent
    public static void registerItemDecorations(net.minecraftforge.client.event.RegisterItemDecorationsEvent event) {
        ThermalBadge badge = new ThermalBadge();
        event.register(DroneItems.THERMAL_DRONE.get(), badge);
        event.register(DroneItems.THERMAL_INTERCEPTOR_DRONE.get(), badge);
    }

    private DroneClientSetup() {
    }
}
