package dev.krutlexik.skyfront.drone.network;

import dev.krutlexik.skyfront.drone.entity.DroneEntity;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The server ends of the drone protocol. */
public final class DroneServerPayloadHandler {
    /** One tick of the pilot's controls, queued on the drone and flown in its turn (see DroneEntity). */
    public static void droneInput(DroneInputPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer pilot)) {
                return;
            }
            if (pilot.level().getEntity(payload.entityId()) instanceof DroneEntity drone
                    && drone.isControlledBy(pilot)) {
                drone.acceptPilotInput(payload.input());
            }
        });
    }

    /** Releases the grenade slung under the drone, at the input the pilot let go at. */
    public static void dropGrenade(DroneDropPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer pilot)) {
                return;
            }
            if (pilot.level().getEntity(payload.entityId()) instanceof DroneEntity drone
                    && drone.isControlledBy(pilot)) {
                drone.orderDrop(pilot, payload.atSeq());
            }
        });
    }

    /** The pilot hit the self-destruct. */
    public static void selfDestruct(DroneSelfDestructPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer pilot)) {
                return;
            }
            if (pilot.level().getEntity(payload.entityId())
                    instanceof dev.krutlexik.skyfront.drone.entity.InterceptorDroneEntity drone
                    && drone.isControlledBy(pilot) && !drone.isWrecked()) {
                drone.orderSelfDestruct(pilot, payload.atSeq());
            }
        });
    }

    /** The pilot's copy of the drone flew into something: see {@link DroneRamPayload}. */
    public static void ramReported(DroneRamPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer pilot)) {
                return;
            }
            if (pilot.level().getEntity(payload.droneId()) instanceof DroneEntity drone) {
                net.minecraft.world.entity.Entity target = pilot.level().getEntity(payload.targetId());
                if (target != null) {
                    drone.acceptReportedRam(pilot, target);
                }
            }
        });
    }

    /** The pilot has left drone view. */
    public static void stopRequest(StopControlRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer pilot)) {
                return;
            }
            DroneEntity drone = pilot.level().getEntity(payload.entityId()) instanceof DroneEntity named
                    && named.isControlledBy(pilot) ? named : null;
            if (drone == null) {
                drone = dev.krutlexik.skyfront.drone.DroneLinks.flownBy(pilot);
            }
            if (drone != null) {
                drone.releaseControl(false);
            }
        });
    }

    private DroneServerPayloadHandler() {
    }
}
