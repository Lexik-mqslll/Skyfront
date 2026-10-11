package dev.krutlexik.skyfront.client;

import dev.krutlexik.skyfront.client.renderer.ShahedRenderer;
import dev.krutlexik.skyfront.entity.ModEntities;
import dev.krutlexik.skyfront.particle.DebrisParticle;
import dev.krutlexik.skyfront.particle.ModParticles;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@OnlyIn(Dist.CLIENT)
@net.neoforged.fml.common.EventBusSubscriber(modid = "sfw", bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SHAHED.get(), ShahedRenderer::new);
        event.registerBlockEntityRenderer(dev.krutlexik.skyfront.block.entity.ModBlockEntities.SHAHED_TERMINAL.get(),
                dev.krutlexik.skyfront.client.renderer.ShahedTerminalRenderer::new);
        event.registerEntityRenderer(ModEntities.LAUNCHER.get(), dev.krutlexik.skyfront.client.renderer.LauncherRenderer::new);
        event.registerEntityRenderer(ModEntities.DEBRIS.get(), dev.krutlexik.skyfront.client.renderer.DebrisRenderer::new);
        System.out.println("[SFW] Renderer registered for ShahedEntity!");
    }

    /** The control post's item is drawn as the post itself, from the same glb its block renders. */
    @SubscribeEvent
    public static void registerClientExtensions(
            net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        dev.krutlexik.skyfront.drone.client.render.DroneItemRenderer renderer =
                new dev.krutlexik.skyfront.drone.client.render.DroneItemRenderer(
                        dev.krutlexik.skyfront.client.renderer.ShahedTerminalRenderer.MODEL);
        event.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return renderer;
            }
        }, dev.krutlexik.skyfront.block.ModBlocks.SHAHED_TERMINAL.get().asItem());

        dev.krutlexik.skyfront.drone.client.render.DroneItemRenderer launcher =
                new dev.krutlexik.skyfront.drone.client.render.DroneItemRenderer(
                        dev.krutlexik.skyfront.client.renderer.LauncherRenderer.MODEL);
        event.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return launcher;
            }
        }, dev.krutlexik.skyfront.item.ModItems.LAUNCHER.get());
    }
    
    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.DEBRIS.get(), DebrisParticle.Provider::new);
        event.registerSpriteSet(ModParticles.EXPLOSION_FLASH.get(), dev.krutlexik.skyfront.particle.FlashParticle.Provider::new);
        event.registerSpriteSet(ModParticles.EMBER.get(), dev.krutlexik.skyfront.particle.EmberParticle.Provider::new);
        event.registerSpriteSet(ModParticles.BLACK_SMOKE.get(), dev.krutlexik.skyfront.particle.BlackSmokeParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SHRAPNEL.get(), dev.krutlexik.skyfront.particle.ShrapnelParticle.Provider::new);
        System.out.println("[SFW] Particles registered!");
    }
}
