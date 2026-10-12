package dev.krutlexik.skyfront.drone.client;

import dev.krutlexik.skyfront.drone.DroneTuning;
import dev.krutlexik.skyfront.drone.entity.DroneEntity;
import dev.krutlexik.skyfront.drone.network.DroneLinkPayload;
import dev.krutlexik.skyfront.drone.network.DroneStatePayload;
import dev.krutlexik.skyfront.drone.network.DroneTuningPayload;
import dev.krutlexik.skyfront.drone.network.StartControlPayload;
import dev.krutlexik.skyfront.drone.network.StopControlPayload;
import dev.krutlexik.skyfront.port.IPayloadContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Only ever executed on the client, but must stay loadable on the server (see {@link DroneControl}). */
public final class DroneClientPayloadHandler {
    /** The drone is still out there. */
    public static void linkUpdate(DroneLinkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> DroneControl.acceptLink(payload));
    }

    /** One stamped tick of a drone this client is watching: onto its playback timeline. */
    public static void droneState(DroneStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null
                    && minecraft.level.getEntity(payload.entityId()) instanceof DroneEntity drone) {
                drone.acceptSnapshot(payload);
            }
        });
    }

    /** The server's flight figures, which every drone on this client is flown and drawn by. */
    public static void tuning(DroneTuningPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> DroneTuning.acceptFromServer(payload.values()));
    }

    public static void startControl(StartControlPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> DroneControl.start(payload));
    }

    public static void stopControl(StopControlPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            switch (payload.reason()) {
                case OUT_OF_RANGE -> {
                    message("message.sfw.drone.out_of_range");
                    DroneControl.beginSignalLoss(DroneSignalLoss.Cause.OUT_OF_RANGE);
                }
                case DESTROYED -> {
                    message("message.sfw.drone.destroyed");
                    DroneControl.beginSignalLoss(DroneSignalLoss.Cause.DESTROYED);
                }
                case CRASHED -> {
                    DroneControl.beginSignalLoss(DroneSignalLoss.Cause.CRASH);
                }
                case BATTERY ->
                    DroneControl.beginSignalLoss(DroneSignalLoss.Cause.OUT_OF_RANGE);
                default -> DroneControl.stop();
            }
        });
    }

    private static void message(String key) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable(key), true);
        }
    }

    private DroneClientPayloadHandler() {
    }
}
