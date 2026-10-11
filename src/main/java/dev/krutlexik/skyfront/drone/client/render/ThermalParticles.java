package dev.krutlexik.skyfront.drone.client.render;

import net.minecraft.client.particle.Particle;

import java.util.Map;

/** Which particles are hot to the thermal camera, and how hot. */
public final class ThermalParticles {
    private enum Kind {
        /** The fireball's flash: white-hot for as long as it lasts. */
        FLASH,
        /** Flame, lava, embers and sparks. */
        FIRE,
        /** Smoke off a blast or a fire: hot gas, cooling as it rises. */
        SMOKE,
        /** Fragments thrown by a blast: hot metal and rock, cooling as they fly. */
        FRAGMENT,
        COLD
    }

    private static final Map<String, Kind> NAMED = Map.ofEntries(
            Map.entry("net.minecraft.client.particle.HugeExplosionParticle", Kind.FLASH),
            Map.entry("net.minecraft.client.particle.ExplodeParticle", Kind.SMOKE),
            Map.entry("net.minecraft.client.particle.FlameParticle", Kind.FIRE),
            Map.entry("net.minecraft.client.particle.LavaParticle", Kind.FIRE),
            Map.entry("net.minecraft.client.particle.FireworkParticles$SparkParticle", Kind.FIRE),
            Map.entry("net.minecraft.client.particle.FireworkParticles$OverlayParticle", Kind.FLASH),
            Map.entry("net.minecraft.client.particle.PlayerCloudParticle", Kind.SMOKE),
            Map.entry("net.minecraft.client.particle.SmokeParticle", Kind.SMOKE),
            Map.entry("net.minecraft.client.particle.CampfireSmokeParticle", Kind.SMOKE),
            Map.entry("dev.krutlexik.skyfront.particle.FlashParticle", Kind.FLASH),
            Map.entry("dev.krutlexik.skyfront.particle.EmberParticle", Kind.FIRE),
            Map.entry("dev.krutlexik.skyfront.particle.BlackSmokeParticle", Kind.SMOKE),
            Map.entry("dev.krutlexik.skyfront.particle.ShrapnelParticle", Kind.FRAGMENT),
            Map.entry("dev.krutlexik.skyfront.particle.DebrisParticle", Kind.FRAGMENT),
            Map.entry("com.atsuishio.superbwarfare.client.particle.CustomFlareParticle", Kind.FLASH),
            Map.entry("com.atsuishio.superbwarfare.client.particle.CannonMuzzleFlareParticle", Kind.FLASH),
            Map.entry("com.atsuishio.superbwarfare.client.particle.FireStarParticle", Kind.FIRE),
            Map.entry("com.atsuishio.superbwarfare.client.particle.WhiteStarParticle", Kind.FIRE),
            Map.entry("com.atsuishio.superbwarfare.client.particle.CustomCloudParticle", Kind.SMOKE),
            Map.entry("com.atsuishio.superbwarfare.client.particle.CustomSmokeParticle", Kind.SMOKE),
            Map.entry("com.atsuishio.superbwarfare.client.particle.RisingSmokeParticle", Kind.SMOKE),
            Map.entry("com.atsuishio.superbwarfare.client.particle.ExplosionDebrisParticle", Kind.FRAGMENT));

    private static final ClassValue<Kind> KINDS = new ClassValue<>() {
        @Override
        protected Kind computeValue(Class<?> type) {
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                Kind kind = NAMED.get(c.getName());
                if (kind != null) {
                    return kind;
                }
            }
            return Kind.COLD;
        }
    };

    /** How hot a particle is to the camera, 0..1, or 0 for one as cold as the world around it. */
    public static float heat(Particle particle, int age, int lifetime) {
        float life = lifetime > 0 ? Math.min(1.0F, Math.max(0.0F, (float) age / lifetime)) : 0.0F;
        return switch (KINDS.get(particle.getClass())) {
            case FLASH -> 1.0F;
            case FIRE -> 0.97F;
            case SMOKE -> 0.98F - 0.14F * life;
            case FRAGMENT -> 0.96F - 0.18F * life;
            case COLD -> 0.0F;
        };
    }

    /**
     * Whether a hot particle is mottled rather than even: smoke and fragments are, a billow of gas never
     * being one temperature through — hotter where it is dense and still boiling, cooler at its thin edges.
     */
    public static boolean mottled(Particle particle) {
        Kind kind = KINDS.get(particle.getClass());
        return kind == Kind.SMOKE || kind == Kind.FRAGMENT;
    }

    private ThermalParticles() {
    }
}
