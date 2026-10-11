package dev.krutlexik.skyfront.drone.mixin;

import dev.krutlexik.skyfront.drone.client.DroneControl;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the drone a player is flying alive on that player's client. */
@Mixin(ClientLevel.class)
public class ClientLevelDroneMixin {
    @Inject(method = "removeEntity", at = @At("HEAD"), cancellable = true)
    private void skyfront$keepPilotedDrone(int entityId, Entity.RemovalReason reason, CallbackInfo ci) {
        if (DroneControl.isProtectedEntity(entityId)) {
            DroneControl.deferRemoval(entityId, reason);
            ci.cancel();
        }
    }

    @Inject(method = "addEntity", at = @At("HEAD"))
    private void skyfront$beginTrackerReplacement(Entity entity, CallbackInfo ci) {
        DroneControl.beginTrackerReplacement(entity);
    }

    @Inject(method = "addEntity", at = @At("TAIL"))
    private void skyfront$finishTrackerReplacement(Entity entity, CallbackInfo ci) {
        DroneControl.finishTrackerReplacement(entity);
    }
}
