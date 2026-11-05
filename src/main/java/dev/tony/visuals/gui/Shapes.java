package dev.tony.visuals.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Smooth shapes drawn at the screen's real pixel resolution (e.g. 1920x1080) instead of
 * Minecraft's chunky GUI pixels, with anti-aliased rounded corners.
 * All inputs are normal GUI coordinates; the current pose scale (like the box's size slider) is respected.
 */
public final class Shapes {
    private Shapes() {}

    /** Real pixels per GUI unit at the current pose. */
    private static float pixelScale(GuiGraphics g) {
        float gui = (float) Minecraft.getInstance().getWindow().getGuiScale();
        float pose = Math.abs(g.pose().m00);
        return Math.max(0.25f, gui * (pose == 0 ? 1 : pose));
    }

    /** Rounded rectangle. */
    public static void round(GuiGraphics g, float x, float y, float w, float h, float r, int colour) {
        roundClip(g, x, y, w, h, r, x, y, x + w, y + h, colour);
    }

    /** The part of a rounded rectangle that falls inside the clip box (for accent bars, timer bars...). */
    public static void roundClip(GuiGraphics g, float x, float y, float w, float h, float r,
                                 float cx1, float cy1, float cx2, float cy2, int colour) {
        if (w <= 0 || h <= 0 || (colour >>> 24) == 0) return;
        float k = pixelScale(g);
        g.pose().pushMatrix();
        g.pose().scale(1 / k, 1 / k);

        int X = Math.round(x * k), Y = Math.round(y * k);
        int W = Math.round((x + w) * k) - X, H = Math.round((y + h) * k) - Y;
        float R = Math.min(r * k, Math.min(W, H) / 2f);
        float CX1 = cx1 * k - X, CX2 = cx2 * k - X;
        int rowFrom = Math.max(0, Math.round(cy1 * k) - Y), rowTo = Math.min(H, Math.round(cy2 * k) - Y);
        int corner = (int) Math.ceil(R);

        int row = rowFrom;
        while (row < rowTo) {
            boolean inCorner = row < corner || row >= H - corner;
            if (!inCorner) {
                // Straight middle part in one go.
                int end = Math.min(rowTo, H - corner);
                span(g, X, Y + row, end - row, Math.max(0, CX1), Math.min(W, CX2), colour, false);
                row = end;
                continue;
            }
            float in = inset(row, H, R);
            span(g, X, Y + row, 1, Math.max(in, CX1), Math.min(W - in, CX2), colour, true);
            row++;
        }
        g.pose().popMatrix();
    }

    /** Rounded outline ring of thickness t (GUI units) drawn outside the given box. */
    public static void ring(GuiGraphics g, float x, float y, float w, float h, float r, float t, int colour) {
        if (t <= 0 || (colour >>> 24) == 0) return;
        float k = pixelScale(g);
        g.pose().pushMatrix();
        g.pose().scale(1 / k, 1 / k);

        float T = Math.max(1, t * k);
        int X = Math.round((x - t) * k), Y = Math.round((y - t) * k);
        int W = Math.round((x + w + t) * k) - X, H = Math.round((y + h + t) * k) - Y;
        float Ro = r > 0 ? Math.min(r * k + T, Math.min(W, H) / 2f) : 0;
        float iw = W - 2 * T, ih = H - 2 * T;
        float Ri = r > 0 ? Math.max(0, Math.min(r * k, Math.min(iw, ih) / 2f)) : 0;
        int cornerO = (int) Math.ceil(Ro), top = Math.round(T), bottom = H - Math.round(T);

        int row = 0;
        while (row < H) {
            if (row < top || row >= bottom) {
                float o = inset(row, H, Ro);
                span(g, X, Y + row, 1, o, W - o, colour, true);
                row++;
                continue;
            }
            int ir = row - top;
            boolean straight = row >= cornerO && row < H - cornerO && ir >= Math.ceil(Ri) && ir < Math.round(ih) - Math.ceil(Ri);
            if (straight) {
                int end = Math.min(H - cornerO, top + Math.round(ih) - (int) Math.ceil(Ri));
                end = Math.max(end, row + 1);
                span(g, X, Y + row, end - row, 0, T, colour, false);
                span(g, X, Y + row, end - row, W - T, W, colour, false);
                row = end;
                continue;
            }
            float o = inset(row, H, Ro);
            float i = inset(ir, Math.round(ih), Ri);
            span(g, X, Y + row, 1, o, T + i, colour, true);
            span(g, X, Y + row, 1, W - T - i, W - o, colour, true);
            row++;
        }
        g.pose().popMatrix();
    }

    /** Filled circle. */
    public static void circle(GuiGraphics g, float cx, float cy, float radius, int colour) {
        round(g, cx - radius, cy - radius, radius * 2, radius * 2, radius, colour);
    }

    // ------------------------------------------------------------ internals

    /** How far a (real-pixel) row is pulled in by a corner of radius R, as a fraction of a pixel. */
    private static float inset(int row, int h, float R) {
        if (R <= 0) return 0;
        float yc = row + 0.5f, d;
        if (yc < R) d = R - yc;
        else if (yc > h - R) d = yc - (h - R);
        else return 0;
        return (float) (R - Math.sqrt(Math.max(0, R * R - d * d)));
    }

    /** Horizontal span from a to b (fractional real pixels), soft edges when aa is on. */
    private static void span(GuiGraphics g, int X, int y, int rows, float a, float b, int colour, boolean aa) {
        if (b <= a) return;
        if (!aa) {
            g.fill(X + Math.round(a), y, X + Math.round(b), y + rows, colour);
            return;
        }
        int fa = (int) Math.ceil(a), fb = (int) Math.floor(b);
        if (fa > fb) {
            g.fill(X + (int) Math.floor(a), y, X + (int) Math.floor(a) + 1, y + rows, fade(colour, b - a));
            return;
        }
        if (fa < fb) g.fill(X + fa, y, X + fb, y + rows, colour);
        if (fa - a > 0.01f) g.fill(X + fa - 1, y, X + fa, y + rows, fade(colour, fa - a));
        if (b - fb > 0.01f) g.fill(X + fb, y, X + fb + 1, y + rows, fade(colour, b - fb));
    }

    private static int fade(int colour, float coverage) {
        int a = Math.round(((colour >>> 24) & 255) * Math.max(0, Math.min(1, coverage)));
        return (a << 24) | (colour & 0xFFFFFF);
    }
}
