package dev.krutlexik.skyfront.drone.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.krutlexik.skyfront.drone.DroneMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A store's own model — SuperbWarfare's RPG rounds — drawn hanging on a drone, read straight out of
 * SuperbWarfare's resources (nothing of theirs is copied into this mod) and drawn by this mod itself.
 */
@OnlyIn(Dist.CLIENT)
public final class BedrockStoreModel {
    /** Bones that are no part of a round sitting on a pylon: the motor's flame, the spread fins. */
    private static final Set<String> SKIPPED = Set.of("flare", "wing");
    /** Out past this from the round's axis, in blocks, a mesh face is the fins'. */
    private static final double FIN_RADIUS = 0.25;

    private static final Map<ResourceLocation, BedrockStoreModel> LOADED = new HashMap<>();
    private static final Set<ResourceLocation> MISSING = new HashSet<>();

    private final ResourceLocation texture;
    /** Quads, as {x y z u v nx ny nz} four times over; a triangle repeats its last corner. */
    private final List<float[]> quads;

    /** Tail to warhead tip, blocks. */
    private final float length;

    private BedrockStoreModel(ResourceLocation texture, List<float[]> quads) {
        this.texture = texture;
        this.quads = quads;
        float tip = 0.0F;
        for (float[] q : quads) {
            for (int k = 0; k < 4; k++) {
                tip = Math.min(tip, q[k * 8 + 2]);
            }
        }
        this.length = -tip;
    }

    /** Tail to warhead tip, blocks: the round lies from the origin out to {@code -length} along Z. */
    public float length() {
        return this.length;
    }

    /**
     * The model of the projectile registered as {@code entity} — SuperbWarfare keeps it at {@code
     * models/bedrock/projectile/.geo.json} with its texture beside it — or null if there is none
     * (SuperbWarfare not installed, or not a store drawn this way).
     */
    @Nullable
    public static BedrockStoreModel forProjectile(ResourceLocation entity) {
        BedrockStoreModel known = LOADED.get(entity);
        if (known != null || MISSING.contains(entity)) {
            return known;
        }
        String ns = entity.getNamespace();
        String path = entity.getPath();
        try {
            BedrockStoreModel model = read(
                    new ResourceLocation(ns, "models/bedrock/projectile/" + path + ".geo.json"),
                    new ResourceLocation(ns, "textures/bedrock/projectile/" + path + ".png"),
                    null);
            Legacy legacy = LEGACY.get(path);
            if (model == null && legacy != null) {
                model = read(new ResourceLocation(ns, "geo/" + path + ".geo.json"),
                        new ResourceLocation(ns, legacy.texture()), legacy.frame());
            }
            if (model == null) {
                MISSING.add(entity);
                return null;
            }
            LOADED.put(entity, model);
            return model;
        } catch (Exception failure) {
            DroneMod.LOGGER.warn("Could not read the store model for {}", entity, failure);
            MISSING.add(entity);
            return null;
        }
    }

    /** Where SuperbWarfare 0.8.8 kept a store's model, and how to lay it into the 0.8.9 frame. */
    private record Legacy(String texture, Matrix4f frame) {
    }

    private static final Map<String, Legacy> LEGACY = Map.of(
            "rpg_rocket_standard", new Legacy("textures/entity/rpg_rocket_standard.png",
                    new Matrix4f().translate(0.0F, 0.0002F, 0.0068F).rotateX((float) Math.toRadians(-90.0D))),
            "rpg_rocket_tbg", new Legacy("textures/entity/rpg_rocket_tbg.png",
                    new Matrix4f().translate(0.0017F, -0.0009F, 0.0060F).scale(1.1235F)
                            .rotateX((float) Math.toRadians(-90.0D))),
            "mortar_shell", new Legacy("textures/entity/mortar.png",
                    new Matrix4f().translate(0.0F, -0.0001F, -0.2809F).rotateX((float) Math.toRadians(90.0D))),
            "c4", new Legacy("textures/item/c4.png", new Matrix4f()));

    /** One model, if its geometry is there; laid into {@code frame} when one is given. */
    @Nullable
    private static BedrockStoreModel read(ResourceLocation geo, ResourceLocation texture,
                                          @Nullable Matrix4f frame) throws java.io.IOException {
        var resource = Minecraft.getInstance().getResourceManager().getResource(geo);
        if (resource.isEmpty()) {
            return null;
        }
        JsonObject root;
        try (Reader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            root = GsonHelper.parse(reader);
        }
        List<float[]> quads = build(root);
        if (frame != null) {
            for (float[] q : quads) {
                for (int k = 0; k < 4; k++) {
                    int i = k * 8;
                    Vector3f p = frame.transformPosition(new Vector3f(q[i], q[i + 1], q[i + 2]));
                    Vector3f n = frame.transformDirection(new Vector3f(q[i + 5], q[i + 6], q[i + 7])).normalize();
                    q[i] = p.x;
                    q[i + 1] = p.y;
                    q[i + 2] = p.z;
                    q[i + 5] = n.x;
                    q[i + 6] = n.y;
                    q[i + 7] = n.z;
                }
            }
        }
        return new BedrockStoreModel(texture, quads);
    }

    /** Draws the round at the pose, in blocks. */
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light) {
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(this.texture));
        PoseStack.Pose pose = poseStack.last();
        for (float[] q : this.quads) {
            for (int k = 0; k < 4; k++) {
                int i = k * 8;
                // Normalized after turning, as 1.21's setNormal(Pose, ..) does under a non-uniform scale.
                Vector3f normal = pose.normal().transform(new Vector3f(q[i + 5], q[i + 6], q[i + 7])).normalize();
                out.vertex(pose.pose(), q[i], q[i + 1], q[i + 2])
                        .color(255, 255, 255, 255)
                        .uv(q[i + 3], q[i + 4])
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(light)
                        .normal(normal.x, normal.y, normal.z)
                        .endVertex();
            }
        }
    }

    private static List<float[]> build(JsonObject root) {
        JsonObject geometry = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonObject description = geometry.getAsJsonObject("description");
        float texW = GsonHelper.getAsFloat(description, "texture_width", 16.0F);
        float texH = GsonHelper.getAsFloat(description, "texture_height", 16.0F);

        Map<String, JsonObject> bones = new HashMap<>();
        for (JsonElement element : geometry.getAsJsonArray("bones")) {
            JsonObject bone = element.getAsJsonObject();
            bones.put(GsonHelper.getAsString(bone, "name"), bone);
        }
        Map<String, Matrix4f> placed = new HashMap<>();
        List<float[]> quads = new ArrayList<>();
        for (JsonObject bone : bones.values()) {
            if (skipped(bone, bones)) {
                continue;
            }
            Matrix4f matrix = boneMatrix(GsonHelper.getAsString(bone, "name"), bones, placed);
            if (bone.has("cubes")) {
                for (JsonElement cube : bone.getAsJsonArray("cubes")) {
                    addCube(cube.getAsJsonObject(), matrix, texW, texH, quads);
                }
            }
            if (bone.has("poly_mesh")) {
                addMesh(bone.getAsJsonObject("poly_mesh"), matrix, texW, texH, quads);
            }
        }
        return quads;
    }

    private static boolean skipped(JsonObject bone, Map<String, JsonObject> bones) {
        JsonObject at = bone;
        while (at != null) {
            if (SKIPPED.contains(GsonHelper.getAsString(at, "name"))) {
                return true;
            }
            at = at.has("parent") ? bones.get(GsonHelper.getAsString(at, "parent")) : null;
        }
        return false;
    }

    private static Matrix4f boneMatrix(String name, Map<String, JsonObject> bones, Map<String, Matrix4f> placed) {
        Matrix4f known = placed.get(name);
        if (known != null) {
            return known;
        }
        JsonObject bone = bones.get(name);
        Vector3f pivot = mirrored(vec(bone, "pivot"));
        Vector3f r = vec(bone, "rotation");
        Matrix4f local = new Matrix4f().translate(pivot).mul(rotation(r)).translate(-pivot.x, -pivot.y, -pivot.z);
        String parent = bone.has("parent") ? GsonHelper.getAsString(bone, "parent") : null;
        Matrix4f matrix = parent != null && bones.containsKey(parent)
                ? new Matrix4f(boneMatrix(parent, bones, placed)).mul(local) : local;
        placed.put(name, matrix);
        return matrix;
    }

    /** A Bedrock rotation as Blockbench turns it: X and Y reversed, applied Z, then Y, then X. */
    private static Matrix4f rotation(Vector3f bedrock) {
        return new Matrix4f()
                .rotateZ((float) Math.toRadians(bedrock.z))
                .rotateY((float) Math.toRadians(-bedrock.y))
                .rotateX((float) Math.toRadians(-bedrock.x));
    }

    private static void addCube(JsonObject cube, Matrix4f bone, float texW, float texH, List<float[]> quads) {
        Vector3f origin = vec(cube, "origin");
        Vector3f size = vec(cube, "size");
        float inflate = GsonHelper.getAsFloat(cube, "inflate", 0.0F);
        Vector3f from = new Vector3f(-(origin.x + size.x) - inflate, origin.y - inflate, origin.z - inflate);
        Vector3f to = new Vector3f(from).add(size).add(2 * inflate, 2 * inflate, 2 * inflate);
        Vector3f pivot = mirrored(vec(cube, "pivot"));
        Matrix4f matrix = new Matrix4f(bone).translate(pivot).mul(rotation(vec(cube, "rotation")))
                .translate(-pivot.x, -pivot.y, -pivot.z);

        Map<String, float[]> faces = new HashMap<>();
        JsonElement uv = cube.get("uv");
        if (uv != null && uv.isJsonArray()) {
            float u = uv.getAsJsonArray().get(0).getAsFloat();
            float v = uv.getAsJsonArray().get(1).getAsFloat();
            float sx = (float) Math.floor(size.x);
            float sy = (float) Math.floor(size.y);
            float sz = (float) Math.floor(size.z);
            faces.put("north", new float[] {u + sz, v + sz, u + sz + sx, v + sz + sy});
            faces.put("east", new float[] {u, v + sz, u + sz, v + sz + sy});
            faces.put("south", new float[] {u + 2 * sz + sx, v + sz, u + 2 * sz + 2 * sx, v + sz + sy});
            faces.put("west", new float[] {u + sz + sx, v + sz, u + 2 * sz + sx, v + sz + sy});
            faces.put("up", new float[] {u + sz + sx, v + sz, u + sz, v});
            faces.put("down", new float[] {u + sz + 2 * sx, v, u + sz + sx, v + sz});
        } else if (uv != null && uv.isJsonObject()) {
            for (Map.Entry<String, JsonElement> face : uv.getAsJsonObject().entrySet()) {
                JsonObject f = face.getValue().getAsJsonObject();
                JsonArray at = f.getAsJsonArray("uv");
                JsonArray span = f.getAsJsonArray("uv_size");
                float u = at.get(0).getAsFloat();
                float v = at.get(1).getAsFloat();
                float su = span.get(0).getAsFloat();
                float sv = span.get(1).getAsFloat();
                boolean flat = face.getKey().equals("up") || face.getKey().equals("down");
                faces.put(face.getKey(), flat ? new float[] {u + su, v + sv, u, v} : new float[] {u, v, u + su, v + sv});
            }
        }
        for (Map.Entry<String, float[]> face : faces.entrySet()) {
            Vector3f[] corners = faceQuad(from, to, face.getKey());
            if (corners == null) {
                continue;
            }
            float[] f = face.getValue();
            float[][] uvs = {{f[0], f[1]}, {f[0], f[3]}, {f[2], f[3]}, {f[2], f[1]}};
            float[] q = new float[32];
            Vector3f normal = normalOf(face.getKey());
            matrix.transformDirection(normal).normalize();
            for (int k = 0; k < 4; k++) {
                Vector3f p = matrix.transformPosition(new Vector3f(corners[k])).div(16.0F);
                put(q, k, p, uvs[k][0] / texW, uvs[k][1] / texH, normal);
            }
            quads.add(q);
        }
    }

    private static void addMesh(JsonObject mesh, Matrix4f bone, float texW, float texH, List<float[]> quads) {
        JsonArray positions = mesh.getAsJsonArray("positions");
        JsonArray uvs = mesh.getAsJsonArray("uvs");
        boolean normalized = GsonHelper.getAsBoolean(mesh, "normalized_uvs", false);
        for (JsonElement polyElement : mesh.getAsJsonArray("polys")) {
            JsonArray poly = polyElement.getAsJsonArray();
            int n = poly.size();
            if (n < 3) {
                continue;
            }
            Vector3f[] points = new Vector3f[n];
            float[][] texels = new float[n][2];
            boolean fin = false;
            for (int k = 0; k < n; k++) {
                JsonArray corner = poly.get(k).getAsJsonArray();
                JsonArray p = positions.get(corner.get(0).getAsInt()).getAsJsonArray();
                Vector3f local = new Vector3f(-p.get(0).getAsFloat(), p.get(1).getAsFloat(), p.get(2).getAsFloat());
                points[k] = bone.transformPosition(local).div(16.0F);
                if (Math.hypot(points[k].x, points[k].y) > FIN_RADIUS) {
                    fin = true;
                }
                JsonArray t = uvs.get(corner.get(2).getAsInt()).getAsJsonArray();
                float u = t.get(0).getAsFloat();
                float v = t.get(1).getAsFloat();
                if (!normalized) {
                    u /= texW;
                    v /= texH;
                }
                texels[k][0] = u;
                texels[k][1] = 1.0F - v;
            }
            if (fin) {
                continue;
            }
            Vector3f normal = new Vector3f(points[1]).sub(points[0]).cross(new Vector3f(points[2]).sub(points[0]));
            if (normal.lengthSquared() < 1.0E-12F) {
                continue;
            }
            normal.normalize();
            float[] q = new float[32];
            for (int k = 0; k < 4; k++) {
                int c = Math.min(k, n - 1);
                put(q, k, points[c], texels[c][0], texels[c][1], normal);
            }
            quads.add(q);
        }
    }

    private static void put(float[] q, int k, Vector3f p, float u, float v, Vector3f n) {
        int i = k * 8;
        q[i] = p.x;
        q[i + 1] = p.y;
        q[i + 2] = p.z;
        q[i + 3] = u;
        q[i + 4] = v;
        q[i + 5] = n.x;
        q[i + 6] = n.y;
        q[i + 7] = n.z;
    }

    /** A box face's corners, in the order that matches (u1,v1), (u1,v2), (u2,v2), (u2,v1). */
    @Nullable
    private static Vector3f[] faceQuad(Vector3f f, Vector3f t, String face) {
        return switch (face) {
            case "down" -> new Vector3f[] {v(f.x, f.y, t.z), v(f.x, f.y, f.z), v(t.x, f.y, f.z), v(t.x, f.y, t.z)};
            case "up" -> new Vector3f[] {v(f.x, t.y, f.z), v(f.x, t.y, t.z), v(t.x, t.y, t.z), v(t.x, t.y, f.z)};
            case "north" -> new Vector3f[] {v(t.x, t.y, f.z), v(t.x, f.y, f.z), v(f.x, f.y, f.z), v(f.x, t.y, f.z)};
            case "south" -> new Vector3f[] {v(f.x, t.y, t.z), v(f.x, f.y, t.z), v(t.x, f.y, t.z), v(t.x, t.y, t.z)};
            case "west" -> new Vector3f[] {v(f.x, t.y, f.z), v(f.x, f.y, f.z), v(f.x, f.y, t.z), v(f.x, t.y, t.z)};
            case "east" -> new Vector3f[] {v(t.x, t.y, t.z), v(t.x, f.y, t.z), v(t.x, f.y, f.z), v(t.x, t.y, f.z)};
            default -> null;
        };
    }

    private static Vector3f normalOf(String face) {
        return switch (face) {
            case "down" -> new Vector3f(0, -1, 0);
            case "up" -> new Vector3f(0, 1, 0);
            case "north" -> new Vector3f(0, 0, -1);
            case "south" -> new Vector3f(0, 0, 1);
            case "west" -> new Vector3f(-1, 0, 0);
            default -> new Vector3f(1, 0, 0);
        };
    }

    private static Vector3f v(float x, float y, float z) {
        return new Vector3f(x, y, z);
    }

    private static Vector3f vec(JsonObject object, String key) {
        if (!object.has(key)) {
            return new Vector3f();
        }
        JsonArray a = object.getAsJsonArray(key);
        return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
    }

    private static Vector3f mirrored(Vector3f bedrock) {
        return new Vector3f(-bedrock.x, bedrock.y, bedrock.z);
    }
}
