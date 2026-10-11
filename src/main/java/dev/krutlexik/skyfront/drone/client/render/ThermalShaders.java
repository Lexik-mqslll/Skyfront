package dev.krutlexik.skyfront.drone.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.krutlexik.skyfront.drone.DroneMod;
import dev.krutlexik.skyfront.drone.client.ThermalVision;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * The thermal camera, done the way it has to be done: by drawing the world itself in heat rather than by
 * recolouring a picture of it afterwards.
 */
@EventBusSubscriber(modid = DroneMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ThermalShaders {
    @Nullable
    private static ShaderInstance solid;
    @Nullable
    private static ShaderInstance cutout;
    @Nullable
    private static ShaderInstance translucent;
    @Nullable
    private static ShaderInstance entitySolid;
    @Nullable
    private static ShaderInstance sky;
    @Nullable
    private static ShaderInstance screen;
    @Nullable
    private static ShaderInstance mask;
    @Nullable
    private static ShaderInstance analog;
    @Nullable
    private static ShaderInstance particle;

    @SubscribeEvent
    public static void register(RegisterShadersEvent event) {
        register(event, "rendertype_solid_thermal", DefaultVertexFormat.BLOCK, loaded -> solid = loaded);
        register(event, "rendertype_cutout_thermal", DefaultVertexFormat.BLOCK, loaded -> cutout = loaded);
        register(event, "rendertype_translucent_thermal", DefaultVertexFormat.BLOCK, loaded -> translucent = loaded);
        register(event, "rendertype_entity_solid_thermal", DefaultVertexFormat.NEW_ENTITY, loaded -> entitySolid = loaded);
        register(event, "sky_thermal", DefaultVertexFormat.POSITION, loaded -> sky = loaded);
        register(event, "thermal_screen", DefaultVertexFormat.POSITION_TEX, loaded -> screen = loaded);
        register(event, "thermal_mask", DefaultVertexFormat.NEW_ENTITY, loaded -> mask = loaded);
        register(event, "analog_feed", DefaultVertexFormat.POSITION_TEX, loaded -> analog = loaded);
        register(event, "particle_thermal", DefaultVertexFormat.PARTICLE, loaded -> particle = loaded);
        DroneMod.LOGGER.info("Registered the thermal camera's core shaders");
    }

    /** Compiles one shader and hands it over. */
    private static void register(RegisterShadersEvent event, String name, VertexFormat format,
                                 Consumer<ShaderInstance> loaded) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), DroneMod.id(name), format), loaded);
        } catch (IOException | RuntimeException failure) {
            loaded.accept(null);
            DroneMod.LOGGER.error("Could not compile the {} shader on this graphics driver; that effect is off",
                    name, failure);
        }
    }

    /** Whether the replacement shaders are there to be used at all. */
    public static boolean loaded() {
        return solid != null && entitySolid != null;
    }

    /** Whether the world should be drawn in heat this frame. */
    public static boolean active() {
        return ThermalVision.isOn();
    }

    @Nullable
    public static ShaderInstance solid() {
        return feed(solid);
    }

    @Nullable
    public static ShaderInstance cutout() {
        return feed(cutout);
    }

    @Nullable
    public static ShaderInstance translucent() {
        return feed(translucent);
    }

    @Nullable
    public static ShaderInstance entitySolid() {
        return feed(entitySolid);
    }

    /** Particles, with an explosion's flash, fire and smoke drawn hot. */
    @Nullable
    public static ShaderInstance particle() {
        return particle;
    }

    @Nullable
    public static ShaderInstance sky() {
        return sky;
    }

    /** The analog video link, over the finished feed. */
    @Nullable
    public static ShaderInstance analog() {
        return analog;
    }

    /** The body mask under a shader pack. */
    @Nullable
    public static ShaderInstance mask() {
        return mask;
    }

    /** The full-screen pass. */
    @Nullable
    public static ShaderInstance screen() {
        return screen;
    }

    /**
     * Hands a shader the few things about the world it cannot work out for itself: how warm the ground is
     * here, and what the weather is doing to it.
     */
    @Nullable
    private static ShaderInstance feed(@Nullable ShaderInstance shader) {
        if (shader == null) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        float biome = 0.5F;
        float weather = 1.0F;
        float ambient = 0.12F;
        if (level != null && minecraft.getCameraEntity() != null) {
            biome = level.getBiome(minecraft.getCameraEntity().blockPosition()).value().getBaseTemperature();
            weather = 1.0F - level.getRainLevel(1.0F) * 0.35F;
            float normalised = Math.max(0.0F, Math.min(1.0F, (biome + 0.5F) / 2.5F));
            ambient = 0.06F + normalised * 0.10F + (level.isDay() ? 0.04F : 0.0F);
        }
        setFloat(shader, "biomeTemp", biome);
        setFloat(shader, "ambientTemp", ambient);
        setFloat(shader, "weatherMod", weather);
        setFloat(shader, "gameTime",
                level != null ? (float) (level.getGameTime() % 24000L) / 24000.0F : 0.0F);
        setInt(shader, "frameCount", level != null ? (int) (level.getGameTime() % 100000L) : 0);
        setFloat(shader, "entityTemp", 0.92F);
        return shader;
    }

    /**
     * Sets a uniform only if this shader actually declares it, and only through the setter for the type it
     * was declared as.
     */
    private static void setFloat(ShaderInstance shader, String name, float value) {
        com.mojang.blaze3d.shaders.Uniform uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(value);
        }
    }

    private static void setInt(ShaderInstance shader, String name, int value) {
        com.mojang.blaze3d.shaders.Uniform uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(value);
        }
    }

    private ThermalShaders() {
    }
}
