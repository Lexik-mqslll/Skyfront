package dev.krutlexik.skyfront.client.renderer;

import dev.krutlexik.skyfront.client.model.ShahedModel;
import dev.krutlexik.skyfront.entity.ShahedEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@OnlyIn(Dist.CLIENT)
public class ShahedRenderer extends GeoEntityRenderer<ShahedEntity> {
    public ShahedRenderer(EntityRendererProvider.Context context) {
        super(context, new ShahedModel());
        this.shadowRadius = 1.5f;
    }

    @Override
    public void preRender(PoseStack poseStack, ShahedEntity animatable, software.bernie.geckolib.cache.object.BakedGeoModel model, net.minecraft.client.renderer.MultiBufferSource bufferSource, com.mojang.blaze3d.vertex.VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
        
        Vec3 motion = animatable.getDeltaMovement();
        if (!animatable.isParked() && motion.lengthSqr() > 1.0E-4) {
            double motionX = motion.x;
            double motionY = motion.y;
            double motionZ = motion.z;
            double horizontalLength = Math.sqrt(motionX * motionX + motionZ * motionZ);
            double yawRad = Math.atan2(motionX, motionZ);
            float yawDeg = (float) Math.toDegrees(yawRad) + 180.0f;
            double pitchRad = Math.atan2(-motionY, horizontalLength);
            float pitchDeg = (float) Math.toDegrees(pitchRad);
            poseStack.mulPose(Axis.YP.rotationDegrees(yawDeg));
            poseStack.mulPose(Axis.XP.rotationDegrees(-pitchDeg));
        } else {
            poseStack.mulPose(Axis.YP.rotationDegrees(animatable.getYRot() + 180.0f));
            poseStack.mulPose(Axis.XP.rotationDegrees(-animatable.getXRot()));
        }

        if (animatable.getIsFalling()) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(animatable.getRenderRoll(partialTick)));
        }
    }
}
