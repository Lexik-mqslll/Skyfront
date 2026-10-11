package dev.krutlexik.skyfront.drone;

import dev.krutlexik.skyfront.drone.entity.DroneEntity;
import dev.krutlexik.skyfront.util.ChunkLoadHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Server-side bookkeeping for the links between pilots and drones that outlives the drones being loaded: who
 * is flying what, where each drone was last seen, and reconnections that have to wait for a drone's chunk to
 * come back.
 */
public final class DroneLinks {
    /** Where a drone was last seen: enough to load it again. */
    private record Sighting(ResourceKey<Level> dimension, ChunkPos chunk, double controlRange) {
    }

    /** A pilot waiting for their drone's chunk to load. */
    private static final class Pending {
        private final UUID drone;
        private final Sighting where;
        private final BiConsumer<ServerPlayer, DroneEntity> connect;
        private int ticksLeft = CONNECT_TIMEOUT_TICKS;

        private Pending(UUID drone, Sighting where, BiConsumer<ServerPlayer, DroneEntity> connect) {
            this.drone = drone;
            this.where = where;
            this.connect = connect;
        }
    }

    /** How long a reconnection waits for the drone's chunk before giving up: ten seconds. */
    private static final int CONNECT_TIMEOUT_TICKS = 200;

    private static final Map<UUID, Sighting> LAST_SEEN = new HashMap<>();
    private static final Map<UUID, DroneEntity> FLOWN_BY = new HashMap<>();
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private DroneLinks() {
    }

    /** The drone is (still) here. */
    public static void remember(DroneEntity drone) {
        if (drone.level().isClientSide) {
            return;
        }
        LAST_SEEN.put(drone.getUUID(),
                new Sighting(drone.level().dimension(), drone.chunkPosition(), drone.controlRange()));
    }

    /** The drone is gone for good — packed up, destroyed, discarded. */
    public static void forget(DroneEntity drone) {
        LAST_SEEN.remove(drone.getUUID());
    }

    /** Keeps the pilot-to-drone index in step with a drone's controller. */
    public static void controllerChanged(DroneEntity drone, @Nullable UUID previous, @Nullable UUID next) {
        if (drone.level().isClientSide) {
            return;
        }
        if (previous != null && FLOWN_BY.get(previous) == drone) {
            FLOWN_BY.remove(previous);
        }
        if (next != null) {
            FLOWN_BY.put(next, drone);
            PENDING.remove(next);
        }
    }

    /** Where this player is seeing and hearing the world from, if that is not their body: the drone they are flying. */
    @Nullable
    public static net.minecraft.world.phys.Vec3 pilotView(ServerPlayer player) {
        if (FLOWN_BY.isEmpty()) {
            return null;
        }
        DroneEntity drone = FLOWN_BY.get(player.getUUID());
        return drone != null && !drone.isRemoved() && drone.level() == player.level() && drone.isControlledBy(player)
                ? drone.position() : null;
    }

    /** Every player flying a drone, with where they are seeing and hearing the world from. */
    public static void forEachPilotView(BiConsumer<ServerPlayer, net.minecraft.world.phys.Vec3> action) {
        if (FLOWN_BY.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, DroneEntity> entry : FLOWN_BY.entrySet()) {
            DroneEntity drone = entry.getValue();
            if (!drone.isRemoved() && drone.level() instanceof ServerLevel level
                    && level.getPlayerByUUID(entry.getKey()) instanceof ServerPlayer pilot
                    && drone.isControlledBy(pilot)) {
                action.accept(pilot, drone.position());
            }
        }
    }

    /** The drone this pilot is flying, whatever the client thinks its id is. */
    @Nullable
    public static DroneEntity flownBy(ServerPlayer pilot) {
        DroneEntity drone = FLOWN_BY.get(pilot.getUUID());
        if (drone != null && (drone.isRemoved() || !drone.isControlledBy(pilot))) {
            FLOWN_BY.remove(pilot.getUUID());
            return null;
        }
        return drone;
    }

    /** A monitor is asking for a drone that is not loaded. */
    public static boolean reconnectWhenLoaded(ServerPlayer pilot, UUID droneId,
                                              BiConsumer<ServerPlayer, DroneEntity> connect) {
        Sighting where = LAST_SEEN.get(droneId);
        if (where == null || where.dimension() != pilot.level().dimension()) {
            return false;
        }
        double dx = where.chunk().getMiddleBlockX() + 0.5D - pilot.getX();
        double dz = where.chunk().getMiddleBlockZ() + 0.5D - pilot.getZ();
        double reach = where.controlRange() + 16.0D;
        if (dx * dx + dz * dz > reach * reach) {
            return false;
        }
        PENDING.put(pilot.getUUID(), new Pending(droneId, where, connect));
        load(pilot.serverLevel(), where.chunk());
        return true;
    }

    /** Every server tick: keeps the chunks of pending reconnections loading, and links up. */
    public static void serverTick(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Pending>> entries = PENDING.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Pending> entry = entries.next();
            Pending pending = entry.getValue();
            ServerPlayer pilot = server.getPlayerList().getPlayer(entry.getKey());
            ServerLevel level = server.getLevel(pending.where.dimension());
            if (pilot == null || pilot.hasDisconnected() || level == null || pilot.level() != level) {
                entries.remove();
                continue;
            }
            if (level.getEntity(pending.drone) instanceof DroneEntity drone && !drone.isRemoved()) {
                entries.remove();
                pending.connect.accept(pilot, drone);
                continue;
            }
            if (--pending.ticksLeft <= 0) {
                entries.remove();
                pilot.displayClientMessage(Component.translatable("message.sfw.drone.drone_missing"), true);
                continue;
            }
            if (pending.ticksLeft % 20 == 0) {
                load(level, pending.where.chunk());
            }
        }
    }

    private static void load(ServerLevel level, ChunkPos chunk) {
        ChunkLoadHelper.keepTicking(level, chunk);
    }
}
