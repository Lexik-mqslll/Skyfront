package dev.krutlexik.skyfront.drone;

import dev.krutlexik.skyfront.drone.network.DroneTuningPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Hands every joining player the server's flight figures (see {@link DroneTuning}). */
@EventBusSubscriber(modid = DroneMod.MODID)
public final class DroneTuningSync {
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.connection.hasChannel(DroneTuningPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, DroneTuningPayload.current());
        }
    }

    /** Sends the server's figures to everybody on it again — after the config has been reloaded. */
    public static void broadcast(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.connection.hasChannel(DroneTuningPayload.TYPE)) {
                PacketDistributor.sendToPlayer(player, DroneTuningPayload.current());
            }
        }
    }

    private DroneTuningSync() {
    }
}
