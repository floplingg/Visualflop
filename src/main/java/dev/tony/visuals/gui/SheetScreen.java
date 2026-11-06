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
 * Apple-style settings window: frosted backdrop, floating rounded panel with a soft shadow,
 * an optional sidebar, a title bar with a Done button, and grouped cards that scroll smoothly.
 */
public class SheetScreen extends Screen {
    public record Page(String name, String icon, int colour, Supplier<List<Sheet.Item>> build) {}

    protected final Screen parent;
    protected final List<Page> pages = new ArrayList<>();
    protected int page;
    protected List<Sheet.Item> items = List.of();
    protected int panelW = 470, panelH = 310;

    private float px, py, pw, ph, sideW, cx, cy, cw, ch;
    private float scroll, scrollTarget, contentH, slide, open;
    private float[] sideHover = new float[0];
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
        page = Math.max(0, Math.min(pages.size() - 1, index));
        scroll = scrollTarget = 0;
        slide = 1;
        rebuild();
    }

    @Override
    protected void init() {
        pw = Math.min(panelW, width - 20);
        ph = Math.min(panelH, height - 20);
        px = (width - pw) / 2f;
        py = (height - ph) / 2f;
        sideW = pages.size() > 1 ? 122 : 0;
        cx = px + sideW + 14;
        cw = pw - sideW - 28;
        cy = py + 36;
        ch = ph - 36 - 10;
        sideHover = new float[pages.size()];
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

    private float sideRow() {
        return Math.max(16, Math.min(22, (ph - 40) / Math.max(1, pages.size()) - 2));
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
        g.fill(0, 0, width, height, Kit.DIM);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Kit.refresh();
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        open = Kit.approach(open, 1, 14, dt);
        slide = Kit.approach(slide, 0, 16, dt);
        scrollTarget = Math.max(0, Math.min(Math.max(0, contentH - ch), scrollTarget));
        float before = scroll;
        scroll = Kit.approach(scroll, scrollTarget, 18, dt);
        scrollbarAlpha = Kit.approach(scrollbarAlpha, Math.abs(before - scroll) > 0.05f || dragging != null ? 1 : 0, scrollbarAlpha > 0.5f ? 3 : 12, dt);

        // Pop-in animation.
        float s = 0.955f + 0.045f * open;
        g.pose().pushMatrix();
        g.pose().translate(width / 2f, height / 2f);
        g.pose().scale(s, s);
        g.pose().translate(-width / 2f, -height / 2f);

        float r = 14;
        Kit.shadow(g, px, py, pw, ph, r, 1.4f);
        Shapes.round(g, px, py, pw, ph, r, Kit.PANEL);
        Shapes.ring(g, px, py, pw, ph, r, 0.5f, Kit.dark ? 0x26FFFFFF : 0x1A000000);

        if (sideW > 0) drawSidebar(g, mouseX, mouseY, dt);

        // Title bar.
        String title = pages.isEmpty() ? getTitle().getString() : pages.get(page).name();
        Kit.textScaled(g, title, cx + 2, py + 13, 1.35f, Kit.LABEL, true);
        float dw = Kit.width("Done", true) + 22, dx = px + pw - dw - 12, dy = py + 10;
        doneHover = Kit.approach(doneHover, inside(mouseX, mouseY, dx, dy, dw, 18) ? 1 : 0, 14, dt);
        Kit.pill(g, dx, dy, dw, 18, "Done", true, doneHover);

        // Content.
        g.enableScissor((int) cx - 2, (int) cy, (int) (cx + cw + 2), (int) (cy + ch));
        float ox = slide * 14;
        drawItems(g, cx + ox, cy - scroll, mouseX, mouseY, dt);
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

    private void drawSidebar(GuiGraphics g, int mx, int my, float dt) {
        Shapes.roundClip(g, px, py, pw, ph, 14, px, py, px + sideW, py + ph, Kit.SIDEBAR);
        Shapes.round(g, px + sideW - 0.5f, py, 0.5f, ph, 0, Kit.SEPARATOR);
        float y = py + 14;
        Kit.text(g, "Flop Visuals", px + 14, y, Kit.SECONDARY, true);
        y += 16;
        float rh = sideRow(), icon = Math.min(16, rh - 5);
        for (int i = 0; i < pages.size(); i++) {
            Page p = pages.get(i);
            float rx = px + 7, rw = sideW - 14;
            boolean sel = i == page;
            sideHover[i] = Kit.approach(sideHover[i], inside(mx, my, rx, y, rw, rh) ? 1 : 0, 14, dt);
            if (sel) Shapes.round(g, rx, y, rw, rh, 7, Kit.ACCENT);
            else if (sideHover[i] > 0.01f) Shapes.round(g, rx, y, rw, rh, 7, ((int) (sideHover[i] * (Kit.dark ? 26 : 20)) << 24) | (Kit.dark ? 0xFFFFFF : 0));
            Kit.icon(g, p.icon(), rx + 4, y + (rh - icon) / 2, icon, p.colour());
            int tc = sel ? (Kit.luminance(Kit.ACCENT) > 0.6f ? 0xFF000000 : 0xFFFFFFFF) : Kit.LABEL;
            Kit.text(g, p.name(), rx + 10 + icon, y + (rh - 8) / 2, tc, sel);
            y += rh + 2;
        }
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
            float vw = Kit.width(v);
            Kit.text(g, v, right - vw - 9, ty, Kit.SECONDARY, false);
            Kit.text(g, "›", right - 1, ty, Kit.TERTIARY, true);
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

    // ------------------------------------------------------------ input

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int button = event.button();
        float dw = Kit.width("Done", true) + 22;
        if (button == 0 && inside(mx, my, px + pw - dw - 12, py + 10, dw, 18)) {
            click();
            onClose();
            return true;
        }
        if (sideW > 0 && button == 0) {
            float y = py + 30, rh = sideRow();
            for (int i = 0; i < pages.size(); i++) {
                if (inside(mx, my, px + 7, y, sideW - 14, rh)) {
                    if (i != page) click();
                    showPage(i);
                    return true;
                }
                y += rh + 2;
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
                c.step(button == 1 ? -1 : 1);
                click();
                rebuild();
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

    protected void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.18F));
    }

    @Override
    public void onClose() {
        Config.save();
        minecraft.setScreen(parent);
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
