package dev.krutlexik.skyfront.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.krutlexik.skyfront.drone.DroneMod;
import dev.krutlexik.skyfront.drone.client.render.DroneRenderer;
import dev.krutlexik.skyfront.drone.client.render.GlbModel;
import dev.krutlexik.skyfront.entity.LauncherEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Draws the launch rail from its glb (built from launcher.bbmodel by tools/bb2glb.py), rails rising the way
 * it faces.
 */
@OnlyIn(Dist.CLIENT)
public class LauncherRenderer extends EntityRenderer<LauncherEntity> {
    public static final ResourceLocation MODEL = DroneMod.id("models/entity/launcher.glb");

    public LauncherRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.2F;
        this.shadowStrength = 0.5F;
    }

    @Override
    public void render(LauncherEntity launcher, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light) {
        GlbModel glb = DroneRenderer.model(MODEL);
        if (glb != null) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(-launcher.getYRot()));
            poseStack.scale(LauncherEntity.SCALE, LauncherEntity.SCALE, LauncherEntity.SCALE);
            poseStack.translate(-LauncherEntity.CENTRE_X / 16.0D, 0.0D, -LauncherEntity.CENTRE_Z / 16.0D);
            glb.render(poseStack, buffers, light, OverlayTexture.NO_OVERLAY, 0.0F);
            poseStack.popPose();
        }
        super.render(launcher, entityYaw, partialTick, poseStack, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(LauncherEntity launcher) {
        return MODEL;
    }
}
