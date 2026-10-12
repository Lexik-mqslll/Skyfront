package dev.krutlexik.skyfront.drone.client;

import dev.krutlexik.skyfront.drone.DroneMod;
import dev.krutlexik.skyfront.drone.entity.DroneEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.Locale;

/**
 * The drone-feed OSD, laid out like a consumer FPV app: status bar on top, camera grid in the middle,
 * attitude dial bottom-left, live telemetry beside it, and capture info bottom-right.
 */
public class DroneOsd {
    private static final ResourceLocation ATLAS = DroneMod.id("textures/gui/osd.png");
    private static final Style FONT = Style.EMPTY.withFont(DroneMod.id("osd"));

    private static final float SMALL = 1.0F;
    private static final float TEXT = 1.35F;
    private static final float BIG = 1.9F;

    /** Stands in for any reading the app no longer has, exactly as the real one does. */
    private static final String NO_VALUE = "--";

    private static final int[] HOME = {24, 48, 20, 20};
    private static final int[] DOTS = {48, 48, 20, 20};
    private static final int[] SAT = {72, 48, 20, 20};
    private static final int[] FILM = {96, 48, 20, 20};
    private static final int[] PLAY = {120, 48, 20, 20};
    private static final int[] LAND = {144, 48, 20, 20};
    private static final int[] ALT = {168, 48, 12, 12};
    private static final int[] DIST = {184, 48, 12, 12};
    private static final int[] GREEN_DOT = {200, 48, 10, 10};
    private static final int[] H_MARK = {212, 48, 14, 14};
    private static final int[] RECORD = {228, 48, 26, 26};
    private static final int[] ZOOM_DISC = {0, 80, 30, 30};
    private static final int[] N_BADGE = {32, 80, 12, 12};
    private static final int[] COMPASS = {0, 120, 84, 84};
    private static final int[] ARROW = {96, 120, 64, 64};

    private static final int WHITE = 0xFFFFFFFF;
    private static final int LABEL = 0xFFC9D2D6;
    private static final int GRID = 0x2CFFFFFF;
    private static final int AMBER = 0xFFF2C846;
    private static final int RED = 0xFFE05244;
    /** Vivid, not the muted {@link #RED} the readouts use — an alert has to carry across the frame. */
    private static final int ALERT_RED = 0xFFFF1B1B;
    /** Healthy charge. */
    private static final int GREEN = 0xFF3FD65B;
    /** The unfilled remainder of the charge ring. */
    private static final int TRACK = 0x59FFFFFF;
    /** Cold night-blue the thermal camera sinks the picture into, leaving the heat to burn through it. */
    private static final int THERMAL_SCRIM = 0x8C061024;
    /** The thermal chip's own amber, hot enough to read as a live camera mode rather than a warning. */
    private static final int THERMAL_CHIP = 0xFFE07A18;

    public void render(GuiGraphics graphics, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        DroneEntity flying = DroneControl.drone();
        if (flying != null && flying.osdStyle() != DroneEntity.Osd.CAMERA) {
            return;
        }

        DroneFeedFreeze.captureIfRequested();

        boolean signalLost = DroneSignalLoss.isActive();
        DroneTelemetry telemetry;
        if (signalLost) {
            telemetry = DroneFeedFreeze.frozenTelemetry();
        } else {
            DroneEntity drone = DroneControl.drone();
            telemetry = drone != null && player != null ? DroneTelemetry.read(minecraft, drone, player) : null;
        }

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        DroneFeedFreeze.render(graphics);

        if (telemetry == null) {
            return;
        }

        boolean blanked = DroneSignalLoss.isBlanked();

        boolean thermal = ThermalVision.isOn() && !signalLost;
        if (thermal && !dev.krutlexik.skyfront.drone.client.render.ThermalScreenPass.isRendering()) {
            graphics.fill(0, 0, width, height, THERMAL_SCRIM);
        }

        graphics.fillGradient(0, 0, width, 26, 0x50000000, 0x00000000);
        graphics.fillGradient(0, height - 46, width, height, 0x00000000, 0x5E000000);

        drawGrid(graphics, width, height);
        drawTopBar(graphics, minecraft, telemetry, blanked, width);
        if (thermal) {
            drawThermalBadge(graphics, minecraft, 6, 24);
        }
        drawSideControls(graphics, width, height);
        drawCompass(graphics, telemetry, blanked, height);
        drawTelemetry(graphics, minecraft, telemetry, blanked, height);
        drawCaptureInfo(graphics, minecraft, telemetry, blanked, width, height);
        drawGrenadeDrop(graphics, width, height);
    }

    private static final ResourceLocation DROP_LIGHT = DroneMod.id("textures/gui/drone_light.png");
    private static final int DROP_LIGHT_W = 1920;
    private static final int DROP_LIGHT_H = 1080;

    private static final int LOGO_U = 610;
    private static final int LOGO_V = 170;
    private static final int LOGO_W = 700;
    private static final int LOGO_H = 654;

    /** The dark panel the confirmation sits on. */
    private static final int PANEL_W = 162;
    private static final int PANEL_H = 96;
    /** Gap kept between the artwork and the panel's edge. */
    private static final int PANEL_INSET = 8;

    /**
     * The release timer's fill bar while the pilot holds attack, and the confirmation image that flashes up
     * once the grenade has let go.
     */
    private void drawGrenadeDrop(GuiGraphics graphics, int width, int height) {
        float progress = DroneControl.dropProgress();
        if (progress > 0.0F) {
            int barWidth = 120;
            int barHeight = 4;
            int x = (width - barWidth) / 2;
            int y = height - 64;
            roundedRect(graphics, x - 2, y - 2, barWidth + 4, barHeight + 4, 2, 0x80000000);
            graphics.fill(x, y, x + Math.round(barWidth * progress), y + barHeight, ALERT_RED);
        }

        float alpha = DroneControl.dropFlashAlpha();
        if (alpha > 0.0F) {
            int panelX = (width - PANEL_W) / 2;
            int panelY = height - 62 - PANEL_H;

            int bgAlpha = Math.round(0x66 * alpha) << 24;
            roundedRect(graphics, panelX, panelY, PANEL_W, PANEL_H, 5, bgAlpha);

            int logoHeight = PANEL_H - PANEL_INSET * 2;
            int logoWidth = logoHeight * LOGO_W / LOGO_H;
            int logoX = panelX + (PANEL_W - logoWidth) / 2;
            int logoY = panelY + PANEL_INSET;

            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
            graphics.blit(DROP_LIGHT, logoX, logoY, logoWidth, logoHeight,
                    LOGO_U, LOGO_V, LOGO_W, LOGO_H, DROP_LIGHT_W, DROP_LIGHT_H);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    /**
     * A filled rectangle with rounded corners, drawn by insetting one-pixel rows near the top and bottom
     * edges — {@link GuiGraphics} can only fill axis-aligned rectangles, so the corners are stepped in the
     * same way the "No signal" badge's are.
     */
    private void roundedRect(GuiGraphics graphics, int x, int y, int w, int h, int radius, int color) {
        graphics.fill(x, y + radius, x + w, y + h - radius, color);
        for (int row = 0; row < radius; row++) {
            int inset = radius - row;
            graphics.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
            graphics.fill(x + inset, y + h - row - 1, x + w - inset, y + h - row, color);
        }
    }

    /** A reading the app still has, or {@value #NO_VALUE} once the link is gone. */
    private static String value(boolean blanked, String formatted) {
        return blanked ? NO_VALUE : formatted;
    }

    private void drawGrid(GuiGraphics graphics, int width, int height) {
        graphics.fill(width / 3, 0, width / 3 + 1, height, GRID);
        graphics.fill(2 * width / 3, 0, 2 * width / 3 + 1, height, GRID);
        graphics.fill(0, height / 3, width, height / 3 + 1, GRID);
        graphics.fill(0, 2 * height / 3, width, 2 * height / 3 + 1, GRID);

        float diagonal = (float) Math.sqrt((double) width * width + (double) height * height);
        float angle = (float) Math.atan2(height, width);
        for (int sign = -1; sign <= 1; sign += 2) {
            graphics.pose().pushPose();
            graphics.pose().translate(width / 2.0F, height / 2.0F, 0.0F);
            graphics.pose().mulPose(Axis.ZP.rotation(sign * angle));
            graphics.fill((int) (-diagonal / 2.0F), 0, (int) (diagonal / 2.0F), 1, GRID);
            graphics.pose().popPose();
        }
    }

    private void drawTopBar(GuiGraphics graphics, Minecraft minecraft, DroneTelemetry telemetry,
                            boolean blanked, int width) {
        icon(graphics, HOME, 6, 4, 14);
        text(graphics, "N Mode", 26, 7, TEXT, WHITE);

        if (DroneSignalLoss.showNoSignal()) {
            drawNoSignalBadge(graphics, minecraft, 74, 4);
        } else {
            text(graphics, telemetry.moving() ? "In flight" : "Hovering", 74, 7, TEXT, WHITE);
        }

        int cursor = width - 6;

        cursor -= 13;
        icon(graphics, DOTS, cursor, 5, 13);

        String satText = value(blanked, String.valueOf(telemetry.satellites()));
        cursor -= 7 + (int) width(minecraft, satText, TEXT);
        text(graphics, satText, cursor, 6, TEXT, !blanked && telemetry.satellites() >= 12 ? WHITE : AMBER);
        cursor -= 15;
        icon(graphics, SAT, cursor, 4, 14);

        int bars = blanked ? 0 : telemetry.linkBars();
        int barColor = bars >= 4 ? WHITE : bars == 3 ? AMBER : RED;
        cursor -= 8 + (int) width(minecraft, "RC", SMALL);
        text(graphics, "RC", cursor, 8, SMALL, WHITE);
        cursor -= 18;
        for (int i = 0; i < 5; i++) {
            int barHeight = 3 + i;
            int x = cursor + i * 3;
            graphics.fill(x, 16 - barHeight, x + 2, 16, i < bars ? barColor : 0x50FFFFFF);
        }

        String timeText = value(blanked, telemetry.remainingTime());
        cursor -= 9 + (int) width(minecraft, timeText, TEXT);
        text(graphics, timeText, cursor, 6, TEXT, WHITE);

        cursor -= 26;
        drawBatteryRing(graphics, minecraft, cursor + 9, 12, telemetry.charge(), blanked);
    }

    /**
     * The charge gauge: a ring that drains clockwise from twelve o'clock with the percentage sitting in the
     * middle of it.
     */
    private void drawBatteryRing(GuiGraphics graphics, Minecraft minecraft, int centreX, int centreY,
                                 float charge, boolean blanked) {
        final int segments = 60;
        final float radius = 8.0F;
        final float thickness = 2.0F;

        int fillColor = charge <= 0.15F ? RED : charge <= 0.30F ? AMBER : GREEN;
        int filled = Mth.clamp(Math.round(charge * segments), 0, segments);

        for (int i = 0; i < segments; i++) {
            float angle = (float) (i * 2.0D * Math.PI / segments);
            float halfChord = (float) Math.PI * radius / segments + 0.35F;

            graphics.pose().pushPose();
            graphics.pose().translate(centreX + 0.5F, centreY + 0.5F, 0.0F);
            graphics.pose().mulPose(Axis.ZP.rotation(angle));
            graphics.fill(Math.round(-halfChord), Math.round(-radius),
                    Math.round(halfChord), Math.round(-radius + thickness),
                    i < filled && !blanked ? fillColor : TRACK);
            graphics.pose().popPose();
        }

        String percent = value(blanked, String.valueOf(Math.round(charge * 100.0F)));
        centreText(graphics, minecraft, percent, centreX + 0.75F, centreY, SMALL,
                !blanked && charge <= 0.15F ? RED : WHITE);
    }

    /** The red "No signal" chip shown in place of the flight mode. */
    private void drawNoSignalBadge(GuiGraphics graphics, Minecraft minecraft, int x, int y) {
        String label = "No signal";
        int padding = 6;
        int textWidth = (int) Math.ceil(width(minecraft, label, TEXT));
        int badgeWidth = textWidth + padding * 2;
        int badgeHeight = 15;
        int radius = 3;

        graphics.fill(x, y + radius, x + badgeWidth, y + badgeHeight - radius, ALERT_RED);
        for (int row = 0; row < radius; row++) {
            int inset = radius - row;
            graphics.fill(x + inset, y + row, x + badgeWidth - inset, y + row + 1, ALERT_RED);
            graphics.fill(x + inset, y + badgeHeight - row - 1, x + badgeWidth - inset, y + badgeHeight - row, ALERT_RED);
        }
        centreText(graphics, minecraft, label, x + badgeWidth / 2, y + badgeHeight / 2, TEXT, WHITE);
    }

    /** The chip that says the camera is on heat rather than light, sitting just under the flight-mode line. */
    private void drawThermalBadge(GuiGraphics graphics, Minecraft minecraft, int x, int y) {
        String label = "Thermal";
        int padding = 6;
        int badgeWidth = (int) Math.ceil(width(minecraft, label, TEXT)) + padding * 2;
        int badgeHeight = 15;
        roundedRect(graphics, x, y, badgeWidth, badgeHeight, 3, THERMAL_CHIP);
        centreText(graphics, minecraft, label, x + badgeWidth / 2.0F, y + badgeHeight / 2.0F, TEXT, WHITE);
    }

    private void drawSideControls(GuiGraphics graphics, int width, int height) {
        icon(graphics, LAND, 6, height / 2 - 40, 15);
        icon(graphics, FILM, width - 21, (int) (height * 0.30F), 14);

        Minecraft minecraft = Minecraft.getInstance();
        int zoomX = width - 36;
        int zoomY = height / 2 - 34;
        int zoomSize = 24;
        icon(graphics, ZOOM_DISC, zoomX, zoomY, zoomSize);
        float zoom = DroneControl.zoom();
        String zoomText = zoom == Math.floor(zoom)
                ? String.format(Locale.ROOT, "%d×", (int) zoom)
                : String.format(Locale.ROOT, "%.1f×", zoom);
        centreText(graphics, minecraft, zoomText, zoomX + zoomSize / 2.0F, zoomY + zoomSize / 2.0F,
                SMALL, WHITE);

        icon(graphics, RECORD, width - 37, height / 2 - 4, 26);
        icon(graphics, PLAY, width - 32, height / 2 + 32, 16);
    }

    private void drawCompass(GuiGraphics graphics, DroneTelemetry telemetry, boolean blanked, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        int boxX = 5;
        int boxY = height - 89;
        icon(graphics, COMPASS, boxX, boxY, 84);
        int centerX = boxX + 42;
        int centerY = boxY + 38;

        graphics.blit(ATLAS, centerX - 32, centerY - 32, 64, 64, ARROW[0], ARROW[1], ARROW[2], ARROW[3], 256, 256);

        float yaw = telemetry.droneYaw();
        String[] names = {"N", "E", "S", "W"};
        float[] worldYaws = {180.0F, -90.0F, 0.0F, 90.0F};
        for (int i = 0; i < 4; i++) {
            double relative = Math.toRadians(Mth.wrapDegrees(worldYaws[i] - yaw));
            float x = centerX + (float) Math.sin(relative) * 26.0F;
            float y = centerY - (float) Math.cos(relative) * 26.0F;
            if (i == 0) {
                icon(graphics, N_BADGE, (int) x - 5, (int) y - 5, 10);
            }
            float letterWidth = width(minecraft, names[i], SMALL);
            text(graphics, names[i], x - letterWidth / 2.0F, y - 3.5F, SMALL, 0xE8FFFFFF);
        }

        if (!blanked) {
            double relative = Math.toRadians(Mth.wrapDegrees(telemetry.operatorYaw() - yaw));
            float radius = 6.0F + (float) Math.min(telemetry.distance() / telemetry.linkRange(), 1.0D) * 20.0F;
            int markX = centerX + (int) (Math.sin(relative) * radius) - 5;
            int markY = centerY - (int) (Math.cos(relative) * radius) - 5;
            icon(graphics, H_MARK, markX, markY, 10);
        }

        icon(graphics, GREEN_DOT, centerX - 3, centerY - 3, 6);

        text(graphics, "L", boxX + 7, boxY + 72, SMALL, LABEL);
        for (int i = 0; i < 3; i++) {
            graphics.fill(boxX + 64 + i * 5, boxY + 74 + (2 - i), boxX + 67 + i * 5, boxY + 79, 0xB4FFFFFF);
        }
    }

    private void drawTelemetry(GuiGraphics graphics, Minecraft minecraft, DroneTelemetry telemetry,
                               boolean blanked, int height) {
        int x = 96;
        icon(graphics, ALT, x, height - 39, 10);
        String altText = value(blanked, String.format(Locale.ROOT, "%.0f m", telemetry.altitude()));
        text(graphics, altText, x + 14, height - 42, BIG, WHITE);
        text(graphics, value(blanked, String.format(Locale.ROOT, "%.1f km/h", telemetry.verticalKmh())),
                x + 18 + width(minecraft, altText, BIG), height - 38, SMALL, LABEL);

        icon(graphics, DIST, x, height - 21, 10);
        String distText = value(blanked, String.format(Locale.ROOT, "%.0f m", telemetry.distance()));
        text(graphics, distText, x + 14, height - 24, BIG, WHITE);
        text(graphics, value(blanked, String.format(Locale.ROOT, "%.1f km/h", telemetry.horizontalKmh())),
                x + 18 + width(minecraft, distText, BIG), height - 20, SMALL, LABEL);
    }

    private void drawCaptureInfo(GuiGraphics graphics, Minecraft minecraft, DroneTelemetry telemetry,
                                 boolean blanked, int width, int height) {
        int labelY = height - 34;
        int valueY = height - 22;
        int cursor = width - 8;

        int chipWidth = 26;
        int chipX = cursor - chipWidth;
        int chipY = valueY - 2;
        graphics.fill(chipX, chipY, chipX + chipWidth, chipY + 1, WHITE);
        graphics.fill(chipX, chipY + 11, chipX + chipWidth, chipY + 12, WHITE);
        graphics.fill(chipX, chipY, chipX + 1, chipY + 12, WHITE);
        graphics.fill(chipX + chipWidth - 1, chipY, chipX + chipWidth, chipY + 12, WHITE);
        float autoWidth = width(minecraft, "AUTO", SMALL);
        text(graphics, "AUTO", chipX + chipWidth / 2.0F - autoWidth / 2.0F, chipY + 3, SMALL, WHITE);
        cursor = chipX - 12;

        cursor = column(graphics, minecraft, "EV", value(blanked, "+0.3"), cursor, labelY, valueY);
        cursor = column(graphics, minecraft, "RES&FPS", value(blanked, telemetry.resolution()), cursor, labelY, valueY);
        int storageSeconds = Math.max(0, 2700 - telemetry.flightSeconds());
        String storage = String.format(Locale.ROOT, "%02d:%02d:%02d",
                storageSeconds / 3600, storageSeconds % 3600 / 60, storageSeconds % 60);
        column(graphics, minecraft, "Storage", value(blanked, storage), cursor, labelY, valueY);
    }

    /** Draws a right-aligned label/value column ending at {@code rightX}; returns the next cursor. */
    private int column(GuiGraphics graphics, Minecraft minecraft, String label, String value,
                       int rightX, int labelY, int valueY) {
        float labelWidth = width(minecraft, label, SMALL);
        float valueWidth = width(minecraft, value, TEXT);
        float columnWidth = Math.max(labelWidth, valueWidth);
        text(graphics, label, rightX - columnWidth, labelY, SMALL, LABEL);
        text(graphics, value, rightX - columnWidth, valueY, TEXT, WHITE);
        return rightX - (int) columnWidth - 12;
    }

    private void icon(GuiGraphics graphics, int[] region, int x, int y, int size) {
        int drawHeight = size * region[3] / region[2];
        graphics.blit(ATLAS, x, y, size, drawHeight, region[0], region[1], region[2], region[3], 256, 256);
    }

    /** Draws text centred on a point, both across and down. */
    private void centreText(GuiGraphics graphics, Minecraft minecraft, String string,
                            float centreX, float centreY, float scale, int color) {
        float textWidth = width(minecraft, string, scale);
        float ascent = (minecraft.font.lineHeight - 1) * scale;
        text(graphics, string, centreX - textWidth / 2.0F, centreY - ascent / 2.0F, scale, color);
    }

    private void text(GuiGraphics graphics, String string, float x, float y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(Minecraft.getInstance().font, component(string), 0, 0, color, false);
        graphics.pose().popPose();
    }

    private float width(Minecraft minecraft, String string, float scale) {
        return minecraft.font.width(component(string)) * scale;
    }

    private static Component component(String string) {
        return Component.literal(string).setStyle(FONT);
    }
}
