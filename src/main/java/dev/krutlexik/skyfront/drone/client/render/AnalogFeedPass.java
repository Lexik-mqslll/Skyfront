package dev.krutlexik.skyfront.drone.client.render;

import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.krutlexik.skyfront.drone.client.DroneControl;
import dev.krutlexik.skyfront.drone.entity.DroneEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import org.lwjgl.opengl.GL11;

import java.util.Random;

/**
 * An analog video link over an analog airframe's feed, modelled on recordings of real 5.8 GHz FPV video (see
 * {@code analog_feed.fsh} for how each effect looks).
 */
public final class AnalogFeedPass {
    private static int texture = -1;
    private static int width;
    private static int height;

    private static final Random RANDOM = new Random();

    private static long nextStep;
    /** A slow random walk: the fading that comes and goes as the airframe moves. */
    private static double fade;
    private static float level;
    private static long burstUntil;
    private static float burstStrength;
    /** A run of tearing bands in progress: until when, and how thick they come. */
    private static long stripesUntil;
    private static float stripesStrength;
    private static long syncUntil;
    private static float syncShift;
    private static long rollUntil;
    private static float roll;
    private static float rollSpeed;

    /**
     * Within this many blocks of the pilot the link is solid: the picture looks analog — soft, a little
     * washed out — but carries no snow, no dropouts and no sync trouble at all.
     */
    private static final double CLEAN_RANGE = 100.0D;

    private AnalogFeedPass() {
    }

    /** Stage 0, before the OSD: the camera picture through the link, as bad as the range makes it. */
    public static void renderPicture(GuiGraphics graphics, DroneEntity drone) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        double range = DroneControl.controlRange();
        double reach = Mth.clamp((minecraft.player.distanceTo(drone) - CLEAN_RANGE)
                / Math.max(1.0D, range - CLEAN_RANGE), 0.0D, 1.0D);
        advance(reach);
        draw(graphics, 0.0F, false);
    }

    /** Stage 1, after the OSD: snow, dropouts, tearing bands and sync trouble over everything. */
    public static void renderOverlay(GuiGraphics graphics) {
        draw(graphics, 1.0F, false);
    }

    /**
     * The link gone altogether: dark static of short dashes over the whole picture, the OSD that froze with
     * it all but gone under it.
     */
    public static void renderStatic(GuiGraphics graphics) {
        draw(graphics, 0.0F, true);
    }

    /** Moves the link on in 50 ms steps, so it behaves the same at any frame rate. */
    private static void advance(double reach) {
        long now = System.currentTimeMillis();
        if (now - nextStep > 1000L) {
            nextStep = now;
        }
        while (now >= nextStep) {
            nextStep += 50L;
            step(reach, nextStep);
        }
    }

    private static void step(double reach, long now) {
        fade = (fade + (RANDOM.nextDouble() - 0.5D) * 0.35D) * 0.92D;
        double base = 0.55D * Math.pow(reach, 2.2D);
        double fading = 0.25D * Math.pow(reach, 1.2D) * Math.max(0.0D, fade);
        level = (float) Mth.clamp(base + fading, 0.0D, 1.0D);

        if (reach <= 0.0D) {
            return;
        }

        if (now >= burstUntil && RANDOM.nextDouble() < 0.0015D + 0.18D * Math.pow(reach, 2.5D)) {
            burstUntil = now + 80L + RANDOM.nextInt(420);
            burstStrength = (float) ((0.3D + 0.7D * RANDOM.nextDouble()) * (0.35D + 0.65D * reach));
        }
        float burst = now < burstUntil ? burstStrength : 0.0F;
        float total = level + burst;

        if (now >= stripesUntil && RANDOM.nextDouble() < 0.012D + 0.22D * Math.pow(level, 1.3D)) {
            stripesUntil = now + 40L + RANDOM.nextInt(180);
            stripesStrength = (float) (0.35D + 0.65D * RANDOM.nextDouble());
        }

        if (now >= syncUntil && total > 0.55F && RANDOM.nextDouble() < 0.12D) {
            syncUntil = now + 100L + RANDOM.nextInt(500);
            syncShift = 0.15F + 0.7F * RANDOM.nextFloat();
        } else if (now < syncUntil) {
            syncShift = (syncShift + (RANDOM.nextFloat() - 0.5F) * 0.02F + 1.0F) % 1.0F;
        }

        if (now >= rollUntil && total > 0.7F && RANDOM.nextDouble() < 0.04D) {
            rollUntil = now + 150L + RANDOM.nextInt(450);
            roll = RANDOM.nextFloat();
            rollSpeed = 0.02F + 0.08F * RANDOM.nextFloat();
        } else if (now < rollUntil) {
            roll = (roll + rollSpeed) % 1.0F;
        }
    }

    private static void draw(GuiGraphics graphics, float stage, boolean staticOnly) {
        ShaderInstance shader = ThermalShaders.analog();
        Minecraft minecraft = Minecraft.getInstance();
        if (shader == null) {
            return;
        }
        int w = minecraft.getMainRenderTarget().width;
        int h = minecraft.getMainRenderTarget().height;
        if (w <= 0 || h <= 0) {
            return;
        }
        long now = System.currentTimeMillis();

        graphics.flush();

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

        float x0 = 0.0F;
        float y0 = 0.0F;
        float x1 = 1.0F;
        float y1 = 1.0F;
        float aspect = (float) w / h;
        float feed = 4.0F / 3.0F;
        if (aspect > feed) {
            float share = feed / aspect;
            x0 = (1.0F - share) / 2.0F;
            x1 = 1.0F - x0;
        } else {
            float share = aspect / feed;
            y0 = (1.0F - share) / 2.0F;
            y1 = 1.0F - y0;
        }

        setFloat(shader, "Time", (now % 100000L) / 1000.0F);
        setFloat(shader, "Interference", level);
        setFloat(shader, "Burst", now < burstUntil ? burstStrength : 0.0F);
        setFloat(shader, "Stripes", Math.max(now < stripesUntil ? stripesStrength : 0.0F, 0.08F * level));
        setFloat(shader, "StaticOnly", staticOnly ? 1.0F : 0.0F);
        setFloat(shader, "Stage", stage);
        setFloat(shader, "SyncShift", now < syncUntil ? Math.max(syncShift, 1.0E-3F) : 0.0F);
        setFloat(shader, "SyncRoll", now < rollUntil ? Math.max(roll, 1.0E-3F) : 0.0F);
        com.mojang.blaze3d.shaders.Uniform rect = shader.getUniform("FeedRect");
        if (rect != null) {
            rect.set(x0, y0, x1, y1);
        }

        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, texture);
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
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private static void setFloat(ShaderInstance shader, String name, float value) {
        com.mojang.blaze3d.shaders.Uniform uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(value);
        }
    }

    /** Dropped on a resource reload, with everything else that belongs to the old context. */
    public static void unload() {
        if (texture != -1) {
            TextureUtil.releaseTextureId(texture);
            texture = -1;
        }
    }
}
