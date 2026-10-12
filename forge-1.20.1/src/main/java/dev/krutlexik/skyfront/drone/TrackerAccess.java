package dev.krutlexik.skyfront.drone;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * What the drone feed needs of the vanilla entity tracker, opened up by mixin (see {@code
 * drone.mixin.ChunkMapMixin} and {@code drone.mixin.TrackedEntityMixin}).
 */
public final class TrackerAccess {
    /** The level's {@code ChunkMap}. */
    public interface Map {
        /** The tracker entry for an entity, or null for one that is not tracked. */
        @Nullable
        Entry sfw$tracked(int entityId);
    }

    /** One entity's tracker entry ({@code ChunkMap.TrackedEntity}). */
    public interface Entry {
        /** Has the tracker decide afresh whether this player is shown the entity. */
        void sfw$updatePlayer(ServerPlayer player);

        /** The connections the entity is being sent to. */
        Set<ServerPlayerConnection> sfw$seenBy();
    }

    @Nullable
    public static Entry tracked(net.minecraft.server.level.ServerLevel level, int entityId) {
        return ((Map) level.getChunkSource().chunkMap).sfw$tracked(entityId);
    }

    private TrackerAccess() {
    }
}
