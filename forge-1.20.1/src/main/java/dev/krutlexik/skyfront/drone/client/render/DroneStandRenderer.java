package dev.krutlexik.skyfront.drone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.krutlexik.skyfront.drone.DroneMod;
import dev.krutlexik.skyfront.drone.entity.DroneStandEntity;
import dev.krutlexik.skyfront.drone.entity.FpvDroneEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * The FPV quad's bricks, from their own glb (built by tools/bricks.py): drawn at the quad's scale and turned
 * the way the quad was put down, stretched upright just enough that their top meets the quad standing on them
 * — a touch taller than in the Blockbench model, where the round under the quad would have dug into the
 * ground.
 */
public class DroneStandRenderer extends EntityRenderer<DroneStandEntity> {
    public static final ResourceLocation MODEL = DroneMod.id("models/entity/fpv_stand.glb");
    /** The bricks' top in the model, blocks at scale 1, and how far the quad's feet sink into it there. */
    private static final float MODEL_HEIGHT = 2.6F / 16.0F;
    private static final float SINK = 0.1F / 16.0F * DroneRenderer.FPV_QUAD_SCALE;

    public DroneStandRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(DroneStandEntity stand, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light) {
        GlbModel glb = DroneRenderer.model(MODEL);
        if (glb != null) {
            float scale = DroneRenderer.FPV_QUAD_SCALE;
            float upright = (float) (FpvDroneEntity.STAND_HEIGHT + SINK) / MODEL_HEIGHT;
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - stand.getYRot()));
            poseStack.scale(scale, upright, scale);
            glb.render(poseStack, buffers, light, OverlayTexture.NO_OVERLAY, 0.0F);
            poseStack.popPose();
        }
        super.render(stand, entityYaw, partialTick, poseStack, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(DroneStandEntity stand) {
        return MODEL;
    }
}
