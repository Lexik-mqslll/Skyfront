package dev.krutlexik.skyfront.client;

import dev.krutlexik.skyfront.client.renderer.ShahedRenderer;
import dev.krutlexik.skyfront.entity.ModEntities;
import dev.krutlexik.skyfront.particle.DebrisParticle;
import dev.krutlexik.skyfront.particle.ModParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = "sfw", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
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
