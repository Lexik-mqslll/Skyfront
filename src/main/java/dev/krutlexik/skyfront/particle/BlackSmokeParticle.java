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

/** Dense dark explosion smoke. */
@OnlyIn(Dist.CLIENT)
public class BlackSmokeParticle extends TextureSheetParticle {
    private final float baseSize;
    private final float spin;
    private final boolean fiery;
    private final int fireTicks;
    private final float smokeShade;

    protected BlackSmokeParticle(ClientLevel level, double x, double y, double z,
                                 double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.lifetime = 160 + this.random.nextInt(160);
        this.hasPhysics = true;
        this.baseSize = 1.0f + this.random.nextFloat() * 2.0f;
        this.quadSize = this.baseSize * 0.5f;
        this.smokeShade = 0.18f + this.random.nextFloat() * 0.22f;
        this.fiery = vy > 0.15 ? this.random.nextFloat() < 0.8f : this.random.nextFloat() < 0.06f;
        if (this.fiery) {
            this.fireTicks = 40 + this.random.nextInt(50);
            this.rCol = 1.0f;
            this.gCol = 0.35f + this.random.nextFloat() * 0.3f;
            this.bCol = 0.08f;
        } else {
            this.fireTicks = 0;
            this.rCol = this.smokeShade;
            this.gCol = this.smokeShade;
            this.bCol = this.smokeShade * 1.05f;
        }
        this.alpha = 0.95f;
        this.spin = (this.random.nextFloat() - 0.5f) * 0.08f;
        this.roll = this.random.nextFloat() * ((float) Math.PI * 2.0f);
        this.oRoll = this.roll;
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
        this.roll += this.spin;

        this.move(this.xd, this.yd, this.zd);
        this.xd *= 0.92;
        this.zd *= 0.92;
        this.yd = this.yd * 0.92 + 0.0025;

        float progress = (float) this.age / (float) this.lifetime;
        this.quadSize = this.baseSize * (0.5f + 2.0f * Math.min(this.age / 50.0f, 1.0f));
        if (this.fiery && this.age <= this.fireTicks) {
            this.gCol = Math.max(0.15f, this.gCol - 0.004f);
        } else if (this.fiery && this.rCol > this.smokeShade) {
            this.rCol = Math.max(this.smokeShade, this.rCol - 0.012f);
            this.gCol = Math.max(this.smokeShade, this.gCol - 0.008f);
            this.bCol = Math.min(this.smokeShade * 1.05f, this.bCol + 0.008f);
        } else {
            this.rCol = Math.min(0.5f, this.rCol + 0.0015f);
            this.gCol = Math.min(0.5f, this.gCol + 0.0015f);
            this.bCol = Math.min(0.52f, this.bCol + 0.0015f);
        }
        if (progress > 0.7f) {
            this.alpha = 0.95f * (1.0f - (progress - 0.7f) / 0.3f);
        }
    }

    @Override
    public int getLightColor(float partialTick) {
        return this.fiery && this.rCol > 0.5f ? 0xF000F0 : super.getLightColor(partialTick);
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
            return new BlackSmokeParticle(level, x, y, z, vx, vy, vz, this.sprites);
        }
    }
}
