package dev.krutlexik.skyfront.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Glowing blast shrapnel: a fullbright ember that flies out of the explosion, arcs under gravity, cools from
 * white-yellow through orange to dark red, leaves a thin smoke trail, and dies shortly after hitting the
 * ground.
 */
@OnlyIn(Dist.CLIENT)
public class EmberParticle extends TextureSheetParticle {
    private final float baseSize;

    protected EmberParticle(ClientLevel level, double x, double y, double z,
                            double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.lifetime = 45 + this.random.nextInt(45);
        this.hasPhysics = true;
        this.gravity = 0.55f + this.random.nextFloat() * 0.35f;
        this.baseSize = 0.14f + this.random.nextFloat() * 0.18f;
        this.quadSize = this.baseSize;
        this.rCol = 1.0f;
        this.gCol = 0.92f;
        this.bCol = 0.6f;
        this.alpha = 1.0f;
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

        this.yd -= 0.04 * this.gravity;
        this.move(this.xd, this.yd, this.zd);
        this.xd *= 0.96;
        this.yd *= 0.98;
        this.zd *= 0.96;

        if (this.onGround) {
            this.xd *= 0.5;
            this.zd *= 0.5;
            this.age += 2;
        } else if (this.random.nextInt(4) == 0) {
            this.level.addParticle(ParticleTypes.SMOKE, this.x, this.y, this.z, 0.0, 0.01, 0.0);
        }

        float progress = (float) this.age / (float) this.lifetime;
        this.gCol = Math.max(0.15f, 0.92f - 0.72f * progress * progress);
        this.bCol = Math.max(0.02f, 0.6f - 0.8f * progress);
        this.quadSize = this.baseSize * (1.0f - 0.5f * progress);
        if (progress > 0.75f) {
            this.alpha = Math.max(0.0f, 1.0f - (progress - 0.75f) * 4.0f);
        }
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
            return new EmberParticle(level, x, y, z, vx, vy, vz, this.sprites);
        }
    }
}
