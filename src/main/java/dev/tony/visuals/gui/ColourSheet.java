package dev.tony.visuals.gui;

import dev.tony.visuals.Theme;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Apple-style colour picker: big preview, swatches, hue / saturation / brightness, hex copy and paste. */
public class ColourSheet extends SheetScreen {
    private static final int[] SWATCHES = {
        0xFF453A, 0xFF9F0A, 0xFFD60A, 0x30D158, 0x63E6E2, 0x64D2FF, 0x0A84FF, 0x5E5CE6,
        0xBF5AF2, 0xFF375F, 0xF9A8D4, 0xFFB020, 0x7DD3FC, 0x6EE7B7, 0xA855F7, 0xAC8E68,
        0xFFFFFF, 0xD1D1D6, 0x8E8E93, 0x48484A, 0x2C2C2E, 0x1C1C1E, 0x111114, 0x000000,
    };

    private final Supplier<Integer> get;
    private final Consumer<Integer> set;
    private final int original;
    private float hue, sat, val;

    public ColourSheet(Screen parent, String name, Supplier<Integer> get, Consumer<Integer> set) {
        super(name, parent);
        this.get = get;
        this.set = set;
        this.original = get.get() & 0xFFFFFF;
        panelW = 330;
        panelH = 330;
        readHsv();
        addPage(name, "appearance", 0x0A84FF, this::build);
    }

    private void readHsv() {
        int c = get.get() & 0xFFFFFF;
        float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        val = max;
        sat = max == 0 ? 0 : d / max;
        if (d == 0) return;   // keep the last hue for greys
        float h;
        if (max == r) h = ((g - b) / d) % 6;
        else if (max == g) h = (b - r) / d + 2;
        else h = (r - g) / d + 4;
        hue = ((h / 6f) % 1 + 1) % 1;
    }

    private void writeHsv() {
        set.accept(Theme.hsv(hue, sat, val));
    }

    private List<Sheet.Item> build() {
        List<Sheet.Item> list = new ArrayList<>();
        list.add(new Sheet.Custom(46, (g, x, y, w, h, mx, my) -> {
            int c = get.get() & 0xFFFFFF;
            Shapes.round(g, x + 10, y + 9, 28, 28, 8, 0xFF000000 | original);
            Shapes.round(g, x + 44, y + 9, w - 54, 28, 8, 0xFF000000 | c);
            String hex = String.format("#%06X", c);
            int tc = Kit.luminance(c) > 0.6f ? 0xFF000000 : 0xFFFFFFFF;
            Kit.textCenter(g, hex, x + 44 + (w - 54) / 2, y + 19, tc, true);
            Kit.textCenter(g, "was", x + 24, y + 19, Kit.luminance(original) > 0.6f ? 0xFF000000 : 0xFFFFFFFF, false);
        }, (x, y, w, h, mx, my) -> {
            if (mx < x + 40) {
                set.accept(original);
                readHsv();
                return true;
            }
            return false;
        }));

        list.add(new Sheet.Header("Swatches"));
        list.add(new Sheet.Custom(64, (g, x, y, w, h, mx, my) -> {
            int per = 12;
            float cell = (w - 20) / per;
            for (int i = 0; i < SWATCHES.length; i++) {
                float cx = x + 10 + (i % per) * cell + cell / 2, cy = y + 14 + (i / per) * 22;
                boolean sel = (get.get() & 0xFFFFFF) == SWATCHES[i];
                if (sel) Shapes.circle(g, cx, cy, 9.5f, Kit.ACCENT);
                Shapes.circle(g, cx, cy, sel ? 7.5f : 8f, Kit.SEPARATOR);
                Shapes.circle(g, cx, cy, sel ? 7f : 7.5f, 0xFF000000 | SWATCHES[i]);
            }
        }, (x, y, w, h, mx, my) -> {
            int per = 12;
            float cell = (w - 20) / per;
            int col = (int) ((mx - x - 10) / cell), row = (int) ((my - y - 3) / 22);
            int i = row * per + col;
            if (col < 0 || col >= per || row < 0 || i >= SWATCHES.length) return false;
            set.accept(SWATCHES[i]);
            readHsv();
            return true;
        }));

        list.add(new Sheet.Header("Adjust"));
        list.add(new Sheet.Slider("Hue", 0, 1, 0.002f, () -> hue, v -> { hue = v; writeHsv(); }, v -> Math.round(v * 360) + "°").rainbow());
        list.add(new Sheet.Slider("Saturation", 0, 1, 0.01f, () -> sat, v -> { sat = v; writeHsv(); }, v -> Math.round(v * 100) + "%"));
        list.add(new Sheet.Slider("Brightness", 0, 1, 0.01f, () -> val, v -> { val = v; writeHsv(); }, v -> Math.round(v * 100) + "%"));

        list.add(new Sheet.Header("Hex"));
        list.add(new Sheet.Action("Copy hex code", () -> String.format("#%06X", get.get() & 0xFFFFFF),
            () -> minecraft.keyboardHandler.setClipboard(String.format("#%06X", get.get() & 0xFFFFFF))).accent());
        list.add(new Sheet.Action("Paste hex code", () -> "", () -> {
            String s = minecraft.keyboardHandler.getClipboard().trim().replace("#", "").toUpperCase(Locale.ROOT);
            if (s.matches("[0-9A-F]{6}")) {
                set.accept(Integer.parseInt(s, 16));
                readHsv();
            }
        }).accent());
        return list;
    }
}
