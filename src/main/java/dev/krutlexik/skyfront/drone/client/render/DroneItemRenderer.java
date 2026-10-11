package dev.krutlexik.skyfront.drone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws a drone item as the drone itself: the same glb the entity renderer flies is posed in the slot, so the
 * icon is always exactly the aircraft — no hand-drawn sprite to fall out of date.
 */
public class DroneItemRenderer extends BlockEntityWithoutLevelRenderer {
    /**
     * How large the airframe's outline is drawn in a slot, in GUI pixels: the slot's own sixteen, so it sits
     * big and bold beside the Shahed rather than lost in the middle, just short of crowding it.
     */
    private static final float GUI_SIZE = 16.0F;

    private final ResourceLocation model;
    /** Silhouettes already measured, by the slot's rotation. */
    private final Map<Matrix3f, float[]> silhouettes = new HashMap<>();
    /** The mesh {@link #silhouettes} were measured on. */
    private GlbModel measured;

    public DroneItemRenderer(ResourceLocation model) {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
        this.model = model;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource buffers, int light, int overlay) {
        GlbModel glb = DroneRenderer.model(this.model);
        if (glb == null) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);

        if (context == ItemDisplayContext.GUI) {
            fitToSlot(glb, poseStack);
        } else {
            float span = Math.max(glb.height(), Math.max(glb.sizeX(), glb.sizeZ()));
            float scale = span > 1.0E-4F ? 0.95F / span : 1.0F;
            poseStack.scale(scale, scale, scale);
            poseStack.translate(-glb.centreX(), -(glb.minY() + glb.height() / 2.0F), -glb.centreZ());
        }

        glb.render(poseStack, buffers, light, overlay, 0.0F);
        poseStack.popPose();
    }

    /** Frames the airframe in a slot by what is actually seen of it. */
    private void fitToSlot(GlbModel glb, PoseStack poseStack) {
        Matrix3f linear = new Matrix3f(poseStack.last().pose());
        float unit = linear.getColumn(0, new Vector3f()).length();
        if (unit < 1.0E-6F) {
            return;
        }
        Matrix3f rotation = new Matrix3f(linear).scale(1.0F / unit);
        if (glb != this.measured) {
            this.silhouettes.clear();
            this.measured = glb;
        }
        if (this.silhouettes.size() > 16) {
            this.silhouettes.clear();
        }
        float[] outline = this.silhouettes.computeIfAbsent(new Matrix3f(rotation), glb::silhouette);

        float span = Math.max(outline[1] - outline[0], outline[3] - outline[2]);
        if (span < 1.0E-4F) {
            return;
        }
        float scale = GUI_SIZE / (unit * span);
        Vector3f middle = new Vector3f((outline[0] + outline[1]) / 2.0F, (outline[2] + outline[3]) / 2.0F, outline[4]);
        rotation.transpose().transform(middle);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-middle.x, -middle.y, -middle.z);
    }
}
