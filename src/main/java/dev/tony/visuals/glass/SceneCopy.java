package dev.tony.visuals.glass;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

/**
 * A copy of the frame drawn so far (colour and depth), so the glass ground can look up what is
 * above it on screen and reflect real blocks, players and mobs. One GPU copy per frame, no extra world render.
 */
public final class SceneCopy {
    private SceneCopy() {}

    public static final Identifier COLOUR = Identifier.fromNamespaceAndPath("flopvisuals", "scene/colour");
    public static final Identifier DEPTH = Identifier.fromNamespaceAndPath("flopvisuals", "scene/depth");

    private static Copy colour, depth;
    private static boolean registered, failed, ready;

    /** A texture the copy lands in; resized with the window. */
    static final class Copy extends AbstractTexture {
        private final String label;
        private final TextureFormat format;

        Copy(String label, TextureFormat format, FilterMode filter) {
            this.label = label;
            this.format = format;
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(filter);
        }

        void ensure(int w, int h) {
            if (texture != null && !texture.isClosed() && texture.getWidth(0) == w && texture.getHeight(0) == h) return;
            close();
            GpuDevice device = RenderSystem.getDevice();
            texture = device.createTexture(() -> label, GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, format, w, h, 1, 1);
            textureView = device.createTextureView(texture);
        }
    }

    public static GpuSampler linear() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    }

    public static GpuSampler nearest() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    }

    /** Makes sure the textures exist (the glass shader always samples them, even when the copy is off). */
    public static void ensureRegistered() {
        if (registered) return;
        Minecraft mc = Minecraft.getInstance();
        colour = new Copy("Flop Visuals scene colour", TextureFormat.RGBA8, FilterMode.LINEAR);
        depth = new Copy("Flop Visuals scene depth", TextureFormat.DEPTH32, FilterMode.NEAREST);
        colour.ensure(1, 1);
        depth.ensure(1, 1);
        mc.getTextureManager().register(COLOUR, colour);
        mc.getTextureManager().register(DEPTH, depth);
        registered = true;
    }

    /** True when this frame's copy is usable for reflections. */
    public static boolean ready() {
        return ready && !failed;
    }

    /** Copies the main frame as drawn so far. */
    public static boolean capture() {
        ready = false;
        if (failed) return false;
        try {
            ensureRegistered();
            Minecraft mc = Minecraft.getInstance();
            RenderTarget main = mc.getMainRenderTarget();
            GpuTexture c = main.getColorTexture(), d = main.getDepthTexture();
            if (c == null || d == null) return false;
            int w = c.getWidth(0), h = c.getHeight(0);
            colour.ensure(w, h);
            depth.ensure(w, h);
            var encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.copyTextureToTexture(c, colour.getTexture(), 0, 0, 0, 0, 0, w, h);
            encoder.copyTextureToTexture(d, depth.getTexture(), 0, 0, 0, 0, 0, w, h);
            ready = true;
            return true;
        } catch (Throwable e) {
            failed = true;
            org.slf4j.LoggerFactory.getLogger("flopvisuals").warn("Glass reflections turned off: couldn't copy the frame", e);
            return false;
        }
    }

    public static boolean failed() {
        return failed;
    }
}
