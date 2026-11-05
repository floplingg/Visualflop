package dev.tony.visuals.gui;

import dev.tony.visuals.Config;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Pick a colour: swatches, RGB sliders, hex code and (optionally) opacity. Changes show live. */
public class ColourScreen extends SettingsScreen {
    private static final int ROW = 18, GAP = 3;
    private static final int[] SWATCHES = {
        0xFFFFFF, 0xD4D4D4, 0x8A8A8A, 0x404040, 0x1A1A1A, 0x000000, 0x0A090D, 0x2C2238,
        0xEF4444, 0xF97316, 0xF59E0B, 0xFFD21F, 0xFDE047, 0xA3E635, 0x55FF55, 0x10B981,
        0x14B8A6, 0x22D3EE, 0x0EA5E9, 0x3B82F6, 0x6366F1, 0x7C3AED, 0xA855F7, 0xC084FC,
        0xD946EF, 0xEC4899, 0xF472B6, 0xF9A8D4, 0xFFC400, 0xB8860B, 0x7DD3FC, 0x6D28D9,
    };

    private final Supplier<Integer> get;
    private final Consumer<Integer> set;
    private final Supplier<Integer> getOpacity;
    private final Consumer<Integer> setOpacity;
    private final int original, originalOpacity;
    private EditBox hex;
    private int previewY, swatchY, hexY;
    private boolean typing;

    public ColourScreen(Screen parent, String name, Supplier<Integer> get, Consumer<Integer> set,
                        Supplier<Integer> getOpacity, Consumer<Integer> setOpacity) {
        super(name, parent);
        this.get = get;
        this.set = set;
        this.getOpacity = getOpacity;
        this.setOpacity = setOpacity;
        this.original = get.get();
        this.originalOpacity = getOpacity == null ? 100 : getOpacity.get();
    }

    public ColourScreen(Screen parent, String name, Supplier<Integer> get, Consumer<Integer> set) {
        this(parent, name, get, set, null, null);
    }

    @Override
    protected void build() {
        panel(280, 300);
        int l = px + 8, w = pw - 16;
        int y = py + 27;

        previewY = y;
        y += 26;

        swatchY = y;
        int per = 8, sw = (w - (per - 1) * 3) / per;
        for (int i = 0; i < SWATCHES.length; i++) {
            int c = SWATCHES[i];
            int bx = l + (i % per) * (sw + 3), by = y + (i / per) * 15;
            add(new Ui.Button(bx, by, sw, 12, () -> "", () -> pick(c)).highlight(() -> (get.get() & 0xFFFFFF) == c));
        }
        y += (SWATCHES.length + per - 1) / per * 15 + 5;

        slider(Slider.ints(l, y, w, ROW, "§cRed", 0, 255, () -> (get.get() >> 16) & 255, v -> setChannel(16, v), "")).fill = 0xFFEF4444;
        y += ROW + GAP;
        slider(Slider.ints(l, y, w, ROW, "§aGreen", 0, 255, () -> (get.get() >> 8) & 255, v -> setChannel(8, v), "")).fill = 0xFF22C55E;
        y += ROW + GAP;
        slider(Slider.ints(l, y, w, ROW, "§9Blue", 0, 255, () -> get.get() & 255, v -> setChannel(0, v), "")).fill = 0xFF3B82F6;
        y += ROW + GAP;
        if (getOpacity != null) {
            slider(Slider.ints(l, y, w, ROW, "Opacity", 0, 100, getOpacity, setOpacity, "%"));
            y += ROW + GAP;
        }

        y += 3;
        hexY = y;
        String current = hexOf(get.get());
        hex = new EditBox(font, l + 36, y + 5, w - 36 - 12, 12, Component.literal("Hex"));
        hex.setBordered(false);
        hex.setTextColor(Ui.TEXT);
        hex.setMaxLength(7);
        hex.setValue(current);
        hex.setResponder(v -> {
            String s = v.trim().replace("#", "");
            if (s.matches("[0-9a-fA-F]{6}")) {
                typing = true;
                set.accept(Integer.parseInt(s, 16));
                typing = false;
            }
        });
        addRenderableWidget(hex);
        y += ROW + 8;

        int third = (w - 8) / 3;
        add(new Ui.Button(l, y, third, ROW, () -> "Undo", () -> {
            set.accept(original);
            if (setOpacity != null) setOpacity.accept(originalOpacity);
            syncHex();
        }));
        add(new Ui.Button(l + third + 4, y, third, ROW, () -> "Copy hex", () -> minecraft.keyboardHandler.setClipboard(hexOf(get.get()))));
        add(new Ui.Button(l + 2 * (third + 4), y, w - 2 * (third + 4), ROW, () -> "§lDone", this::onClose).highlight(() -> true));
        ph = y + ROW + 8 - py;
    }

    private void pick(int c) {
        set.accept(c);
        syncHex();
        Config.save();
    }

    private void setChannel(int shift, int v) {
        int c = get.get() & ~(255 << shift) & 0xFFFFFF;
        set.accept(c | ((v & 255) << shift));
        syncHex();
    }

    private void syncHex() {
        if (hex != null && !typing) hex.setValue(hexOf(get.get()));
    }

    private static String hexOf(int c) {
        return "#" + String.format("%06X", c & 0xFFFFFF).toUpperCase(Locale.ROOT);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY) {
        int l = px + 8, w = pw - 16;
        int c = get.get() & 0xFFFFFF;
        int alpha = getOpacity == null ? 255 : Math.round(getOpacity.get() * 2.55f);

        // Checkerboard behind the preview so see-through colours are obvious.
        for (int i = 0; i < w; i += 6) {
            for (int j = 0; j < 20; j += 6) {
                boolean dark = ((i / 6) + (j / 6)) % 2 == 0;
                g.fill(l + i, previewY + j, Math.min(l + w, l + i + 6), Math.min(previewY + 20, previewY + j + 6), dark ? 0xFF3A3A3A : 0xFF6A6A6A);
            }
        }
        Shapes.round(g, l, previewY, w, 20, Ui.RADIUS, (alpha << 24) | c);

        Ui.box(g, l, hexY, w, ROW, Ui.BUTTON, hex != null && hex.isFocused() ? Ui.PURPLE : Ui.BORDER);
        g.drawString(font, "Hex", l + 7, hexY + 5, Ui.MUTED, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        // Swatch colours on top of their buttons.
        int l = px + 8, w = pw - 16, per = 8, sw = (w - (per - 1) * 3) / per;
        for (int i = 0; i < SWATCHES.length; i++) {
            int bx = l + (i % per) * (sw + 3), by = swatchY + (i / per) * 15;
            Shapes.round(g, bx + 2, by + 2, sw - 4, 8, Math.min(2.5f, Ui.RADIUS), 0xFF000000 | SWATCHES[i]);
        }
    }

    @Override
    protected void changed() {
        super.changed();
        syncHex();
    }
}
