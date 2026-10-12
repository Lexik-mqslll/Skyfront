package dev.krutlexik.skyfront.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

/** How stale, in ticks, a position packet is by the time this client acts on it. */
public final class ClientLatency {
    /** Milliseconds in one tick. */
    private static final double MS_PER_TICK = 50.0D;

    /**
     * Beyond this there is no sensible amount of guessing left to do: leading a target by a third of a second
     * of its own flight is already most of a Shahed's length, and doubling down on a bad connection puts the
     * picture further from the truth rather than closer.
     */
    private static final double MAX_LEAD_TICKS = 6.0D;

    /**
     * One-way packet age in ticks: half the round trip the server reports for this player, or 0 when there is
     * nothing to measure (singleplayer, or before the player list has been populated).
     */
    public static double packetAgeTicks() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) {
            return 0.0D;
        }
        PlayerInfo info = minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID());
        if (info == null) {
            return 0.0D;
        }
        double oneWayMs = Math.max(0, info.getLatency()) / 2.0D;
        return Math.min(oneWayMs / MS_PER_TICK, MAX_LEAD_TICKS);
    }

    private ClientLatency() {
    }
}
