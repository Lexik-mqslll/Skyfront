package dev.krutlexik.skyfront.drone.mixin;

import dev.krutlexik.skyfront.drone.DroneLinks;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sounds and level events near a drone reach its pilot, wherever their body is (see {@link
 * DroneLinks#forEachPilotView}).
 */
@Mixin(PlayerList.class)
public abstract class PlayerListViewMixin {
    @Inject(method = "broadcast", at = @At("TAIL"))
    private void sfw$heardFromDrone(@Nullable Player except, double x, double y, double z, double radius,
                                    ResourceKey<Level> dimension, Packet<?> packet, CallbackInfo ci) {
        double reach = radius * radius;
        DroneLinks.forEachPilotView((pilot, view) -> {
            if (pilot != except && pilot.level().dimension() == dimension
                    && pilot.distanceToSqr(x, y, z) >= reach
                    && view.distanceToSqr(x, y, z) < reach) {
                pilot.connection.send(packet);
            }
        });
    }
}
