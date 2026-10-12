package dev.krutlexik.skyfront.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Route rules mirrored from the server (they live in the server config, which a connected client cannot read). */
@OnlyIn(Dist.CLIENT)
public final class ClientTerminalLimits {
    private static int maxWaypoints = 15;
    private static int minSpacing = 50;
    private static int minLaunchDistance = 100;
    private static int maxAltitude = 320;
    private static boolean whitelistEnabled = false;
    private static int[] whitelistZones = new int[0];
    private static boolean blacklistEnabled = false;
    private static int[] blacklistZones = new int[0];

    private ClientTerminalLimits() {
    }

    public static void update(int maxWaypoints, int minSpacing, int minLaunchDistance, int maxAltitude,
                              boolean whitelistEnabled, int[] whitelistZones,
                              boolean blacklistEnabled, int[] blacklistZones) {
        ClientTerminalLimits.maxWaypoints = maxWaypoints;
        ClientTerminalLimits.minSpacing = minSpacing;
        ClientTerminalLimits.minLaunchDistance = minLaunchDistance;
        ClientTerminalLimits.maxAltitude = maxAltitude;
        ClientTerminalLimits.whitelistEnabled = whitelistEnabled;
        ClientTerminalLimits.whitelistZones = whitelistZones;
        ClientTerminalLimits.blacklistEnabled = blacklistEnabled;
        ClientTerminalLimits.blacklistZones = blacklistZones;
    }

    /** Runs the server's own zone rule against the zones it sent us. */
    public static boolean isForbidden(int x, int z) {
        return dev.krutlexik.skyfront.config.SkyfrontConfig.isBlocked(x, z,
                whitelistEnabled, whitelistZones, blacklistEnabled, blacklistZones);
    }

    public static int maxWaypoints() {
        return maxWaypoints;
    }

    public static int minSpacing() {
        return minSpacing;
    }

    public static int minLaunchDistance() {
        return minLaunchDistance;
    }

    public static int maxAltitude() {
        return maxAltitude;
    }
}
