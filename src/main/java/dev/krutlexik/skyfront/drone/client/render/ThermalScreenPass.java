package dev.krutlexik.skyfront.drone.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.krutlexik.skyfront.drone.client.ThermalVision;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.world.level.Level;
import org.lwjgl.opengl.GL11;

/** The half of the thermal camera that catches the world itself. */
public final class ThermalScreenPass {
    private static int texture = -1;
    private static int width;
    private static int height;

    /** Draws the camera's picture over the finished world. */
    public static void render(float partialTick) {
        render(partialTick, 0);
    }

    public static void render(float partialTick, int bodyMask) {
        if (!ThermalVision.isOn()) {
            return;
        }
        ShaderInstance shader = ThermalShaders.screen();
        if (shader == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        int w = minecraft.getMainRenderTarget().width;
        int h = minecraft.getMainRenderTarget().height;
        if (w <= 0 || h <= 0) {
            return;
        }

        if (texture == -1 || w != width || h != height) {
            if (texture != -1) {
                TextureUtil.releaseTextureId(texture);
            }
            texture = TextureUtil.generateTextureId();
            RenderSystem.bindTexture(texture);
            TextureUtil.prepareImage(texture, 0, w, h);
            width = w;
            height = h;
        }
        RenderSystem.bindTexture(texture);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);

        setUniforms(shader, minecraft, partialTick);
        com.mojang.blaze3d.shaders.Uniform useMask = shader.getUniform("UseMask");
        if (useMask != null) {
            useMask.set(bodyMask != 0 ? 1.0F : 0.0F);
        }

        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShaderTexture(1, bodyMask);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(-1.0F, -1.0F, 0.0F).setUv(0.0F, 0.0F);
        buffer.addVertex(1.0F, -1.0F, 0.0F).setUv(1.0F, 0.0F);
        buffer.addVertex(1.0F, 1.0F, 0.0F).setUv(1.0F, 1.0F);
        buffer.addVertex(-1.0F, 1.0F, 0.0F).setUv(0.0F, 1.0F);
        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    /** How warm the ground is here, and where the sensor's noise has got to. */
    private static void setUniforms(ShaderInstance shader, Minecraft minecraft, float partialTick) {
        float ambient = 0.16F;
        Level level = minecraft.level;
        if (level != null && minecraft.getCameraEntity() != null) {
            float biome = level.getBiome(minecraft.getCameraEntity().blockPosition()).value()
                    .getBaseTemperature();
            float normalised = Math.max(0.0F, Math.min(1.0F, (biome + 0.5F) / 2.5F));
            ambient = 0.10F + normalised * 0.10F + (level.isDay() ? 0.04F : 0.0F);
        }
        com.mojang.blaze3d.shaders.Uniform uniform = shader.getUniform("ambientTemp");
        if (uniform != null) {
            uniform.set(ambient);
        }
        uniform = shader.getUniform("Time");
        if (uniform != null) {
            uniform.set((System.currentTimeMillis() % 100000L) / 1000.0F);
        }
    }

    /** Dropped on a resource reload: the texture belongs to the context that is going away. */
    public static void unload() {
        if (texture != -1) {
            TextureUtil.releaseTextureId(texture);
            texture = -1;
        }
        width = 0;
        height = 0;
    }

    /** Whether the camera can actually draw, as opposed to merely being switched on. */
    public static boolean isRendering() {
        return ThermalShaders.screen() != null;
    }

    private ThermalScreenPass() {
    }
}
