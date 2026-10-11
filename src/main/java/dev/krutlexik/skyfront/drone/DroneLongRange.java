package dev.krutlexik.skyfront.drone;

import dev.krutlexik.skyfront.drone.entity.DroneEntity;
import dev.krutlexik.skyfront.drone.entity.DroneFlightState;
import dev.krutlexik.skyfront.drone.network.DroneLinkPayload;
import dev.krutlexik.skyfront.drone.DroneMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.network.PacketDistributor;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Flying a drone further out than the server would normally send anything, and being able to see it. */
public final class DroneLongRange {
    /** Chunks either side of the drone streamed to the pilot — the terrain bubble the feed shows. */
    public static int streamRadius() {
        return dev.krutlexik.skyfront.config.SkyfrontConfig.DRONE_CHUNK_RADIUS.get();
    }

    /** The server loads one chunk more than that, so the streamed window's own edge is resident. */
    public static int loadedRadius() {
        return streamRadius() + 1;
    }

    /** How often the streamed window is recomputed. */
    private static final int REFRESH_TICKS = 2;
    /**
     * Most chunks sent to one player in a single tick, whether streaming ahead or dripping the player's own
     * world back.
     */
    private static final int CHUNK_BUDGET = 10;

    /** Per pilot flying out past their own view: the drone, and what of the world around it they are being shown. */
    private static final Map<UUID, Feed> FEEDS = new ConcurrentHashMap<>();
    /** Per player: the queue of their own chunks still to be dripped back after a flight. */
    private static final Map<UUID, Reseed> RESEEDS = new HashMap<>();

    /** How often the window is re-scanned for entities entering and leaving it. */
    private static final int ENTITY_RESCAN_TICKS = 5;

    /** How far around the drone entities are fed to the pilot, in chunks. */
    private static final int ENTITY_STREAM_CHUNKS = 16;

    /** One pilot's view out at their drone. */
    private static final class Feed {
        private final DroneEntity drone;
        private final ServerLevel level;
        /** Chunks sent to the pilot's client and still inside its grid. */
        private final LongOpenHashSet held = new LongOpenHashSet();
        /** Entities shown to the pilot because of the drone at the last rescan, so those leaving are known. */
        private IntOpenHashSet shown = new IntOpenHashSet();
        /** Whether the client's grid has been moved out to the drone, and so has to be put back. */
        private boolean tookGrid;

        private Feed(DroneEntity drone, ServerLevel level) {
            this.drone = drone;
            this.level = level;
        }

        private boolean shows(ServerPlayer pilot, Entity entity, boolean alreadyShown) {
            if (this.drone.isRemoved() || entity.level() != this.level || pilot.level() != this.level) {
                return false;
            }
            if (entity == this.drone) {
                return true;
            }
            if (entity == pilot || !worthShowing(entity)) {
                return false;
            }
            int reach = ENTITY_STREAM_CHUNKS + (alreadyShown ? 1 : 0);
            ChunkPos at = entity.chunkPosition();
            ChunkPos centre = this.drone.chunkPosition();
            return Math.abs(at.x - centre.x) <= reach && Math.abs(at.z - centre.z) <= reach;
        }
    }

    /** A player's own surroundings being handed back after they stop flying. */
    private static final class Reseed {
        private final UUID player;
        private final Deque<Long> pending;

        private Reseed(UUID player, Deque<Long> pending) {
            this.player = player;
            this.pending = pending;
        }
    }

    private DroneLongRange() {
    }

    /** One server tick of the link for a drone being flown by {@code pilot}. */
    public static void tick(DroneEntity drone, ServerPlayer pilot) {
        try {
            if (pilot.connection.hasChannel(DroneLinkPayload.TYPE)) {
                DroneFlightState state = new DroneFlightState();
                drone.captureFlightState(state);
                PacketDistributor.sendToPlayer(pilot, new DroneLinkPayload(
                        drone.getId(),
                        BuiltInRegistries.ENTITY_TYPE.getId(drone.getType()),
                        drone.linkAckSeq(), state));
            }

            if (drone.needsLongRangeLink()
                    && drone.level() instanceof ServerLevel level && pilot.level() == level) {
                RESEEDS.remove(pilot.getUUID());

                Feed feed = FEEDS.get(pilot.getUUID());
                if (feed == null || feed.drone != drone) {
                    if (feed != null) {
                        endFeed(pilot, feed);
                    }
                    feed = new Feed(drone, level);
                    FEEDS.put(pilot.getUUID(), feed);
                }

                if (drone.tickCount % REFRESH_TICKS == 0) {
                    streamChunks(feed, pilot);
                }
                if (drone.tickCount % ENTITY_RESCAN_TICKS == 0) {
                    rescanEntities(feed, pilot);
                }
            }
        } catch (Throwable failure) {
            DroneMod.LOGGER.error("Long-range link tick failed for drone {}", drone.getId(), failure);
        }
    }

    /**
     * Asked by the chunk map (see {@code ChunkMapMixin}): whether this chunk has been streamed to this player
     * out at their drone, and so counts as one they are watching.
     */
    public static boolean streams(ServerPlayer player, ServerLevel level, int x, int z) {
        if (FEEDS.isEmpty()) {
            return false;
        }
        Feed feed = FEEDS.get(player.getUUID());
        return feed != null && feed.level == level && feed.held.contains(ChunkPos.asLong(x, z));
    }

    /**
     * Asked by the tracker (see {@code TrackedEntityMixin}): whether this player is shown {@code entity}
     * because it is near the drone they are flying, wherever their body is.
     */
    public static boolean shows(ServerPlayer player, Entity entity, boolean alreadyShown) {
        if (FEEDS.isEmpty()) {
            return false;
        }
        Feed feed = FEEDS.get(player.getUUID());
        return feed != null && feed.shows(player, entity, alreadyShown);
    }

    /**
     * The window has moved with the drone since the tracker last looked: has it reconsider the pilot for
     * everything that has come into it, and everything that has left it.
     */
    private static void rescanEntities(Feed feed, ServerPlayer pilot) {
        DroneEntity drone = feed.drone;
        ServerLevel level = feed.level;
        double reach = (ENTITY_STREAM_CHUNKS + 2) * 16.0D;
        AABB around = new AABB(drone.getX() - reach, level.getMinBuildHeight() - 64.0D, drone.getZ() - reach,
                drone.getX() + reach, level.getMaxBuildHeight() + 64.0D, drone.getZ() + reach);

        IntOpenHashSet now = new IntOpenHashSet();
        for (Entity other : level.getEntities((Entity) null, around, entity -> true)) {
            TrackerAccess.Entry tracked = TrackerAccess.tracked(level, other.getId());
            if (tracked == null) {
                continue;
            }
            boolean seen = tracked.sfw$seenBy().contains(pilot.connection);
            if (feed.shows(pilot, other, seen)) {
                now.add(other.getId());
                if (!seen) {
                    tracked.sfw$updatePlayer(pilot);
                }
            }
        }
        IntOpenHashSet left = feed.shown;
        feed.shown = now;
        left.removeAll(now);
        for (int id : left) {
            TrackerAccess.Entry tracked = TrackerAccess.tracked(level, id);
            if (tracked != null) {
                tracked.sfw$updatePlayer(pilot);
            }
        }
    }

    /**
     * What the camera bothers showing: not items and orbs (clutter), not spectators, not other drones'
     * pilots' ghosts.
     */
    private static boolean worthShowing(Entity entity) {
        return !entity.isRemoved() && !entity.isSpectator()
                && !(entity instanceof net.minecraft.world.entity.item.ItemEntity)
                && !(entity instanceof net.minecraft.world.entity.ExperienceOrb);
    }

    private static void streamChunks(Feed feed, ServerPlayer pilot) {
        ServerLevel level = feed.level;
        LongOpenHashSet held = feed.held;
        ChunkPos centre = feed.drone.chunkPosition();

        pilot.connection.send(new ClientboundSetChunkCacheCenterPacket(centre.x, centre.z));
        feed.tookGrid = true;

        LongOpenHashSet wanted = new LongOpenHashSet();
        List<Long> fresh = new ArrayList<>();
        int radius = streamRadius();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                long chunk = ChunkPos.asLong(centre.x + dx, centre.z + dz);
                wanted.add(chunk);
                if (!held.contains(chunk)) {
                    fresh.add(chunk);
                }
            }
        }
        fresh.sort(Comparator.comparingLong(chunk -> distanceSqr(chunk, centre)));

        held.removeIf((long chunk) -> !wanted.contains(chunk));

        int budget = CHUNK_BUDGET;
        for (long chunk : fresh) {
            LevelChunk loaded = level.getChunkSource().getChunkNow(ChunkPos.getX(chunk), ChunkPos.getZ(chunk));
            if (loaded == null) {
                continue;
            }
            sendChunk(pilot, level, loaded);
            held.add(chunk);
            if (--budget <= 0) {
                break;
            }
        }
    }

    /** The flight is over. */
    public static void release(DroneEntity drone, @Nullable ServerPlayer pilot) {
        if (pilot == null) {
            FEEDS.values().removeIf(feed -> feed.drone == drone);
            return;
        }
        Feed feed = FEEDS.get(pilot.getUUID());
        if (feed == null || feed.drone != drone) {
            return;
        }
        endFeed(pilot, feed);
    }

    private static void endFeed(ServerPlayer pilot, Feed feed) {
        FEEDS.remove(pilot.getUUID(), feed);
        if (pilot.hasDisconnected() || pilot.level() != feed.level) {
            return;
        }
        for (int id : feed.shown) {
            try {
                TrackerAccess.Entry tracked = TrackerAccess.tracked(feed.level, id);
                if (tracked != null) {
                    tracked.sfw$updatePlayer(pilot);
                }
            } catch (Throwable failure) {
                DroneMod.LOGGER.error("Could not hand a streamed entity back to the tracker for {}",
                        pilot.getGameProfile().getName(), failure);
            }
        }
        if (!feed.tookGrid) {
            return;
        }
        try {
            queueReseed(pilot, feed.level);
        } catch (Throwable failure) {
            DroneMod.LOGGER.error("Long-range link tear-down failed for {}", pilot.getGameProfile().getName(), failure);
        }
    }

    private static void queueReseed(ServerPlayer pilot, ServerLevel level) {
        ChunkPos home = pilot.chunkPosition();
        pilot.connection.send(new ClientboundSetChunkCacheCenterPacket(home.x, home.z));

        int view = Math.max(2, pilot.server.getPlayerList().getViewDistance());
        List<Long> box = new ArrayList<>();
        for (int dx = -view; dx <= view; dx++) {
            for (int dz = -view; dz <= view; dz++) {
                box.add(ChunkPos.asLong(home.x + dx, home.z + dz));
            }
        }
        box.sort(Comparator.comparingLong(chunk -> distanceSqr(chunk, home)));
        RESEEDS.put(pilot.getUUID(), new Reseed(pilot.getUUID(), new ArrayDeque<>(box)));
    }

    /** Drips queued chunks back to players who have just stopped flying. */
    public static void serverTick(MinecraftServer server) {
        if (RESEEDS.isEmpty()) {
            return;
        }
        RESEEDS.values().removeIf(reseed -> {
          try {
            ServerPlayer pilot = server.getPlayerList().getPlayer(reseed.player);
            if (pilot == null || pilot.hasDisconnected() || !(pilot.level() instanceof ServerLevel level)) {
                return true;
            }
            int budget = CHUNK_BUDGET;
            while (budget > 0 && !reseed.pending.isEmpty()) {
                long chunk = reseed.pending.poll();
                LevelChunk loaded = level.getChunkSource().getChunkNow(ChunkPos.getX(chunk), ChunkPos.getZ(chunk));
                if (loaded != null) {
                    sendChunk(pilot, level, loaded);
                    budget--;
                }
            }
            return reseed.pending.isEmpty();
          } catch (Throwable failure) {
            DroneMod.LOGGER.error("Long-range reseed failed", failure);
            return true;
          }
        });
    }

    /** Square of the chunk distance, for ordering a window from its centre outwards. */
    private static long distanceSqr(long chunk, ChunkPos from) {
        long dx = ChunkPos.getX(chunk) - from.x;
        long dz = ChunkPos.getZ(chunk) - from.z;
        return dx * dx + dz * dz;
    }

    private static void sendChunk(ServerPlayer pilot, ServerLevel level, LevelChunk chunk) {
        pilot.connection.send(new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null));
    }
}
