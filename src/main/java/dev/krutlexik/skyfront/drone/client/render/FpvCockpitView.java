package dev.krutlexik.skyfront.drone.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import dev.krutlexik.skyfront.drone.client.DroneControl;
import dev.krutlexik.skyfront.drone.entity.DroneEntity;
import dev.krutlexik.skyfront.drone.entity.FpvDroneEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;
import org.joml.Vector3f;

/**
 * What the FPV quad's own camera sees of the airframe it is bolted to: the front props turning at either side
 * of the picture, and the nose of the round it carries at the bottom of the picture.
 */
@OnlyIn(Dist.CLIENT)
public final class FpvCockpitView {
    /**
     * Each front prop is seen nearly edge-on, as a thin sliver of spinning blade at the side of the picture a
     * little above the middle: its disc tilted back by {@code PROP_BAND} so it lies along that line of sight,
     * and lifted {@code PROP_LIFT} above the eye so it shows as a sliver rather than a line.
     */
    private static final float PROP_X = 0.55F;
    private static final float PROP_Z = -0.35F;
    private static final float PROP_BAND = 3.5F;
    private static final float PROP_LIFT = 0.03F;

    /** Where the round's tip sits, and how far its nose dips below the line of sight. */
    private static final Vector3f ROUND_TIP = new Vector3f(0.0F, -0.31F, -0.72F);
    private static final float ROUND_DIP = 0.0F;

    private FpvCockpitView() {
    }

    /** The field of view the cockpit is drawn with, degrees vertical — fixed, as a held item's is. */
    private static final float COCKPIT_FOV = 70.0F;

    /**
     * Drawn over the finished world, before the HUD, with a projection of its own — the way the game draws a
     * held item.
     */
    public static void render(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        DroneEntity drone = DroneControl.drone();
        if (!(drone instanceof FpvDroneEntity) || minecraft.level == null || !DroneControl.onSticks()
                || DroneControl.isPictureHeld() || !minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }
        var window = minecraft.getWindow();
        if (window.getWidth() <= 0 || window.getHeight() <= 0) {
            return;
        }
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        int light = LevelRenderer.getLightColor(minecraft.level, BlockPos.containing(drone.getEyePosition(partialTick)));

        Matrix4f oldProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting oldSorting = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(new Matrix4f().setPerspective(
                (float) Math.toRadians(COCKPIT_FOV), (float) window.getWidth() / window.getHeight(), 0.05F, 64.0F),
                VertexSorting.DISTANCE_TO_ORIGIN);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

        PoseStack poseStack = new PoseStack();
        GlbModel glb = DroneRenderer.model(DroneRenderer.FPV_QUAD_MODEL);
        if (glb != null) {
            float spin = drone.getPropPhase(partialTick);
            for (int i = 0; i < glb.animatedGroupCount(); i++) {
                Vector3f shaft = glb.groupPivot(i);
                Vector3f pivot = new Vector3f(shaft.x, glb.groupCentre(i).y, shaft.z);
                if (pivot.z >= 0.0F) {
                    continue;
                }
                poseStack.pushPose();
                double band = Math.toRadians(PROP_BAND);
                float propY = (float) ((PROP_LIFT - Math.sin(band) * PROP_Z) / Math.cos(band));
                poseStack.translate(Math.signum(pivot.x) * PROP_X, propY, PROP_Z);
                poseStack.mulPose(Axis.XP.rotationDegrees(PROP_BAND));
                poseStack.scale(DroneRenderer.FPV_QUAD_SCALE, DroneRenderer.FPV_QUAD_SCALE, DroneRenderer.FPV_QUAD_SCALE);
                poseStack.translate(-pivot.x, -pivot.y, -pivot.z);
                glb.renderGroup(i, poseStack, buffers, light, OverlayTexture.NO_OVERLAY, spin);
                poseStack.popPose();
            }
        }

        if (drone.hasAttachment()) {
            var mount = drone.mountFor(drone.getAttachment());
            BedrockStoreModel round = mount.display() != null ? BedrockStoreModel.forProjectile(mount.display()) : null;
            if (round != null) {
                poseStack.pushPose();
                poseStack.translate(ROUND_TIP.x, ROUND_TIP.y, ROUND_TIP.z);
                poseStack.mulPose(Axis.XP.rotationDegrees(-ROUND_DIP));
                poseStack.translate(0.0F, 0.0F, round.length() * mount.scale());
                poseStack.scale(mount.scale(), mount.scale(), mount.scale());
                round.render(poseStack, buffers, light);
                poseStack.popPose();
            }
        }
        buffers.endBatch();

        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(oldProjection, oldSorting);
    }
}
