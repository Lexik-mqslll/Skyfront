package dev.krutlexik.skyfront.drone.client.render;

import dev.krutlexik.skyfront.drone.DroneMod;

import java.lang.reflect.Method;

/** What Iris is doing, asked through its public API by reflection so the mod neither needs Iris to build nor to run. */
public final class IrisCompat {
    private static boolean looked;
    private static Object api;
    private static Method shaderPackInUse;
    private static Method renderingShadowPass;

    private static void lookUp() {
        looked = true;
        try {
            Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api = type.getMethod("getInstance").invoke(null);
            shaderPackInUse = type.getMethod("isShaderPackInUse");
            renderingShadowPass = type.getMethod("isRenderingShadowPass");
            DroneMod.LOGGER.info("Iris found: the thermal camera will follow its shader packs");
        } catch (ClassNotFoundException absent) {
            api = null;
        } catch (ReflectiveOperationException | LinkageError failure) {
            DroneMod.LOGGER.warn("Iris is installed but its API could not be read; treating shaders as off",
                    failure);
            api = null;
        }
    }

    /** Whether Iris is drawing the world with a shader pack right now. */
    public static boolean shaderPackInUse() {
        return ask(shaderPackInUse);
    }

    /** Whether the frame being drawn is Iris's shadow pass rather than the picture itself. */
    public static boolean renderingShadowPass() {
        return ask(renderingShadowPass);
    }

    private static boolean ask(Method method) {
        if (!looked) {
            lookUp();
        }
        if (api == null || method == null) {
            return false;
        }
        try {
            return (Boolean) method.invoke(api);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return false;
        }
    }

    private IrisCompat() {
    }
}
