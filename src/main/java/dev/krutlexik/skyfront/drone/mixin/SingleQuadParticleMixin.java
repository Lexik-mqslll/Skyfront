package dev.krutlexik.skyfront.drone.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.krutlexik.skyfront.drone.client.render.ThermalParticles;
import dev.krutlexik.skyfront.drone.client.render.ThermalShaders;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks a hot particle for the thermal camera as it is drawn. */
@Mixin(SingleQuadParticle.class)
public abstract class SingleQuadParticleMixin {
    /** The mark's red for smoke and fragments: still a mark, one step down from full. */
    @Unique
    private static final float MOTTLED_RED = 252.0F / 255.0F;

    @Unique
    private boolean sfw$marked;
    @Unique
    private float sfw$red;
    @Unique
    private float sfw$green;
    @Unique
    private float sfw$blue;

    @Inject(method = "render", at = @At("HEAD"))
    private void sfw$markHot(VertexConsumer buffer, Camera camera, float partialTicks, CallbackInfo ci) {
        this.sfw$marked = false;
        if (!ThermalShaders.active() || ThermalShaders.particle() == null) {
            return;
        }
        ParticleAccessor particle = (ParticleAccessor) this;
        float heat = ThermalParticles.heat((Particle) (Object) this, particle.sfw$getAge(), particle.sfw$getLifetime());
        if (heat <= 0.0F) {
            return;
        }
        this.sfw$red = particle.sfw$getRed();
        this.sfw$green = particle.sfw$getGreen();
        this.sfw$blue = particle.sfw$getBlue();
        particle.sfw$setRed(ThermalParticles.mottled((Particle) (Object) this) ? MOTTLED_RED : 1.0F);
        particle.sfw$setGreen(0.0F);
        particle.sfw$setBlue(Math.max(0.03F, heat));
        this.sfw$marked = true;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void sfw$unmark(VertexConsumer buffer, Camera camera, float partialTicks, CallbackInfo ci) {
        if (this.sfw$marked) {
            ParticleAccessor particle = (ParticleAccessor) this;
            particle.sfw$setRed(this.sfw$red);
            particle.sfw$setGreen(this.sfw$green);
            particle.sfw$setBlue(this.sfw$blue);
            this.sfw$marked = false;
        }
    }
}
