package dev.krutlexik.skyfront.client.gui;

import dev.krutlexik.skyfront.client.ClientWorldMapData;
import dev.krutlexik.skyfront.network.NetworkHandler;
import dev.krutlexik.skyfront.network.RequestRadarUpdatePacket;
import dev.krutlexik.skyfront.network.RequestWorldMapPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@OnlyIn(Dist.CLIENT)
public class ShahedRadarScreen extends Screen {
    private final BlockPos radarPos;
    private static final int MAP_RADIUS = 2000;
    /** Detection radius reported by the server (config-driven); the default until the first sync. */
    private int droneDetectRadius = 1000;
    private static final int UPDATE_INTERVAL = 60;
    private static final int MAP_UPDATE_INTERVAL = 200;
    /** Terrain map texture: one pixel per chunk, rebuilt only when new map data arrives. */
    private final TerrainMapView mapView = new TerrainMapView(
            new ResourceLocation("sfw", "dynamic/radar_map"));
    
    private static ShahedRadarScreen currentInstance = null;
    
    private List<Vec3> shahedPositions = new CopyOnWriteArrayList<>();
    private int tickCounter = 0;
    private int mapTickCounter = 0;
    private float scanAngle = 0;
    
    private float zoom = 1.0f;
    private float offsetX = 0;
    private float offsetZ = 0;
    private boolean isDragging = false;
    private int lastMouseX;
    private int lastMouseY;

    public ShahedRadarScreen(BlockPos radarPos) {
        super(Component.translatable("gui.sfw.radar.title"));
        this.radarPos = radarPos;
    }

    public static void open(BlockPos pos) {
        ShahedRadarScreen screen = new ShahedRadarScreen(pos);
        currentInstance = screen;
        Minecraft.getInstance().setScreen(screen);
    }
    
    public static void updateDronePositions(BlockPos radarPos, int detectRange, List<Vec3> positions) {
        if (currentInstance != null && currentInstance.radarPos.equals(radarPos)) {
            currentInstance.droneDetectRadius = detectRange;
            currentInstance.shahedPositions.clear();
            currentInstance.shahedPositions.addAll(positions);
        }
    }

    @Override
    protected void init() {
        super.init();
        requestRadarUpdate();
        requestMapUpdate();
    }
    
    @Override
    public void removed() {
        super.removed();
        currentInstance = null;
        this.mapView.close();
    }

    @Override
    public void tick() {
        super.tick();
        
        if (Minecraft.getInstance().player != null) {
            double distance = Minecraft.getInstance().player.position().distanceTo(Vec3.atCenterOf(radarPos));
            if (distance > 7.0) {
                this.onClose();
                return;
            }
        }
        
        tickCounter++;
        mapTickCounter++;
        scanAngle += 2.0f;
        if (scanAngle >= 360) scanAngle -= 360;
        
        if (tickCounter >= UPDATE_INTERVAL) {
            tickCounter = 0;
            requestRadarUpdate();
        }
        
        if (mapTickCounter >= MAP_UPDATE_INTERVAL) {
            mapTickCounter = 0;
            requestMapUpdate();
        }
    }

    private void requestRadarUpdate() {
        NetworkHandler.sendToServer(new RequestRadarUpdatePacket(radarPos));
    }
    
    private void requestMapUpdate() {
        NetworkHandler.sendToServer(new RequestWorldMapPacket(radarPos.getX(), radarPos.getZ(), MAP_RADIUS));
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, 0x88000000);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int mapSize = Math.min(this.width, this.height) - 60;
        int mapHalf = mapSize / 2;

        graphics.fill(centerX - mapHalf - 2, centerY - mapHalf - 2, 
                     centerX + mapHalf + 2, centerY + mapHalf + 2, 0xFF1a1a2e);
        graphics.fill(centerX - mapHalf, centerY - mapHalf, 
                     centerX + mapHalf, centerY + mapHalf, 0xFF000000);

        renderTerrain(graphics, centerX, centerY, mapHalf);

        renderGrid(graphics, centerX, centerY, mapHalf);
        
        renderRadarSweep(graphics, centerX, centerY, mapHalf);
        
        renderDrones(graphics, centerX, centerY, mapHalf, mouseX, mouseY);
        
        renderRadarCenter(graphics, centerX, centerY);

        renderUI(graphics, centerX, centerY, mapHalf);

        // The 1.21 Screen.render painted the background again here, over the whole display; kept so it looks the same.
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    
    /** Draws the terrain map as a single textured quad, scaled/panned with the radar view. */
    private void renderTerrain(GuiGraphics graphics, int centerX, int centerY, int mapHalf) {
        float scale = (float) mapHalf / (MAP_RADIUS / zoom);
        this.mapView.render(graphics, centerX, centerY, mapHalf,
                radarPos.getX(), radarPos.getZ(), scale, offsetX, offsetZ);
    }
    
    private void renderGrid(GuiGraphics graphics, int centerX, int centerY, int mapHalf) {
        int gridColor = 0xFF1a3a1a;
        
        float scale = (float) mapHalf / (MAP_RADIUS / zoom);
        int gridSpacing = 1000;
        
        int radarX = radarPos.getX();
        int radarZ = radarPos.getZ();
        
        int startX = (int) (radarX - MAP_RADIUS / zoom - offsetX);
        int endX = (int) (radarX + MAP_RADIUS / zoom - offsetX);
        int startZ = (int) (radarZ - MAP_RADIUS / zoom - offsetZ);
        int endZ = (int) (radarZ + MAP_RADIUS / zoom - offsetZ);
        
        startX = (startX / gridSpacing) * gridSpacing;
        startZ = (startZ / gridSpacing) * gridSpacing;
        
        for (int x = startX; x <= endX; x += gridSpacing) {
            float relX = (x - radarX + offsetX) * scale;
            if (Math.abs(relX) <= mapHalf) {
                int screenX = centerX + (int) relX;
                graphics.vLine(screenX, centerY - mapHalf, centerY + mapHalf, gridColor);
            }
        }
        
        for (int z = startZ; z <= endZ; z += gridSpacing) {
            float relZ = (z - radarZ + offsetZ) * scale;
            if (Math.abs(relZ) <= mapHalf) {
                int screenY = centerY + (int) relZ;
                graphics.hLine(centerX - mapHalf, centerX + mapHalf, screenY, gridColor);
            }
        }
        
        graphics.hLine(centerX - mapHalf, centerX + mapHalf, centerY, 0xFF2d5a3d);
        graphics.vLine(centerX, centerY - mapHalf, centerY + mapHalf, 0xFF2d5a3d);
    }
    
    private void renderRadarSweep(GuiGraphics graphics, int centerX, int centerY, int mapHalf) {
        float scale = (float) mapHalf / (MAP_RADIUS / zoom);
        int droneRadiusOnScreen = (int) (droneDetectRadius * scale);
        int originX = centerX + (int) (offsetX * scale);
        int originY = centerY + (int) (offsetZ * scale);

        graphics.enableScissor(centerX - mapHalf, centerY - mapHalf, centerX + mapHalf, centerY + mapHalf);

        if (droneRadiusOnScreen > 10) {
            drawCircle(graphics, originX, originY, droneRadiusOnScreen, 0xFF2d5a3d);
        }

        double scanRad = Math.toRadians(scanAngle);
        int scanEndX = originX + (int) (Math.cos(scanRad) * droneRadiusOnScreen);
        int scanEndY = originY - (int) (Math.sin(scanRad) * droneRadiusOnScreen);
        drawLine(graphics, originX, originY, scanEndX, scanEndY, 0x8800ff00);

        graphics.disableScissor();
    }
    
    private void renderDrones(GuiGraphics graphics, int centerX, int centerY, int mapHalf, int mouseX, int mouseY) {
        float scale = (float) mapHalf / (MAP_RADIUS / zoom);
        int radarX = radarPos.getX();
        int radarZ = radarPos.getZ();

        graphics.enableScissor(centerX - mapHalf, centerY - mapHalf, centerX + mapHalf, centerY + mapHalf);
        for (Vec3 shahedPos : shahedPositions) {
            float relX = (float) (shahedPos.x - radarX + offsetX) * scale;
            float relZ = (float) (shahedPos.z - radarZ + offsetZ) * scale;
            
            if (Math.abs(relX) > mapHalf || Math.abs(relZ) > mapHalf) {
                continue;
            }
            
            int dotX = centerX + (int) relX;
            int dotY = centerY + (int) relZ;
            
            int alpha = (int)(200 + 55 * Math.sin(tickCounter * 0.3));
            int color = (alpha << 24) | 0xFF0000;
            drawFilledCircle(graphics, dotX, dotY, 4, color);
            
            double distance = Math.sqrt(Math.pow(shahedPos.x - radarX, 2) + Math.pow(shahedPos.z - radarZ, 2));
            if (Math.abs(mouseX - dotX) < 10 && Math.abs(mouseY - dotY) < 10) {
                String distText = Component.translatable("gui.sfw.radar.distance", String.format("%.0f", distance)).getString();
                graphics.drawString(this.font, distText, dotX + 8, dotY - 4, 0xFFFFFF);
            }
        }
        graphics.disableScissor();
    }
    
    private void renderRadarCenter(GuiGraphics graphics, int centerX, int centerY) {
        int mapHalf = (Math.min(this.width, this.height) - 60) / 2;
        float scale = (float) mapHalf / (MAP_RADIUS / zoom);
        int markerX = centerX + (int) (offsetX * scale);
        int markerY = centerY + (int) (offsetZ * scale);

        graphics.enableScissor(centerX - mapHalf, centerY - mapHalf, centerX + mapHalf, centerY + mapHalf);
        drawFilledCircle(graphics, markerX, markerY, 4, 0xFF00FF00);
        graphics.drawString(this.font, "R", markerX - 3, markerY - 4, 0xFF000000);
        graphics.disableScissor();
    }
    
    private void renderUI(GuiGraphics graphics, int centerX, int centerY, int mapHalf) {
        graphics.drawCenteredString(this.font, Component.translatable("gui.sfw.radar.title").getString(), 
                                   centerX, centerY - mapHalf - 15, 0x00FF00);
        
        String countText = Component.translatable("gui.sfw.radar.detected", shahedPositions.size()).getString();
        graphics.drawCenteredString(this.font, countText, centerX, centerY + mapHalf + 5, 0xFFFFFF);
        
        ClientWorldMapData mapData = ClientWorldMapData.getInstance();
        if (!mapData.isScanComplete() && mapData.getTotalChunks() > 0) {
            String scanText = Component.translatable("gui.sfw.radar.scanning", 
                String.format("%.1f", mapData.getScanPercent()), mapData.getScanProgress(), mapData.getTotalChunks()).getString();
            graphics.drawCenteredString(this.font, scanText, centerX, centerY + mapHalf + 17, 0xFFAA00);
        }
        
        String zoomText = Component.translatable("gui.sfw.radar.zoom", String.format("%.1f", zoom)).getString();
        graphics.drawString(this.font, zoomText, 5, 5, 0xAAAAAA);
        
        String coordText = Component.translatable("gui.sfw.radar.center", radarPos.getX(), radarPos.getZ()).getString();
        graphics.drawString(this.font, coordText, 5, 17, 0xAAAAAA);
        
        graphics.drawString(this.font, Component.translatable("gui.sfw.radar.controls").getString(), 5, this.height - 12, 0x555555);
    }

    private void drawFilledCircle(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        for (int y = -radius; y <= radius; y++) {
            int halfWidth = (int) Math.sqrt(radius * radius - y * y);
            graphics.hLine(centerX - halfWidth, centerX + halfWidth, centerY + y, color);
        }
    }

    private void drawCircle(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        int segments = 64;
        for (int i = 0; i < segments; i++) {
            double angle1 = 2 * Math.PI * i / segments;
            double angle2 = 2 * Math.PI * (i + 1) / segments;
            int x1 = centerX + (int)(Math.cos(angle1) * radius);
            int y1 = centerY + (int)(Math.sin(angle1) * radius);
            int x2 = centerX + (int)(Math.cos(angle2) * radius);
            int y2 = centerY + (int)(Math.sin(angle2) * radius);
            drawLine(graphics, x1, y1, x2, y2, color);
        }
    }

    private void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            graphics.fill(x1, y1, x1 + 1, y1 + 1, color);
            if (x1 == x2 && y1 == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x1 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y1 += sy;
            }
        }
    }
    
    /** How far the view may be panned before it would show blank space past the map's edge. */
    private float maxPan() {
        return Math.max(0.0f, MAP_RADIUS - MAP_RADIUS / zoom);
    }

    private void clampPan() {
        float max = maxPan();
        offsetX = net.minecraft.util.Mth.clamp(offsetX, -max, max);
        offsetZ = net.minecraft.util.Mth.clamp(offsetZ, -max, max);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (scrollY > 0) {
            zoom = Math.min(zoom * 1.2f, 10.0f);
        } else {
            zoom = Math.max(zoom / 1.2f, 1.0f);
        }
        clampPan();
        return true;
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            isDragging = true;
            lastMouseX = (int) mouseX;
            lastMouseY = (int) mouseY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            isDragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDragging && button == 0) {
            int mapHalf = (Math.min(this.width, this.height) - 60) / 2;
            float scale = (float) mapHalf / (MAP_RADIUS / zoom);

            offsetX += (float) (dragX / scale);
            offsetZ += (float) (dragY / scale);
            clampPan();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
