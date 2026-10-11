package dev.krutlexik.skyfront.drone.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import dev.krutlexik.skyfront.drone.client.render.ThermalShaders;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * SuperbWarfare draws its explosions' flash with a soft-particle shader of its own rather than the game's
 * particle shader, so swapping that one (see {@code GameRendererMixin}) never reaches it and the brightest
 * part of the blast stayed cold to the thermal camera.
 */
@Pseudo
@Mixin(targets = {
        "com.atsuishio.superbwarfare.client.renderer.ModParticleRenderTypes$PARTICLE_SHEET_SOFT_TRANSLUCENT$1",
        "com.atsuishio.superbwarfare.client.renderer.ModParticleRenderTypes$1"},
        remap = false)
public class SbwSoftParticleMixin {
    @Inject(method = "begin", at = @At("RETURN"), require = 0)
    private void sfw$thermal(Tesselator tesselator, TextureManager textures, CallbackInfoReturnable<BufferBuilder> cir) {
        ShaderInstance thermal = ThermalShaders.active() ? ThermalShaders.particle() : null;
        if (thermal != null) {
            RenderSystem.setShader(() -> thermal);
        }
    }
}
