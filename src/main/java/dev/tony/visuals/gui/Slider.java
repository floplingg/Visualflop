package dev.tony.visuals.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** A full-width row: name on the left, a draggable track and the value on the right. */
public final class Slider {
    public final int x, y, w, h;
    private final String name;
    private final float min, max, step;
    private final Supplier<Float> get;
    private final Consumer<Float> set;
    private final Function<Float, String> format;
    public int fill = Ui.PURPLE;

    public Slider(int x, int y, int w, int h, String name, float min, float max, float step,
                  Supplier<Float> get, Consumer<Float> set, Function<Float, String> format) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        this.name = name;
        this.min = min; this.max = max; this.step = step;
        this.get = get; this.set = set; this.format = format;
    }

    public static Slider ints(int x, int y, int w, int h, String name, int min, int max,
                              Supplier<Integer> get, Consumer<Integer> set, String suffix) {
        return new Slider(x, y, w, h, name, min, max, 1, () -> (float) get.get(), v -> set.accept(Math.round(v)),
            v -> Math.round(v) + suffix);
    }

    private int trackL() { return x + Math.max(90, w * 2 / 5); }
    private int trackR() { return x + w - 46; }

    public boolean inside(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public void setFromMouse(double mx) {
        float t = (float) ((mx - trackL()) / Math.max(1, trackR() - trackL()));
        t = Math.max(0, Math.min(1, t));
        float v = min + (max - min) * t;
        v = Math.round(v / step) * step;
        set.accept(Math.max(min, Math.min(max, v)));
    }

    /** Mouse wheel nudges by one step. */
    public void nudge(int dir) {
        set.accept(Math.max(min, Math.min(max, get.get() + dir * step)));
    }

    public void render(GuiGraphics g, Font font, int mx, int my, boolean dragging) {
        boolean hover = inside(mx, my) || dragging;
        Ui.box(g, x, y, w, h, hover ? Ui.BUTTON_HOVER : Ui.BUTTON, hover ? Ui.PURPLE : Ui.BORDER);
        int ty = y + (h - 8) / 2;
        g.drawString(font, name, x + 7, ty, Ui.TEXT, false);

        int l = trackL(), r = trackR(), cy = y + h / 2;
        float t = (get.get() - min) / Math.max(0.0001f, max - min);
        float knob = l + (r - l) * Math.max(0, Math.min(1, t));
        Shapes.round(g, l, cy - 1.25f, r - l, 2.5f, 1.25f, Ui.OFF);
        Shapes.round(g, l, cy - 1.25f, knob - l, 2.5f, 1.25f, fill);
        Shapes.circle(g, knob, cy, hover ? 4f : 3.5f, hover ? 0xFFFFFFFF : 0xFFE5E5E5);

        String v = format.apply(get.get());
        g.drawString(font, v, x + w - 7 - font.width(v), ty, Ui.PURPLE_SOFT, false);
    }
}
