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

/** Square blast shrapnel. */
@OnlyIn(Dist.CLIENT)
public class ShrapnelParticle extends TextureSheetParticle {
    private final boolean burning;
    private final float rotationSpeed;

    protected ShrapnelParticle(ClientLevel level, double x, double y, double z,
                               double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.burning = this.random.nextFloat() < 0.55f;
        this.lifetime = 60 + this.random.nextInt(60);
        this.hasPhysics = true;
        this.gravity = 0.6f + this.random.nextFloat() * 0.35f;
        this.quadSize = 0.133f + this.random.nextFloat() * 0.133f;
        this.rotationSpeed = (this.random.nextFloat() - 0.5f) * 0.5f;
        this.roll = this.random.nextFloat() * ((float) Math.PI * 2.0f);
        this.oRoll = this.roll;
        if (this.burning) {
            this.rCol = 1.0f;
            this.gCol = 0.55f + this.random.nextFloat() * 0.25f;
            this.bCol = 0.12f;
        } else {
            float shade = 0.08f + this.random.nextFloat() * 0.12f;
            this.rCol = shade;
            this.gCol = shade;
            this.bCol = shade;
        }
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

        this.oRoll = this.roll;
        this.roll += this.rotationSpeed * (this.onGround ? 0.2f : 1.0f);

        this.yd -= 0.04 * this.gravity;
        this.move(this.xd, this.yd, this.zd);
        this.xd *= 0.98;
        this.yd *= 0.99;
        this.zd *= 0.98;

        if (this.onGround) {
            this.xd *= 0.6;
            this.zd *= 0.6;
            this.age += 1;
        }

        float progress = (float) this.age / (float) this.lifetime;
        if (this.burning) {
            this.gCol = Math.max(0.12f, this.gCol - 0.008f);
            if (!this.onGround && this.random.nextInt(6) == 0) {
                this.level.addParticle(ParticleTypes.SMOKE, this.x, this.y, this.z, 0.0, 0.01, 0.0);
            }
        }
        if (progress > 0.8f) {
            this.alpha = Math.max(0.0f, 1.0f - (progress - 0.8f) * 5.0f);
        }
    }

    @Override
    public int getLightColor(float partialTick) {
        return this.burning ? 0xF000F0 : super.getLightColor(partialTick);
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
            return new ShrapnelParticle(level, x, y, z, vx, vy, vz, this.sprites);
        }
    }
}
