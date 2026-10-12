package dev.krutlexik.skyfront.port;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Sending payloads, under the names NeoForge 1.21 gives them, over the mod's Forge channel (opened by
 * {@link PayloadRegistrar}).
 */
public final class PacketDistributor {

    private static SimpleChannel channel;

    static void use(SimpleChannel opened) {
        channel = opened;
    }

    public static void sendToServer(CustomPacketPayload payload) {
        channel.sendToServer(payload);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        channel.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), payload);
    }

    public static void sendToAllPlayers(CustomPacketPayload payload) {
        channel.send(net.minecraftforge.network.PacketDistributor.ALL.noArg(), payload);
    }

    /**
     * Whether this player's connection has the mod's channel — false for a connection that cannot take
     * a payload (a test's mock player, another mod's stand-in), which would otherwise throw on one.
     * NeoForge asks this per payload type; on Forge the whole channel is either there or not. Forge's own
     * check reads the connection's netty channel without looking, and a fake player's connection has
     * none, so that is ruled out first.
     */
    public static boolean hasChannel(ServerPlayer player) {
        if (player instanceof FakePlayer || player.connection == null) {
            return false;
        }
        Connection connection = player.connection.connection;
        return connection != null && connection.channel() != null && channel.isRemotePresent(connection);
    }

    private PacketDistributor() {
    }
}
