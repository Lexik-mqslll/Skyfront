package dev.krutlexik.skyfront.network;

import dev.krutlexik.skyfront.drone.client.DroneClientPayloadHandler;
import dev.krutlexik.skyfront.drone.network.DroneDropPayload;
import dev.krutlexik.skyfront.drone.network.DroneInputPayload;
import dev.krutlexik.skyfront.drone.network.DroneLinkPayload;
import dev.krutlexik.skyfront.drone.network.DroneServerPayloadHandler;
import dev.krutlexik.skyfront.drone.network.StartControlPayload;
import dev.krutlexik.skyfront.drone.network.StopControlPayload;
import dev.krutlexik.skyfront.drone.network.StopControlRequestPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class NetworkHandler {
    /**
     * Bumped whenever a payload's layout changes, so a client and server from different builds are turned
     * away at the door rather than reading each other's packets wrong. 2: numbered drone inputs, the full
     * flight state in the link feed, stamped onlooker states, synced tuning. 3: debris thrown on the clients
     * from one burst packet. 4: the rail pitch in the flight state, the flat-pack stop reason. 5: the grenade
     * release and the self-destruct name the input they were given at; the separate arming packet is gone
     * (the switch rides in the input).
     */
    private static final String PROTOCOL_VERSION = "5";

    public static void register(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        registrar.playToClient(ShahedSpawnPacket.TYPE, ShahedSpawnPacket.STREAM_CODEC, ShahedSpawnPacket::handle);
        registrar.playToClient(ShahedRemovePacket.TYPE, ShahedRemovePacket.STREAM_CODEC, ShahedRemovePacket::handle);
        registrar.playToClient(ShahedExplosionPacket.TYPE, ShahedExplosionPacket.STREAM_CODEC, ShahedExplosionPacket::handle);
        registrar.playToClient(DebrisBurstPacket.TYPE, DebrisBurstPacket.STREAM_CODEC, DebrisBurstPacket::handle);
        registrar.playToClient(TerminalDataPacket.TYPE, TerminalDataPacket.STREAM_CODEC, TerminalDataPacket::handle);
        registrar.playToServer(TerminalActionPacket.TYPE, TerminalActionPacket.STREAM_CODEC, TerminalActionPacket::handle);
        registrar.playToClient(OpenRadarScreenPacket.TYPE, OpenRadarScreenPacket.STREAM_CODEC, OpenRadarScreenPacket::handle);
        registrar.playToClient(SirenSoundPacket.TYPE, SirenSoundPacket.STREAM_CODEC, SirenSoundPacket::handle);
        registrar.playToClient(RadarUpdatePacket.TYPE, RadarUpdatePacket.STREAM_CODEC, RadarUpdatePacket::handle);
        registrar.playToServer(RequestRadarUpdatePacket.TYPE, RequestRadarUpdatePacket.STREAM_CODEC, RequestRadarUpdatePacket::handle);
        registrar.playToClient(WorldMapPacket.TYPE, WorldMapPacket.STREAM_CODEC, WorldMapPacket::handle);
        registrar.playToServer(RequestWorldMapPacket.TYPE, RequestWorldMapPacket.STREAM_CODEC, RequestWorldMapPacket::handle);

        registrar.playToServer(DroneInputPayload.TYPE, DroneInputPayload.STREAM_CODEC,
                DroneServerPayloadHandler::droneInput);
        registrar.playToServer(StopControlRequestPayload.TYPE, StopControlRequestPayload.STREAM_CODEC,
                DroneServerPayloadHandler::stopRequest);
        registrar.playToServer(DroneDropPayload.TYPE, DroneDropPayload.STREAM_CODEC,
                DroneServerPayloadHandler::dropGrenade);
        registrar.playToServer(dev.krutlexik.skyfront.drone.network.DroneSelfDestructPayload.TYPE,
                dev.krutlexik.skyfront.drone.network.DroneSelfDestructPayload.STREAM_CODEC,
                DroneServerPayloadHandler::selfDestruct);
        registrar.playToServer(dev.krutlexik.skyfront.drone.network.DroneRamPayload.TYPE,
                dev.krutlexik.skyfront.drone.network.DroneRamPayload.STREAM_CODEC,
                DroneServerPayloadHandler::ramReported);
        registrar.playToClient(StartControlPayload.TYPE, StartControlPayload.STREAM_CODEC,
                DroneClientPayloadHandler::startControl);
        registrar.playToClient(StopControlPayload.TYPE, StopControlPayload.STREAM_CODEC,
                DroneClientPayloadHandler::stopControl);
        registrar.playToClient(DroneLinkPayload.TYPE, DroneLinkPayload.STREAM_CODEC,
                DroneClientPayloadHandler::linkUpdate);
        registrar.playToClient(dev.krutlexik.skyfront.drone.network.DroneStatePayload.TYPE,
                dev.krutlexik.skyfront.drone.network.DroneStatePayload.STREAM_CODEC,
                DroneClientPayloadHandler::droneState);
        registrar.playToClient(dev.krutlexik.skyfront.drone.network.DroneTuningPayload.TYPE,
                dev.krutlexik.skyfront.drone.network.DroneTuningPayload.STREAM_CODEC,
                DroneClientPayloadHandler::tuning);
    }

    public static <MSG extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> void sendToServer(MSG message) {
        PacketDistributor.sendToServer(message);
    }

    public static <MSG extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> void sendToPlayer(ServerPlayer player, MSG message) {
        PacketDistributor.sendToPlayer(player, message);
    }

    public static <MSG extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> void sendToAllPlayers(MSG message) {
        PacketDistributor.sendToAllPlayers(message);
    }

    /** Sends only to players within {@code radius} blocks of (x,z) in the given level. */
    public static <MSG extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> void sendToNear(
            net.minecraft.server.level.ServerLevel level, double x, double y, double z, double radius, MSG message) {
        double rSq = radius * radius;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) <= rSq) {
                PacketDistributor.sendToPlayer(player, message);
            }
        }
    }
}
