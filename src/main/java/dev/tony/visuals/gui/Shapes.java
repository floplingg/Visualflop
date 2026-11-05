package dev.tony.visuals.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Smooth rounded shapes at the screen's real pixel resolution (e.g. 1080p).
 *
 * Fast: each anti-aliased corner is a small cached texture (made once, reused forever),
 * so a rounded box is 4 corner quads + 3 flat rectangles, about 7 draws, no matter how big.
 * All inputs are normal GUI coordinates and the current pose scale is respected.
 */
public final class Shapes {
    private Shapes() {}

    private static final Map<Long, Identifier> TEXTURES = new HashMap<>();

    private static float pixelScale(GuiGraphics g) {
        float gui = (float) Minecraft.getInstance().getWindow().getGuiScale();
        float pose = Math.abs(g.pose().m00);
        return Math.max(0.25f, gui * (pose == 0 ? 1 : pose));
    }

    // ------------------------------------------------------------ public shapes

    public static void round(GuiGraphics g, float x, float y, float w, float h, float r, int colour) {
        roundClip(g, x, y, w, h, r, -1e9f, -1e9f, 1e9f, 1e9f, colour);
    }

    public static void circle(GuiGraphics g, float cx, float cy, float radius, int colour) {
        round(g, cx - radius, cy - radius, radius * 2, radius * 2, radius, colour);
    }

    /** The part of a rounded box inside a clip rectangle (for accent strips and bars). */
    public static void roundClip(GuiGraphics g, float x, float y, float w, float h, float r,
                                 float cx1, float cy1, float cx2, float cy2, int colour) {
        if (w <= 0 || h <= 0 || (colour >>> 24) == 0) return;
        float k = pixelScale(g);
        int X = Math.round(x * k), Y = Math.round(y * k);
        int W = Math.round((x + w) * k) - X, H = Math.round((y + h) * k) - Y;
        if (W <= 0 || H <= 0) return;
        int R = Math.round(Math.min(r * k, Math.min(W, H) / 2f));
        Clip clip = new Clip(Math.round(cx1 * k), Math.round(cy1 * k), Math.round(cx2 * k), Math.round(cy2 * k));

        g.pose().pushMatrix();
        g.pose().scale(1 / k, 1 / k);
        if (R <= 0) {
            clip.fill(g, X, Y, X + W, Y + H, colour);
        } else {
            Identifier tex = texture(R, 0);
            corners(g, tex, X, Y, W, H, R, clip, colour);
            clip.fill(g, X + R, Y, X + W - R, Y + H, colour);
            clip.fill(g, X, Y + R, X + R, Y + H - R, colour);
            clip.fill(g, X + W - R, Y + R, X + W, Y + H - R, colour);
        }
        g.pose().popMatrix();
    }

    /** Outline of thickness t drawn just outside the box. */
    public static void ring(GuiGraphics g, float x, float y, float w, float h, float r, float t, int colour) {
        if (t <= 0 || (colour >>> 24) == 0) return;
        float k = pixelScale(g);
        int T = Math.max(1, Math.round(t * k));
        int X = Math.round(x * k) - T, Y = Math.round(y * k) - T;
        int W = Math.round((x + w) * k) + T - X, H = Math.round((y + h) * k) + T - Y;
        int R = r > 0 ? Math.round(Math.min(r * k + T, Math.min(W, H) / 2f)) : 0;
        Clip none = Clip.NONE;

        g.pose().pushMatrix();
        g.pose().scale(1 / k, 1 / k);
        if (R <= T) {
            none.fill(g, X, Y, X + W, Y + T, colour);
            none.fill(g, X, Y + H - T, X + W, Y + H, colour);
            none.fill(g, X, Y + T, X + T, Y + H - T, colour);
            none.fill(g, X + W - T, Y + T, X + W, Y + H - T, colour);
        } else {
            corners(g, texture(R, T), X, Y, W, H, R, none, colour);
            none.fill(g, X + R, Y, X + W - R, Y + T, colour);
            none.fill(g, X + R, Y + H - T, X + W - R, Y + H, colour);
            none.fill(g, X, Y + R, X + T, Y + H - R, colour);
            none.fill(g, X + W - T, Y + R, X + W, Y + H - R, colour);
        }
        g.pose().popMatrix();
    }

    // ------------------------------------------------------------ internals

    private record Clip(int x1, int y1, int x2, int y2) {
        static final Clip NONE = new Clip(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);

        void fill(GuiGraphics g, int ax, int ay, int bx, int by, int colour) {
            ax = Math.max(ax, x1); ay = Math.max(ay, y1);
            bx = Math.min(bx, x2); by = Math.min(by, y2);
            if (bx > ax && by > ay) g.fill(ax, ay, bx, by, colour);
        }

        /** Textured R x R corner piece, cut to the clip. (u, v) is the corner's spot in the 2R texture. */
        void blit(GuiGraphics g, Identifier tex, int ax, int ay, int size, int u, int v, int texSize, int colour) {
            int bx = ax + size, by = ay + size;
            int cx = Math.max(ax, x1), cy = Math.max(ay, y1);
            int dx = Math.min(bx, x2), dy = Math.min(by, y2);
            if (dx <= cx || dy <= cy) return;
            g.blit(RenderPipelines.GUI_TEXTURED, tex, cx, cy, u + (cx - ax), v + (cy - ay), dx - cx, dy - cy, texSize, texSize, colour);
        }
    }

    private static void corners(GuiGraphics g, Identifier tex, int X, int Y, int W, int H, int R, Clip clip, int colour) {
        int size = R * 2;
        clip.blit(g, tex, X, Y, R, 0, 0, size, colour);
        clip.blit(g, tex, X + W - R, Y, R, R, 0, size, colour);
        clip.blit(g, tex, X, Y + H - R, R, 0, R, size, colour);
        clip.blit(g, tex, X + W - R, Y + H - R, R, R, R, size, colour);
    }

    /**
     * White anti-aliased circle (thickness 0) or ring of the given thickness, 2R x 2R pixels.
     * Tinted by the draw colour. Made once per size.
     */
    private static Identifier texture(int R, int thickness) {
        long key = ((long) R << 32) | thickness;
        Identifier id = TEXTURES.get(key);
        if (id != null) return id;

        int size = R * 2;
        NativeImage img = new NativeImage(size, size, true);
        float inner = thickness > 0 ? R - thickness : -1;
        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                float dx = px + 0.5f - R, dy = py + 0.5f - R;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float a = clamp01(R - d + 0.5f);
                if (inner >= 0) a = Math.min(a, clamp01(d - inner + 0.5f));
                int alpha = Math.round(a * 255);
                img.setPixel(px, py, (alpha << 24) | 0xFFFFFF);
            }
        }
        id = Identifier.fromNamespaceAndPath("flopvisuals", "shape/" + R + "_" + thickness);
        DynamicTexture texture = new DynamicTexture(() -> "flopvisuals shape " + R + "_" + thickness, img);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        TEXTURES.put(key, id);
        return id;
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }
}
