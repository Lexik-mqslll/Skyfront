package dev.krutlexik.skyfront.drone.client.render;

import dev.krutlexik.skyfront.drone.DroneMod;
import dev.krutlexik.skyfront.drone.entity.DroneEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Draws every drone variant. */
public class DroneRenderer<T extends DroneEntity> extends EntityRenderer<T> {
    /** The recon and thermal drones' airframe, authored in Blockbench units (~45 wide). */
    public static final ResourceLocation RECON_MODEL = DroneMod.id("models/entity/drone.glb");
    /** Shrinks {@link #RECON_MODEL} to roughly one block across. */
    public static final float RECON_SCALE = 0.025F;

    /** The interceptor's airframe, baked from the Blockbench project — already about a block wide. */
    public static final ResourceLocation FPV_MODEL = DroneMod.id("models/entity/p1sun.glb");
    /** The baked model is ~1.08 blocks across, so it needs only a hair of trimming, not scaling up. */
    public static final float FPV_SCALE = 1.0F;
    /** The airframe pitch, degrees (nose up positive, as on the OSD), at which the P1-SUN's body lies flat. */
    public static final float FPV_LEVEL_AT_PITCH = -40.7F;

    /** The Molniya's airframe, converted from its Blockbench project by tools/molniya.py. */
    public static final ResourceLocation MOLNIYA_MODEL = DroneMod.id("models/entity/molniya.glb");
    /** The FPV quad, built from fpv.bbmodel by tools/fpv.py: about 0.66 blocks across, drawn a fifth up. */
    public static final ResourceLocation FPV_QUAD_MODEL = DroneMod.id("models/entity/fpv.glb");
    public static final float FPV_QUAD_SCALE = 1.2F;
    public static final float MOLNIYA_SCALE = 1.4F;
    /** The Molniya's pitch, degrees on the OSD, when it flies level flat out. */
    public static final float MOLNIYA_LEVEL_AT_PITCH = -21.1F;

    /** Every model a drone is drawn from, so a resource reload can warm them all up at once. */
    private static final List<ResourceLocation> MODELS = List.of(RECON_MODEL, FPV_MODEL, MOLNIYA_MODEL, FPV_QUAD_MODEL);

    /** Whether a model's propellers should be spun for want of a baked animation in the file itself. */
    private static final Map<ResourceLocation, Boolean> SPIN_PROPELLERS =
            Map.of(RECON_MODEL, Boolean.TRUE, FPV_MODEL, Boolean.TRUE, MOLNIYA_MODEL, Boolean.TRUE,
                    FPV_QUAD_MODEL, Boolean.TRUE);

    private static final Map<ResourceLocation, GlbModel> LOADED = new HashMap<>();
    private static final Map<ResourceLocation, Boolean> FAILED = new HashMap<>();

    private final ResourceLocation model;
    private final float scale;
    /** Whether to draw the model around the middle of its own bounding box rather than around its origin. */
    private final boolean centreOnBounds;
    /** How much further than the airframe the body is drawn pitched (see {@link ModelTrim}). */
    private final ModelTrim trim;

    /**
     * Extra pitch, degrees nose up, the body is drawn at on top of the airframe's own — about the middle of
     * the model.
     */
    @FunctionalInterface
    public interface ModelTrim {
        float degrees(DroneEntity drone, float partialTick);
    }

    /** The model drawn exactly as the airframe is held. */
    public static final ModelTrim AS_HELD = (drone, partialTick) -> 0.0F;

    /**
     * A quad drawn leaning less than it really is: {@code share} of its pitch taken back off, so a racing
     * quad flown flat out does not look stood on its nose.
     */
    public static ModelTrim gentlerLean(float share) {
        return (drone, partialTick) -> {
            float pitch = drone.bodyPitch(partialTick);
            float t = Mth.clamp((Math.abs(pitch) - 50.0F) / 35.0F, 0.0F, 1.0F);
            return -pitch * share * (1.0F - t * t * (3.0F - 2.0F * t));
        };
    }

    /**
     * A body built standing on its tail that flies body-forward: upright at zero pitch, lying flat at {@code
     * levelAtPitch} (see {@link #bodyTip}).
     */
    public static ModelTrim tailSitter(float levelAtPitch) {
        return (drone, partialTick) -> bodyTip(drone.bodyPitch(partialTick), levelAtPitch);
    }

    /** A fixed-wing that has to hold its nose down to fly level when it is fast. */
    public static ModelTrim fastCruise(float levelAtPitch) {
        return (drone, partialTick) -> {
            if (!(drone instanceof dev.krutlexik.skyfront.drone.entity.InterceptorDroneEntity plane)) {
                return 0.0F;
            }
            double speed = Math.sqrt(drone.distanceToSqr(drone.xo, drone.yo, drone.zo));
            double cruise = plane.trimSpeed();
            double top = plane.topSpeedPerTick();
            double span = top * top - cruise * cruise;
            if (span <= 1.0E-6D) {
                return 0.0F;
            }
            float t = (float) Mth.clamp((speed * speed - cruise * cruise) / span, 0.0D, 1.0D);
            float eased = t * t * (3.0F - 2.0F * t);
            return -levelAtPitch * eased;
        };
    }

    public DroneRenderer(EntityRendererProvider.Context context) {
        this(context, RECON_MODEL, RECON_SCALE, false);
    }

    public DroneRenderer(EntityRendererProvider.Context context, ResourceLocation model, float scale,
                         boolean centreOnBounds) {
        this(context, model, scale, centreOnBounds, AS_HELD);
    }

    public DroneRenderer(EntityRendererProvider.Context context, ResourceLocation model, float scale,
                         boolean centreOnBounds, ModelTrim trim) {
        super(context);
        this.model = model;
        this.scale = scale;
        this.centreOnBounds = centreOnBounds;
        this.trim = trim;
        this.shadowRadius = 0.5F;
        this.shadowStrength = 0.4F;
    }

    /** Parses the models and uploads their embedded textures. */
    public static void reload(ResourceManager resources) {
        LOADED.clear();
        FAILED.clear();
        for (ResourceLocation model : MODELS) {
            load(resources, model);
        }
    }

    @Nullable
    private static GlbModel load(ResourceManager resources, ResourceLocation location) {
        try {
            GlbModel loaded = GlbModel.load(resources, location,
                    SPIN_PROPELLERS.getOrDefault(location, Boolean.TRUE));
            LOADED.put(location, loaded);
            DroneMod.LOGGER.info(
                    "Loaded drone model {} ({} triangles, {} animated groups, resting {} model units above the entity origin)",
                    location, loaded.triangleCount(), loaded.animatedGroupCount(), -loaded.minY());
            return loaded;
        } catch (Exception exception) {
            FAILED.put(location, Boolean.TRUE);
            DroneMod.LOGGER.error("Could not load drone model {}", location, exception);
            return null;
        }
    }

    /** The parsed model, loading it on first ask — shared with the item renderer that draws the icons. */
    @Nullable
    public static GlbModel model(ResourceLocation location) {
        GlbModel loaded = LOADED.get(location);
        if (loaded == null && !FAILED.containsKey(location)) {
            loaded = load(Minecraft.getInstance().getResourceManager(), location);
        }
        return loaded;
    }

    @Nullable
    private GlbModel model() {
        return model(this.model);
    }

    @Override
    public void render(T drone, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light) {
        GlbModel glb = model();
        if (glb != null) {
            poseStack.pushPose();
            poseStack.translate(0.0F, -glb.minY() * this.scale, 0.0F);
            org.joml.Quaternionf attitude = drone.isWrecked() ? drone.wreckRotation(partialTick)
                    : drone.renderRotation(partialTick);
            if (attitude != null) {
                poseStack.mulPose(attitude);
            } else {
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
                float pitch = drone.isWrecked() ? -drone.getViewXRot(partialTick) : drone.bodyPitch(partialTick);
                float roll = drone.isWrecked() ? drone.getRoll(partialTick) : drone.bodyRoll(partialTick);
                if (pitch != 0.0F) {
                    poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
                }
                if (roll != 0.0F) {
                    poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
                }
            }
            poseStack.scale(this.scale, this.scale, this.scale);
            if (this.centreOnBounds) {
                poseStack.translate(-glb.centreX(), 0.0F, -glb.centreZ());
            }
            float tip = drone.isWrecked() ? 0.0F : this.trim.degrees(drone, partialTick);
            if (tip != 0.0F) {
                float midY = glb.minY() + glb.height() / 2.0F;
                poseStack.translate(glb.centreX(), midY, glb.centreZ());
                poseStack.mulPose(Axis.XP.rotationDegrees(tip));
                poseStack.translate(-glb.centreX(), -midY, -glb.centreZ());
            }
            glb.render(poseStack, buffers, light, OverlayTexture.NO_OVERLAY, drone.getPropPhase(partialTick));
            poseStack.popPose();
        }

        renderAttachment(drone, entityYaw, partialTick, poseStack, buffers, light);

        super.render(drone, entityYaw, partialTick, poseStack, buffers, light);
    }

    /**
     * How much further than the airframe a tail-sitter's body is drawn pitched, degrees, so that it stands
     * straight up at zero pitch and lies flat at {@code levelAtPitch}.
     */
    static float bodyTip(float pitch, float levelAtPitch) {
        float level = Math.abs(levelAtPitch);
        if (level < 1.0E-3F || level >= 90.0F) {
            return 0.0F;
        }
        float t = Mth.clamp(Math.abs(pitch) / level, 0.0F, 1.0F);
        float eased = t * t * (3.0F - 2.0F * t);
        return Math.signum(pitch) * (90.0F - level) * eased;
    }

    /** The slung grenade, hung just under the belly as a small item model that yaws with the airframe. */
    private void renderAttachment(DroneEntity drone, float entityYaw, float partialTick,
                                  PoseStack poseStack, MultiBufferSource buffers, int light) {
        if (!drone.hasAttachment()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        var attachment = drone.getAttachment();
        var mount = drone.mountFor(attachment);

        poseStack.pushPose();
        org.joml.Quaternionf attitude = !drone.storesFollowAttitude() ? null
                : drone.isWrecked() ? drone.wreckRotation(partialTick) : drone.renderRotation(partialTick);
        GlbModel glb = model();
        if (attitude != null && glb != null) {
            float lift = -glb.minY() * this.scale;
            poseStack.translate(0.0F, lift, 0.0F);
            poseStack.mulPose(attitude);
            float tip = drone.isWrecked() ? 0.0F : this.trim.degrees(drone, partialTick);
            if (tip != 0.0F) {
                float midY = (glb.minY() + glb.height() / 2.0F) * this.scale;
                poseStack.translate(0.0F, midY, 0.0F);
                poseStack.mulPose(Axis.XP.rotationDegrees(tip));
                poseStack.translate(0.0F, -midY, 0.0F);
            }
            poseStack.translate(0.0F, mount.y() - lift, 0.0F);
        } else {
            poseStack.translate(0.0F, mount.y(), 0.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
        }
        poseStack.translate(0.0F, 0.0F, mount.z());
        if (mount.yaw() != 0.0F) {
            poseStack.mulPose(Axis.YP.rotationDegrees(mount.yaw()));
        }
        if (mount.pitch() != 0.0F) {
            poseStack.mulPose(Axis.XP.rotationDegrees(mount.pitch()));
        }
        if (mount.roll() != 0.0F) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(mount.roll()));
        }
        poseStack.scale(mount.scale(), mount.scale(), mount.scale());

        BedrockStoreModel round = mount.display() != null ? BedrockStoreModel.forProjectile(mount.display()) : null;
        if (round != null) {
            round.render(poseStack, buffers, light);
        } else {
            minecraft.getItemRenderer().renderStatic(attachment,
                    net.minecraft.world.item.ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY,
                    poseStack, buffers, drone.level(), 0);
        }
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(T drone) {
        return this.model;
    }
}
