package dev.tony.visuals.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.tony.visuals.Config;
import dev.tony.visuals.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** The Flop Visuals menu. Everything previews live and saves automatically. */
public class MainScreen extends SettingsScreen {
    private static final int ROW = 18, GAP = 3;
    private static final String[] TABS = {"Theme", "HUD", "Keystrokes", "Trails", "Petals"};
    private static int tab;

    private final List<int[]> swatches = new ArrayList<>();
    private final List<Supplier<Integer>> swatchColours = new ArrayList<>();
    private boolean waitingForKey;
    private String note = "";

    public MainScreen(Screen parent) {
        super("Flop Visuals", parent);
    }

    @Override
    protected String headerRight() {
        return note.isEmpty() ? "§7saves automatically" : note;
    }

    @Override
    protected void build() {
        swatches.clear();
        swatchColours.clear();
        panel(320, 330);
        int l = px + 8, w = pw - 16, half = (w - 4) / 2;
        int y = py + 27;

        int tw = (w - (TABS.length - 1) * 3) / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            int idx = i;
            int bx = l + i * (tw + 3), bw = i == TABS.length - 1 ? w - i * (tw + 3) : tw;
            add(new Ui.Button(bx, y, bw, ROW, () -> (tab == idx ? "§l" : "§7") + TABS[idx], () -> {
                tab = idx;
                note = "";
                rebuildWidgets();
            }).highlight(() -> tab == idx));
        }
        y += ROW + 7;

        Config c = Config.get();
        Theme t = c.theme;
        switch (tab) {
            case 0 -> {
                y = colour(l, y, w, "Accent colour", () -> t.accentColor, v -> t.accentColor = v);
                add(Ui.Button.toggle(l, y, half, ROW, "Rainbow accent", () -> t.chroma, v -> { t.chroma = v; Config.save(); }));
                slider(Slider.ints(l + half + 4, y, w - half - 4, ROW, "Speed", 1, 10, () -> t.chromaSpeed, v -> t.chromaSpeed = v, ""));
                y += ROW + GAP;
                y = colour(l, y, w, "Background", () -> t.background, v -> t.background = v);
                y = sliderI(l, y, w, "HUD opacity", 0, 100, () -> t.widgetOpacity, v -> t.widgetOpacity = v, "%");
                y = sliderI(l, y, w, "Rounding", 0, 10, () -> t.rounding, v -> t.rounding = v, "");
                add(Ui.Button.toggle(l, y, half, ROW, "Outline", () -> t.outline, v -> { t.outline = v; Config.save(); }));
                slider(Slider.ints(l + half + 4, y, w - half - 4, ROW, "Opacity", 0, 100, () -> t.outlineOpacity, v -> t.outlineOpacity = v, "%"));
                y += ROW + GAP;
                add(Ui.Button.toggle(l, y, half, ROW, "Accent strip", () -> t.accentStrip, v -> { t.accentStrip = v; Config.save(); }));
                add(Ui.Button.toggle(l + half + 4, y, w - half - 4, ROW, "Text shadow", () -> t.textShadow, v -> { t.textShadow = v; Config.save(); }));
                y += ROW + GAP;
                colourBtn(l, y, half, "Text", () -> t.text, v -> t.text = v);
                colourBtn(l + half + 4, y, w - half - 4, "Faded text", () -> t.muted, v -> t.muted = v);
                y += ROW + GAP;
                y = sliderI(l, y, w, "Padding", 2, 10, () -> t.padding, v -> t.padding = v, "px");
                int col = (w - 3 * 6) / 7;
                for (int i = 0; i < Theme.PRESETS.length; i++) {
                    String name = Theme.PRESETS[i];
                    int bx = l + i * (col + 3), bw = i == Theme.PRESETS.length - 1 ? w - i * (col + 3) : col;
                    add(new Ui.Button(bx, y, bw, ROW, () -> name, () -> {
                        c.theme = Theme.preset(name, c.theme);
                        Config.save();
                        note = "§aApplied " + name;
                        rebuildWidgets();
                    }));
                }
                y += ROW + GAP;
            }
            case 1 -> {
                for (int i = 0; i < Config.WIDGETS.length; i++) {
                    String id = Config.WIDGETS[i];
                    Config.Widget wd = c.widget(id);
                    int bx = i % 2 == 0 ? l : l + half + 4, bw = i % 2 == 0 ? half : w - half - 4;
                    add(Ui.Button.toggle(bx, y, bw, ROW, Config.widgetName(id), () -> wd.enabled, v -> { wd.enabled = v; Config.save(); }));
                    if (i % 2 == 1 || i == Config.WIDGETS.length - 1) y += ROW + GAP;
                }
                y += 4;
                add(new Ui.Button(l, y, w, ROW + 2, () -> "§lEdit HUD layout", () -> minecraft.setScreen(new LayoutScreen(this)))
                    .highlight(() -> true));
                y += ROW + 2 + GAP;
                add(new Ui.Button(l, y, w, ROW, () -> "Drag to move · scroll to resize · snaps to edges", () -> {}).display());
                y += ROW + GAP;
            }
            case 2 -> {
                Config.Keystrokes k = c.keystrokes;
                add(Ui.Button.toggle(l, y, w, ROW, "Show keystrokes", () -> c.widget("keystrokes").enabled,
                    v -> { c.widget("keystrokes").enabled = v; Config.save(); }));
                y += ROW + GAP;
                add(Ui.Button.toggle(l, y, half, ROW, "Mouse buttons", () -> k.mouse, v -> { k.mouse = v; Config.save(); }));
                add(Ui.Button.toggle(l + half + 4, y, w - half - 4, ROW, "Show CPS", () -> k.cps, v -> { k.cps = v; Config.save(); }));
                y += ROW + GAP;
                add(Ui.Button.toggle(l, y, half, ROW, "Space bar", () -> k.space, v -> { k.space = v; Config.save(); }));
                add(Ui.Button.toggle(l + half + 4, y, w - half - 4, ROW, "Key backgrounds", () -> k.background, v -> { k.background = v; Config.save(); }));
                y += ROW + GAP;
                y = sliderI(l, y, w, "Key size", 12, 40, () -> k.keySize, v -> k.keySize = v, "px");
                y = sliderI(l, y, w, "Gap", 0, 6, () -> k.gap, v -> k.gap = v, "px");
                y = sliderI(l, y, w, "Press fade speed", 1, 10, () -> k.fade, v -> k.fade = v, "");
                add(new Ui.Button(l, y, w, ROW, () -> "Pressed keys light up in your accent colour", () -> {}).display());
                y += ROW + GAP;
            }
            case 3 -> {
                Config.Trails tr = c.trails;
                add(Ui.Button.toggle(l, y, w, ROW, "Trails", () -> tr.enabled, v -> { tr.enabled = v; Config.save(); }));
                y += ROW + GAP;
                add(cycle(l, y, w, "Style", Config.TrailType.values(), () -> tr.type, v -> tr.type = v));
                y += ROW + GAP;
                y = sliderI(l, y, w, "Density", 1, 10, () -> tr.density, v -> tr.density = v, "");
                y = sliderI(l, y, w, "Spread", 1, 10, () -> tr.spread, v -> tr.spread = v, "");
                add(Ui.Button.toggle(l, y, half, ROW, "Only when moving", () -> tr.onlyWhenMoving, v -> { tr.onlyWhenMoving = v; Config.save(); }));
                add(Ui.Button.toggle(l + half + 4, y, w - half - 4, ROW, "While flying", () -> tr.whileFlying, v -> { tr.whileFlying = v; Config.save(); }));
                y += ROW + GAP;
                add(new Ui.Button(l, y, w, ROW, () -> "Only you see trails. Accent Dust uses your accent colour.", () -> {}).display());
                y += ROW + GAP;
            }
            default -> {
                Config.Petals pe = c.petals;
                add(Ui.Button.toggle(l, y, w, ROW, "Ambience", () -> pe.enabled, v -> { pe.enabled = v; Config.save(); }));
                y += ROW + GAP;
                add(cycle(l, y, w, "Type", Config.PetalType.values(), () -> pe.type, v -> pe.type = v));
                y += ROW + GAP;
                y = sliderI(l, y, w, "Radius", 2, 16, () -> pe.radius, v -> pe.radius = v, " blocks");
                y = sliderI(l, y, w, "Density", 1, 10, () -> pe.density, v -> pe.density = v, "");
                add(Ui.Button.toggle(l, y, w, ROW, "Also indoors / underground", () -> pe.indoors, v -> { pe.indoors = v; Config.save(); }));
                y += ROW + GAP;
                add(new Ui.Button(l, y, w, ROW, () -> "Spawns only in a bubble around you, only you see it", () -> {}).display());
                y += ROW + GAP;
            }
        }

        y += 5;
        add(new Ui.Button(l, y, half, ROW + 2, () -> "Menu key", () -> {
            waitingForKey = true;
            note = "§ePress a key...";
        }).right(() -> waitingForKey ? "§e..." : keyName(c.menuKey)));
        add(new Ui.Button(l + half + 4, y, w - half - 4, ROW + 2, () -> "§lDone", this::onClose).highlight(() -> true));
        ph = y + ROW + 2 + 8 - py;
    }

    // ------------------------------------------------------------ helpers

    private int sliderI(int l, int y, int w, String name, int min, int max, Supplier<Integer> get, Consumer<Integer> set, String suffix) {
        slider(Slider.ints(l, y, w, ROW, name, min, max, get, set, suffix));
        return y + ROW + GAP;
    }

    private int colour(int l, int y, int w, String name, Supplier<Integer> get, Consumer<Integer> set) {
        Ui.Button b = colourBtn(l, y, w, name, get, set);
        b.right(() -> String.format("#%06X", get.get() & 0xFFFFFF) + "     ");
        return y + ROW + GAP;
    }

    private Ui.Button colourBtn(int x, int y, int w, String name, Supplier<Integer> get, Consumer<Integer> set) {
        Ui.Button b = add(new Ui.Button(x, y, w, ROW, () -> name,
            () -> minecraft.setScreen(new ColourScreen(this, name, get, set))).left());
        swatches.add(new int[]{x + w - 20, y + 4});
        swatchColours.add(get);
        return b;
    }

    private <E extends Enum<E>> Ui.Button cycle(int x, int y, int w, String name, E[] values, Supplier<E> get, Consumer<E> set) {
        Ui.Button b = new Ui.Button(x, y, w, ROW, () -> name, () -> {
            set.accept(values[(get.get().ordinal() + 1) % values.length]);
            Config.save();
        });
        return b.right(() -> "‹ " + pretty(get.get().name()) + " ›");
    }

    private static String pretty(String enumName) {
        String n = enumName.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    private static String keyName(int key) {
        if (key <= GLFW.GLFW_KEY_UNKNOWN) return "none";
        return InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!waitingForKey) return super.keyPressed(event);
        int key = event.key();
        if (key != GLFW.GLFW_KEY_ESCAPE) {
            Config.get().menuKey = key;
            Config.save();
            note = "§aMenu key: " + keyName(key);
        } else {
            note = "";
        }
        waitingForKey = false;
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        for (int i = 0; i < swatches.size(); i++) {
            int[] p = swatches.get(i);
            float r = Math.min(3f, Ui.RADIUS);
            Shapes.round(g, p[0] - 0.5f, p[1] - 0.5f, 13, 11, r + 0.5f, 0xFF000000);
            Shapes.round(g, p[0], p[1], 12, 10, r, 0xFF000000 | swatchColours.get(i).get());
        }
    }
}
