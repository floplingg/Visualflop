package dev.tony.visuals.compat;

import java.lang.reflect.Method;

/** Iris shader pack detection without depending on Iris (works with or without it installed). */
public final class Shaders {
    private Shaders() {}

    private static boolean checked;
    private static Object api;
    private static Method inUse, shadowPass;

    private static void lookup() {
        checked = true;
        try {
            Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api = c.getMethod("getInstance").invoke(null);
            inUse = c.getMethod("isShaderPackInUse");
            shadowPass = c.getMethod("isRenderingShadowPass");
        } catch (Throwable t) {
            api = null;
        }
    }

    /** True while an Iris shader pack is loaded and running. */
    public static boolean active() {
        if (!checked) lookup();
        if (api == null) return false;
        try {
            return (Boolean) inUse.invoke(api);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean shadowPass() {
        if (!checked) lookup();
        if (api == null) return false;
        try {
            return (Boolean) shadowPass.invoke(api);
        } catch (Throwable t) {
            return false;
        }
    }
}
