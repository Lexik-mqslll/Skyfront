package dev.krutlexik.skyfront.drone.client.render;

import dev.krutlexik.skyfront.drone.client.ThermalVision;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/** The thermal camera under an Iris shader pack. */
public final class ThermalPackPass {
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f MODEL_VIEW = new Matrix4f();
    private static Vec3 cameraPosition = Vec3.ZERO;
    private static boolean captured;

    /** During the world pass, under a pack: remembers how the world was looked at this frame. */
    public static void capture(RenderLevelStageEvent event) {
        if (IrisCompat.renderingShadowPass()) {
            return;
        }
        PROJECTION.set(event.getProjectionMatrix());
        MODEL_VIEW.set(event.getModelViewMatrix());
        cameraPosition = event.getCamera().getPosition();
        captured = true;
    }

    /** Just before the HUD, under a pack: the bodies, then the camera over the finished picture. */
    public static void render(float partialTick) {
        if (!captured || !ThermalVision.isOn()) {
            return;
        }
        captured = false;
        Minecraft minecraft = Minecraft.getInstance();
        com.mojang.blaze3d.pipeline.RenderTarget main = minecraft.getMainRenderTarget();

        if (mask == null) {
            mask = new com.mojang.blaze3d.pipeline.TextureTarget(main.width, main.height, false,
                    Minecraft.ON_OSX);
        } else if (mask.width != main.width || mask.height != main.height) {
            mask.resize(main.width, main.height, Minecraft.ON_OSX);
        }
        mask.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        mask.clear(Minecraft.ON_OSX);
        mask.bindWrite(true);

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.colorMask(true, true, true, true);
        org.lwjgl.opengl.GL11.glColorMask(true, true, true, true);
        RenderSystem.disableDepthTest();
        RenderSystem.disableScissor();
        RenderSystem.disableBlend();

        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(PROJECTION, VertexSorting.DISTANCE_TO_ORIGIN);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(MODEL_VIEW);
        RenderSystem.applyModelViewMatrix();
        try {
            ThermalHeatRenderer.renderSignatures(new PoseStack(), cameraPosition, partialTick);
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }

        boolean probing = ThermalHeatRenderer.reportDue();
        int px = 0;
        int py = 0;
        String maskValue = "-";
        if (probing && ThermalHeatRenderer.bodiesDrawn > 0 && ThermalHeatRenderer.bodyInFront) {
            px = Math.round((ThermalHeatRenderer.bodyNdcX * 0.5F + 0.5F) * (mask.width - 1));
            py = Math.round((ThermalHeatRenderer.bodyNdcY * 0.5F + 0.5F) * (mask.height - 1));
            maskValue = readPixel(px, py);
        }

        main.bindWrite(true);
        String before = probing && !"-".equals(maskValue) ? readPixel(px, py) : "-";

        ThermalScreenPass.render(partialTick, mask.getColorTextureId());

        if (probing) {
            String after = !"-".equals(maskValue) ? readPixel(px, py) : "-";
            ThermalHeatRenderer.report(String.format(java.util.Locale.ROOT,
                    "; probe at pixel (%d, %d): mask %s, frame %s, thermal %s, mask texture %d",
                    px, py, maskValue, before, after, mask.getColorTextureId()));
        }
    }

    /** One pixel of whatever framebuffer is bound, as r/g/b in 0..255. */
    private static String readPixel(int x, int y) {
        java.nio.ByteBuffer pixel = org.lwjgl.BufferUtils.createByteBuffer(4);
        org.lwjgl.opengl.GL11.glReadPixels(x, y, 1, 1, org.lwjgl.opengl.GL11.GL_RGBA,
                org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, pixel);
        return String.format(java.util.Locale.ROOT, "%d/%d/%d",
                pixel.get(0) & 0xFF, pixel.get(1) & 0xFF, pixel.get(2) & 0xFF);
    }

    /** The bodies, as their own temperatures, for the screen pass to read. */
    private static com.mojang.blaze3d.pipeline.TextureTarget mask;

    /** Dropped on a resource reload, with everything else that belongs to the old context. */
    public static void unload() {
        ThermalRenderTypes.clearMaskTypes();
        if (mask != null) {
            mask.destroyBuffers();
            mask = null;
        }
    }

    private ThermalPackPass() {
    }
}
