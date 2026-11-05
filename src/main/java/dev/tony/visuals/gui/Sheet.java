package dev.tony.visuals.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** The rows a settings page is made of. Rows between headers are grouped into one rounded card. */
public final class Sheet {
    private Sheet() {}

    public abstract static class Item {
        /** Layout, filled in by the screen. */
        float y, h;
        float hover;

        abstract float height();

        boolean grouped() { return true; }

        boolean clickable() { return false; }
    }

    public static final class Header extends Item {
        final String text;
        public Header(String text) { this.text = text; }
        float height() { return 20; }
        boolean grouped() { return false; }
    }

    public static final class Footer extends Item {
        final String text;
        public Footer(String text) { this.text = text; }
        float height() { return 16; }
        boolean grouped() { return false; }
    }

    public static final class Toggle extends Item {
        final String label;
        final Supplier<Boolean> get;
        final Consumer<Boolean> set;
        float anim = -1;
        public Toggle(String label, Supplier<Boolean> get, Consumer<Boolean> set) { this.label = label; this.get = get; this.set = set; }
        float height() { return 26; }
        boolean clickable() { return true; }
    }

    public static final class Slider extends Item {
        final String label;
        final float min, max, step;
        final Supplier<Float> get;
        final Consumer<Float> set;
        final Function<Float, String> format;
        int fill;
        boolean rainbow;
        public Slider(String label, float min, float max, float step, Supplier<Float> get, Consumer<Float> set, Function<Float, String> format) {
            this.label = label; this.min = min; this.max = max; this.step = step; this.get = get; this.set = set; this.format = format;
        }
        public static Slider ints(String label, int min, int max, Supplier<Integer> get, Consumer<Integer> set, String suffix) {
            return new Slider(label, min, max, 1, () -> (float) get.get(), v -> set.accept(Math.round(v)), v -> Math.round(v) + suffix);
        }
        public Slider rainbow() { this.rainbow = true; return this; }
        public Slider fill(int colour) { this.fill = colour; return this; }
        float height() { return 38; }
        boolean clickable() { return true; }
    }

    public static final class Choice<E extends Enum<E>> extends Item {
        final String label;
        final E[] values;
        final Supplier<E> get;
        final Consumer<E> set;
        public Choice(String label, E[] values, Supplier<E> get, Consumer<E> set) { this.label = label; this.values = values; this.get = get; this.set = set; }
        float height() { return 26; }
        boolean clickable() { return true; }
        void step(int dir) {
            int n = values.length;
            set.accept(values[((get.get().ordinal() + dir) % n + n) % n]);
        }
        String valueName() {
            String s = get.get().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
            return Character.toUpperCase(s.charAt(0)) + s.substring(1);
        }
    }

    public static final class Colour extends Item {
        final String label;
        final Supplier<Integer> get;
        final Consumer<Integer> set;
        public Colour(String label, Supplier<Integer> get, Consumer<Integer> set) { this.label = label; this.get = get; this.set = set; }
        float height() { return 26; }
        boolean clickable() { return true; }
    }

    public static final class Action extends Item {
        final String label;
        final Supplier<String> value;
        final Runnable run;
        boolean destructive, accent, chevron;
        public Action(String label, Supplier<String> value, Runnable run) { this.label = label; this.value = value; this.run = run; }
        public Action destructive() { destructive = true; return this; }
        public Action accent() { accent = true; return this; }
        public Action chevron() { chevron = true; return this; }
        float height() { return 26; }
        boolean clickable() { return run != null; }
    }

    public static final class Info extends Item {
        final String label;
        final Supplier<String> value;
        public Info(String label, Supplier<String> value) { this.label = label; this.value = value; }
        float height() { return 26; }
    }

    /** A wrapping row of chips (presets, wallpapers...). */
    public static final class Chips extends Item {
        final List<String> names;
        final Supplier<String> selected;
        final Consumer<String> pick;
        final List<float[]> boxes = new ArrayList<>();
        int rows = 1;
        public Chips(List<String> names, Supplier<String> selected, Consumer<String> pick) { this.names = names; this.selected = selected; this.pick = pick; }
        float height() { return 10 + rows * 22; }
        boolean clickable() { return true; }
    }

    /** Custom-drawn row (colour previews, swatch grids...). */
    public static final class Custom extends Item {
        public interface Painter { void paint(net.minecraft.client.gui.GuiGraphics g, float x, float y, float w, float h, int mouseX, int mouseY); }
        public interface Clicker { boolean click(float x, float y, float w, float h, double mx, double my); }
        final float height;
        final Painter painter;
        final Clicker clicker;
        public Custom(float height, Painter painter, Clicker clicker) { this.height = height; this.painter = painter; this.clicker = clicker; }
        float height() { return height; }
        boolean clickable() { return clicker != null; }
    }
}
