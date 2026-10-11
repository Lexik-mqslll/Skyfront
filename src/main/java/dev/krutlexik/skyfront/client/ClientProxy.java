package dev.krutlexik.skyfront.client;

import dev.krutlexik.skyfront.client.gui.ShahedRouteScreen;
import dev.krutlexik.skyfront.client.gui.ShahedTerminalScreen;
import dev.krutlexik.skyfront.network.TerminalDataPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class ClientProxy {
    /** Terminal data arrived: open the list screen, or refresh whichever terminal screen is open. */
    public static void handleTerminalData(BlockPos terminalPos, boolean openScreen, int maxWaypoints, int minSpacing,
                                          int minLaunchDistance, int maxAltitude,
                                          boolean whitelistEnabled, int[] whitelistZones,
                                          boolean blacklistEnabled, int[] blacklistZones,
                                          List<TerminalDataPacket.Row> rows) {
        ClientTerminalLimits.update(maxWaypoints, minSpacing, minLaunchDistance, maxAltitude,
                whitelistEnabled, whitelistZones, blacklistEnabled, blacklistZones);
        Minecraft mc = Minecraft.getInstance();
        Screen current = mc.screen;
        if (current instanceof ShahedTerminalScreen list && list.getTerminalPos().equals(terminalPos)) {
            list.updateData(rows);
            return;
        }
        if (current instanceof ShahedRouteScreen route && route.getTerminalPos().equals(terminalPos)) {
            route.updateData(rows);
            return;
        }
        if (openScreen) {
            mc.setScreen(new ShahedTerminalScreen(terminalPos, rows));
        }
    }
}
