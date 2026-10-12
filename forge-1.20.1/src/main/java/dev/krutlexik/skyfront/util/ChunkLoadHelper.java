package dev.krutlexik.skyfront.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.Comparator;

/** Chunk loading for in-flight Shaheds, built for big servers. */
public final class ChunkLoadHelper {
    private static final Logger LOGGER = LogManager.getLogger("SFW-ChunkLoader");

    /** Auto-expiring ticket: 60 ticks lifetime, refreshed every second while the drone flies. */
    private static final int TICKET_LIFETIME_TICKS = 60;
    public static final TicketType<ChunkPos> SHAHED_TICKET =
            TicketType.create("skyfront_shahed", Comparator.comparingLong(ChunkPos::toLong), TICKET_LIFETIME_TICKS);

    private enum Mode {UNKNOWN, FORGE_TICKETS, YOUER_ASYNC}

    private static Mode mode = Mode.UNKNOWN;
    private static Method getBukkitWorld;
    private static Method getChunkAtAsync;

    private ChunkLoadHelper() {
    }

    private static void detect(ServerLevel level) {
        if (mode != Mode.UNKNOWN) {
            return;
        }
        try {
            Class.forName("org.bukkit.Bukkit");
            Method getWorld = level.getClass().getMethod("getWorld");
            Object bukkitWorld = getWorld.invoke(level);
            Method async = bukkitWorld.getClass().getMethod("getChunkAtAsync", int.class, int.class, boolean.class);
            getBukkitWorld = getWorld;
            getChunkAtAsync = async;
            mode = Mode.YOUER_ASYNC;
            LOGGER.info("[SFW] Hybrid Bukkit/Paper core detected - use ASYNC chunk loading");
        } catch (Throwable t) {
            mode = Mode.FORGE_TICKETS;
            LOGGER.info("[SFW] Forge server — use vanilla chunk loading");
        }
    }

    /**
     * Vanilla turns {@code addRegionTicket(type, pos, r, v)} into ticket level {@code 33 - r}, and only level
     * &lt;= 31 ticks entities — so radius 2 is the bare minimum that makes the drone's own chunk simulate,
     * and radius 3 additionally keeps the ring around it ticking.
     */
    private static final int TICKING_RADIUS = 3;

    /** Keeps a chunk (and its immediate ring) entity-ticking so the drone actually simulates there. */
    public static void keepTicking(ServerLevel level, ChunkPos pos) {
        detect(level);
        level.getChunkSource().addRegionTicket(SHAHED_TICKET, pos, TICKING_RADIUS, pos);
        requestAsync(level, pos);
    }

    /**
     * Pre-warms a chunk further along the flight path so it is generated and ready by the time the drone gets
     * there.
     */
    public static void preloadAhead(ServerLevel level, ChunkPos pos) {
        detect(level);
        level.getChunkSource().addRegionTicket(SHAHED_TICKET, pos, 0, pos);
        requestAsync(level, pos);
    }

    /** On Paper-based cores, additionally kick their async loader so the ticket resolves promptly. */
    private static void requestAsync(ServerLevel level, ChunkPos pos) {
        if (mode != Mode.YOUER_ASYNC) {
            return;
        }
        try {
            Object bukkitWorld = getBukkitWorld.invoke(level);
            getChunkAtAsync.invoke(bukkitWorld, pos.x, pos.z, true);
        } catch (Throwable t) {
            mode = Mode.FORGE_TICKETS;
            LOGGER.warn("[SFW] Paper async chunk load failed, falling back to vanilla chunk tickets only", t);
        }
    }

    /** Keeps a single chunk resident (loaded, but not entity-ticking) around a drone that is being flown far out. */
    public static void keepLoaded(ServerLevel level, ChunkPos pos) {
        detect(level);
        level.getChunkSource().addRegionTicket(SHAHED_TICKET, pos, 0, pos);
        if (level.getChunkSource().getChunkNow(pos.x, pos.z) == null) {
            requestAsync(level, pos);
        }
    }

    /** Chunk the given world position falls into. */
    public static ChunkPos chunkAt(double x, double z) {
        return new ChunkPos(BlockPos.containing(x, 0, z));
    }
}
