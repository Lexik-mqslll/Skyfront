package dev.krutlexik.skyfront.port;

import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/** What a packet handler is handed, as NeoForge 1.21 names it, over Forge 1.20.1's network context. */
public interface IPayloadContext {

    /** The sender on the server; this client's own player on the client. */
    @Nullable
    Player player();

    /** Runs {@code task} on the main thread of the receiving side. */
    CompletableFuture<Void> enqueueWork(Runnable task);
}
