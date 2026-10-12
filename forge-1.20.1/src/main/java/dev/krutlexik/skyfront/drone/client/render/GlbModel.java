package dev.krutlexik.skyfront.drone.client.render;

import dev.krutlexik.skyfront.drone.DroneMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import org.joml.AxisAngle4f;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal glTF 2.0 binary (.glb) loader: enough of the spec to draw a Blockbench/Sketchfab export as a
 * Minecraft entity.
 */
public final class GlbModel {
    private static final int MAGIC = 0x46546C67;
    private static final int CHUNK_JSON = 0x4E4F534A;
    private static final int CHUNK_BIN = 0x004E4942;

    private static final int MODE_TRIANGLES = 4;
    private static final int FLOAT = 5126;
    private static final int UNSIGNED_BYTE = 5121;
    private static final int UNSIGNED_SHORT = 5123;
    private static final int UNSIGNED_INT = 5125;

    /** One primitive. */
    private record Part(float[] positions, float[] normals, float[] uvs, int[] indices, ResourceLocation texture) {
    }

    /** A node subtree that turns rather than sitting still — the propellers. */
    private record AnimatedGroup(Matrix4f restTransform, @javax.annotation.Nullable AnimTrack track,
                                 List<Part> parts) {
    }

    /** Radians of spin per unit of playback time for a propeller with no baked clip. */
    private static final float SPIN_RADIANS = 1.0F;

    /** Whether this node is a propeller that should be spun for want of a baked animation. */
    private static boolean isPropeller(JsonObject node) {
        String name = GsonHelper.getAsString(node, "name", "");
        return name.matches("(?i)^prop(eller)?[_ -]?\\d+$");
    }

    /**
     * A baked rotation loop on one node, sampled from glTF keyframes with linear/slerp interpolation — the
     * only kind this asset uses.
     */
    private static final class AnimTrack {
        private final float[] times;
        private final Quaternionf[] values;
        private final float duration;
        private final AxisAngle4f perLap;

        AnimTrack(float[] times, float[] flatRotations) {
            this.times = times;
            this.values = new Quaternionf[times.length];
            for (int i = 0; i < times.length; i++) {
                this.values[i] = new Quaternionf(
                        flatRotations[i * 4], flatRotations[i * 4 + 1],
                        flatRotations[i * 4 + 2], flatRotations[i * 4 + 3]);
            }
            this.duration = times[times.length - 1];
            Quaternionf net = new Quaternionf(this.values[this.values.length - 1])
                    .mul(new Quaternionf(this.values[0]).conjugate());
            this.perLap = new AxisAngle4f(net);
        }

        Quaternionf sample(float time) {
            if (this.duration <= 0.0F || this.times.length == 1) {
                return new Quaternionf(this.values[0]);
            }

            int lap = (int) Math.floor(time / this.duration);
            float localTime = time - lap * this.duration;

            int hi = 1;
            while (hi < this.times.length - 1 && this.times[hi] < localTime) {
                hi++;
            }
            int lo = hi - 1;
            float span = this.times[hi] - this.times[lo];
            float factor = span > 0.0F ? (localTime - this.times[lo]) / span : 0.0F;
            factor = Math.max(0.0F, Math.min(1.0F, factor));
            Quaternionf local = new Quaternionf(this.values[lo]).slerp(this.values[hi], factor);

            if (lap == 0) {
                return local;
            }
            Quaternionf accumulated = new Quaternionf()
                    .fromAxisAngleRad(this.perLap.x, this.perLap.y, this.perLap.z, this.perLap.angle * lap);
            return accumulated.mul(local);
        }
    }

    private final List<Part> staticParts;
    private final List<AnimatedGroup> animatedGroups;
    private final float minY;
    private final float centreX;
    private final float centreZ;
    private final float sizeX;
    private final float sizeZ;
    private final float height;

    private GlbModel(List<Part> staticParts, List<AnimatedGroup> animatedGroups) {
        this.staticParts = staticParts;
        this.animatedGroups = animatedGroups;

        float[] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE,
                Float.MAX_VALUE, -Float.MAX_VALUE};
        for (Part part : staticParts) {
            measure(part.positions(), null, bounds);
        }
        for (AnimatedGroup group : animatedGroups) {
            for (Part part : group.parts()) {
                measure(part.positions(), group.restTransform(), bounds);
            }
        }
        boolean empty = bounds[0] == Float.MAX_VALUE;
        this.centreX = empty ? 0.0F : (bounds[0] + bounds[2]) / 2.0F;
        this.centreZ = empty ? 0.0F : (bounds[1] + bounds[3]) / 2.0F;
        this.minY = empty ? 0.0F : bounds[4];
        this.sizeX = empty ? 0.0F : bounds[2] - bounds[0];
        this.sizeZ = empty ? 0.0F : bounds[3] - bounds[1];
        this.height = empty ? 0.0F : bounds[5] - bounds[4];
    }

    private static void measure(float[] positions, @javax.annotation.Nullable Matrix4f restTransform, float[] bounds) {
        Vector3f scratch = new Vector3f();
        for (int i = 0; i + 2 < positions.length; i += 3) {
            scratch.set(positions[i], positions[i + 1], positions[i + 2]);
            if (restTransform != null) {
                restTransform.transformPosition(scratch);
            }
            bounds[0] = Math.min(bounds[0], scratch.x);
            bounds[1] = Math.min(bounds[1], scratch.z);
            bounds[2] = Math.max(bounds[2], scratch.x);
            bounds[3] = Math.max(bounds[3], scratch.z);
            bounds[4] = Math.min(bounds[4], scratch.y);
            bounds[5] = Math.max(bounds[5], scratch.y);
        }
    }

    /** Width of the mesh across (X), in model units — for fitting it into an item slot. */
    public float sizeX() {
        return this.sizeX;
    }

    /** Depth of the mesh along (Z), in model units. */
    public float sizeZ() {
        return this.sizeZ;
    }

    /** Height of the mesh (Y), in model units. */
    public float height() {
        return this.height;
    }

    public int triangleCount() {
        int total = this.staticParts.stream().mapToInt(part -> part.indices().length / 3).sum();
        for (AnimatedGroup group : this.animatedGroups) {
            total += group.parts().stream().mapToInt(part -> part.indices().length / 3).sum();
        }
        return total;
    }

    /** How many nodes carry a baked rotation loop (propellers, on this model) — for load logging. */
    public int animatedGroupCount() {
        return this.animatedGroups.size();
    }

    /** Lowest point of the mesh in model units — how far the landing gear sits below the origin. */
    public float minY() {
        return this.minY;
    }

    /** Middle of the model across, in model units. */
    public float centreX() {
        return this.centreX;
    }

    /** Middle of the model along, in model units; see {@link #centreX()}. */
    public float centreZ() {
        return this.centreZ;
    }

    /**
     * The model's silhouette as seen through {@code rotation}: every vertex, propellers at rest included,
     * turned and measured, as {@code {minX, maxX, minY, maxY, depth}} — the last being where the middle of
     * the bounds lands along the view axis.
     */
    public float[] silhouette(Matrix3fc rotation) {
        float[] out = {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, 0.0F};
        Vector3f scratch = new Vector3f();
        for (Part part : this.staticParts) {
            project(part.positions(), null, rotation, scratch, out);
        }
        for (AnimatedGroup group : this.animatedGroups) {
            for (Part part : group.parts()) {
                project(part.positions(), group.restTransform(), rotation, scratch, out);
            }
        }
        if (out[0] == Float.MAX_VALUE) {
            return new float[5];
        }
        out[4] = rotation.transform(this.centreX, this.minY + this.height / 2.0F, this.centreZ, scratch).z;
        return out;
    }

    private static void project(float[] positions, @javax.annotation.Nullable Matrix4f restTransform,
                                Matrix3fc rotation, Vector3f scratch, float[] out) {
        for (int i = 0; i + 2 < positions.length; i += 3) {
            scratch.set(positions[i], positions[i + 1], positions[i + 2]);
            if (restTransform != null) {
                restTransform.transformPosition(scratch);
            }
            rotation.transform(scratch);
            out[0] = Math.min(out[0], scratch.x);
            out[1] = Math.max(out[1], scratch.x);
            out[2] = Math.min(out[2], scratch.y);
            out[3] = Math.max(out[3], scratch.y);
        }
    }

    /** Must be called on the render thread: embedded images become {@link DynamicTexture}s. */
    public static GlbModel load(ResourceManager resources, ResourceLocation location,
                                boolean spinPropellers) throws IOException {
        byte[] bytes;
        try (InputStream stream = resources.getResourceOrThrow(location).open()) {
            bytes = stream.readAllBytes();
        }

        ByteBuffer file = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (file.remaining() < 12 || file.getInt() != MAGIC) {
            throw new IOException(location + " is not a glb file");
        }
        file.getInt();
        file.getInt();

        JsonObject gltf = null;
        byte[] binary = new byte[0];
        while (file.remaining() >= 8) {
            int chunkLength = file.getInt();
            int chunkType = file.getInt();
            byte[] chunk = new byte[Math.min(chunkLength, file.remaining())];
            file.get(chunk);
            if (chunkType == CHUNK_JSON) {
                gltf = GsonHelper.parse(new String(chunk, StandardCharsets.UTF_8));
            } else if (chunkType == CHUNK_BIN) {
                binary = chunk;
            }
        }
        if (gltf == null) {
            throw new IOException(location + " has no JSON chunk");
        }

        ResourceLocation[] textures = uploadTextures(gltf, binary, location);
        Map<Integer, AnimTrack> tracks = readAnimationTracks(gltf, binary);

        List<Part> staticParts = new ArrayList<>();
        List<AnimatedGroup> animatedGroups = new ArrayList<>();
        for (int nodeIndex : sceneRoots(gltf)) {
            collectNode(gltf, binary, nodeIndex, new Matrix4f(), textures, staticParts, animatedGroups,
                    tracks, null, spinPropellers);
        }
        if (staticParts.isEmpty() && animatedGroups.isEmpty()) {
            throw new IOException(location + " contains no triangle geometry");
        }
        return new GlbModel(staticParts, animatedGroups);
    }

    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, int overlay, float animTime) {
        renderParts(this.staticParts, poseStack.last(), buffers, light, overlay);

        for (int i = 0; i < this.animatedGroups.size(); i++) {
            renderGroup(i, poseStack, buffers, light, overlay, animTime);
        }
    }

    /** Where an animated group turns about — a propeller's shaft — in model units. */
    public Vector3f groupPivot(int index) {
        return this.animatedGroups.get(index).restTransform().getTranslation(new Vector3f());
    }

    /**
     * The middle of an animated group's own geometry, in model units — where a propeller's blades actually
     * are, which is not where it turns about: a pivot can sit well down the motor.
     */
    public Vector3f groupCentre(int index) {
        AnimatedGroup group = this.animatedGroups.get(index);
        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
        Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
        for (Part part : group.parts()) {
            float[] positions = part.positions();
            for (int i = 0; i + 2 < positions.length; i += 3) {
                min.min(new Vector3f(positions[i], positions[i + 1], positions[i + 2]));
                max.max(new Vector3f(positions[i], positions[i + 1], positions[i + 2]));
            }
        }
        if (min.x > max.x) {
            return groupPivot(index);
        }
        return group.restTransform().transformPosition(min.add(max).mul(0.5F));
    }

    /** One animated group on its own, turning as it does in {@link #render}. */
    public void renderGroup(int index, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay,
                            float animTime) {
        AnimatedGroup group = this.animatedGroups.get(index);
        poseStack.pushPose();
        Vector3f translation = group.restTransform().getTranslation(new Vector3f());
        Quaternionf baseRotation = group.restTransform().getNormalizedRotation(new Quaternionf());
        poseStack.translate(translation.x, translation.y, translation.z);
        poseStack.mulPose(baseRotation);
        poseStack.mulPose(group.track() != null
                ? group.track().sample(animTime)
                : new Quaternionf().rotateY(animTime * SPIN_RADIANS));
        renderParts(group.parts(), poseStack.last(), buffers, light, overlay);
        poseStack.popPose();
    }

    private static void renderParts(List<Part> parts, PoseStack.Pose pose, MultiBufferSource buffers,
                                    int light, int overlay) {
        for (Part part : parts) {
            VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(part.texture()));
            float[] positions = part.positions();
            float[] normals = part.normals();
            float[] uvs = part.uvs();
            int[] indices = part.indices();

            for (int i = 0; i + 2 < indices.length; i += 3) {
                int a = indices[i];
                int b = indices[i + 1];
                int c = indices[i + 2];
                vertex(consumer, pose, positions, normals, uvs, a, light, overlay);
                vertex(consumer, pose, positions, normals, uvs, b, light, overlay);
                vertex(consumer, pose, positions, normals, uvs, c, light, overlay);
                vertex(consumer, pose, positions, normals, uvs, c, light, overlay);
            }
        }
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose,
                               float[] positions, float[] normals, float[] uvs,
                               int index, int light, int overlay) {
        // Normalized after turning, as 1.21's setNormal(Pose, ..) does under a non-uniform scale.
        Vector3f normal = pose.normal().transform(
                new Vector3f(normals[index * 3], normals[index * 3 + 1], normals[index * 3 + 2])).normalize();
        consumer.vertex(pose.pose(), positions[index * 3], positions[index * 3 + 1], positions[index * 3 + 2])
                .color(255, 255, 255, 255)
                .uv(uvs[index * 2], uvs[index * 2 + 1])
                .overlayCoords(overlay)
                .uv2(light)
                .normal(normal.x, normal.y, normal.z)
                .endVertex();
    }

    private static int[] sceneRoots(JsonObject gltf) {
        int sceneIndex = GsonHelper.getAsInt(gltf, "scene", 0);
        JsonArray scenes = GsonHelper.getAsJsonArray(gltf, "scenes", new JsonArray());
        if (sceneIndex < scenes.size()) {
            JsonArray nodes = GsonHelper.getAsJsonArray(scenes.get(sceneIndex).getAsJsonObject(), "nodes", new JsonArray());
            int[] roots = new int[nodes.size()];
            for (int i = 0; i < roots.length; i++) {
                roots[i] = nodes.get(i).getAsInt();
            }
            return roots;
        }
        int count = GsonHelper.getAsJsonArray(gltf, "nodes", new JsonArray()).size();
        int[] roots = new int[count];
        for (int i = 0; i < count; i++) {
            roots[i] = i;
        }
        return roots;
    }

    private static void collectNode(JsonObject gltf, byte[] binary, int nodeIndex, Matrix4f parent,
                                    ResourceLocation[] textures, List<Part> staticOut,
                                    List<AnimatedGroup> animatedOut, Map<Integer, AnimTrack> tracks,
                                    List<Part> activeGroup, boolean spinPropellers) {
        JsonObject node = GsonHelper.getAsJsonArray(gltf, "nodes").get(nodeIndex).getAsJsonObject();
        Matrix4f transform = new Matrix4f(parent).mul(localTransform(node));

        Matrix4f bakeTransform = transform;
        List<Part> target = activeGroup;

        AnimTrack track = tracks.get(nodeIndex);
        if (track != null || (spinPropellers && isPropeller(node))) {
            List<Part> group = new ArrayList<>();
            animatedOut.add(new AnimatedGroup(new Matrix4f(transform), track, group));
            target = group;
            bakeTransform = new Matrix4f();
        }

        if (node.has("mesh")) {
            JsonObject mesh = GsonHelper.getAsJsonArray(gltf, "meshes")
                    .get(GsonHelper.getAsInt(node, "mesh")).getAsJsonObject();
            for (JsonElement element : GsonHelper.getAsJsonArray(mesh, "primitives")) {
                Part part = readPrimitive(gltf, binary, element.getAsJsonObject(), bakeTransform, textures);
                if (part != null) {
                    (target != null ? target : staticOut).add(part);
                }
            }
        }

        for (JsonElement child : GsonHelper.getAsJsonArray(node, "children", new JsonArray())) {
            collectNode(gltf, binary, child.getAsInt(), bakeTransform, textures, staticOut, animatedOut,
                    tracks, target, spinPropellers);
        }
    }

    private static Map<Integer, AnimTrack> readAnimationTracks(JsonObject gltf, byte[] binary) {
        Map<Integer, AnimTrack> tracks = new HashMap<>();
        for (JsonElement animElement : GsonHelper.getAsJsonArray(gltf, "animations", new JsonArray())) {
            JsonObject animation = animElement.getAsJsonObject();
            JsonArray samplers = GsonHelper.getAsJsonArray(animation, "samplers");
            for (JsonElement channelElement : GsonHelper.getAsJsonArray(animation, "channels")) {
                JsonObject channel = channelElement.getAsJsonObject();
                JsonObject target = GsonHelper.getAsJsonObject(channel, "target");
                if (!target.has("node") || !"rotation".equals(GsonHelper.getAsString(target, "path"))) {
                    continue;
                }
                JsonObject sampler = samplers.get(GsonHelper.getAsInt(channel, "sampler")).getAsJsonObject();
                float[] times = readFloats(gltf, binary, GsonHelper.getAsInt(sampler, "input"), 1);
                float[] rotations = readFloats(gltf, binary, GsonHelper.getAsInt(sampler, "output"), 4);
                tracks.put(GsonHelper.getAsInt(target, "node"), new AnimTrack(times, rotations));
            }
        }
        return tracks;
    }

    private static Matrix4f localTransform(JsonObject node) {
        if (node.has("matrix")) {
            float[] values = floats(GsonHelper.getAsJsonArray(node, "matrix"));
            return new Matrix4f().set(values);
        }
        float[] translation = node.has("translation")
                ? floats(GsonHelper.getAsJsonArray(node, "translation")) : new float[]{0, 0, 0};
        float[] rotation = node.has("rotation")
                ? floats(GsonHelper.getAsJsonArray(node, "rotation")) : new float[]{0, 0, 0, 1};
        float[] scale = node.has("scale")
                ? floats(GsonHelper.getAsJsonArray(node, "scale")) : new float[]{1, 1, 1};
        return new Matrix4f().translationRotateScale(
                translation[0], translation[1], translation[2],
                rotation[0], rotation[1], rotation[2], rotation[3],
                scale[0], scale[1], scale[2]);
    }

    private static Part readPrimitive(JsonObject gltf, byte[] binary, JsonObject primitive,
                                      Matrix4f transform, ResourceLocation[] textures) {
        if (GsonHelper.getAsInt(primitive, "mode", MODE_TRIANGLES) != MODE_TRIANGLES) {
            return null;
        }
        JsonObject attributes = GsonHelper.getAsJsonObject(primitive, "attributes");
        if (!attributes.has("POSITION")) {
            return null;
        }

        float[] positions = readFloats(gltf, binary, GsonHelper.getAsInt(attributes, "POSITION"), 3);
        int vertexCount = positions.length / 3;

        float[] normals = attributes.has("NORMAL")
                ? readFloats(gltf, binary, GsonHelper.getAsInt(attributes, "NORMAL"), 3)
                : new float[positions.length];
        float[] uvs = attributes.has("TEXCOORD_0")
                ? readFloats(gltf, binary, GsonHelper.getAsInt(attributes, "TEXCOORD_0"), 2)
                : new float[vertexCount * 2];

        Vector3f scratch = new Vector3f();
        for (int i = 0; i < vertexCount; i++) {
            scratch.set(positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2]);
            transform.transformPosition(scratch);
            positions[i * 3] = scratch.x;
            positions[i * 3 + 1] = scratch.y;
            positions[i * 3 + 2] = scratch.z;

            scratch.set(normals[i * 3], normals[i * 3 + 1], normals[i * 3 + 2]);
            if (scratch.lengthSquared() > 1.0E-8F) {
                transform.transformDirection(scratch).normalize();
            } else {
                scratch.set(0.0F, 1.0F, 0.0F);
            }
            normals[i * 3] = scratch.x;
            normals[i * 3 + 1] = scratch.y;
            normals[i * 3 + 2] = scratch.z;
        }

        int[] indices;
        if (primitive.has("indices")) {
            indices = readIndices(gltf, binary, GsonHelper.getAsInt(primitive, "indices"));
        } else {
            indices = new int[vertexCount];
            for (int i = 0; i < vertexCount; i++) {
                indices[i] = i;
            }
        }

        int material = GsonHelper.getAsInt(primitive, "material", -1);
        ResourceLocation texture = material >= 0 && material < textures.length && textures[material] != null
                ? textures[material]
                : MissingTextureAtlasSprite.getLocation();
        return new Part(positions, normals, uvs, indices, texture);
    }

    private static ResourceLocation[] uploadTextures(JsonObject gltf, byte[] binary, ResourceLocation source) {
        JsonArray materials = GsonHelper.getAsJsonArray(gltf, "materials", new JsonArray());
        JsonArray textures = GsonHelper.getAsJsonArray(gltf, "textures", new JsonArray());
        JsonArray images = GsonHelper.getAsJsonArray(gltf, "images", new JsonArray());

        String prefix = source.getPath()
                .replaceAll("^.*/", "")
                .replaceAll("\\.glb$", "")
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9_]", "_");

        ResourceLocation[] uploaded = new ResourceLocation[images.size()];
        for (int i = 0; i < images.size(); i++) {
            JsonObject image = images.get(i).getAsJsonObject();
            if (!image.has("bufferView")) {
                DroneMod.LOGGER.warn("{}: image {} has no bufferView (external URIs are unsupported)", source, i);
                continue;
            }
            try {
                byte[] png = readBufferView(gltf, binary, GsonHelper.getAsInt(image, "bufferView"));
                NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(png));
                ResourceLocation id = DroneMod.id("glb/" + prefix + "_" + i);
                Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(nativeImage));
                uploaded[i] = id;
            } catch (Exception exception) {
                DroneMod.LOGGER.error("{}: failed to decode embedded image {}", source, i, exception);
            }
        }

        ResourceLocation[] byMaterial = new ResourceLocation[Math.max(materials.size(), 1)];
        for (int i = 0; i < materials.size(); i++) {
            JsonObject pbr = GsonHelper.getAsJsonObject(materials.get(i).getAsJsonObject(),
                    "pbrMetallicRoughness", new JsonObject());
            if (!pbr.has("baseColorTexture")) {
                continue;
            }
            int textureIndex = GsonHelper.getAsInt(GsonHelper.getAsJsonObject(pbr, "baseColorTexture"), "index");
            if (textureIndex < 0 || textureIndex >= textures.size()) {
                continue;
            }
            int imageIndex = GsonHelper.getAsInt(textures.get(textureIndex).getAsJsonObject(), "source", -1);
            if (imageIndex >= 0 && imageIndex < uploaded.length) {
                byMaterial[i] = uploaded[imageIndex];
            }
        }
        return byMaterial;
    }

    private static float[] readFloats(JsonObject gltf, byte[] binary, int accessorIndex, int components) {
        JsonObject accessor = GsonHelper.getAsJsonArray(gltf, "accessors").get(accessorIndex).getAsJsonObject();
        int componentType = GsonHelper.getAsInt(accessor, "componentType");
        if (componentType != FLOAT) {
            throw new IllegalArgumentException("accessor " + accessorIndex + ": only float attributes are supported");
        }
        int count = GsonHelper.getAsInt(accessor, "count");
        ByteBuffer buffer = accessorBuffer(gltf, binary, accessor);
        int stride = accessorStride(gltf, accessor, components * 4);

        float[] values = new float[count * components];
        for (int i = 0; i < count; i++) {
            int base = i * stride;
            for (int c = 0; c < components; c++) {
                values[i * components + c] = buffer.getFloat(base + c * 4);
            }
        }
        return values;
    }

    private static int[] readIndices(JsonObject gltf, byte[] binary, int accessorIndex) {
        JsonObject accessor = GsonHelper.getAsJsonArray(gltf, "accessors").get(accessorIndex).getAsJsonObject();
        int componentType = GsonHelper.getAsInt(accessor, "componentType");
        int count = GsonHelper.getAsInt(accessor, "count");
        ByteBuffer buffer = accessorBuffer(gltf, binary, accessor);

        int size = switch (componentType) {
            case UNSIGNED_BYTE -> 1;
            case UNSIGNED_SHORT -> 2;
            case UNSIGNED_INT -> 4;
            default -> throw new IllegalArgumentException("accessor " + accessorIndex + ": bad index type " + componentType);
        };
        int stride = accessorStride(gltf, accessor, size);

        int[] indices = new int[count];
        for (int i = 0; i < count; i++) {
            int at = i * stride;
            indices[i] = switch (size) {
                case 1 -> buffer.get(at) & 0xFF;
                case 2 -> buffer.getShort(at) & 0xFFFF;
                default -> buffer.getInt(at);
            };
        }
        return indices;
    }

    /** Slices the binary chunk down to where this accessor's data starts. */
    private static ByteBuffer accessorBuffer(JsonObject gltf, byte[] binary, JsonObject accessor) {
        int viewIndex = GsonHelper.getAsInt(accessor, "bufferView");
        JsonObject view = GsonHelper.getAsJsonArray(gltf, "bufferViews").get(viewIndex).getAsJsonObject();
        int offset = GsonHelper.getAsInt(view, "byteOffset", 0) + GsonHelper.getAsInt(accessor, "byteOffset", 0);
        return ByteBuffer.wrap(binary, offset, binary.length - offset)
                .slice()
                .order(ByteOrder.LITTLE_ENDIAN);
    }

    private static int accessorStride(JsonObject gltf, JsonObject accessor, int packedSize) {
        JsonObject view = GsonHelper.getAsJsonArray(gltf, "bufferViews")
                .get(GsonHelper.getAsInt(accessor, "bufferView")).getAsJsonObject();
        int stride = GsonHelper.getAsInt(view, "byteStride", 0);
        return stride > 0 ? stride : packedSize;
    }

    private static byte[] readBufferView(JsonObject gltf, byte[] binary, int viewIndex) {
        JsonObject view = GsonHelper.getAsJsonArray(gltf, "bufferViews").get(viewIndex).getAsJsonObject();
        int offset = GsonHelper.getAsInt(view, "byteOffset", 0);
        int length = GsonHelper.getAsInt(view, "byteLength");
        byte[] slice = new byte[length];
        System.arraycopy(binary, offset, slice, 0, length);
        return slice;
    }

    private static float[] floats(JsonArray array) {
        float[] values = new float[array.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = array.get(i).getAsFloat();
        }
        return values;
    }
}
