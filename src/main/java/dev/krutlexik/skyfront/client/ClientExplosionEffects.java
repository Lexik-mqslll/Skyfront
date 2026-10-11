package dev.krutlexik.skyfront.client;

import dev.krutlexik.skyfront.particle.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Client-side explosion visuals. */
@OnlyIn(Dist.CLIENT)
public final class ClientExplosionEffects {
    /** Beyond this the individual particles aren't worth spawning; the sound still carries further. */
    private static final double MAX_RENDER_DISTANCE = 256.0;

    private ClientExplosionEffects() {
    }

    public static void spawn(Vec3 pos, float mult) {
        if (mult <= 0.0f) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return;
        }
        if (ClientViewpoint.position().distanceToSqr(pos) > MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE) {
            return;
        }
        RandomSource random = level.random;

        add(level, ModParticles.EXPLOSION_FLASH.get(), pos.x, pos.y + 2.0, pos.z, 0, 0, 0);

        int smokeCloud = (int) (400 * mult);
        for (int i = 0; i < smokeCloud; i++) {
            double yaw = random.nextDouble() * Math.PI * 2;
            double pitchAng = (random.nextDouble() - 0.5) * Math.PI;
            double dx = Math.cos(pitchAng) * Math.cos(yaw);
            double dy = Math.sin(pitchAng) * 0.7 + 0.25;
            double dz = Math.cos(pitchAng) * Math.sin(yaw);
            double speed = 0.08 + Math.pow(random.nextDouble(), 2.0) * 0.65;
            double ox = (random.nextDouble() - 0.5) * 4.0;
            double oy = random.nextDouble() * 3.0;
            double oz = (random.nextDouble() - 0.5) * 4.0;
            add(level, ModParticles.BLACK_SMOKE.get(), pos.x + ox, pos.y + 0.5 + oy, pos.z + oz,
                    dx * speed, dy * speed, dz * speed);
        }

        int smokeRing = (int) (220 * mult);
        for (int i = 0; i < smokeRing; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = 3.0 + random.nextDouble() * 6.0;
            double ox = Math.cos(angle) * radius;
            double oz = Math.sin(angle) * radius;
            double oy = random.nextDouble() * 2.0;
            double outSpeed = 0.03 + random.nextDouble() * 0.12;
            add(level, ModParticles.BLACK_SMOKE.get(), pos.x + ox, pos.y + 0.3 + oy, pos.z + oz,
                    Math.cos(angle) * outSpeed, 0.02 + random.nextDouble() * 0.05, Math.sin(angle) * outSpeed);
        }

        BlockState groundState = findGroundState(level, pos);
        BlockParticleOption terrain = new BlockParticleOption(ParticleTypes.BLOCK, groundState);
        int dirtCount = (int) (140 * mult);
        for (int i = 0; i < dirtCount; i++) {
            double yaw = random.nextDouble() * Math.PI * 2;
            double pitchAng = random.nextDouble() * Math.PI * 0.45;
            double speed = 0.3 + random.nextDouble() * 1.0;
            double ox = (random.nextDouble() - 0.5) * 3.0;
            double oz = (random.nextDouble() - 0.5) * 3.0;
            add(level, terrain, pos.x + ox, pos.y + 0.5, pos.z + oz,
                    Math.cos(pitchAng) * Math.cos(yaw) * speed,
                    Math.sin(pitchAng) * speed + 0.3,
                    Math.cos(pitchAng) * Math.sin(yaw) * speed);
        }

        int shrapnelCount = (int) (1200 * mult);
        for (int i = 0; i < shrapnelCount; i++) {
            double yaw = random.nextDouble() * Math.PI * 2;
            double pitchAng = (random.nextDouble() - 0.65) * Math.PI;
            double speed = 0.3 + random.nextDouble() * 0.9;
            double ox = (random.nextDouble() - 0.5) * 1.5;
            double oy = random.nextDouble() * 1.0;
            double oz = (random.nextDouble() - 0.5) * 1.5;
            add(level, ModParticles.SHRAPNEL.get(), pos.x + ox, pos.y + 0.5 + oy, pos.z + oz,
                    Math.cos(pitchAng) * Math.cos(yaw) * speed,
                    Math.sin(pitchAng) * speed + 0.05,
                    Math.cos(pitchAng) * Math.sin(yaw) * speed);
        }
    }

    /** addParticle with forceAlwaysRender=true so the blast stays visible past the 32-block cull. */
    private static void add(ClientLevel level, ParticleOptions particle,
                            double x, double y, double z, double vx, double vy, double vz) {
        level.addParticle(particle, true, x, y, z, vx, vy, vz);
    }

    /** Finds the first solid block at/under the blast to sample terrain-debris particles from. */
    private static BlockState findGroundState(ClientLevel level, Vec3 pos) {
        BlockPos probe = BlockPos.containing(pos.x, pos.y, pos.z);
        for (int i = 0; i < 10; i++) {
            BlockState state = level.getBlockState(probe);
            if (!state.isAir() && state.getFluidState().isEmpty()) {
                return state;
            }
            probe = probe.below();
        }
        return Blocks.DIRT.defaultBlockState();
    }
}
