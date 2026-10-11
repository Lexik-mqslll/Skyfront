package dev.krutlexik.skyfront.compat.sbw;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.init.ModDamageTypes;
import com.atsuishio.superbwarfare.tools.DamageHandler;
import com.atsuishio.superbwarfare.tools.ParticleTool;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * The SuperbWarfare integration itself: this mod's warheads routed into SBW's own vehicle damage system, so
 * vehicles take them the way SBW's ordnance does.
 */
public final class SbwVehicles {
    private SbwVehicles() {
    }

    /**
     * Damages every SBW vehicle around {@code center} with SBW's custom-explosion damage type, using the same
     * quadratic falloff as their {@code CustomExplosion}: damage fades to zero at {@code 2 * radius} blocks.
     */
    public static void damageVehiclesAround(ServerLevel level, Entity source, @Nullable Entity attacker,
                                            Vec3 center, float damage, float radius) {
        double diameter = radius * 2.0;
        AABB box = new AABB(center, center).inflate(diameter);
        for (VehicleEntity vehicle : level.getEntitiesOfClass(VehicleEntity.class, box)) {
            double distanceRate = Math.sqrt(vehicle.distanceToSqr(center)) / diameter;
            if (distanceRate > 1.0) {
                continue;
            }
            double damagePercent = 1.0 - distanceRate;
            float damageFinal = (float) ((damagePercent * damagePercent + damagePercent) / 2.0 * damage);
            DamageSource damageSource = ModDamageTypes.causeCustomExplosionDamage(level.registryAccess(), source, attacker);
            vehicle.invulnerableTime = 0;
            DamageHandler.doDamage(vehicle, damageSource, damageFinal);
        }
    }

    /**
     * Plays SuperbWarfare's own explosion — the rolling smoke, the flare, the debris and the ground shake —
     * at {@code at}.
     */
    public static void spawnExplosionEffect(ServerLevel level, Vec3 at, int scale) {
        ParticleTool.ParticleType type = switch (Math.max(0, Math.min(scale, 3))) {
            case 0 -> ParticleTool.ParticleType.MEDIUM;
            case 1 -> ParticleTool.ParticleType.LARGE;
            case 2 -> ParticleTool.ParticleType.HUGE;
            default -> ParticleTool.ParticleType.GIANT;
        };
        ParticleTool.spawnExplosionParticles(type, level, at);
    }

    /** Whether {@code entity} is one of SuperbWarfare's vehicles. */
    public static boolean isVehicle(Entity entity) {
        return entity instanceof VehicleEntity;
    }

    /**
     * Full-force hull damage for a vehicle that was struck directly, matching how SBW's own cruise missiles
     * deal their direct-hit component.
     */
    public static void damageVehicleDirectHit(Entity entity, Entity source, @Nullable Entity attacker, float damage) {
        if (entity instanceof VehicleEntity vehicle) {
            DamageSource damageSource = ModDamageTypes.causeProjectileHitDamage(entity.level().registryAccess(), source, attacker);
            vehicle.invulnerableTime = 0;
            DamageHandler.doDamage(vehicle, damageSource, damage);
        }
    }
}
