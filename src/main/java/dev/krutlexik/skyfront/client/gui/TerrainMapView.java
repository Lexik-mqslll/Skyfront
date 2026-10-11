package dev.krutlexik.skyfront.client.gui;

import dev.krutlexik.skyfront.client.ClientWorldMapData;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Draws the scanned terrain map (one pixel per chunk) as a single textured quad. */
@OnlyIn(Dist.CLIENT)
public class TerrainMapView implements AutoCloseable {
    private final ResourceLocation textureId;
    private DynamicTexture texture;
    private int builtVersion = -1;
    private int textureSize;

    public TerrainMapView(ResourceLocation textureId) {
        this.textureId = textureId;
    }

    private void ensureTexture() {
        ClientWorldMapData mapData = ClientWorldMapData.getInstance();
        if (!mapData.hasData() || mapData.getVersion() == this.builtVersion) {
            return;
        }
        int size = mapData.getRadiusChunks() * 2 + 1;
        int[] colors = mapData.getColors();
        if (colors.length < size * size) {
            return;
        }
        NativeImage image = new NativeImage(size, size, false);
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int rgb = colors[z * size + x];
                if (rgb == 0) {
                    image.setPixelRGBA(x, z, 0);
                } else {
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    image.setPixelRGBA(x, z, 0xFF000000 | (b << 16) | (g << 8) | r);
                }
            }
        }
        if (this.texture != null) {
            Minecraft.getInstance().getTextureManager().release(this.textureId);
        }
        this.texture = new DynamicTexture(image);
        Minecraft.getInstance().getTextureManager().register(this.textureId, this.texture);
        this.builtVersion = mapData.getVersion();
        this.textureSize = size;
    }

    /** Full-brightness render (the radar's own display). */
    public void render(GuiGraphics graphics, int centerX, int centerY, int mapHalf,
                       double originX, double originZ, float scale, float offsetX, float offsetZ) {
        render(graphics, centerX, centerY, mapHalf, originX, originZ, scale, offsetX, offsetZ, 1.0f);
    }

    public void render(GuiGraphics graphics, int centerX, int centerY, int mapHalf,
                       double originX, double originZ, float scale, float offsetX, float offsetZ,
                       float brightness) {
        ensureTexture();
        if (this.texture == null) {
            return;
        }
        ClientWorldMapData mapData = ClientWorldMapData.getInstance();
        double worldX0 = (mapData.getCenterChunkX() - mapData.getRadiusChunks()) * 16.0;
        double worldZ0 = (mapData.getCenterChunkZ() - mapData.getRadiusChunks()) * 16.0;
        int x0 = centerX + Math.round((float) ((worldX0 - originX + offsetX) * scale));
        int y0 = centerY + Math.round((float) ((worldZ0 - originZ + offsetZ) * scale));
        int span = Math.round(this.textureSize * 16.0f * scale);
        if (span <= 0) {
            return;
        }
        graphics.enableScissor(centerX - mapHalf, centerY - mapHalf, centerX + mapHalf, centerY + mapHalf);
        if (brightness != 1.0f) {
            graphics.setColor(brightness, brightness, brightness, 1.0f);
        }
        graphics.blit(this.textureId, x0, y0, span, span, 0.0f, 0.0f,
                this.textureSize, this.textureSize, this.textureSize, this.textureSize);
        if (brightness != 1.0f) {
            graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
        graphics.disableScissor();
    }

    @Override
    public void close() {
        if (this.texture != null) {
            Minecraft.getInstance().getTextureManager().release(this.textureId);
            this.texture = null;
            this.builtVersion = -1;
        }
    }
}
