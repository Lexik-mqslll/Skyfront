package dev.krutlexik.skyfront.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.krutlexik.skyfront.block.ShahedTerminalBlock;
import dev.krutlexik.skyfront.block.entity.ShahedTerminalBlockEntity;
import dev.krutlexik.skyfront.drone.DroneMod;
import dev.krutlexik.skyfront.drone.client.render.DroneRenderer;
import dev.krutlexik.skyfront.drone.client.render.GlbModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Draws the Shahed control post — an equipment case with a laptop open on top of it — from its own glb, built
 * from the Blockbench model by tools/bb2glb.py.
 */
@OnlyIn(Dist.CLIENT)
public class ShahedTerminalRenderer implements BlockEntityRenderer<ShahedTerminalBlockEntity> {
    public static final ResourceLocation MODEL = DroneMod.id("models/block/shahed_terminal.glb");

    public ShahedTerminalRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ShahedTerminalBlockEntity terminal, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light, int overlay) {
        GlbModel glb = DroneRenderer.model(MODEL);
        if (glb == null) {
            return;
        }
        Direction facing = terminal.getBlockState().getValue(ShahedTerminalBlock.FACING);
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(frontToward(facing)));
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        glb.render(poseStack, buffers, light, overlay, 0.0F);
        poseStack.popPose();
    }

    /** The turn about the vertical that brings the model's east-facing front round to {@code facing}. */
    static float frontToward(Direction facing) {
        return -facing.toYRot() - 90.0F;
    }

    // The render bounding box (the laptop stands half a block above the case, so the post is drawn while any
    // of it is in view) is on Forge 1.20.1 the block entity's own: ShahedTerminalBlockEntity#getRenderBoundingBox.
}
