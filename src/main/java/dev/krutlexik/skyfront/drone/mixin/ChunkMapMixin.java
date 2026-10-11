package dev.krutlexik.skyfront.drone.mixin;

import dev.krutlexik.skyfront.drone.DroneLongRange;
import dev.krutlexik.skyfront.drone.TrackerAccess;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Counts the chunks streamed to a long-range pilot (see {@link DroneLongRange}) as chunks that pilot is tracking. */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin implements TrackerAccess.Map {
    @Shadow
    @Final
    ServerLevel level;

    @Shadow
    @Final
    private Int2ObjectMap<?> entityMap;

    @Inject(method = "isChunkTracked", at = @At("RETURN"), cancellable = true)
    private void sfw$streamedToPilot(ServerPlayer player, int x, int z, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && DroneLongRange.streams(player, this.level, x, z)) {
            cir.setReturnValue(true);
        }
    }

    @Override
    @Nullable
    public TrackerAccess.Entry sfw$tracked(int entityId) {
        return (TrackerAccess.Entry) this.entityMap.get(entityId);
    }
}
