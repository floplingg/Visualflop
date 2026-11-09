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
 * The Flop Visuals window, built to match the preview: a dark rounded glass panel (centred by default), a sidebar with
 * the logo, line icons and accent swatches, a header with the page's description and a close chip, and each setting
 * on its own rounded card. Every hover, switch, selection and the window itself move on cubic-out curves.
 * Size, position, glass, corners and animation speed are set on the Appearance page.
 */
public class SheetScreen extends Screen {
    public record Page(String name, String icon, int colour, Supplier<List<Sheet.Item>> build) {}

    protected final Screen parent;
    protected final List<Page> pages = new ArrayList<>();
    protected int page;
    protected List<Sheet.Item> items = List.of();
    /** Size of a single-page sheet (the colour picker). The main window sizes itself from the menu settings. */
    protected int panelW = 330, panelH = 330;

    private float px, py, pw, ph, sideW, cx, cy, cw, ch, headerH;
    private float scroll, scrollTarget, contentH;
    private boolean closing;
    private final Ease openE = new Ease(0.42f, 0), pageE = new Ease(0.34f, 1), closeHover = new Ease(0.2f, 0);
    private final Ease selY = new Ease(0.32f);
    private Ease[] sideHover = new Ease[0];
    private final Ease[] swatchHover = new Ease[SWATCHES.length];
    private long lastFrame;
    private Sheet.Slider dragging;
    private float scrollbarAlpha;

    private static final int[] SWATCHES = {0x8B7CFF, 0x4FD1FF, 0xFF6FB5, 0x5CFFB0, 0xFFA235};
    private static final float CARD_GAP = 5;

    // The preview's colours (dark glass). Light appearance keeps the regular palette.
    private static int text() { return Kit.dark ? 0xFFEEF0FA : Kit.LABEL; }
    private static int sub() { return Kit.dark ? 0xFFB4BACE : Kit.SECONDARY; }
    private static int muted() { return Kit.dark ? 0xFFA9B0C7 : Kit.SECONDARY; }
    private static int glass(float a) { return Kit.dark ? ((int) (a * 255) << 24) | 0xFFFFFF : ((int) (a * 255 * 0.8f) << 24); }

    public SheetScreen(String title, Screen parent) {
        super(Component.literal(title));
        this.parent = parent;
        for (int i = 0; i < swatchHover.length; i++) swatchHover[i] = new Ease(0.2f, 0);
    }

    protected void addPage(String name, String icon, int colour, Supplier<List<Sheet.Item>> build) {
        pages.add(new Page(name, icon, colour, build));
    }

    /** One line under the page title. */
    protected String describe(int page) {
        return "";
    }

    protected void rebuild() {
        items = pages.isEmpty() ? List.of() : pages.get(page).build().get();
        layoutItems();
    }

    protected void showPage(int index) {
        if (index == page && !items.isEmpty()) return;
        page = Math.max(0, Math.min(pages.size() - 1, index));
        scroll = scrollTarget = 0;
        pageE.snap(0);
        rebuild();
    }

    private boolean multi() {
        return pages.size() > 1;
    }

    private static String lineIconFor(String icon) {
        return switch (icon) {
            case "appearance" -> "appearance";
            case "hud" -> "hud";
            case "keyboard" -> "keyboard";
            case "trails" -> "trails";
            case "petals" -> "petals";
            case "sky" -> "sky";
            case "glass" -> "glass";
            case "wallpaper" -> "wallpaper";
            case "music" -> "music";
            case "halo" -> "halo";
            case "pay" -> "pay";
            default -> "hud";
        };
    }

    @Override
    protected void init() {
        Config.Menu m = Config.get().menu;
        float k = m.scale / 100f;
        if (multi()) {
            pw = Math.min(width - 16, Math.max(420, 520 * k));
            ph = Math.min(height - 14, Math.max(260, 330 * k));
            float margin = Math.max(10, width * 0.03f);
            px = switch (m.position) {
                case LEFT -> margin;
                case RIGHT -> width - pw - margin;
                default -> (width - pw) / 2f;
            };
            if (px < 8 || px + pw > width - 8) px = (width - pw) / 2f;
            py = (height - ph) / 2f;
            sideW = m.sidebarLabels ? Math.min(132, pw * 0.27f) : 44;
            headerH = 50;
        } else {
            pw = Math.min(panelW, width - 20);
            ph = Math.min(panelH, height - 20);
            px = (width - pw) / 2f;
            py = (height - ph) / 2f;
            sideW = 0;
            headerH = 36;
        }
        cx = px + sideW + 14;
        cw = pw - sideW - 28;
        cy = py + headerH;
        ch = ph - headerH - 10;
        sideHover = new Ease[pages.size()];
        for (int i = 0; i < sideHover.length; i++) sideHover[i] = new Ease(0.22f, 0);
        rebuild();
    }

    private void layoutItems() {
        float y = 0;
        Sheet.Item prev = null;
        for (Sheet.Item it : items) {
            if (it instanceof Sheet.Chips chips) measureChips(chips);
            if (it instanceof Sheet.Choice<?> c) measureChoice(c);
            if (it instanceof Sheet.Footer f) f.lines = wrap(f.text, (int) cw - 12);
            if (it instanceof Sheet.Header && prev != null) y += 4;
            if (it.grouped() && prev != null && prev.grouped()) y += CARD_GAP;
            it.y = y;
            it.h = it.height();
            y += it.h;
            prev = it;
        }
        contentH = y + 8;
    }

    private static List<String> wrap(String text, int max) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
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

    /** Pills on the right when the options fit (like the preview's selectors), otherwise a grid of option cards. */
    private void measureChoice(Sheet.Choice<?> c) {
        c.boxes.clear();
        c.optHover.clear();
        int n = c.count();
        for (int i = 0; i < n; i++) c.optHover.add(new Ease(0.2f, 0));
        float pills = 6;
        for (int i = 0; i < n; i++) pills += Kit.width(c.name(i), true) + 18 + 3;
        float labelW = Kit.width(c.label, true) + 34;
        c.grid = n > 4 || pills > cw - labelW;
        if (!c.grid) {
            float x = cw - 12 - pills + 6;
            for (int i = 0; i < n; i++) {
                float w = Kit.width(c.name(i), true) + 18;
                c.boxes.add(new float[]{x, 6, w, 18});
                x += w + 3;
            }
            c.rows = 1;
            return;
        }
        int cols = cw > 330 ? 3 : 2;
        float gap = 6, cellW = (cw - 22 - gap * (cols - 1)) / cols;
        for (int i = 0; i < n; i++) {
            c.boxes.add(new float[]{11 + (i % cols) * (cellW + gap), 28 + (i / cols) * 26, cellW, 21});
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
        if (Config.get().menu.blurWorld) renderBlurredBackground(g);
        float o = openE.value();
        g.fill(0, 0, width, height, ((int) (0x55 * o) << 24) | 0x05060C);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Kit.refresh();
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        float open = openE.to(closing ? 0 : 1);
        if (closing && open < 0.01f) {
            closing = false;
            Config.save();
            minecraft.setScreen(parent);
            return;
        }
        float pageT = pageE.to(1);
        scrollTarget = Math.max(0, Math.min(Math.max(0, contentH - ch), scrollTarget));
        float before = scroll;
        scroll = Kit.approach(scroll, scrollTarget, 18, dt);
        scrollbarAlpha = Kit.approach(scrollbarAlpha, Math.abs(before - scroll) > 0.05f || dragging != null ? 1 : 0, scrollbarAlpha > 0.5f ? 3 : 12, dt);

        // Opens with a cubic-out rise and grow.
        float rise = (1 - open) * 18, s = 0.94f + 0.06f * open;
        g.pose().pushMatrix();
        g.pose().translate(width / 2f, height / 2f + rise);
        g.pose().scale(s, s);
        g.pose().translate(-width / 2f, -height / 2f);
        int my = (int) ((mouseY - height / 2f - rise) / s + height / 2f);
        int mx = (int) ((mouseX - width / 2f) / s + width / 2f);

        Config.Menu m = Config.get().menu;
        float r = multi() ? m.radius : 16;
        Kit.shadow(g, px, py, pw, ph, r, 1.8f * open);
        int panel = Kit.dark ? (Math.round(m.opacity * 2.55f * Math.max(0.2f, open)) << 24) | 0x0C0E1C
            : (Math.round(Math.min(100, m.opacity + 20) * 2.55f * Math.max(0.2f, open)) << 24) | 0xF2F2F7;
        Shapes.round(g, px, py, pw, ph, r, panel);
        Shapes.ring(g, px, py, pw, ph, r, 0.5f, Kit.dark ? 0x1FFFFFFF : 0x1A000000);

        if (multi()) drawSidebar(g, mx, my, r);
        drawHeader(g, mx, my);

        g.enableScissor((int) cx - 3, (int) cy, (int) (cx + cw + 3), (int) (cy + ch));
        float ox = (1 - pageT) * 16;
        drawItems(g, cx + ox, cy - scroll, mx, my, dt, pageT);
        g.disableScissor();

        if (contentH > ch && scrollbarAlpha > 0.02f) {
            float barH = Math.max(20, ch * ch / contentH);
            float barY = cy + (ch - barH) * (scroll / Math.max(1, contentH - ch));
            Shapes.round(g, px + pw - 7, barY, 3, barH, 1.5f, ((int) (scrollbarAlpha * (Kit.dark ? 110 : 90)) << 24) | (Kit.dark ? 0xFFFFFF : 0));
        }
        g.pose().popMatrix();
        super.render(g, mouseX, mouseY, partialTick);
    }

    private int onAccent() {
        return Kit.luminance(Kit.ACCENT) > 0.6f ? 0xFF0B0D16 : 0xFFFFFFFF;
    }

    private String closeKey() {
        return dev.tony.visuals.FlopVisuals.menuKey == null ? "Esc" : dev.tony.visuals.FlopVisuals.menuKey.getTranslatedKeyMessage().getString();
    }

    private float[] closeChip() {
        float w = Kit.width(closeKey(), true) + Kit.width(" to close") + 20;
        return new float[]{px + pw - w - 16, py + 14, w, 18};
    }

    private void drawHeader(GuiGraphics g, int mx, int my) {
        String title = pages.isEmpty() ? getTitle().getString() : pages.get(page).name();
        float tx = cx + 2;
        if (multi()) {
            Kit.textScaled(g, title, tx, py + 14, 1.55f, text(), true);
            String d = describe(page);
            if (!d.isEmpty()) Kit.text(g, Kit.fit(d, (int) (cw - 100), false), tx, py + 32, sub(), false);
            float[] c = closeChip();
            float hv = closeHover.to(inside(mx, my, c[0], c[1], c[2], c[3]) ? 1 : 0);
            Shapes.round(g, c[0], c[1], c[2], c[3], 9, Kit.lerpColour(glass(0.06f), glass(0.14f), hv));
            Kit.text(g, closeKey(), c[0] + 10, c[1] + 5, text(), true);
            Kit.text(g, " to close", c[0] + 10 + Kit.width(closeKey(), true), c[1] + 5, sub(), false);
        } else {
            Kit.textScaled(g, title, tx, py + 13, 1.35f, text(), true);
            float dw = Kit.width("Done", true) + 22, dx = px + pw - dw - 12, dy = py + 10;
            float hv = closeHover.to(inside(mx, my, dx, dy, dw, 18) ? 1 : 0);
            Kit.pill(g, dx, dy, dw, 18, "Done", true, hv);
        }
    }

    /** {first row y, row height, swatch label y or -1}. */
    private float[] sideLayout() {
        float y = py + 46;
        int n = Math.max(1, pages.size());
        float swatchTop = py + ph - 42;
        float rh = (swatchTop - 8 - y) / n - 3;
        if (rh < 14) {
            swatchTop = -1;
            rh = (py + ph - 10 - y) / n - 3;
        }
        return new float[]{y, Math.max(12, Math.min(22, rh)), swatchTop};
    }

    private float swatchStep() {
        return Math.min(22, (sideW - 30) / (SWATCHES.length - 1f));
    }

    private void drawSidebar(GuiGraphics g, int mx, int my, float r) {
        Shapes.roundClip(g, px, py, pw, ph, r, px, py, px + sideW, py + ph, glass(0.03f));
        Shapes.round(g, px + sideW - 0.5f, py, 0.5f, ph, 0, glass(0.07f));
        boolean labels = Config.get().menu.sidebarLabels;

        // Logo: rounded tile with an accent ring, glow and dot.
        float lx = px + 12, ly = py + 13, ls = 18;
        Shapes.round(g, lx - 3, ly - 3, ls + 6, ls + 6, 9, (0x40 << 24) | (Kit.ACCENT & 0xFFFFFF));
        Shapes.ring(g, lx, ly, ls, ls, 6, 1.2f, Kit.ACCENT);
        Shapes.circle(g, lx + ls / 2, ly + ls / 2, 3.2f, Kit.ACCENT);
        if (labels) {
            Kit.text(g, "Flop Visuals", lx + ls + 7, ly + 1, text(), true);
            Kit.text(g, "Fabric 1.21.11", lx + ls + 7, ly + 11, muted(), false);
        }

        float[] sl = sideLayout();
        float y = sl[0], rh = sl[1], swatchTop = sl[2];
        float rx = px + 7, rw = sideW - 14, rr = Math.min(7, rh / 2f - 0.5f);
        // Selected-page highlight glides between rows.
        float sy = selY.to(y + page * (rh + 3));
        Shapes.round(g, rx, sy, rw, rh, rr, glass(0.10f));
        for (int i = 0; i < pages.size(); i++) {
            Page p = pages.get(i);
            boolean sel = i == page;
            float hv = sideHover[i].to(!sel && inside(mx, my, rx, y, rw, rh) ? 1 : 0);
            if (hv > 0.01f) Shapes.round(g, rx, y, rw, rh, rr, glass(0.06f * hv));
            int col = Kit.lerpColour(sub(), text(), sel ? 1 : hv);
            float icon = Math.min(11, rh - 6);
            float ix = labels ? rx + 8 : rx + (rw - icon) / 2;
            Kit.lineIcon(g, lineIconFor(p.icon()), ix, y + (rh - icon) / 2, icon, col);
            if (labels) Kit.text(g, Kit.fit(p.name(), (int) (rw - icon - 20), true), ix + icon + 7, y + (rh - 8) / 2, col, true);
            y += rh + 3;
        }

        if (swatchTop < 0 || !labels) return;
        Kit.text(g, "ACCENT", px + 14, swatchTop, muted(), true);
        int cur = Config.theme().accentColor & 0xFFFFFF;
        float sx = px + 21, step = swatchStep(), rad = Math.min(7f, step * 0.4f);
        for (int i = 0; i < SWATCHES.length; i++) {
            int sw = SWATCHES[i];
            boolean on = !Config.theme().chroma && cur == sw;
            float hv = swatchHover[i].to(Math.hypot(mx - sx, my - (swatchTop + 20)) <= rad + 2 || on ? 1 : 0);
            Shapes.circle(g, sx, swatchTop + 20, rad + 4 * hv, (Math.round(70 * hv) << 24) | sw);
            if (on) Shapes.circle(g, sx, swatchTop + 20, rad + 1.6f, 0xFFFFFFFF);
            Shapes.circle(g, sx, swatchTop + 20, rad, 0xFF000000 | sw);
            sx += step;
        }
    }

    private void drawItems(GuiGraphics g, float x, float top, int mx, int my, float dt, float pageT) {
        for (Sheet.Item it : items) {
            float y = top + it.y;
            if (y > cy + ch + 40 || y + it.h < cy - 40) continue;
            boolean over = it.clickable() && my >= cy && my <= cy + ch && inside(mx, my, x, y, cw, it.h);
            it.hover = it.hoverE.to(over && dragging == null ? 1 : 0);
            if (it.grouped()) {
                // Each setting on its own glass card, like the preview.
                float k = it instanceof Sheet.Slider || it instanceof Sheet.Choice<?> ? 0.4f : 1f;
                Shapes.round(g, x, y, cw, it.h, 10, Kit.lerpColour(glass(0.045f), glass(0.085f), it.hover * k));
                Shapes.ring(g, x, y, cw, it.h, 10, 0.5f, glass(0.06f));
            }
            drawItem(g, it, x, y, cw, mx, my);
        }
    }

    /** The preview's switch: accent track with a soft glow, white knob. */
    private void drawToggle(GuiGraphics g, float x, float y, float t) {
        float w = 27, h = 16;
        if (t > 0.01f) Shapes.round(g, x - 3, y - 3, w + 6, h + 6, (h + 6) / 2f, (Math.round(70 * t) << 24) | (Kit.ACCENT & 0xFFFFFF));
        Shapes.round(g, x, y, w, h, h / 2f, Kit.lerpColour(glass(0.16f), 0xFF000000 | (Kit.ACCENT & 0xFFFFFF), t));
        float kx = x + 2 + (w - h) * t, ks = h - 4;
        Shapes.circle(g, kx + ks / 2, y + h / 2 + 0.8f, ks / 2 + 0.6f, 0x40000000);
        Shapes.circle(g, kx + ks / 2, y + h / 2, ks / 2, 0xFFFFFFFF);
    }

    private void drawItem(GuiGraphics g, Sheet.Item it, float x, float y, float w, int mx, int my) {
        float ty = y + (it.h - 8) / 2f;
        float right = x + w - 12;
        if (it instanceof Sheet.Header h) {
            Kit.text(g, h.text.toUpperCase(java.util.Locale.ROOT), x + 4, y + it.h - 11, muted(), true);
        } else if (it instanceof Sheet.Footer f) {
            for (int k = 0; k < f.lines.size(); k++) Kit.text(g, f.lines.get(k), x + 4, y + 4 + k * 10, muted(), false);
        } else if (it instanceof Sheet.Toggle t) {
            float v = t.knob.to(t.get.get() ? 1 : 0);
            Kit.text(g, t.label, x + 12, ty, text(), true);
            drawToggle(g, right - 27, y + (it.h - 16) / 2f, v);
        } else if (it instanceof Sheet.Slider s) {
            Kit.text(g, s.label, x + 12, y + 9, text(), true);
            Kit.textRight(g, s.format.apply(s.get.get()), right, y + 9, text(), false);
            float tx = x + 18, tw = w - 36, t = Math.max(0, Math.min(1, (s.get.get() - s.min) / Math.max(1e-4f, s.max - s.min)));
            float ky = y + 28;
            if (s.rainbow) {
                for (int k = 0; k < 24; k++) {
                    float a = k / 24f;
                    Shapes.round(g, tx + tw * a, ky - 2, tw / 24f + 0.6f, 4, 0, 0xFF000000 | dev.tony.visuals.Theme.hsv(a, 0.85f, 1f));
                }
                Shapes.circle(g, tx + tw * t, ky, 6, 0xFFFFFFFF);
                Shapes.circle(g, tx + tw * t, ky, 4, 0xFF000000 | dev.tony.visuals.Theme.hsv(t, 0.85f, 1f));
            } else {
                int fill = s.fill != 0 ? s.fill : Kit.ACCENT;
                Shapes.round(g, tx, ky - 2, tw, 4, 2, glass(0.10f));
                Shapes.round(g, tx, ky - 2, Math.max(4, tw * t), 4, 2, 0xFF000000 | (fill & 0xFFFFFF));
                float kx = tx + tw * t;
                Shapes.circle(g, kx, ky, 8 + 2 * it.hover, (0x55 << 24) | (fill & 0xFFFFFF));
                Shapes.circle(g, kx, ky, 5.5f, 0xFFFFFFFF);
            }
        } else if (it instanceof Sheet.Choice<?> c) {
            int sel = c.index();
            if (!c.grid) {
                Kit.text(g, c.label, x + 12, ty, text(), true);
                float[] first = c.boxes.get(0), last = c.boxes.get(c.boxes.size() - 1);
                Shapes.round(g, x + first[0] - 3, y + first[1] - 3, last[0] + last[2] - first[0] + 6, first[3] + 6, 12, glass(0.06f));
                float[] sb = c.boxes.get(sel);
                float bx = c.selX.to(sb[0]), bw = c.selW.to(sb[2]);
                Shapes.round(g, x + bx, y + sb[1], bw, sb[3], 9, 0xFF000000 | (Kit.ACCENT & 0xFFFFFF));
                for (int k = 0; k < c.count(); k++) {
                    float[] b = c.boxes.get(k);
                    boolean on = k == sel;
                    float hv = c.optHover.get(k).to(!on && inside(mx, my, x + b[0], y + b[1], b[2], b[3]) && my >= cy && my <= cy + ch ? 1 : 0);
                    if (hv > 0.01f) Shapes.round(g, x + b[0], y + b[1], b[2], b[3], 9, glass(0.10f * hv));
                    Kit.textCenter(g, c.name(k), x + b[0] + b[2] / 2, y + b[1] + 5, on ? onAccent() : text(), true);
                }
            } else {
                Kit.text(g, c.label, x + 12, y + 10, text(), true);
                Kit.textRight(g, c.valueName(), right, y + 10, sub(), false);
                for (int k = 0; k < c.count(); k++) {
                    float[] b = c.boxes.get(k);
                    boolean on = k == sel;
                    float hv = c.optHover.get(k).to(on || (inside(mx, my, x + b[0], y + b[1], b[2], b[3]) && my >= cy && my <= cy + ch) ? 1 : 0);
                    float bx = x + b[0], by = y + b[1];
                    if (on) Shapes.round(g, bx - 3, by - 3, b[2] + 6, b[3] + 6, 11, (0x45 << 24) | (Kit.ACCENT & 0xFFFFFF));
                    Shapes.round(g, bx, by, b[2], b[3], 9, Kit.lerpColour(glass(0.04f), glass(0.09f), on ? 0 : hv));
                    Shapes.ring(g, bx, by, b[2], b[3], 9, on ? 1.3f : 0.6f, on ? 0xFF000000 | (Kit.ACCENT & 0xFFFFFF) : glass(0.08f));
                    Kit.text(g, Kit.fit(c.name(k), (int) b[2] - 14, true), bx + 8, by + 6.5f, on ? text() : Kit.lerpColour(sub(), text(), hv), true);
                }
            }
        } else if (it instanceof Sheet.Colour c) {
            Kit.text(g, c.label, x + 12, ty, text(), true);
            int col = c.get.get() & 0xFFFFFF;
            String hex = String.format("#%06X", col);
            Kit.text(g, hex, right - 30 - Kit.width(hex), ty, sub(), false);
            float sx = right - 16, sy = y + it.h / 2f;
            Shapes.circle(g, sx, sy, 7.5f + it.hover, glass(0.2f));
            Shapes.circle(g, sx, sy, 7f, 0xFF000000 | col);
            Kit.text(g, "›", right - 1, ty, sub(), true);
        } else if (it instanceof Sheet.Action a) {
            int col = a.destructive ? 0xFFFF453A : a.accent ? 0xFF000000 | (Kit.ACCENT & 0xFFFFFF) : text();
            Kit.text(g, a.label, x + 12 + 2 * it.hover, ty, col, true);
            String v = a.value == null ? "" : a.value.get();
            float vr = a.chevron ? right - 9 : right;
            if (!v.isEmpty()) Kit.textRight(g, Kit.fit(v, (int) (w * 0.5f), false), vr, ty, sub(), false);
            if (a.chevron) Kit.text(g, "›", right - 1 + 2 * it.hover, ty, sub(), true);
        } else if (it instanceof Sheet.Info in) {
            Kit.text(g, in.label, x + 12, ty, text(), true);
            Kit.textRight(g, Kit.fit(in.value.get(), (int) (w * 0.55f), false), right, ty, sub(), false);
        } else if (it instanceof Sheet.Chips c) {
            String sel = c.selected == null ? null : c.selected.get();
            for (int k = 0; k < c.names.size(); k++) {
                float[] b = c.boxes.get(k);
                String name = c.names.get(k);
                boolean on = name.equals(sel);
                boolean hov = inside(mx, my, x + b[0], y + b[1], b[2], b[3]) && my >= cy && my <= cy + ch;
                int fill = on ? 0xFF000000 | (Kit.ACCENT & 0xFFFFFF) : hov ? glass(0.14f) : glass(0.07f);
                Shapes.round(g, x + b[0], y + b[1], b[2], b[3], 9, fill);
                Kit.textCenter(g, name, x + b[0] + b[2] / 2, y + b[1] + 5, on ? onAccent() : text(), true);
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
            float[] c = closeChip();
            if (button == 0 && inside(mx, my, c[0], c[1], c[2], c[3])) {
                click();
                onClose();
                return true;
            }
            if (button == 0 && inside(mx, my, px, py, sideW, ph)) {
                float[] sl = sideLayout();
                float y = sl[0], rh = sl[1], swatchTop = sl[2];
                for (int i = 0; i < pages.size(); i++) {
                    if (inside(mx, my, px + 7, y, sideW - 14, rh)) {
                        if (i != page) click();
                        showPage(i);
                        return true;
                    }
                    y += rh + 3;
                }
                if (swatchTop >= 0 && Config.get().menu.sidebarLabels) {
                    float sx = px + 21, step = swatchStep();
                    for (int sw : SWATCHES) {
                        if (Math.hypot(mx - sx, my - (swatchTop + 20)) <= 9) {
                            click();
                            Config.theme().accentColor = sw;
                            Config.theme().chroma = false;
                            Config.save();
                            return true;
                        }
                        sx += step;
                    }
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
            Sheet.Slider was = dragging;
            dragging = null;
            Config.save();
            // Window size and layout settings apply as soon as you let go.
            if (was.relayout) rebuildWidgets();
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
        if (dev.tony.visuals.FlopVisuals.menuKey != null && dev.tony.visuals.FlopVisuals.menuKey.matches(event) && minecraft.level != null) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    protected void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.18F));
    }

    /** Re-lays out the window (after its size or position setting changes). */
    protected void relayout() {
        rebuildWidgets();
    }

    @Override
    public void onClose() {
        Config.save();
        closing = true;
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
