package dev.krutlexik.skyfront.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The detonation flash: a huge yellow-white fullbright billboard that blooms slightly and burns out in about
 * half a second (~11 ticks).
 */
@OnlyIn(Dist.CLIENT)
public class FlashParticle extends TextureSheetParticle {
    private final float baseSize;

    protected FlashParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        this.lifetime = 11;
        this.hasPhysics = false;
        this.gravity = 0.0f;
        this.baseSize = 9.0f + this.random.nextFloat() * 3.0f;
        this.quadSize = this.baseSize;
        this.rCol = 1.0f;
        this.gCol = 0.88f;
        this.bCol = 0.5f;
        this.alpha = 0.9f;
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        float progress = (float) this.age / (float) this.lifetime;
        float fade = (1.0f - progress) * (1.0f - progress);
        this.alpha = 0.9f * fade;
        this.gCol = 0.88f - 0.18f * progress;
        this.bCol = 0.5f - 0.25f * progress;
    }

    @Override
    public float getQuadSize(float partialTick) {
        float progress = ((float) this.age + partialTick) / (float) this.lifetime;
        return this.baseSize * (1.0f + 0.5f * Math.min(progress * 2.0f, 1.0f));
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                double x, double y, double z, double vx, double vy, double vz) {
            return new FlashParticle(level, x, y, z, this.sprites);
        }
    }
}
