package dev.tony.visuals.hud;

import dev.tony.visuals.Config;
import dev.tony.visuals.Theme;
import dev.tony.visuals.gui.Shapes;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** W A S D, mouse buttons with CPS and the space bar, with smooth press fades in the accent colour. */
public final class Keystrokes {
    private Keystrokes() {}

    // W, A, S, D, LMB, RMB, SPACE
    private static final float[] glow = new float[7];
    private static long lastFrame;

    public static int[] size() {
        Config.Keystrokes k = Config.get().keystrokes;
        int s = k.keySize, gap = k.gap;
        int w = s * 3 + gap * 2;
        int h = s * 2 + gap;
        if (k.mouse) h += gap + mouseH(s);
        if (k.space) h += gap + spaceH(s);
        return new int[]{w, h};
    }

    private static int mouseH(int s) { return Math.round(s * 0.85f); }
    private static int spaceH(int s) { return Math.max(8, Math.round(s * 0.5f)); }

    public static void draw(GuiGraphics g, Font font) {
        Minecraft mc = Minecraft.getInstance();
        Config.Keystrokes k = Config.get().keystrokes;
        Theme t = Config.theme();
        int s = k.keySize, gap = k.gap, w = s * 3 + gap * 2;

        // Fade towards pressed / released, framerate independent.
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        float rate = k.fade >= 10 ? 1000 : 4 + k.fade * 3;
        KeyMapping[] keys = {mc.options.keyUp, mc.options.keyLeft, mc.options.keyDown, mc.options.keyRight,
            mc.options.keyAttack, mc.options.keyUse, mc.options.keyJump};
        for (int i = 0; i < keys.length; i++) {
            float target = keys[i].isDown() ? 1 : 0;
            float step = rate * dt;
            glow[i] += Math.max(-step, Math.min(step, target - glow[i]));
        }

        key(g, font, t, k, s + gap, 0, s, s, label(keys[0]), glow[0]);
        int y = s + gap;
        key(g, font, t, k, 0, y, s, s, label(keys[1]), glow[1]);
        key(g, font, t, k, s + gap, y, s, s, label(keys[2]), glow[2]);
        key(g, font, t, k, 2 * (s + gap), y, s, s, label(keys[3]), glow[3]);
        y += s + gap;
        if (k.mouse) {
            int mw = (w - gap) / 2, mh = mouseH(s);
            String l = k.cps ? Stats.cps(true) + " CPS" : "LMB";
            String r = k.cps ? Stats.cps(false) + " CPS" : "RMB";
            key(g, font, t, k, 0, y, mw, mh, l, glow[4]);
            key(g, font, t, k, w - mw, y, mw, mh, r, glow[5]);
            y += mh + gap;
        }
        if (k.space) {
            int sh = spaceH(s);
            key(g, font, t, k, 0, y, w, sh, "", glow[6]);
            // Space bar line.
            float lw = w * 0.35f, ly = y + sh / 2f - 0.5f;
            int c = mix(t.text, t.background, glow[6]);
            Shapes.round(g, (w - lw) / 2f, ly, lw, 1.25f, 0.6f, Theme.opaque(c));
        }
    }

    private static void key(GuiGraphics g, Font font, Theme t, Config.Keystrokes k,
                            float x, float y, float w, float h, String text, float glow) {
        float r = Math.min(t.rounding, Math.min(w, h) / 2);
        if (k.background) {
            if (t.outline && t.outlineOpacity > 0) {
                Shapes.ring(g, x, y, w, h, r, 0.75f, Theme.argb(mix(t.accent(), t.background, 0.45f), t.outlineOpacity));
            }
            if (t.widgetOpacity > 0) Shapes.round(g, x, y, w, h, r, Theme.argb(t.background, t.widgetOpacity));
        }
        if (glow > 0.01f) Shapes.round(g, x, y, w, h, r, Theme.argb(t.accent(), Math.round(glow * 92)));
        if (text.isEmpty()) return;
        int c = mix(t.text, t.background, glow * 0.9f);
        float scale = text.length() > 3 ? Math.min(1f, (w - 4) / Math.max(1f, font.width(text))) : 1f;
        g.pose().pushMatrix();
        g.pose().translate(x + w / 2f, y + h / 2f);
        g.pose().scale(scale, scale);
        g.drawString(font, text, -font.width(text) / 2, -4, Theme.opaque(c), t.textShadow && glow < 0.5f);
        g.pose().popMatrix();
    }

    private static String label(KeyMapping key) {
        String s = key.getTranslatedKeyMessage().getString();
        return s.length() > 3 ? s.substring(0, 3) : s;
    }

    /** Blend a toward b by k (0..1). */
    private static int mix(int a, int b, float k) {
        k = Math.max(0, Math.min(1, k));
        int r = Math.round(((a >> 16) & 255) * (1 - k) + ((b >> 16) & 255) * k);
        int gr = Math.round(((a >> 8) & 255) * (1 - k) + ((b >> 8) & 255) * k);
        int bl = Math.round((a & 255) * (1 - k) + (b & 255) * k);
        return r << 16 | gr << 8 | bl;
    }
}
