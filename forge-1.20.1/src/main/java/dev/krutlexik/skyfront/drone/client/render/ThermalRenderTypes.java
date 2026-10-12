package dev.krutlexik.skyfront.drone.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * The render type heat signatures are drawn with: flat colour, no texture, no lighting, and — the point of
 * the whole thing — no depth test, so a warm body reads through the wall it is standing behind exactly as it
 * does through open air.
 */
public abstract class ThermalRenderTypes extends RenderType {
    /** Sized for a crowd of signatures in one batch; the buffer grows if it has to. */
    private static final int BUFFER_SIZE = 1536;

    /** The bodies as drawn into the thermal camera's body mask under a shader pack (see {@code ThermalPackPass}). */
    public static final RenderType HEAT_MASK = create("thermal_heat_mask",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, BUFFER_SIZE, false, false,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    private static final java.util.Map<RenderType, RenderType> MASK_TYPES = new java.util.IdentityHashMap<>();

    /**
     * The render type an entity asked for, redone for the body mask: the same texture, the same vertex
     * format, everything the original sets up — then the mask shader in place of its own, and nothing
     * blended, depth-tested or culled.
     */
    public static RenderType maskTypeOf(RenderType original) {
        return MASK_TYPES.computeIfAbsent(original, type -> new RenderType("thermal_mask",
                type.format(), type.mode(), type.bufferSize(), false, false,
                () -> {
                    type.setupRenderState();
                    RenderSystem.setShader(ThermalShaders::mask);
                    RenderSystem.disableBlend();
                    RenderSystem.disableDepthTest();
                    RenderSystem.disableCull();
                    RenderSystem.colorMask(true, true, true, true);
                },
                type::clearRenderState) {
        });
    }

    /** Dropped on a resource reload: the types it keeps hold the old ones as keys. */
    public static void clearMaskTypes() {
        MASK_TYPES.clear();
    }

    public static final RenderType HEAT = create("thermal_heat",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, BUFFER_SIZE, false, true,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    private ThermalRenderTypes() {
        super("thermal_holder", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
                BUFFER_SIZE, false, false, () -> {
                }, () -> {
                });
        throw new UnsupportedOperationException("holder class");
    }
}
