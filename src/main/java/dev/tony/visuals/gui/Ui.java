package dev.tony.visuals.gui;

import dev.tony.visuals.Config;
import dev.tony.visuals.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Menu theme: colours (follow your Customize settings, or the original purple) plus
 * smooth rounded drawing helpers and a custom-drawn button.
 */
public final class Ui {
    private Ui() {}

    public static int DIM = 0xB8000000;
    public static int PANEL = 0xF20A090D;
    public static int HEADER_TOP = 0xFF2B0F4A;
    public static int HEADER_BOTTOM = 0xFF120A1C;
    public static int BUTTON = 0xFF15111B;
    public static int BUTTON_HOVER = 0xFF241833;
    public static int BORDER = 0xFF2C2238;
    public static int PURPLE = 0xFFA855F7;
    public static int PURPLE_DEEP = 0xFF6D28D9;
    public static int PURPLE_SOFT = 0xFFE9D5FF;
    public static int TEXT = 0xFFEDE7F6;
    public static int MUTED = 0xFF8B7F9E;
    public static int OFF = 0xFF2A2830;
    public static int KNOB_OFF = 0xFF6B6774;
    public static float RADIUS = 4;

    /** Re-reads the theme from your settings. Cheap; called every frame a menu draws. */
    public static void applyTheme() {
        Theme t = Config.theme();
        RADIUS = t.rounding;
        int bg = t.background & 0xFFFFFF, accent = t.accent() & 0xFFFFFF;
        PANEL = 0xF2000000 | bg;
        HEADER_TOP = 0xFF000000 | mix(bg, accent, 0.38f);
        HEADER_BOTTOM = 0xFF000000 | mix(bg, accent, 0.10f);
        BUTTON = 0xFF000000 | mix(bg, 0xFFFFFF, 0.05f);
        BUTTON_HOVER = 0xFF000000 | mix(bg, accent, 0.20f);
        BORDER = 0xFF000000 | mix(bg, accent, 0.28f);
        PURPLE = 0xFF000000 | accent;
        PURPLE_DEEP = 0xFF000000 | mix(accent, 0x000000, 0.35f);
        PURPLE_SOFT = 0xFF000000 | mix(accent, 0xFFFFFF, 0.70f);
        TEXT = 0xFF000000 | (t.text & 0xFFFFFF);
        MUTED = 0xFF000000 | (t.muted & 0xFFFFFF);
        OFF = 0xFF000000 | mix(bg, 0xFFFFFF, 0.14f);
        KNOB_OFF = 0xFF000000 | mix(bg, 0xFFFFFF, 0.40f);
    }

    /** Blend a toward b by t (0..1). RGB only. */
    public static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int gr = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | gr << 8 | bl;
    }

    /** Square 1px outline (used where sharp edges are wanted). */
    public static void outline(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y + 1, x + 1, y + h - 1, c);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, c);
    }

    /** Smooth rounded box with a thin border, using the menu rounding. */
    public static void box(GuiGraphics g, float x, float y, float w, float h, int fill, int border) {
        float r = Math.min(RADIUS, Math.min(w, h) / 2);
        if ((border >>> 24) != 0) Shapes.ring(g, x + 0.5f, y + 0.5f, w - 1, h - 1, Math.max(0, r - 0.5f), 0.5f, border);
        Shapes.round(g, x + 0.5f, y + 0.5f, w - 1, h - 1, Math.max(0, r - 0.5f), fill);
    }

    /** Panel with a gradient header bar and outline, rounded and smooth. */
    public static void panel(GuiGraphics g, Font font, int x, int y, int w, int h, String title, String right) {
        float r = RADIUS + 2;
        Shapes.ring(g, x, y, w, h, r, 1, PURPLE_DEEP);
        Shapes.round(g, x, y, w, h, r, PANEL);
        int top = (int) Math.ceil(r);
        Shapes.roundClip(g, x, y, w, h, r, x, y, x + w, y + top, HEADER_TOP);
        g.fillGradient(x, y + top, x + w, y + 20, HEADER_TOP, HEADER_BOTTOM);
        Shapes.roundClip(g, x, y, w, h, r, x, y + 20, x + w, y + 21, PURPLE);
        g.drawString(font, "§l" + title, x + 8, y + 6, PURPLE_SOFT, false);
        if (right != null && !right.isEmpty()) {
            g.drawString(font, right, x + w - 8 - font.width(right), y + 6, MUTED, false);
        }
    }

    public static void section(GuiGraphics g, Font font, int x, int y, int w, String label) {
        g.drawString(font, label, x, y, PURPLE, false);
        int lx = x + font.width(label) + 6;
        Shapes.round(g, lx, y + 4, x + w - lx, 0.75f, 0.375f, BORDER);
    }

    /** Pill switch drawn at the right edge of a row. */
    public static void pill(GuiGraphics g, int right, int cy, boolean on) {
        float x2 = right - 6, x1 = x2 - 18;
        Shapes.round(g, x1, cy - 4.5f, 18, 9, 4.5f, on ? PURPLE : OFF);
        float kx = on ? x2 - 4.5f : x1 + 4.5f;
        Shapes.circle(g, kx, cy, 3.25f, on ? 0xFFFFFFFF : KNOB_OFF);
    }

    public static final class Button {
        public int x, y, w, h;
        public Supplier<String> label;
        public Supplier<String> right;
        public Supplier<Boolean> toggle;
        public Runnable onClick;
        public int accent;
        public boolean enabled = true;
        public boolean centered = true;
        public Supplier<Boolean> highlight;

        public Button(int x, int y, int w, int h, Supplier<String> label, Runnable onClick) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.label = label;
            this.onClick = onClick;
        }

        public static Button toggle(int x, int y, int w, int h, String name, Supplier<Boolean> get, Consumer<Boolean> set) {
            Button b = new Button(x, y, w, h, () -> name, () -> set.accept(!get.get()));
            b.toggle = get;
            b.centered = false;
            return b;
        }

        public Button accent(int colour) { this.accent = colour; this.centered = false; return this; }
        public Button right(Supplier<String> r) { this.right = r; this.centered = false; return this; }
        public Button left() { this.centered = false; return this; }
        public Button display() { this.enabled = false; this.centered = false; return this; }
        public Button highlight(Supplier<Boolean> h) { this.highlight = h; return this; }

        public boolean inside(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        public void render(GuiGraphics g, Font font, int mx, int my) {
            boolean hover = enabled && inside(mx, my);
            boolean on = toggle != null && toggle.get();
            boolean lit = highlight != null && highlight.get();

            int fill = !enabled ? PANEL : hover ? BUTTON_HOVER : BUTTON;
            int border = hover || lit ? PURPLE : enabled ? BORDER : (BORDER & 0x00FFFFFF) | 0x60000000;
            box(g, x, y, w, h, fill, border);
            if (on || lit) {
                float r = Math.min(RADIUS, h / 2f);
                Shapes.roundClip(g, x + 0.5f, y + 0.5f, w - 1, h - 1, Math.max(0, r - 0.5f), x, y, x + 2.5f, y + h, PURPLE);
            }

            int ty = y + (h - 8) / 2;
            int tx = x + 7;
            if (accent != 0) {
                Shapes.circle(g, tx + 2, y + h / 2f, 2.5f, accent);
                tx += 9;
            }

            String text = label.get();
            int colour = !enabled ? MUTED : TEXT;
            if (centered) g.drawString(font, text, x + (w - font.width(text)) / 2, ty, colour, false);
            else g.drawString(font, text, tx, ty, toggle != null && !on ? MUTED : colour, false);

            if (toggle != null) pill(g, x + w, y + h / 2, on);
            else if (right != null) {
                String r = right.get();
                g.drawString(font, r, x + w - 7 - font.width(r), ty, lit ? PURPLE_SOFT : PURPLE, false);
            }
        }
    }
}
