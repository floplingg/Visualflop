package dev.tony.visuals.hud;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import dev.tony.visuals.Config;
import dev.tony.visuals.render.Pipes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

/**
 * Frosted glass behind HUD cards: once per frame the game picture is copied, and each card draws a blurred, darkened
 * piece of it with smooth rounded corners (one shader pass, no extra world render).
 */
public final class Backdrop {
    private Backdrop() {}

    private static final Identifier ID = Identifier.fromNamespaceAndPath("flopvisuals", "hud/backdrop");

    public static final RenderPipeline PIPELINE = Pipes.register(Pipes.guiTextured("gui_blur", "gui_blur").build());

    static final class Copy extends AbstractTexture {
        Copy() {
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }

        void ensure(int w, int h, com.mojang.renderpearl.api.GpuFormat format) {
            if (texture != null && !texture.isClosed() && texture.getWidth(0) == w && texture.getHeight(0) == h
                && texture.getFormat() == format) return;
            close();
            GpuDevice device = RenderSystem.getDevice();
            texture = device.createTexture(() -> "Flop Visuals HUD backdrop", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                format, w, h, 1, 1);
            textureView = device.createTextureView(texture);
        }
    }

    private static Copy copy;
    private static boolean failed, ready;

    public static void init() {}

    /**
     * Copies the game picture for this frame's cards. Runs at the end of the world pass (the HUD is built before the
     * world is drawn in 26.3, but drawn after it, so the copy is in place when the cards are drawn).
     */
    public static void capture() {
        if (failed || !Config.theme().hudBlur) {
            ready = false;
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (copy == null) {
                copy = new Copy();
                copy.ensure(1, 1, com.mojang.renderpearl.api.GpuFormat.RGBA8_UNORM);
                mc.getTextureManager().register(ID, copy);
            }
            RenderTarget main = mc.gameRenderer.mainRenderTarget();
            GpuTexture c = main.getColorTexture();
            if (c == null) return;
            int w = c.getWidth(0), h = c.getHeight(0);
            copy.ensure(w, h, c.getFormat());
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(c, copy.getTexture(), 0, 0, 0, 0, 0, w, h);
            ready = true;
        } catch (Throwable e) {
            failed = true;
            org.slf4j.LoggerFactory.getLogger("flopvisuals").warn("HUD blur turned off: couldn't copy the frame", e);
        }
    }

    /** No world: nothing to blur. */
    public static void reset() {
        ready = false;
    }

    public static boolean ready() {
        return ready;
    }

    /**
     * Blurred game behind a rounded box. Returns false when there's nothing to blur (then draw a plain card).
     * The shader reads the box's size from the texture coordinates and the corner radius (in pixels) from red.
     */
    public static boolean draw(GuiGraphicsExtractor g, float x, float y, float w, float h, float r) {
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
