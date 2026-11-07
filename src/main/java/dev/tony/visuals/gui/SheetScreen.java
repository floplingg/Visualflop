package dev.tony.visuals.gui;

import dev.tony.visuals.Config;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The Flop Visuals window: a large rounded glass panel on the right of the screen (the game stays visible on the left),
 * a sidebar with the logo, pages and accent swatches, a header with the page's description, and each setting on its
 * own rounded card: glowing switches, sliders, pill selectors and grids of option cards.
 */
public class SheetScreen extends Screen {
    public record Page(String name, String icon, int colour, Supplier<List<Sheet.Item>> build) {}

    protected final Screen parent;
    protected final List<Page> pages = new ArrayList<>();
    protected int page;
    protected List<Sheet.Item> items = List.of();
    /** Size of a single-page sheet (the colour picker). The main window sizes itself to the screen. */
    protected int panelW = 330, panelH = 330;

    private float px, py, pw, ph, sideW, cx, cy, cw, ch, headerH;
    private float scroll, scrollTarget, contentH, slide, open;
    private boolean closing;
    private float[] sideHover = new float[0];
    private float swatchHover;
    private float doneHover;
    private long lastFrame;
    private Sheet.Slider dragging;
    private float scrollbarAlpha;

    private static final int[][] SWATCHES = {
        {0x8B7CFF}, {0x4FD1FF}, {0xFF6FB5}, {0x5CFFB0}, {0xFFA235},
    };
    private static final float CARD_GAP = 5;

    public SheetScreen(String title, Screen parent) {
        super(Component.literal(title));
        this.parent = parent;
    }

    protected void addPage(String name, String icon, int colour, Supplier<List<Sheet.Item>> build) {
        pages.add(new Page(name, icon, colour, build));
    }

    /** One line under the page title. */
    protected String describe(int page) {
        return "";
    }

    /** Rebuild the current page's rows (after a change that adds or removes rows). */
    protected void rebuild() {
        items = pages.isEmpty() ? List.of() : pages.get(page).build().get();
        layoutItems();
    }

    protected void showPage(int index) {
        if (index == page && !items.isEmpty()) return;
        page = Math.max(0, Math.min(pages.size() - 1, index));
        scroll = scrollTarget = 0;
        slide = 1;
        rebuild();
    }

    private boolean multi() {
        return pages.size() > 1;
    }

    @Override
    protected void init() {
        if (multi()) {
            // Like the preview: about half the screen wide, on the right, vertically centred.
            pw = Math.max(Math.min(460, width - 16), Math.min(width - 16, Math.min(620, width * 0.56f)));
            ph = Math.max(Math.min(280, height - 14), Math.min(height - 14, Math.min(400, height * 0.66f)));
            float margin = Math.max(8, width * 0.02f);
            px = width - pw - margin;
            if (px < 8) px = (width - pw) / 2f;
            py = (height - ph) / 2f;
            sideW = Math.min(132, pw * 0.26f);
            headerH = 46;
        } else {
            pw = Math.min(panelW, width - 20);
            ph = Math.min(panelH, height - 20);
            px = (width - pw) / 2f;
            py = (height - ph) / 2f;
            sideW = 0;
            headerH = 34;
        }
        cx = px + sideW + 14;
        cw = pw - sideW - 28;
        cy = py + headerH;
        ch = ph - headerH - 10;
        sideHover = new float[pages.size()];
        rebuild();
    }

    private void layoutItems() {
        float y = 0;
        Sheet.Item prev = null;
        for (Sheet.Item it : items) {
            if (it instanceof Sheet.Chips chips) measureChips(chips);
            if (it instanceof Sheet.Choice<?> c) measureChoice(c);
            if (it instanceof Sheet.Footer f) f.lines = wrap(f.text, (int) cw - 24);
            if (it instanceof Sheet.Header && prev != null) y += 4;
            if (it.grouped() && prev != null && prev.grouped()) y += CARD_GAP;
            it.y = y;
            it.h = it.height();
            y += it.h;
            prev = it;
        }
        contentH = y + 8;
    }

    private static java.util.List<String> wrap(String text, int max) {
        java.util.List<String> out = new ArrayList<>();
        String lead = text.startsWith("   ") ? "   " : "";
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split(" ")) {
            String next = line.isEmpty() ? lead + word : line + " " + word;
            if (Kit.width(next) > max && !line.isEmpty()) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out;
    }

    private void measureChips(Sheet.Chips chips) {
        chips.boxes.clear();
        float x = 10, y = 6, rowH = 22, max = cw - 10;
        int rows = 1;
        for (String n : chips.names) {
            float w = Kit.width(n, true) + 20;
            if (x + w > max && x > 10) {
                x = 10;
                y += rowH;
                rows++;
            }
            chips.boxes.add(new float[]{x, y, w, 18});
            x += w + 6;
        }
        chips.rows = rows;
    }

    /** Pills on the right of the row when the options fit, otherwise a grid of cards under the label. */
    private void measureChoice(Sheet.Choice<?> c) {
        c.boxes.clear();
        int n = c.count();
        float pills = 4;
        for (int i = 0; i < n; i++) pills += Kit.width(c.name(i), true) + 16 + 2;
        float labelW = Kit.width(c.label) + 30;
        c.grid = n > 4 || pills > cw - labelW;
        if (!c.grid) {
            float x = cw - 10 - pills + 4;
            for (int i = 0; i < n; i++) {
                float w = Kit.width(c.name(i), true) + 16;
                c.boxes.add(new float[]{x, 5, w, 18});
                x += w + 2;
            }
            c.rows = 1;
            return;
        }
        int cols = cw > 330 ? 3 : 2;
        float gap = 6, cellW = (cw - 20 - gap * (cols - 1)) / cols;
        for (int i = 0; i < n; i++) {
            c.boxes.add(new float[]{10 + (i % cols) * (cellW + gap), 26 + (i / cols) * 24, cellW, 20});
        }
        c.rows = (n + cols - 1) / cols;
    }

    // ------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Kit.refresh();
        if (minecraft.level == null) {
            super.renderBackground(g, mouseX, mouseY, partialTick);
            return;
        }
        // Keep the world visible like the preview; just a soft shade towards the panel.
        if (Config.theme().blurMenus && !multi()) renderBlurredBackground(g);
        int a = Math.round((Kit.DIM >>> 24) * Math.max(0, Math.min(1, open)));
        g.fill(0, 0, width, height, (a << 24) | (Kit.DIM & 0xFFFFFF));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Kit.refresh();
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        open = Kit.approach(open, closing ? 0 : 1, closing ? 20 : 12, dt);
        if (closing && open < 0.02f) {
            closing = false;
            Config.save();
            minecraft.setScreen(parent);
            return;
        }
        slide = Kit.approach(slide, 0, 16, dt);
        scrollTarget = Math.max(0, Math.min(Math.max(0, contentH - ch), scrollTarget));
        float before = scroll;
        scroll = Kit.approach(scroll, scrollTarget, 18, dt);
        scrollbarAlpha = Kit.approach(scrollbarAlpha, Math.abs(before - scroll) > 0.05f || dragging != null ? 1 : 0, scrollbarAlpha > 0.5f ? 3 : 12, dt);

        // Slides in from the right with a soft settle.
        float e = easeOutBack(Math.max(0, Math.min(1, open)));
        float shift = multi() ? (width - px + 10) * (1 - e) : 0;
        float s = multi() ? 1 : 0.955f + 0.045f * open;
        g.pose().pushMatrix();
        g.pose().translate(shift, 0);
        if (!multi()) {
            g.pose().translate(width / 2f, height / 2f);
            g.pose().scale(s, s);
            g.pose().translate(-width / 2f, -height / 2f);
        }
        int mx = (int) (mouseX - shift);

        float r = 18;
        Kit.shadow(g, px, py, pw, ph, r, 1.8f);
        Shapes.round(g, px, py, pw, ph, r, Kit.PANEL);
        Shapes.ring(g, px, py, pw, ph, r, 0.5f, Kit.dark ? 0x30FFFFFF : 0x1A000000);

        if (multi()) drawSidebar(g, mx, mouseY, dt);
        drawHeader(g, mx, mouseY, dt);

        // Content.
        g.enableScissor((int) cx - 2, (int) cy, (int) (cx + cw + 2), (int) (cy + ch));
        float ox = slide * 14;
        drawItems(g, cx + ox, cy - scroll, mx, mouseY, dt);
        g.disableScissor();

        // Overlay scrollbar.
        if (contentH > ch && scrollbarAlpha > 0.02f) {
            float barH = Math.max(20, ch * ch / contentH);
            float barY = cy + (ch - barH) * (scroll / Math.max(1, contentH - ch));
            Shapes.round(g, px + pw - 7, barY, 3, barH, 1.5f, ((int) (scrollbarAlpha * (Kit.dark ? 110 : 90)) << 24) | (Kit.dark ? 0xFFFFFF : 0));
        }
        g.pose().popMatrix();

        super.render(g, mouseX, mouseY, partialTick);
    }

    private static float easeOutBack(float t) {
        float c1 = 1.2f, c3 = c1 + 1;
        float u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }

    private int onAccent() {
        return Kit.luminance(Kit.ACCENT) > 0.6f ? 0xFF0B0D16 : 0xFFFFFFFF;
    }

    private void drawHeader(GuiGraphics g, int mx, int my, float dt) {
        String title = pages.isEmpty() ? getTitle().getString() : pages.get(page).name();
        float tx = cx + 2;
        if (multi()) {
            Kit.textScaled(g, title, tx, py + 13, 1.45f, Kit.LABEL, true);
            String d = describe(page);
            if (!d.isEmpty()) Kit.text(g, Kit.fit(d, (int) (cw - 90), false), tx, py + 30, Kit.SECONDARY, false);
            // "= to close" chip, like the preview.
            String key = dev.tony.visuals.FlopVisuals.menuKey == null ? "Esc" : dev.tony.visuals.FlopVisuals.menuKey.getTranslatedKeyMessage().getString();
            String label = " to close";
            float kw = Kit.width(key, true), lw = Kit.width(label);
            float w = kw + lw + 18, x = px + pw - w - 14, y = py + 12;
            boolean over = inside(mx, my, x, y, w, 17);
            doneHover = Kit.approach(doneHover, over ? 1 : 0, 14, dt);
            Shapes.round(g, x, y, w, 17, 8.5f, Kit.lerpColour(Kit.dark ? 0x14FFFFFF : 0x10000000, Kit.dark ? 0x26FFFFFF : 0x1C000000, doneHover));
            Kit.text(g, key, x + 9, y + 4.5f, Kit.LABEL, true);
            Kit.text(g, label, x + 9 + kw, y + 4.5f, Kit.SECONDARY, false);
        } else {
            Kit.textScaled(g, title, tx, py + 13, 1.35f, Kit.LABEL, true);
            float dw = Kit.width("Done", true) + 22, dx = px + pw - dw - 12, dy = py + 10;
            doneHover = Kit.approach(doneHover, inside(mx, my, dx, dy, dw, 18) ? 1 : 0, 14, dt);
            Kit.pill(g, dx, dy, dw, 18, "Done", true, doneHover);
        }
    }

    private float closeChipX() {
        String key = dev.tony.visuals.FlopVisuals.menuKey == null ? "Esc" : dev.tony.visuals.FlopVisuals.menuKey.getTranslatedKeyMessage().getString();
        float w = Kit.width(key, true) + Kit.width(" to close") + 18;
        return px + pw - w - 14;
    }

    private void drawSidebar(GuiGraphics g, int mx, int my, float dt) {
        Shapes.roundClip(g, px, py, pw, ph, 18, px, py, px + sideW, py + ph, Kit.dark ? 0x0AFFFFFF : 0x0A000000);
        Shapes.round(g, px + sideW - 0.5f, py, 0.5f, ph, 0, Kit.SEPARATOR);

        // Logo: an accent ring with a dot, then the name.
        float lx = px + 12, ly = py + 13;
        Shapes.circle(g, lx + 8, ly + 8, 10, Kit.ACCENT_SOFT);
        Shapes.ring(g, lx, ly, 16, 16, 5, 1.4f, Kit.ACCENT);
        Shapes.circle(g, lx + 8, ly + 8, 2.6f, Kit.ACCENT);
        Kit.text(g, "Flop Visuals", lx + 22, ly + 1, Kit.LABEL, true);
        Kit.text(g, "Fabric 1.21.11", lx + 22, ly + 10, Kit.SECONDARY, false);

        float y = py + 40;
        float swatchTop = py + ph - 38;
        float rh = Math.max(15, Math.min(21, (swatchTop - y - 6) / Math.max(1, pages.size()) - 2));
        float icon = Math.min(14, rh - 5);
        for (int i = 0; i < pages.size(); i++) {
            Page p = pages.get(i);
            float rx = px + 7, rw = sideW - 14;
            boolean sel = i == page;
            sideHover[i] = Kit.approach(sideHover[i], inside(mx, my, rx, y, rw, rh) ? 1 : 0, 14, dt);
            if (sel) Shapes.round(g, rx, y, rw, rh, 8, Kit.dark ? 0x1FFFFFFF : 0x14000000);
            else if (sideHover[i] > 0.01f) Shapes.round(g, rx, y, rw, rh, 8, ((int) (sideHover[i] * (Kit.dark ? 16 : 12)) << 24) | (Kit.dark ? 0xFFFFFF : 0));
            Kit.icon(g, p.icon(), rx + 5, y + (rh - icon) / 2, icon, p.colour());
            Kit.text(g, Kit.fit(p.name(), (int) (rw - icon - 16), true), rx + 11 + icon, y + (rh - 8) / 2, sel ? Kit.LABEL : Kit.SECONDARY, true);
            y += rh + 2;
        }

        // Accent swatches.
        Kit.text(g, "ACCENT", px + 14, swatchTop, Kit.SECONDARY, false);
        int cur = Config.theme().accentColor & 0xFFFFFF;
        float sx = px + 21;
        for (int[] sw : SWATCHES) {
            boolean on = !Config.theme().chroma && cur == sw[0];
            if (on) Shapes.circle(g, sx, swatchTop + 20, 8.8f, 0xFFFFFFFF);
            Shapes.circle(g, sx, swatchTop + 20, on ? 7.4f : 7f, 0xFF000000 | sw[0]);
            sx += 21;
        }
    }

    private void drawItems(GuiGraphics g, float x, float top, int mx, int my, float dt) {
        for (Sheet.Item it : items) {
            float y = top + it.y;
            if (y > cy + ch + 40 || y + it.h < cy - 40) continue;
            boolean over = it.clickable() && my >= cy && my <= cy + ch && inside(mx, my, x, y, cw, it.h);
            it.hover = Kit.approach(it.hover, over && dragging == null ? 1 : 0, 16, dt);
            if (it.grouped()) {
                // Each setting on its own rounded glass card.
                int bg = Kit.dark ? 0x12FFFFFF : 0xB3FFFFFF;
                int hov = Kit.dark ? 0x1CFFFFFF : 0xFFFFFFFF;
                Shapes.round(g, x, y, cw, it.h, 11, Kit.lerpColour(bg, hov, it.hover * (it instanceof Sheet.Slider || it instanceof Sheet.Choice<?> ? 0.35f : 1)));
                Shapes.ring(g, x, y, cw, it.h, 11, 0.5f, Kit.dark ? 0x14FFFFFF : 0x12000000);
            }
            drawItem(g, it, x, y, cw, mx, my, dt);
        }
    }

    private void drawToggle(GuiGraphics g, float x, float y, float t) {
        // Accent glow behind an "on" switch.
        if (t > 0.01f) {
            int glow = (Math.round(60 * t) << 24) | (Kit.ACCENT & 0xFFFFFF);
            Shapes.round(g, x - 2.5f, y - 2.5f, 31, 20, 10, glow);
        }
        Kit.toggle(g, x, y, t);
    }

    private void drawItem(GuiGraphics g, Sheet.Item it, float x, float y, float w, int mx, int my, float dt) {
        float ty = y + (it.h - 8) / 2f;
        float right = x + w - 12;
        if (it instanceof Sheet.Header h) {
            Kit.text(g, h.text.toUpperCase(java.util.Locale.ROOT), x + 4, y + it.h - 11, Kit.SECONDARY, true);
        } else if (it instanceof Sheet.Footer f) {
            for (int k = 0; k < f.lines.size(); k++) Kit.text(g, f.lines.get(k), x + 4, y + 4 + k * 10, Kit.SECONDARY, false);
        } else if (it instanceof Sheet.Toggle t) {
            boolean on = t.get.get();
            if (t.anim < 0) t.anim = on ? 1 : 0;
            t.anim = Kit.approach(t.anim, on ? 1 : 0, 18, dt);
            Kit.text(g, t.label, x + 12, ty, Kit.LABEL, true);
            drawToggle(g, right - 26, y + (it.h - 15) / 2f, t.anim);
        } else if (it instanceof Sheet.Slider s) {
            Kit.text(g, s.label, x + 12, y + 8, Kit.LABEL, true);
            Kit.textRight(g, s.format.apply(s.get.get()), right, y + 8, Kit.LABEL, false);
            float tx = x + 18, tw = w - 36, t = (s.get.get() - s.min) / Math.max(1e-4f, s.max - s.min);
            float ky = y + 27;
            if (s.rainbow) {
                for (int k = 0; k < 24; k++) {
                    float a = k / 24f;
                    Shapes.round(g, tx + tw * a, ky - 2, tw / 24f + 0.6f, 4, 0, 0xFF000000 | dev.tony.visuals.Theme.hsv(a, 0.85f, 1f));
                }
                Shapes.circle(g, tx + tw * t, ky + 0.6f, 7.2f, 0x26000000);
                Shapes.circle(g, tx + tw * t, ky, 6.5f, 0xFFFFFFFF);
                Shapes.circle(g, tx + tw * t, ky, 4.5f, 0xFF000000 | dev.tony.visuals.Theme.hsv(t, 0.85f, 1f));
            } else {
                int fill = s.fill != 0 ? s.fill : Kit.ACCENT;
                Shapes.circle(g, tx + tw * Math.max(0, Math.min(1, t)), ky, 9, (0x40 << 24) | (fill & 0xFFFFFF));
                Kit.slider(g, tx, ky, tw, t, fill, dragging == s);
            }
        } else if (it instanceof Sheet.Choice<?> c) {
            int sel = c.index();
            if (!c.grid) {
                Kit.text(g, c.label, x + 12, ty, Kit.LABEL, true);
                // Segmented pills.
                float[] first = c.boxes.get(0), last = c.boxes.get(c.boxes.size() - 1);
                Shapes.round(g, x + first[0] - 2, y + first[1] - 2, last[0] + last[2] - first[0] + 4, first[3] + 4, 11, Kit.dark ? 0x14FFFFFF : 0x10000000);
                for (int k = 0; k < c.count(); k++) {
                    float[] b = c.boxes.get(k);
                    boolean on = k == sel;
                    boolean hov = !on && inside(mx, my, x + b[0], y + b[1], b[2], b[3]) && my >= cy && my <= cy + ch;
                    if (on) Shapes.round(g, x + b[0], y + b[1], b[2], b[3], 9, Kit.ACCENT);
                    else if (hov) Shapes.round(g, x + b[0], y + b[1], b[2], b[3], 9, Kit.dark ? 0x1AFFFFFF : 0x14000000);
                    Kit.textCenter(g, c.name(k), x + b[0] + b[2] / 2, y + b[1] + 5, on ? onAccent() : Kit.LABEL, true);
                }
            } else {
                Kit.text(g, c.label, x + 12, y + 9, Kit.LABEL, true);
                Kit.textRight(g, c.valueName(), right, y + 9, Kit.SECONDARY, false);
                // Grid of option cards; the chosen one gets an accent border and glow.
                for (int k = 0; k < c.count(); k++) {
                    float[] b = c.boxes.get(k);
                    boolean on = k == sel;
                    boolean hov = inside(mx, my, x + b[0], y + b[1], b[2], b[3]) && my >= cy && my <= cy + ch;
                    float bx = x + b[0], by = y + b[1];
                    if (on) Shapes.round(g, bx - 2, by - 2, b[2] + 4, b[3] + 4, 10, (0x38 << 24) | (Kit.ACCENT & 0xFFFFFF));
                    Shapes.round(g, bx, by, b[2], b[3], 8, hov && !on ? (Kit.dark ? 0x1FFFFFFF : 0x18000000) : (Kit.dark ? 0x0DFFFFFF : 0x0A000000));
                    Shapes.ring(g, bx, by, b[2], b[3], 8, on ? 1.2f : 0.5f, on ? Kit.ACCENT : (Kit.dark ? 0x17FFFFFF : 0x14000000));
                    Kit.text(g, Kit.fit(c.name(k), (int) b[2] - 12, true), bx + 7, by + 6, on ? Kit.LABEL : Kit.SECONDARY, true);
                }
            }
        } else if (it instanceof Sheet.Colour c) {
            Kit.text(g, c.label, x + 12, ty, Kit.LABEL, true);
            int col = c.get.get() & 0xFFFFFF;
            String hex = String.format("#%06X", col);
            Kit.text(g, hex, right - 30 - Kit.width(hex), ty, Kit.SECONDARY, false);
            float sx = right - 16, sy = y + it.h / 2f;
            Shapes.circle(g, sx, sy, 7.5f, Kit.SEPARATOR);
            Shapes.circle(g, sx, sy, 7f, 0xFF000000 | col);
            Kit.text(g, "›", right - 1, ty, Kit.TERTIARY, true);
        } else if (it instanceof Sheet.Action a) {
            int col = a.destructive ? 0xFFFF453A : a.accent ? Kit.ACCENT : Kit.LABEL;
            Kit.text(g, a.label, x + 12, ty, col, true);
            String v = a.value == null ? "" : a.value.get();
            float vr = a.chevron ? right - 9 : right;
            if (!v.isEmpty()) Kit.textRight(g, Kit.fit(v, (int) (w * 0.5f), false), vr, ty, Kit.SECONDARY, false);
            if (a.chevron) Kit.text(g, "›", right - 1, ty, Kit.TERTIARY, true);
        } else if (it instanceof Sheet.Info in) {
            Kit.text(g, in.label, x + 12, ty, Kit.LABEL, true);
            Kit.textRight(g, Kit.fit(in.value.get(), (int) (w * 0.55f), false), right, ty, Kit.SECONDARY, false);
        } else if (it instanceof Sheet.Chips c) {
            String sel = c.selected == null ? null : c.selected.get();
            for (int k = 0; k < c.names.size(); k++) {
                float[] b = c.boxes.get(k);
                String name = c.names.get(k);
                boolean on = name.equals(sel);
                boolean hov = inside(mx, my, x + b[0], y + b[1], b[2], b[3]) && my >= cy && my <= cy + ch;
                int fill = on ? Kit.ACCENT : hov ? (Kit.dark ? 0x26FFFFFF : 0x1C000000) : (Kit.dark ? 0x14FFFFFF : 0x10000000);
                Shapes.round(g, x + b[0], y + b[1], b[2], b[3], 9, fill);
                Kit.textCenter(g, name, x + b[0] + b[2] / 2, y + b[1] + 5, on ? onAccent() : Kit.LABEL, true);
            }
        } else if (it instanceof Sheet.Custom c) {
            c.painter.paint(g, x, y, w, it.h, mx, my);
        }
    }

    // ------------------------------------------------------------ input

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int button = event.button();
        if (closing) return true;
        if (multi()) {
            if (button == 0 && inside(mx, my, closeChipX(), py + 12, px + pw - 14 - closeChipX(), 17)) {
                click();
                onClose();
                return true;
            }
            if (button == 0 && inside(mx, my, px, py, sideW, ph)) {
                float y = py + 40, swatchTop = py + ph - 38;
                float rh = Math.max(15, Math.min(21, (swatchTop - y - 6) / Math.max(1, pages.size()) - 2));
                for (int i = 0; i < pages.size(); i++) {
                    if (inside(mx, my, px + 7, y, sideW - 14, rh)) {
                        if (i != page) click();
                        showPage(i);
                        return true;
                    }
                    y += rh + 2;
                }
                float sx = px + 21;
                for (int[] sw : SWATCHES) {
                    if (Math.hypot(mx - sx, my - (swatchTop + 20)) <= 9) {
                        click();
                        Config.theme().accentColor = sw[0];
                        Config.theme().chroma = false;
                        Config.save();
                        return true;
                    }
                    sx += 21;
                }
                return true;
            }
        } else {
            float dw = Kit.width("Done", true) + 22;
            if (button == 0 && inside(mx, my, px + pw - dw - 12, py + 10, dw, 18)) {
                click();
                onClose();
                return true;
            }
        }
        if (my < cy || my > cy + ch || mx < cx || mx > cx + cw) return super.mouseClicked(event, doubleClick);
        float top = cy - scroll;
        for (Sheet.Item it : items) {
            float y = top + it.y;
            if (!it.clickable() || !inside(mx, my, cx, y, cw, it.h)) continue;
            if (it instanceof Sheet.Toggle t) {
                t.set.accept(!t.get.get());
                click();
            } else if (it instanceof Sheet.Slider s) {
                dragging = s;
                setSlider(s, mx);
            } else if (it instanceof Sheet.Choice<?> c) {
                for (int k = 0; k < c.count(); k++) {
                    float[] b = c.boxes.get(k);
                    if (inside(mx, my, cx + b[0], y + b[1], b[2], b[3])) {
                        click();
                        c.pick(k);
                        rebuild();
                        break;
                    }
                }
            } else if (it instanceof Sheet.Colour c) {
                click();
                minecraft.setScreen(new ColourSheet(this, c.label, c.get, c.set));
            } else if (it instanceof Sheet.Action a) {
                click();
                a.run.run();
            } else if (it instanceof Sheet.Chips c) {
                for (int k = 0; k < c.names.size(); k++) {
                    float[] b = c.boxes.get(k);
                    if (inside(mx, my, cx + b[0], y + b[1], b[2], b[3])) {
                        click();
                        c.pick.accept(c.names.get(k));
                        break;
                    }
                }
            } else if (it instanceof Sheet.Custom c) {
                if (c.clicker.click(cx, y, cw, it.h, mx, my)) click();
            }
            Config.save();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void setSlider(Sheet.Slider s, double mx) {
        float tx = cx + 18, tw = cw - 36;
        float t = (float) Math.max(0, Math.min(1, (mx - tx) / tw));
        float v = s.min + (s.max - s.min) * t;
        v = Math.round(v / s.step) * s.step;
        s.set.accept(Math.max(s.min, Math.min(s.max, v)));
        Config.get().theme.clamp();
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != null) {
            setSlider(dragging, event.x());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null) {
            dragging = null;
            Config.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scrollTarget -= (float) vertical * 28;
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        // The menu key closes the menu again.
        if (dev.tony.visuals.FlopVisuals.menuKey != null && dev.tony.visuals.FlopVisuals.menuKey.matches(event) && minecraft.level != null) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    protected void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.18F));
    }

    @Override
    public void onClose() {
        Config.save();
        closing = true;   // slides away, then closes
    }

    @Override
    public void removed() {
        Config.save();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
