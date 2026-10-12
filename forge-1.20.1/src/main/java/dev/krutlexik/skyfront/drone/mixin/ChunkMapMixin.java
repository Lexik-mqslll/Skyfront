package dev.krutlexik.skyfront.drone.mixin;

import dev.krutlexik.skyfront.drone.DroneLongRange;
import dev.krutlexik.skyfront.drone.TrackerAccess;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.PlayerMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** Counts the chunks streamed to a long-range pilot (see {@link DroneLongRange}) as chunks that pilot is tracking. */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin implements TrackerAccess.Map {
    @Shadow
    @Final
    ServerLevel level;

    @Shadow
    @Final
    private PlayerMap playerMap;

    @Shadow
    @Final
    private Int2ObjectMap<?> entityMap;

    @Shadow
    int viewDistance;

    /**
     * 1.20.1 has no {@code isChunkTracked}: who watches a chunk (block and light updates, chunk-addressed
     * packets) is decided here, from the view distance around each player's body. A pilot is added for the
     * chunks streamed to them out at their drone; for the boundary-only query (light updates), only for those
     * on the edge of everything they hold, as vanilla does for the edge of a view.
     */
    @Inject(method = "getPlayers(Lnet/minecraft/world/level/ChunkPos;Z)Ljava/util/List;",
            at = @At("RETURN"), cancellable = true)
    private void sfw$streamedToPilot(ChunkPos pos, boolean boundaryOnly,
                                     CallbackInfoReturnable<List<ServerPlayer>> cir) {
        List<ServerPlayer> players = null;
        for (ServerPlayer player : this.playerMap.getPlayers(pos.toLong())) {
            if (!DroneLongRange.streams(player, this.level, pos.x, pos.z)
                    || boundaryOnly && !sfw$onTrackedBorder(player, pos.x, pos.z)) {
                continue;
            }
            if (players == null) {
                if (cir.getReturnValue().contains(player)) {
                    continue;
                }
                players = new ArrayList<>(cir.getReturnValue());
            } else if (players.contains(player)) {
                continue;
            }
            players.add(player);
        }
        if (players != null) {
            cir.setReturnValue(players);
        }
    }

    /**
     * A chunk that starts ticking is handed (again) to everyone {@code getPlayers} names, which above now
     * includes a pilot it was streamed to. 1.21.1 only resends it to players whose own view holds it, so a
     * chunk already streamed out at the drone is not sent twice, nor reported as newly watched. Vanilla only
     * gets here for chunks in the player's own view, so this only skips the streamed-only ones.
     */
    @Inject(method = "playerLoadedChunk(Lnet/minecraft/server/level/ServerPlayer;"
            + "Lorg/apache/commons/lang3/mutable/MutableObject;Lnet/minecraft/world/level/chunk/LevelChunk;)V",
            at = @At("HEAD"), cancellable = true)
    private void sfw$notResentToPilot(ServerPlayer player, MutableObject<ClientboundLevelChunkWithLightPacket> packet,
                                      LevelChunk chunk, CallbackInfo ci) {
        ChunkPos pos = chunk.getPos();
        SectionPos at = player.getLastSectionPos();
        if (!ChunkMap.isChunkInRange(pos.x, pos.z, at.x(), at.z(), this.viewDistance)
                && DroneLongRange.streams(player, this.level, pos.x, pos.z)) {
            ci.cancel();
        }
    }

    /** Whether this chunk is sent to the player, around their body or out at their drone. */
    @Unique
    private boolean sfw$isChunkTracked(ServerPlayer player, int x, int z) {
        SectionPos at = player.getLastSectionPos();
        return ChunkMap.isChunkInRange(x, z, at.x(), at.z(), this.viewDistance)
                || DroneLongRange.streams(player, this.level, x, z);
    }

    /** Whether a chunk the player holds has a neighbour they do not. */
    @Unique
    private boolean sfw$onTrackedBorder(ServerPlayer player, int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx != 0 || dz != 0) && !sfw$isChunkTracked(player, x + dx, z + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    @Nullable
    public TrackerAccess.Entry sfw$tracked(int entityId) {
        return (TrackerAccess.Entry) this.entityMap.get(entityId);
    }
}
