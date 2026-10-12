package dev.krutlexik.skyfront.network;

import dev.krutlexik.skyfront.SkyfrontMod;
import dev.krutlexik.skyfront.drone.client.DroneClientPayloadHandler;
import dev.krutlexik.skyfront.drone.network.DroneDropPayload;
import dev.krutlexik.skyfront.drone.network.DroneInputPayload;
import dev.krutlexik.skyfront.drone.network.DroneLinkPayload;
import dev.krutlexik.skyfront.drone.network.DroneServerPayloadHandler;
import dev.krutlexik.skyfront.drone.network.StartControlPayload;
import dev.krutlexik.skyfront.drone.network.StopControlPayload;
import dev.krutlexik.skyfront.drone.network.StopControlRequestPayload;
import dev.krutlexik.skyfront.port.PacketDistributor;
import dev.krutlexik.skyfront.port.PayloadRegistrar;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

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

    public static void register() {
        final PayloadRegistrar registrar = new PayloadRegistrar(
                new ResourceLocation(SkyfrontMod.MOD_ID, "main"), PROTOCOL_VERSION);

        registrar.playToClient(ShahedSpawnPacket.class, ShahedSpawnPacket.STREAM_CODEC, ShahedSpawnPacket::handle);
        registrar.playToClient(ShahedRemovePacket.class, ShahedRemovePacket.STREAM_CODEC, ShahedRemovePacket::handle);
        registrar.playToClient(ShahedExplosionPacket.class, ShahedExplosionPacket.STREAM_CODEC, ShahedExplosionPacket::handle);
        registrar.playToClient(DebrisBurstPacket.class, DebrisBurstPacket.STREAM_CODEC, DebrisBurstPacket::handle);
        registrar.playToClient(TerminalDataPacket.class, TerminalDataPacket.STREAM_CODEC, TerminalDataPacket::handle);
        registrar.playToServer(TerminalActionPacket.class, TerminalActionPacket.STREAM_CODEC, TerminalActionPacket::handle);
        registrar.playToClient(OpenRadarScreenPacket.class, OpenRadarScreenPacket.STREAM_CODEC, OpenRadarScreenPacket::handle);
        registrar.playToClient(SirenSoundPacket.class, SirenSoundPacket.STREAM_CODEC, SirenSoundPacket::handle);
        registrar.playToClient(RadarUpdatePacket.class, RadarUpdatePacket.STREAM_CODEC, RadarUpdatePacket::handle);
        registrar.playToServer(RequestRadarUpdatePacket.class, RequestRadarUpdatePacket.STREAM_CODEC, RequestRadarUpdatePacket::handle);
        registrar.playToClient(WorldMapPacket.class, WorldMapPacket.STREAM_CODEC, WorldMapPacket::handle);
        registrar.playToServer(RequestWorldMapPacket.class, RequestWorldMapPacket.STREAM_CODEC, RequestWorldMapPacket::handle);

        registrar.playToServer(DroneInputPayload.class, DroneInputPayload.STREAM_CODEC,
                DroneServerPayloadHandler::droneInput);
        registrar.playToServer(StopControlRequestPayload.class, StopControlRequestPayload.STREAM_CODEC,
                DroneServerPayloadHandler::stopRequest);
        registrar.playToServer(DroneDropPayload.class, DroneDropPayload.STREAM_CODEC,
                DroneServerPayloadHandler::dropGrenade);
        registrar.playToServer(dev.krutlexik.skyfront.drone.network.DroneSelfDestructPayload.class,
                dev.krutlexik.skyfront.drone.network.DroneSelfDestructPayload.STREAM_CODEC,
                DroneServerPayloadHandler::selfDestruct);
        registrar.playToServer(dev.krutlexik.skyfront.drone.network.DroneRamPayload.class,
                dev.krutlexik.skyfront.drone.network.DroneRamPayload.STREAM_CODEC,
                DroneServerPayloadHandler::ramReported);
        registrar.playToClient(StartControlPayload.class, StartControlPayload.STREAM_CODEC,
                DroneClientPayloadHandler::startControl);
        registrar.playToClient(StopControlPayload.class, StopControlPayload.STREAM_CODEC,
                DroneClientPayloadHandler::stopControl);
        registrar.playToClient(DroneLinkPayload.class, DroneLinkPayload.STREAM_CODEC,
                DroneClientPayloadHandler::linkUpdate);
        registrar.playToClient(dev.krutlexik.skyfront.drone.network.DroneStatePayload.class,
                dev.krutlexik.skyfront.drone.network.DroneStatePayload.STREAM_CODEC,
                DroneClientPayloadHandler::droneState);
        registrar.playToClient(dev.krutlexik.skyfront.drone.network.DroneTuningPayload.class,
                dev.krutlexik.skyfront.drone.network.DroneTuningPayload.STREAM_CODEC,
                DroneClientPayloadHandler::tuning);
    }

    public static <MSG extends dev.krutlexik.skyfront.port.CustomPacketPayload> void sendToServer(MSG message) {
        PacketDistributor.sendToServer(message);
    }

    public static <MSG extends dev.krutlexik.skyfront.port.CustomPacketPayload> void sendToPlayer(ServerPlayer player, MSG message) {
        PacketDistributor.sendToPlayer(player, message);
    }

    public static <MSG extends dev.krutlexik.skyfront.port.CustomPacketPayload> void sendToAllPlayers(MSG message) {
        PacketDistributor.sendToAllPlayers(message);
    }

    /** Sends only to players within {@code radius} blocks of (x,z) in the given level. */
    public static <MSG extends dev.krutlexik.skyfront.port.CustomPacketPayload> void sendToNear(
            net.minecraft.server.level.ServerLevel level, double x, double y, double z, double radius, MSG message) {
        double rSq = radius * radius;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) <= rSq) {
                PacketDistributor.sendToPlayer(player, message);
            }
        }
    }
}
