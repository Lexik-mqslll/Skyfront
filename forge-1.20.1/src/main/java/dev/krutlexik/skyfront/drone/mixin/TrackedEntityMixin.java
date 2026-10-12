package dev.krutlexik.skyfront.drone.mixin;

import dev.krutlexik.skyfront.drone.DroneLongRange;
import dev.krutlexik.skyfront.drone.TrackerAccess;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * Lets the vanilla tracker show a long-range pilot what is around their drone, not just what is around their
 * body (see {@link DroneLongRange}).
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntityMixin implements TrackerAccess.Entry {
    @Shadow
    @Final
    ServerEntity serverEntity;

    @Shadow
    @Final
    Entity entity;

    @Shadow
    @Final
    private Set<ServerPlayerConnection> seenBy;

    @Shadow
    public abstract void updatePlayer(ServerPlayer player);

    @Inject(method = "updatePlayer", at = @At("HEAD"), cancellable = true)
    private void sfw$shownThroughDrone(ServerPlayer player, CallbackInfo ci) {
        if (player == this.entity) {
            return;
        }
        if (DroneLongRange.shows(player, this.entity, this.seenBy.contains(player.connection))
                && this.entity.broadcastToPlayer(player)) {
            if (this.seenBy.add(player.connection)) {
                this.serverEntity.addPairing(player);
            }
            ci.cancel();
        }
    }

    @Override
    public void sfw$updatePlayer(ServerPlayer player) {
        updatePlayer(player);
    }

    @Override
    public Set<ServerPlayerConnection> sfw$seenBy() {
        return this.seenBy;
    }
}
