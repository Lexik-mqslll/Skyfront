package dev.krutlexik.skyfront.drone.mixin;

import dev.krutlexik.skyfront.drone.client.render.ThermalShaders;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Swaps the core shaders the world is drawn with while the thermal camera is on. */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "getRendertypeSolidShader", at = @At("HEAD"), cancellable = true)
    private static void skyfront$thermalSolid(CallbackInfoReturnable<ShaderInstance> cir) {
        skyfront$swap(cir, ThermalShaders.active() ? ThermalShaders.solid() : null);
    }

    @Inject(method = "getRendertypeCutoutShader", at = @At("HEAD"), cancellable = true)
    private static void skyfront$thermalCutout(CallbackInfoReturnable<ShaderInstance> cir) {
        skyfront$swap(cir, ThermalShaders.active() ? ThermalShaders.cutout() : null);
    }

    @Inject(method = "getRendertypeCutoutMippedShader", at = @At("HEAD"), cancellable = true)
    private static void skyfront$thermalCutoutMipped(CallbackInfoReturnable<ShaderInstance> cir) {
        skyfront$swap(cir, ThermalShaders.active() ? ThermalShaders.cutout() : null);
    }

    @Inject(method = "getRendertypeTranslucentShader", at = @At("HEAD"), cancellable = true)
    private static void skyfront$thermalTranslucent(CallbackInfoReturnable<ShaderInstance> cir) {
        skyfront$swap(cir, ThermalShaders.active() ? ThermalShaders.translucent() : null);
    }

    @Inject(method = "getRendertypeEntityCutoutNoCullShader", at = @At("HEAD"), cancellable = true)
    private static void skyfront$thermalEntityCutoutNoCull(CallbackInfoReturnable<ShaderInstance> cir) {
        skyfront$swap(cir, ThermalShaders.active() ? ThermalShaders.entitySolid() : null);
    }

    @Inject(method = "getRendertypeEntityTranslucentShader", at = @At("HEAD"), cancellable = true)
    private static void skyfront$thermalEntityTranslucent(CallbackInfoReturnable<ShaderInstance> cir) {
        skyfront$swap(cir, ThermalShaders.active() ? ThermalShaders.entitySolid() : null);
    }

    @Inject(method = "getParticleShader", at = @At("HEAD"), cancellable = true)
    private static void skyfront$thermalParticle(CallbackInfoReturnable<ShaderInstance> cir) {
        skyfront$swap(cir, ThermalShaders.active() ? ThermalShaders.particle() : null);
    }

    private static void skyfront$swap(CallbackInfoReturnable<ShaderInstance> cir, ShaderInstance replacement) {
        if (replacement != null) {
            cir.setReturnValue(replacement);
        }
    }
}
