package dev.krutlexik.skyfront.drone;

import dev.krutlexik.skyfront.drone.network.DroneTuningPayload;
import dev.krutlexik.skyfront.port.PacketDistributor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Hands every joining player the server's flight figures (see {@link DroneTuning}). */
@Mod.EventBusSubscriber(modid = DroneMod.MODID)
public final class DroneTuningSync {
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && PacketDistributor.hasChannel(player)) {
            PacketDistributor.sendToPlayer(player, DroneTuningPayload.current());
        }
    }

    /** Sends the server's figures to everybody on it again — after the config has been reloaded. */
    public static void broadcast(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (PacketDistributor.hasChannel(player)) {
                PacketDistributor.sendToPlayer(player, DroneTuningPayload.current());
            }
        }
    }

    private DroneTuningSync() {
    }
}
