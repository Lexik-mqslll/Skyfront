package dev.krutlexik.skyfront.drone.mixin;

import dev.krutlexik.skyfront.drone.DroneLinks;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Explosions and particles near a drone reach its pilot, wherever their body is (see {@link
 * DroneLinks#forEachPilotView}).
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelViewMixin {
    /** Vanilla's own reach for the explosion packet, squared. */
    private static final double SFW_EXPLOSION_REACH_SQR = 64.0D * 64.0D;

    @Inject(method = "explode(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;"
            + "Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;)"
            + "Lnet/minecraft/world/level/Explosion;",
            at = @At("RETURN"))
    private void sfw$explosionSeenFromDrone(@Nullable Entity source, @Nullable DamageSource damageSource,
                                            @Nullable ExplosionDamageCalculator damageCalculator,
                                            double x, double y, double z, float radius, boolean fire,
                                            Level.ExplosionInteraction interaction,
                                            CallbackInfoReturnable<Explosion> cir) {
        Explosion explosion = cir.getReturnValue();
        Object self = this;
        DroneLinks.forEachPilotView((pilot, view) -> {
            if (pilot.level() == self
                    && pilot.distanceToSqr(x, y, z) >= SFW_EXPLOSION_REACH_SQR
                    && view.distanceToSqr(x, y, z) < SFW_EXPLOSION_REACH_SQR) {
                pilot.connection.send(new ClientboundExplodePacket(x, y, z, radius,
                        explosion.getToBlow(),
                        explosion.getHitPlayers().get(pilot)));
            }
        });
    }

    @Inject(method = "sendParticles(Lnet/minecraft/server/level/ServerPlayer;ZDDDLnet/minecraft/network/protocol/Packet;)Z",
            at = @At("HEAD"), cancellable = true)
    private void sfw$particlesSeenFromDrone(ServerPlayer player, boolean longDistance, double x, double y, double z,
                                            Packet<?> packet, CallbackInfoReturnable<Boolean> cir) {
        if (player.level() != (Object) this) {
            return;
        }
        Vec3 view = DroneLinks.pilotView(player);
        double reach = longDistance ? 512.0D : 32.0D;
        if (view != null && view.distanceToSqr(x, y, z) < reach * reach) {
            player.connection.send(packet);
            cir.setReturnValue(true);
        }
    }
}
