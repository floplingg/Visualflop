package dev.tony.visuals.hud;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.tony.visuals.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

/**
 * Frosted glass behind HUD cards: once per frame the game picture is copied, and each card draws a blurred, darkened
 * piece of it with smooth rounded corners (one shader pass, no extra world render).
 */
public final class Backdrop {
    private Backdrop() {}

    private static final Identifier ID = Identifier.fromNamespaceAndPath("flopvisuals", "hud/backdrop");

    public static final RenderPipeline PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("flopvisuals", "pipeline/gui_blur"))
            .withVertexShader(Identifier.fromNamespaceAndPath("flopvisuals", "core/gui_blur"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("flopvisuals", "core/gui_blur"))
            .withSampler("Sampler0")
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .build());

    static final class Copy extends AbstractTexture {
        Copy() {
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }

        void ensure(int w, int h) {
            if (texture != null && !texture.isClosed() && texture.getWidth(0) == w && texture.getHeight(0) == h) return;
            close();
            GpuDevice device = RenderSystem.getDevice();
            texture = device.createTexture(() -> "Flop Visuals HUD backdrop", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                TextureFormat.RGBA8, w, h, 1, 1);
            textureView = device.createTextureView(texture);
        }
    }

    private static Copy copy;
    private static boolean failed, ready;

    public static void init() {}

    /** Copies the game picture for this frame's cards. Call before drawing the HUD. */
    public static void capture() {
        ready = false;
        if (failed || !Config.theme().hudBlur) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (copy == null) {
                copy = new Copy();
                copy.ensure(1, 1);
                mc.getTextureManager().register(ID, copy);
            }
            RenderTarget main = mc.getMainRenderTarget();
            GpuTexture c = main.getColorTexture();
            if (c == null) return;
            int w = c.getWidth(0), h = c.getHeight(0);
            copy.ensure(w, h);
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(c, copy.getTexture(), 0, 0, 0, 0, 0, w, h);
            ready = true;
        } catch (Throwable e) {
            failed = true;
            org.slf4j.LoggerFactory.getLogger("flopvisuals").warn("HUD blur turned off: couldn't copy the frame", e);
        }
    }

    public static boolean ready() {
        return ready;
    }

    /**
     * Blurred game behind a rounded box. Returns false when there's nothing to blur (then draw a plain card).
     * The shader reads the box's size from the texture coordinates and the corner radius (in pixels) from red.
     */
    public static boolean draw(GuiGraphics g, float x, float y, float w, float h, float r) {
        if (!ready || w <= 0 || h <= 0) return false;
        float gui = (float) Minecraft.getInstance().getWindow().getGuiScale();
        float pose = Math.abs(g.pose().m00);
        float k = Math.max(0.25f, gui * (pose == 0 ? 1 : pose));
        int X = Math.round(x * k), Y = Math.round(y * k);
        int W = Math.round((x + w) * k) - X, H = Math.round((y + h) * k) - Y;
        if (W <= 0 || H <= 0) return false;
        int R = Math.max(0, Math.min(255, Math.round(Math.min(r * k, Math.min(W, H) / 2f))));
        int strength = Math.max(0, Math.min(100, Config.theme().blurStrength));
        int colour = 0xFF000000 | (R << 16) | (Math.round(strength * 2.55f) << 8);
        g.pose().pushMatrix();
        g.pose().scale(1 / k, 1 / k);
        g.blit(PIPELINE, ID, X, Y, 0f, 0f, W, H, W, H, colour);
        g.pose().popMatrix();
        return true;
    }
}
