package dev.krutlexik.skyfront.drone.client;

import dev.krutlexik.skyfront.drone.DroneMod;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

/**
 * The stalled picture behind a dropped link: the last frame the pilot actually saw, held on screen and then
 * drained of colour, with the last telemetry to arrive frozen alongside it.
 */
@OnlyIn(Dist.CLIENT)
public final class DroneFeedFreeze {
    private static final ResourceLocation FROZEN_FRAME = DroneMod.id("frozen_feed");
    private static final ResourceLocation GREY_FRAME = DroneMod.id("frozen_feed_grey");

    /** Set when the link drops and consumed by the next {@link #captureIfRequested} call. */
    private static boolean captureRequested;
    private static boolean frameReady;
    /** Whether the freeze was armed for an FPV feed rather than a Mavic's. */
    private static boolean heldForFpv;
    private static int frameWidth;
    private static int frameHeight;

    @Nullable
    private static DroneTelemetry frozenTelemetry;

    /** Arms the freeze: the next frame rendered is the one the feed stops on. */
    public static void arm(@Nullable DroneTelemetry lastReading) {
        frozenTelemetry = lastReading;
        captureRequested = true;
        frameReady = false;
        heldForFpv = false;
        fromFrameBefore = false;
    }

    /** The same, for an FPV feed: the picture is held as it was, in colour, and never drained. */
    public static void armHold() {
        frozenTelemetry = null;
        captureRequested = true;
        frameReady = false;
        heldForFpv = true;
        fromFrameBefore = false;
    }

    @Nullable
    private static RenderTarget lastFrame;
    private static boolean lastFrameTaken;
    private static boolean fromFrameBefore;
    /** Frames rendered, so the copy is taken once a frame however many overlays ask. */
    private static long frameCount;
    private static long copiedFrame = -1L;

    /** Called at the start of every rendered frame. */
    public static void newFrame() {
        frameCount++;
    }

    /**
     * Freeze on the frame drawn before the one about to be: for a death the server reported, whose own
     * effects are already in the world.
     */
    public static void useFrameBefore() {
        fromFrameBefore = true;
    }

    /** Copies this frame, as the freeze would grab it, for the death that might arrive before the next. */
    private static void keepFrame() {
        if (!DroneControl.isActive() || DroneControl.isPictureHeld() || DroneSignalLoss.isActive()
                || copiedFrame == frameCount) {
            return;
        }
        copiedFrame = frameCount;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (lastFrame == null || lastFrame.width != main.width || lastFrame.height != main.height) {
            if (lastFrame != null) {
                lastFrame.destroyBuffers();
            }
            lastFrame = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            lastFrameTaken = false;
        }
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, lastFrame.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, lastFrame.width, lastFrame.height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        main.bindWrite(false);
        lastFrameTaken = true;
    }

    /** The last telemetry from before the link dropped, or null if the feed is live. */
    @Nullable
    public static DroneTelemetry frozenTelemetry() {
        return frozenTelemetry;
    }

    /** Whether the last frame has been grabbed and is standing in for the feed. */
    public static boolean isFrameReady() {
        return frameReady;
    }

    public static void release() {
        captureRequested = false;
        frozenTelemetry = null;
        heldForFpv = false;
        lastFrameTaken = false;
        fromFrameBefore = false;
        if (frameReady) {
            frameReady = false;
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.getTextureManager().release(FROZEN_FRAME);
            minecraft.getTextureManager().release(GREY_FRAME);
        }
    }

    /** Copies the frame currently in the framebuffer, if one was asked for. */
    public static void captureIfRequested() {
        if (!captureRequested) {
            keepFrame();
            return;
        }
        captureRequested = false;
        if (!heldForFpv && DroneSignalLoss.elapsed() > DroneSignalLoss.COLOUR_TICKS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        try {
            boolean before = fromFrameBefore && lastFrame != null && lastFrameTaken;
            fromFrameBefore = false;
            NativeImage colour = Screenshot.takeScreenshot(before ? lastFrame : minecraft.getMainRenderTarget());
            NativeImage grey = desaturate(colour);
            frameWidth = colour.getWidth();
            frameHeight = colour.getHeight();
            minecraft.getTextureManager().register(FROZEN_FRAME, new DynamicTexture(colour));
            minecraft.getTextureManager().register(GREY_FRAME, new DynamicTexture(grey));
            frameReady = true;
        } catch (Exception exception) {
            DroneMod.LOGGER.error("Could not capture the drone feed's last frame", exception);
        }
    }

    /** Draws the frozen frame over the whole screen. */
    public static void render(GuiGraphics graphics) {
        if (!DroneSignalLoss.isActive() || !frameReady) {
            return;
        }
        ResourceLocation frame = DroneSignalLoss.isBlanked() ? GREY_FRAME : FROZEN_FRAME;
        blitFrame(graphics, frame);
    }

    /** The held FPV picture: the last field to arrive, in colour, exactly as it was. */
    public static void renderHold(GuiGraphics graphics) {
        if (!heldForFpv || !frameReady) {
            return;
        }
        blitFrame(graphics, FROZEN_FRAME);
    }

    private static void blitFrame(GuiGraphics graphics, ResourceLocation frame) {
        graphics.blit(frame, 0, 0, graphics.guiWidth(), graphics.guiHeight(),
                0.0F, 0.0F, frameWidth, frameHeight, frameWidth, frameHeight);
    }

    /**
     * Rec. 601 luma, the same weighting video hardware uses, so the drained frame reads as a washed-out
     * version of the picture rather than a flat average that muddies bright colours.
     */
    private static NativeImage desaturate(NativeImage source) {
        NativeImage grey = new NativeImage(source.getWidth(), source.getHeight(), false);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int abgr = source.getPixelRGBA(x, y);
                int luma = (int) (0.299D * FastColor.ABGR32.red(abgr)
                        + 0.587D * FastColor.ABGR32.green(abgr)
                        + 0.114D * FastColor.ABGR32.blue(abgr));
                grey.setPixelRGBA(x, y, FastColor.ABGR32.color(FastColor.ABGR32.alpha(abgr), luma, luma, luma));
            }
        }
        return grey;
    }

    private DroneFeedFreeze() {
    }
}
