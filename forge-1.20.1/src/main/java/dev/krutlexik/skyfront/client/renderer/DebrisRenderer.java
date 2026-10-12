package dev.krutlexik.skyfront.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.krutlexik.skyfront.drone.DroneMod;
import dev.krutlexik.skyfront.drone.client.render.DroneRenderer;
import dev.krutlexik.skyfront.drone.client.render.GlbModel;
import dev.krutlexik.skyfront.entity.DebrisEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Quaternionf;

import java.util.EnumMap;
import java.util.Map;

/**
 * Draws a burnt piece of a blown-up drone from its own glb (cut from the airframe's model by
 * tools/debris.py), at the airframe's scale, turned about its middle by the tumble the entity keeps.
 */
@OnlyIn(Dist.CLIENT)
public class DebrisRenderer extends EntityRenderer<DebrisEntity> {
    private static final Map<DebrisEntity.Piece, ResourceLocation> MODELS = new EnumMap<>(DebrisEntity.Piece.class);

    static {
        for (DebrisEntity.Piece piece : DebrisEntity.Piece.values()) {
            MODELS.put(piece, DroneMod.id("models/entity/" + piece.model + ".glb"));
        }
    }

    private final Quaternionf attitude = new Quaternionf();

    public DebrisRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.35F;
        this.shadowStrength = 0.6F;
    }

    @Override
    public void render(DebrisEntity debris, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light) {
        DebrisEntity.Piece piece = debris.piece();
        GlbModel glb = DroneRenderer.model(MODELS.get(piece));
        if (glb != null) {
            float scale = piece.airframe.scale;
            poseStack.pushPose();
            poseStack.translate(0.0F, piece.pivotHeight(), 0.0F);
            poseStack.mulPose(debris.renderAttitude(partialTick, this.attitude));
            poseStack.scale(scale, scale, scale);
            poseStack.translate(0.0F, -piece.size.y / 32.0F, 0.0F);
            glb.render(poseStack, buffers, light, OverlayTexture.NO_OVERLAY, 0.0F);
            poseStack.popPose();
        }
        super.render(debris, entityYaw, partialTick, poseStack, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(DebrisEntity debris) {
        return MODELS.get(debris.piece());
    }
}
