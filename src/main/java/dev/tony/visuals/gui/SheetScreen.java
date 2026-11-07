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
 * Modern drop-down settings panel: it slides down from the top of the screen, pages sit in a
 * segmented tab bar with a sliding highlight, option pickers open dropdown lists, and grouped
 * cards scroll smoothly.
 */
public class SheetScreen extends Screen {
    public record Page(String name, String icon, int colour, Supplier<List<Sheet.Item>> build) {}

    protected final Screen parent;
    protected final List<Page> pages = new ArrayList<>();
    protected int page;
    protected List<Sheet.Item> items = List.of();
    protected int panelW = 540, panelH = 320;

    private float px, py, pw, ph, cx, cy, cw, ch;
    private float scroll, scrollTarget, contentH, slide, open;
    private boolean closing;
    // Tab bar.
    private float tabsY, tabsH = 22;
    private float[] tabX = new float[0], tabW = new float[0], tabHover = new float[0];
    private boolean tabLabels;
    private float indX = -1, indW;
    // Dropdown list.
    private Sheet.Choice<?> dd;
    private float ddX, ddY, ddW, ddH, ddAnim, ddScroll, ddRowY, ddRowH;
    private boolean ddUp;
    private int ddVisible;
    private static final float DD_ROW = 18;
    private float doneHover;
    private long lastFrame;
    private Sheet.Slider dragging;
    private float scrollbarAlpha;

    public SheetScreen(String title, Screen parent) {
        super(Component.literal(title));
        this.parent = parent;
    }

    protected void addPage(String name, String icon, int colour, Supplier<List<Sheet.Item>> build) {
        pages.add(new Page(name, icon, colour, build));
    }

    /** Rebuild the current page's rows (after a change that adds or removes rows). */
    protected void rebuild() {
        items = pages.isEmpty() ? List.of() : pages.get(page).build().get();
        layoutItems();
    }

    protected void showPage(int index) {
        if (index == page && !items.isEmpty()) return;
        dd = null;
        page = Math.max(0, Math.min(pages.size() - 1, index));
        scroll = scrollTarget = 0;
        slide = 1;
        rebuild();
    }

    @Override
    protected void init() {
        pw = Math.min(panelW, width - 16);
        ph = Math.min(panelH, height - 14);
        px = (width - pw) / 2f;
        // Hangs from the top of the screen like a drop-down.
        py = Math.max(7, Math.min((height - ph) / 2f, 18));
        boolean tabs = pages.size() > 1;
        tabsY = py + 32;
        cx = px + 14;
        cw = pw - 28;
        cy = tabs ? tabsY + tabsH + 10 : py + 36;
        ch = py + ph - 14 - cy;
        tabHover = new float[pages.size()];
        layoutTabs();
        dd = null;
        rebuild();
    }

    private void layoutItems() {
        float y = 0;
        Sheet.Item prev = null;
        for (Sheet.Item it : items) {
            if (it instanceof Sheet.Chips chips) measureChips(chips);
            if (it instanceof Sheet.Footer f) f.lines = wrap(f.text, (int) cw - 24);
            if (it.grouped() && (prev == null || !prev.grouped()) && prev != null && !(prev instanceof Sheet.Header)) y += 10;
            if (!it.grouped() && it instanceof Sheet.Header && prev != null) y += 4;
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

    /** Segmented tab bar: icon + name when everything fits, otherwise icons with the name on the selected tab. */
    private void layoutTabs() {
        int n = pages.size();
        tabX = new float[n];
        tabW = new float[n];
        if (n <= 1) return;
        float icon = 14, gap = 3, full = 0;
        for (Page p : pages) full += 12 + icon + 5 + Kit.width(p.name(), true) + gap;
        tabLabels = full - gap <= pw - 24;
        float x = 0;
        float[] w = new float[n];
        for (int i = 0; i < n; i++) {
            boolean label = tabLabels || i == page;
            w[i] = 12 + icon + (label ? 5 + Kit.width(pages.get(i).name(), true) : 0);
            x += w[i] + (i < n - 1 ? gap : 0);
        }
        float start = px + (pw - x) / 2f;
        for (int i = 0; i < n; i++) {
            tabX[i] = start;
            tabW[i] = w[i];
            start += w[i] + gap;
        }
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

    // ------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Kit.refresh();
        if (minecraft.level == null) {
            super.renderBackground(g, mouseX, mouseY, partialTick);
            return;
        }
        if (Config.theme().blurMenus) renderBlurredBackground(g);
        int a = Math.round((Kit.DIM >>> 24) * Math.max(0, Math.min(1, open)));
        g.fill(0, 0, width, height, (a << 24) | (Kit.DIM & 0xFFFFFF));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Kit.refresh();
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        open = Kit.approach(open, closing ? 0 : 1, closing ? 20 : 11, dt);
        if (closing && open < 0.02f) {
            closing = false;
            Config.save();
            minecraft.setScreen(parent);
            return;
        }
        slide = Kit.approach(slide, 0, 16, dt);
        ddAnim = Kit.approach(ddAnim, dd != null ? 1 : 0, 22, dt);
        scrollTarget = Math.max(0, Math.min(Math.max(0, contentH - ch), scrollTarget));
        float before = scroll;
        scroll = Kit.approach(scroll, scrollTarget, 18, dt);
        scrollbarAlpha = Kit.approach(scrollbarAlpha, Math.abs(before - scroll) > 0.05f || dragging != null ? 1 : 0, scrollbarAlpha > 0.5f ? 3 : 12, dt);

        // Drop-down animation: slides from above the screen with a soft settle.
        float e = easeOutBack(Math.max(0, Math.min(1, open)));
        float drop = -(py + ph + 12) * (1 - e);
        g.pose().pushMatrix();
        g.pose().translate(0, drop);

        float r = 16;
        Kit.shadow(g, px, py, pw, ph, r, 1.6f);
        Shapes.round(g, px, py, pw, ph, r, Kit.PANEL);
        Shapes.ring(g, px, py, pw, ph, r, 0.5f, Kit.dark ? 0x2EFFFFFF : 0x1A000000);

        // Title bar.
        boolean tabs = pages.size() > 1;
        String title = tabs ? getTitle().getString() : (pages.isEmpty() ? getTitle().getString() : pages.get(page).name());
        Kit.textScaled(g, title, px + 16, py + 12, 1.3f, Kit.LABEL, true);
        if (tabs) drawTabs(g, mouseX, mouseY - drop, dt);
        float dw = Kit.width("Done", true) + 22, dx = px + pw - dw - 12, dy = py + 10;
        doneHover = Kit.approach(doneHover, inside(mouseX, mouseY, dx, dy, dw, 18) ? 1 : 0, 14, dt);
        Kit.pill(g, dx, dy, dw, 18, "Done", true, doneHover);

        // Content.
        int my = (int) (mouseY - drop);
        boolean ddBlocks = dd != null;
        g.enableScissor((int) cx - 2, (int) (cy + drop), (int) (cx + cw + 2), (int) (cy + ch + drop));
        float ox = slide * 14;
        drawItems(g, cx + ox, cy - scroll, ddBlocks ? -9999 : mouseX, ddBlocks ? -9999 : my, dt);
        g.disableScissor();

        // Grabber handle, like a pull-down sheet.
        Shapes.round(g, px + pw / 2f - 18, py + ph - 7, 36, 3, 1.5f, Kit.dark ? 0x40FFFFFF : 0x30000000);

        // Overlay scrollbar.
        if (contentH > ch && scrollbarAlpha > 0.02f) {
            float barH = Math.max(20, ch * ch / contentH);
            float barY = cy + (ch - barH) * (scroll / Math.max(1, contentH - ch));
            Shapes.round(g, px + pw - 7, barY, 3, barH, 1.5f, ((int) (scrollbarAlpha * (Kit.dark ? 110 : 90)) << 24) | (Kit.dark ? 0xFFFFFF : 0));
        }
        // Dropdown list floats above everything else in the panel.
        if (dd != null || ddAnim > 0.01f) {
            g.nextStratum();
            drawDropdown(g, mouseX, my, dt);
        }
        g.pose().popMatrix();

        super.render(g, mouseX, mouseY, partialTick);
    }

    private static float easeOutBack(float t) {
        float c1 = 1.25f, c3 = c1 + 1;
        float u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }

    private void drawTabs(GuiGraphics g, int mx, float my, float dt) {
        int n = pages.size();
        if (!tabLabels) layoutTabs();
        float x0 = tabX[0] - 3, x1 = tabX[n - 1] + tabW[n - 1] + 3;
        Shapes.round(g, x0, tabsY - 3, x1 - x0, tabsH + 6, (tabsH + 6) / 2f, Kit.dark ? 0x59000000 : 0x14000000);
        // Sliding highlight behind the selected tab.
        if (indX < 0) {
            indX = tabX[page];
            indW = tabW[page];
        }
        indX = Kit.approach(indX, tabX[page], 18, dt);
        indW = Kit.approach(indW, tabW[page], 18, dt);
        Shapes.round(g, indX, tabsY, indW, tabsH, tabsH / 2f, Kit.ACCENT);
        for (int i = 0; i < n; i++) {
            Page p = pages.get(i);
            boolean sel = i == page;
            boolean over = dd == null && inside(mx, my, tabX[i], tabsY, tabW[i], tabsH);
            tabHover[i] = Kit.approach(tabHover[i], over && !sel ? 1 : 0, 16, dt);
            if (tabHover[i] > 0.01f) {
                Shapes.round(g, tabX[i], tabsY, tabW[i], tabsH, tabsH / 2f, ((int) (tabHover[i] * (Kit.dark ? 30 : 22)) << 24) | (Kit.dark ? 0xFFFFFF : 0));
            }
            float icon = 14;
            float ix = tabX[i] + 6;
            Kit.icon(g, p.icon(), ix, tabsY + (tabsH - icon) / 2f, icon, p.colour());
            if (tabLabels || sel) {
                int tc = sel ? (Kit.luminance(Kit.ACCENT) > 0.6f ? 0xFF000000 : 0xFFFFFFFF) : Kit.LABEL;
                Kit.text(g, p.name(), ix + icon + 5, tabsY + (tabsH - 8) / 2f, tc, true);
            }
        }
    }

    // ------------------------------------------------------------ dropdown

    private void openDropdown(Sheet.Choice<?> c, float rowY, float rowH) {
        dd = c;
        ddRowY = rowY;
        ddRowH = rowH;
        int n = c.count();
        float maxText = 0;
        for (int i = 0; i < n; i++) maxText = Math.max(maxText, Kit.width(c.name(i), true));
        ddW = Math.max(120, maxText + 40);
        ddX = cx + cw - ddW - 6;
        float below = height - (rowY + rowH) - 10, above = rowY - 10;
        int fitBelow = (int) ((below - 10) / DD_ROW), fitAbove = (int) ((above - 10) / DD_ROW);
        ddUp = fitBelow < Math.min(n, 5) && fitAbove > fitBelow;
        ddVisible = Math.max(1, Math.min(n, Math.min(10, ddUp ? fitAbove : fitBelow)));
        ddH = ddVisible * DD_ROW + 8;
        ddY = ddUp ? rowY - ddH - 2 : rowY + rowH + 2;
        float maxScroll = Math.max(0, (n - ddVisible) * DD_ROW);
        ddScroll = Math.max(0, Math.min(maxScroll, (c.index() - ddVisible / 2f + 0.5f) * DD_ROW));
        ddAnim = 0;
    }

    private void drawDropdown(GuiGraphics g, int mx, int my, float dt) {
        if (dd == null) return;
        float e = 1 - (float) Math.pow(1 - Math.max(0, Math.min(1, ddAnim)), 3);
        float h = ddH * (0.6f + 0.4f * e);
        float y = ddUp ? ddY + ddH - h : ddY;
        int alpha = Math.round(255 * e);
        Kit.shadow(g, ddX, y, ddW, h, 11, 1.3f * e);
        int bg = Kit.dark ? 0x2C2C2E : 0xFFFFFF;
        Shapes.round(g, ddX, y, ddW, h, 11, (Math.round(alpha * 0.97f) << 24) | bg);
        Shapes.ring(g, ddX, y, ddW, h, 11, 0.5f, ((Math.round(alpha * (Kit.dark ? 0.16f : 0.1f))) << 24) | (Kit.dark ? 0xFFFFFF : 0));
        g.enableScissor((int) ddX, (int) (y + 4), (int) (ddX + ddW), (int) (y + h - 4));
        int n = dd.count(), sel = dd.index();
        float top = y + 4 - ddScroll;
        for (int i = 0; i < n; i++) {
            float ry = top + i * DD_ROW;
            if (ry + DD_ROW < y || ry > y + h) continue;
            boolean over = inside(mx, my, ddX + 4, ry, ddW - 8, DD_ROW) && my > y + 4 && my < y + h - 4;
            if (over) Shapes.round(g, ddX + 4, ry + 1, ddW - 8, DD_ROW - 2, 7, (Math.round(alpha * 0.9f) << 24) | (Kit.ACCENT & 0xFFFFFF));
            int tc = over ? (Kit.luminance(Kit.ACCENT) > 0.6f ? 0xFF000000 : 0xFFFFFFFF) : i == sel ? Kit.ACCENT : Kit.LABEL;
            tc = (Math.round(alpha * ((tc >>> 24) / 255f)) << 24) | (tc & 0xFFFFFF);
            if (i == sel) Shapes.circle(g, ddX + 13, ry + DD_ROW / 2f, 2.5f, over ? tc : (alpha << 24) | (Kit.ACCENT & 0xFFFFFF));
            Kit.text(g, dd.name(i), ddX + 22, ry + (DD_ROW - 8) / 2f, tc, i == sel);
        }
        g.disableScissor();
        // Scroll hint.
        if (n > ddVisible) {
            float barH = Math.max(14, (h - 8) * ddVisible / n);
            float barY = y + 4 + (h - 8 - barH) * (ddScroll / Math.max(1, (n - ddVisible) * DD_ROW));
            Shapes.round(g, ddX + ddW - 5, barY, 2.5f, barH, 1.25f, (Math.round(alpha * 0.35f) << 24) | (Kit.dark ? 0xFFFFFF : 0));
        }
    }

    /** For the automated screenshots: open the dropdown of the n-th option picker on this page. */
    public void testOpenDropdown(int n) {
        int seen = 0;
        for (Sheet.Item it : items) {
            if (it instanceof Sheet.Choice<?> c && seen++ == n) {
                openDropdown(c, cy - scroll + it.y, it.h);
                return;
            }
        }
    }

    /** Handles a click while the dropdown is open. Always consumes it. */
    private void clickDropdown(double mx, double my) {
        if (inside(mx, my, ddX, ddY, ddW, ddH)) {
            int i = (int) ((my - ddY - 4 + ddScroll) / DD_ROW);
            if (i >= 0 && i < dd.count() && my > ddY + 4 && my < ddY + ddH - 4) {
                dd.pick(i);
                click();
                Config.save();
                dd = null;
                rebuild();
            }
            return;
        }
        dd = null;
    }

    private void drawItems(GuiGraphics g, float x, float top, int mx, int my, float dt) {
        int n = items.size();
        for (int i = 0; i < n; i++) {
            Sheet.Item it = items.get(i);
            float y = top + it.y;
            if (y > cy + ch + 40 || y + it.h < cy - 40) continue;
            boolean first = it.grouped() && (i == 0 || !items.get(i - 1).grouped());
            boolean last = it.grouped() && (i == n - 1 || !items.get(i + 1).grouped());
            boolean over = it.clickable() && my >= cy && my <= cy + ch && inside(mx, my, x, y, cw, it.h);
            it.hover = Kit.approach(it.hover, over && dragging == null ? 1 : 0, 16, dt);

            if (it.grouped()) {
                float r = 10;
                // Card background drawn per row with rounded corners only at the ends.
                float rt = first ? r : 0, rb = last ? r : 0;
                drawRowBg(g, x, y, cw, it.h, rt, rb, Kit.lerpColour(Kit.CARD, Kit.CARD_HOVER, it.hover * (it instanceof Sheet.Slider ? 0.4f : 1)));
                if (!last) Shapes.round(g, x + 12, y + it.h - 0.5f, cw - 12, 0.5f, 0, Kit.SEPARATOR);
            }
            drawItem(g, it, x, y, cw, mx, my, dt);
        }
    }

    private static void drawRowBg(GuiGraphics g, float x, float y, float w, float h, float rt, float rb, int colour) {
        if (rt == 0 && rb == 0) {
            Shapes.round(g, x, y, w, h, 0, colour);
        } else if (rt > 0 && rb > 0) {
            Shapes.round(g, x, y, w, h, rt, colour);
        } else if (rt > 0) {
            Shapes.roundClip(g, x, y, w, h + rt * 2, rt, x, y, x + w, y + h, colour);
        } else {
            Shapes.roundClip(g, x, y - rb * 2, w, h + rb * 2, rb, x, y, x + w, y + h, colour);
        }
    }

    private void drawItem(GuiGraphics g, Sheet.Item it, float x, float y, float w, int mx, int my, float dt) {
        float ty = y + (it.h - 8) / 2f;
        float right = x + w - 12;
        if (it instanceof Sheet.Header h) {
            Kit.text(g, h.text.toUpperCase(java.util.Locale.ROOT), x + 12, y + it.h - 11, Kit.SECONDARY, false);
        } else if (it instanceof Sheet.Footer f) {
            for (int k = 0; k < f.lines.size(); k++) Kit.text(g, f.lines.get(k), x + 12, y + 4 + k * 10, Kit.SECONDARY, false);
        } else if (it instanceof Sheet.Toggle t) {
            boolean on = t.get.get();
            if (t.anim < 0) t.anim = on ? 1 : 0;
            t.anim = Kit.approach(t.anim, on ? 1 : 0, 18, dt);
            Kit.text(g, t.label, x + 12, ty, Kit.LABEL, false);
            Kit.toggle(g, right - 26, y + (it.h - 15) / 2f, t.anim);
        } else if (it instanceof Sheet.Slider s) {
            Kit.text(g, s.label, x + 12, y + 8, Kit.LABEL, false);
            Kit.textRight(g, s.format.apply(s.get.get()), right, y + 8, Kit.SECONDARY, false);
            float tx = x + 18, tw = w - 36, t = (s.get.get() - s.min) / Math.max(1e-4f, s.max - s.min);
            float ky = y + 27;
            if (s.rainbow) {
                for (int k = 0; k < 24; k++) {
                    float a = k / 24f, b = (k + 1) / 24f;
                    Shapes.round(g, tx + tw * a, ky - 2, tw / 24f + 0.6f, 4, 0, 0xFF000000 | dev.tony.visuals.Theme.hsv(a, 0.85f, 1f));
                }
                Shapes.circle(g, tx + tw * t, ky + 0.6f, 7.2f, 0x26000000);
                Shapes.circle(g, tx + tw * t, ky, 6.5f, 0xFFFFFFFF);
                Shapes.circle(g, tx + tw * t, ky, 4.5f, 0xFF000000 | dev.tony.visuals.Theme.hsv(t, 0.85f, 1f));
            } else {
                Kit.slider(g, tx, ky, tw, t, s.fill != 0 ? s.fill : Kit.ACCENT, dragging == s);
            }
        } else if (it instanceof Sheet.Choice<?> c) {
            Kit.text(g, c.label, x + 12, ty, Kit.LABEL, false);
            String v = c.valueName();
            float vw = Kit.width(v, true);
            boolean open = c == dd;
            float bw = vw + 26, bx = right - bw + 4, by = y + (it.h - 17) / 2f;
            Shapes.round(g, bx, by, bw, 17, 8.5f, open ? Kit.ACCENT_SOFT : (Kit.dark ? 0xFF3A3A3C : 0xFFE5E5EA));
            Kit.text(g, v, bx + 9, ty, open ? Kit.ACCENT : Kit.LABEL, true);
            chevron(g, bx + bw - 10, y + it.h / 2f, open, open ? Kit.ACCENT : Kit.SECONDARY);
        } else if (it instanceof Sheet.Colour c) {
            Kit.text(g, c.label, x + 12, ty, Kit.LABEL, false);
            int col = c.get.get() & 0xFFFFFF;
            String hex = String.format("#%06X", col);
            Kit.text(g, hex, right - 30 - Kit.width(hex), ty, Kit.SECONDARY, false);
            float sx = right - 16, sy = y + it.h / 2f;
            Shapes.circle(g, sx, sy, 7.5f, Kit.SEPARATOR);
            Shapes.circle(g, sx, sy, 7f, 0xFF000000 | col);
            Kit.text(g, "›", right - 1, ty, Kit.TERTIARY, true);
        } else if (it instanceof Sheet.Action a) {
            int col = a.destructive ? 0xFFFF453A : a.accent ? Kit.ACCENT : Kit.LABEL;
            Kit.text(g, a.label, x + 12, ty, col, false);
            String v = a.value == null ? "" : a.value.get();
            float vr = a.chevron ? right - 9 : right;
            if (!v.isEmpty()) Kit.textRight(g, Kit.fit(v, (int) (w * 0.5f), false), vr, ty, Kit.SECONDARY, false);
            if (a.chevron) Kit.text(g, "›", right - 1, ty, Kit.TERTIARY, true);
        } else if (it instanceof Sheet.Info in) {
            Kit.text(g, in.label, x + 12, ty, Kit.LABEL, false);
            Kit.textRight(g, Kit.fit(in.value.get(), (int) (w * 0.55f), false), right, ty, Kit.SECONDARY, false);
        } else if (it instanceof Sheet.Chips c) {
            String sel = c.selected == null ? null : c.selected.get();
            for (int k = 0; k < c.names.size(); k++) {
                float[] b = c.boxes.get(k);
                String name = c.names.get(k);
                boolean on = name.equals(sel);
                boolean hov = inside(mx, my, x + b[0], y + b[1], b[2], b[3]) && my >= cy && my <= cy + ch;
                int fill = on ? Kit.ACCENT : hov ? Kit.CARD_HOVER : (Kit.dark ? 0xFF3A3A3C : 0xFFE5E5EA);
                Shapes.round(g, x + b[0], y + b[1], b[2], b[3], 9, fill);
                int tc = on ? (Kit.luminance(Kit.ACCENT) > 0.6f ? 0xFF000000 : 0xFFFFFFFF) : Kit.LABEL;
                Kit.textCenter(g, name, x + b[0] + b[2] / 2, y + b[1] + 5, tc, true);
            }
        } else if (it instanceof Sheet.Custom c) {
            c.painter.paint(g, x, y, w, it.h, mx, my);
        }
    }

    /** Small down chevron (flips up while its list is open). */
    private static void chevron(GuiGraphics g, float cx, float cy, boolean up, int colour) {
        float s = up ? -1 : 1;
        for (int k = -1; k <= 1; k += 2) {
            g.pose().pushMatrix();
            g.pose().translate(cx + k * 1.6f, cy);
            g.pose().rotate((float) Math.toRadians(45 * k * s));
            Shapes.round(g, -0.7f, -2.4f, 1.4f, 4.8f, 0.7f, colour);
            g.pose().popMatrix();
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
        if (dd != null) {
            clickDropdown(mx, my);
            return true;
        }
        float dw = Kit.width("Done", true) + 22;
        if (button == 0 && inside(mx, my, px + pw - dw - 12, py + 10, dw, 18)) {
            click();
            onClose();
            return true;
        }
        if (pages.size() > 1 && button == 0) {
            for (int i = 0; i < pages.size(); i++) {
                if (inside(mx, my, tabX[i], tabsY, tabW[i], tabsH)) {
                    if (i != page) click();
                    showPage(i);
                    layoutTabs();
                    return true;
                }
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
                click();
                openDropdown(c, y, it.h);
                return true;
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
        if (dd != null) {
            if (inside(mouseX, mouseY, ddX, ddY, ddW, ddH)) {
                float max = Math.max(0, (dd.count() - ddVisible) * DD_ROW);
                ddScroll = Math.max(0, Math.min(max, ddScroll - (float) vertical * DD_ROW));
            }
            return true;
        }
        scrollTarget -= (float) vertical * 28;
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && dd != null) {
            dd = null;
            return true;
        }
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
        dd = null;
        closing = true;   // slides back up, then closes
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
