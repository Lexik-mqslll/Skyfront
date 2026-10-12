package dev.krutlexik.skyfront.drone.client.render;

import dev.krutlexik.skyfront.drone.client.DroneControl;
import dev.krutlexik.skyfront.drone.client.DroneSignalLoss;
import dev.krutlexik.skyfront.drone.client.ThermalVision;
import dev.krutlexik.skyfront.drone.entity.DroneEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the thermal camera's picture of what is warm: every living thing, every burning thing and every
 * manned machine, painted flat over the finished world with the depth test off ({@link
 * ThermalRenderTypes#HEAT}), so a body behind a wall reads exactly as it does in open air.
 */
public final class ThermalHeatRenderer {
    /**
     * How far out the camera resolves heat: as far as the feed draws entities at all (see {@code
     * EntityRenderRangeMixin}), so nothing that is in the picture is left out of the heat.
     */
    private static final double REACH = 320.0D;

    /** Players and anything actually burning: the hottest thing in the frame. */
    private static final int HOT = 0xFFFFFFFF;
    /** Everything else with a pulse, and manned machines: plainly warm, short of white-hot. */
    private static final int WARM = 0xFFC8C8C8;

    public static void render(PoseStack poseStack, Camera camera, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        DroneEntity drone = DroneControl.drone();
        if (!ThermalVision.isOn() || minecraft.level == null
                || !ThermalVision.isAvailable(drone) || DroneSignalLoss.isActive()) {
            return;
        }

        Vec3 view = camera.getPosition();

        if (ThermalShaders.loaded()) {
            return;
        }
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        HeatBuffers heat = new HeatBuffers(buffers.getBuffer(ThermalRenderTypes.HEAT));

        dispatcher.setRenderShadow(false);
        try {
            for (Entity entity : minecraft.level.entitiesForRendering()) {
                if (entity == drone || entity == minecraft.getCameraEntity() || entity.isRemoved()) {
                    continue;
                }
                int warmth = warmthOf(entity);
                if (warmth == 0 || entity.distanceToSqr(view) > REACH * REACH) {
                    continue;
                }
                heat.color = warmth;
                heat.machine = !(entity instanceof LivingEntity);
                double x = Mth.lerp(partialTick, entity.xOld, entity.getX()) - view.x;
                double y = Mth.lerp(partialTick, entity.yOld, entity.getY()) - view.y;
                double z = Mth.lerp(partialTick, entity.zOld, entity.getZ()) - view.z;
                float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
                dispatcher.render(entity, x, y, z, yaw, partialTick, poseStack, heat,
                        LightTexture.FULL_BRIGHT);
            }
        } finally {
            dispatcher.setRenderShadow(true);
        }
        // On Forge 1.20.1 the level hands this stage (AFTER_WEATHER) the view rotation twice over: in the pose
        // stack the bodies were just placed with, and on the model-view stack it pushed for its clouds and
        // weather. Drawn under both, every body would be turned by the camera twice, so the model-view is held
        // at identity for the draw.
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();
        try {
            buffers.endBatch(ThermalRenderTypes.HEAT);
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    /** How warm each kind of body reads under a shader pack, as the screen pass's temperature. */
    private static final float SIGNATURE_HOT = 0.95F;
    private static final float SIGNATURE_WARM = 0.84F;

    /** The bodies, drawn for the screen pass to find: see {@link ThermalPackPass}. */
    public static void renderSignatures(PoseStack poseStack, Vec3 view, float partialTick) {
        bodiesDrawn = 0;
        bodyInFront = false;
        Minecraft minecraft = Minecraft.getInstance();
        DroneEntity drone = DroneControl.drone();
        if (minecraft.level == null || !ThermalVision.isAvailable(drone) || DroneSignalLoss.isActive()) {
            return;
        }
        refreshSight(minecraft, view);

        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        MultiBufferSource.BufferSource buffers = packBuffers();
        HeatBuffers heat = ThermalShaders.mask() != null
                ? new HeatBuffers(buffers)
                : new HeatBuffers(buffers.getBuffer(ThermalRenderTypes.HEAT_MASK));
        int drawn = 0;
        dispatcher.setRenderShadow(false);
        try {
            for (Entity entity : minecraft.level.entitiesForRendering()) {
                if (entity == drone || entity == minecraft.getCameraEntity() || entity.isRemoved()
                        || !IN_SIGHT.contains(entity.getId())) {
                    continue;
                }
                int warmth = warmthOf(entity);
                if (warmth == 0) {
                    continue;
                }
                double x = Mth.lerp(partialTick, entity.xOld, entity.getX()) - view.x;
                double y = Mth.lerp(partialTick, entity.yOld, entity.getY()) - view.y;
                double z = Mth.lerp(partialTick, entity.zOld, entity.getZ()) - view.z;
                heat.heat.grade((float) y, Math.max(0.2F, entity.getBbHeight()),
                        warmth == HOT ? SIGNATURE_HOT : SIGNATURE_WARM);
                heat.machine = !(entity instanceof LivingEntity);
                float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
                dispatcher.render(entity, x, y, z, yaw, partialTick, poseStack, heat,
                        LightTexture.FULL_BRIGHT);
                if (drawn == 0) {
                    org.joml.Vector4f clip = new org.joml.Vector4f((float) x,
                            (float) y + entity.getBbHeight() * 0.5F, (float) z, 1.0F);
                    RenderSystem.getModelViewMatrix().transform(clip);
                    RenderSystem.getProjectionMatrix().transform(clip);
                    firstOnScreen = clip.w > 0.0F
                            ? String.format(java.util.Locale.ROOT, "(%.2f, %.2f)", clip.x / clip.w, clip.y / clip.w)
                            : "behind the camera";
                    bodyInFront = clip.w > 0.0F;
                    bodyNdcX = bodyInFront ? clip.x / clip.w : 0.0F;
                    bodyNdcY = bodyInFront ? clip.y / clip.w : 0.0F;
                }
                drawn++;
            }
        } finally {
            dispatcher.setRenderShadow(true);
            heat.heat.ungrade();
        }
        buffers.endBatch();
        bodiesDrawn = drawn;
    }

    private static MultiBufferSource.BufferSource packBuffer;

    private static MultiBufferSource.BufferSource packBuffers() {
        if (packBuffer == null) {
            packBuffer = MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.BufferBuilder(1 << 18));
        }
        return packBuffer;
    }

    private static int warmNear;
    private static long lastReport;

    static void report(String probe) {
        lastReport = System.currentTimeMillis();
        dev.krutlexik.skyfront.drone.DroneMod.LOGGER.info(
                "Thermal under a shader pack: {} warm bodies in reach, {} in sight, {} drawn, first at {}{}",
                warmNear, IN_SIGHT.size(), bodiesDrawn, bodiesDrawn > 0 ? firstOnScreen : "-", probe);
    }

    private static String firstOnScreen = "-";

    /** Where the first body drawn this frame landed, for ThermalPackPass's pixel probe. */
    static boolean bodyInFront;
    static float bodyNdcX;
    static float bodyNdcY;
    static int bodiesDrawn;

    /** Whether a report is due, so the pack pass can add what it read back from the GPU to it. */
    static boolean reportDue() {
        return System.currentTimeMillis() - lastReport >= 5000L;
    }

    /** The bodies the drone can see, worked out once a tick rather than every frame. */
    private static final java.util.Set<Integer> IN_SIGHT = new java.util.HashSet<>();
    private static long sightTick = Long.MIN_VALUE;

    private static void refreshSight(Minecraft minecraft, Vec3 view) {
        long tick = minecraft.level.getGameTime();
        if (tick == sightTick) {
            return;
        }
        sightTick = tick;
        IN_SIGHT.clear();
        warmNear = 0;
        Entity viewer = minecraft.getCameraEntity();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity == viewer || warmthOf(entity) == 0 || entity.distanceToSqr(view) > REACH * REACH) {
                continue;
            }
            warmNear++;
            if (inSight(minecraft, view, entity, viewer)) {
                IN_SIGHT.add(entity.getId());
            }
        }
    }

    /**
     * Whether anything of this body can be seen from the camera: feet, middle or head, any one of them in the
     * clear.
     */
    private static boolean inSight(Minecraft minecraft, Vec3 eye, Entity entity, @javax.annotation.Nullable Entity viewer) {
        net.minecraft.world.phys.AABB box = entity.getBoundingBox();
        Vec3 centre = box.getCenter();
        for (double share : new double[] {0.15D, 0.5D, 0.9D}) {
            Vec3 point = new Vec3(centre.x, box.minY + box.getYsize() * share, centre.z);
            net.minecraft.world.phys.BlockHitResult hit = minecraft.level.clip(new net.minecraft.world.level.ClipContext(
                    eye, point, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,
                    viewer != null ? viewer : entity));
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
                return true;
            }
        }
        return false;
    }

    /** How hot this entity reads, or {@code 0} for something the camera has no reason to pick out. */
    private static int warmthOf(Entity entity) {
        if (entity.isOnFire() || entity instanceof Player) {
            return HOT;
        }
        if (entity instanceof LivingEntity) {
            return WARM;
        }
        if (entity instanceof net.minecraft.world.entity.item.ItemEntity
                || entity instanceof net.minecraft.world.entity.ExperienceOrb
                || entity instanceof net.minecraft.world.entity.decoration.HangingEntity
                || entity instanceof net.minecraft.world.entity.item.FallingBlockEntity
                || entity instanceof net.minecraft.world.entity.Display
                || entity instanceof net.minecraft.world.entity.Marker
                || entity instanceof net.minecraft.world.entity.AreaEffectCloud
                || entity instanceof net.minecraft.world.entity.projectile.AbstractArrow) {
            return entity.getPassengers().isEmpty() ? 0 : WARM;
        }
        return WARM;
    }

    /**
     * Hands every render type the entity models ask for to the one heat buffer, so a body comes out as a
     * single flat silhouette whatever it is normally drawn with.
     */
    private static final class HeatBuffers implements MultiBufferSource {
        private final HeatVertices heat;
        private final VertexConsumer discard = new DiscardVertices();
        private int color;
        private boolean machine;
        @javax.annotation.Nullable
        private final MultiBufferSource.BufferSource textured;

        private HeatBuffers(VertexConsumer target) {
            this.heat = new HeatVertices(target);
            this.textured = null;
        }

        private HeatBuffers(MultiBufferSource.BufferSource textured) {
            this.heat = new HeatVertices(null);
            this.textured = textured;
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            if (type.mode() != VertexFormat.Mode.QUADS || isText(type)
                    || this.machine && !isHeatedEntityType(type)) {
                return this.discard;
            }
            if (this.textured != null) {
                if (type.format() != com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY) {
                    return this.discard;
                }
                this.heat.target = this.textured.getBuffer(ThermalRenderTypes.maskTypeOf(type));
                this.heat.passThrough = true;
            }
            this.heat.color = this.color;
            return this.heat;
        }

        /** Whether the type is one of the game's text types ("text", "text_see_through", ...). */
        private static boolean isText(RenderType type) {
            return nameOf(type).startsWith("text");
        }

        /** The types drawn with the entity shaders the camera heats without a pack. */
        private static boolean isHeatedEntityType(RenderType type) {
            String name = nameOf(type);
            return name.equals("entity_cutout_no_cull") || name.equals("entity_cutout_no_cull_z_offset")
                    || name.equals("entity_translucent");
        }

        /** "entity_translucent" out of "RenderType[entity_translucent:CompositeState[...]]". */
        private static String nameOf(RenderType type) {
            String description = type.toString();
            if (!description.startsWith("RenderType[")) {
                return description;
            }
            int end = description.indexOf(':', 11);
            return end < 0 ? description.substring(11) : description.substring(11, end);
        }
    }

    /**
     * Passes vertex positions through and replaces everything else: one flat colour, and no texture, light or
     * normal — the heat buffer's format has nowhere to put them, and a signature has no business showing a
     * skin anyway.
     */
    private static final class HeatVertices implements VertexConsumer {
        private VertexConsumer delegate;
        private int color;

        @javax.annotation.Nullable
        private VertexConsumer target;
        private boolean passThrough;
        private float lastTemperature;

        private boolean graded;
        private float floorY;
        private float height;
        private float temperature;

        private HeatVertices(VertexConsumer delegate) {
            this.delegate = delegate;
        }

        /** Shades the next body from its feet (at {@code floorY}, camera-relative) up to its head. */
        private void grade(float floorY, float height, float temperature) {
            this.graded = true;
            this.floorY = floorY;
            this.height = height;
            this.temperature = temperature;
        }

        private void ungrade() {
            this.graded = false;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            if (this.passThrough) {
                float up = Mth.clamp(((float) y - this.floorY) / this.height, 0.0F, 1.0F);
                this.lastTemperature = this.temperature * (0.86F + 0.14F * up);
                this.target.vertex(x, y, z);
                return this;
            }
            if (!this.graded) {
                this.delegate.vertex(x, y, z).color(this.color).endVertex();
                return this;
            }
            float up = Mth.clamp(((float) y - this.floorY) / this.height, 0.0F, 1.0F);
            float t = this.temperature * (0.86F + 0.14F * up);
            this.delegate.vertex(x, y, z).color(t, t, t * 0.90F, 1.0F).endVertex();
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            if (this.passThrough) {
                float t = this.lastTemperature;
                this.target.color(t, t, t, alpha / 255.0F);
            }
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            if (this.passThrough) {
                this.target.uv(u, v);
            }
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            if (this.passThrough) {
                this.target.overlayCoords(u, v);
            }
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            if (this.passThrough) {
                this.target.uv2(u, v);
            }
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            if (this.passThrough) {
                this.target.normal(x, y, z);
            }
            return this;
        }

        /**
         * Closes a passed-through vertex in its mask buffer: 1.20.1 buffers want every vertex ended. A flat
         * heat vertex is complete (position and colour) the moment it is begun, so {@link #vertex} closes it
         * there and then, one heat vertex per position handed in, whatever the model does about ending it.
         */
        @Override
        public void endVertex() {
            if (this.passThrough) {
                this.target.endVertex();
            }
        }

        /** The heat paints every vertex itself, so a model's default colour has nothing to add. */
        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
        }

        @Override
        public void unsetDefaultColor() {
        }
    }

    /** Swallows geometry the heat buffer cannot take (see {@link HeatBuffers#getBuffer}). */
    private static final class DiscardVertices implements VertexConsumer {
        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
        }

        @Override
        public void unsetDefaultColor() {
        }
    }

    private ThermalHeatRenderer() {
    }
}
