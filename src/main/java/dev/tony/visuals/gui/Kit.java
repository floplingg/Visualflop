package dev.tony.visuals.gui;

import dev.tony.visuals.Config;
import dev.tony.visuals.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Apple-style design kit: system colours (dark and light), smooth Inter text,
 * soft shadows, cards, switches, sliders and icon tiles.
 */
public final class Kit {
    private Kit() {}

    // ------------------------------------------------------------ palette (refreshed every frame)

    public static boolean dark = true;
    public static int PANEL, SIDEBAR, CARD, CARD_HOVER, SEPARATOR, LABEL, SECONDARY, TERTIARY, ACCENT, ACCENT_SOFT,
        SWITCH_OFF, TRACK, DIM;

    public static void refresh() {
        Theme t = Config.theme();
        dark = !t.lightMenus;
        int accent = t.accent() & 0xFFFFFF;
        ACCENT = 0xFF000000 | accent;
        if (dark) {
            PANEL = 0xD91C1C1E;
            SIDEBAR = 0x66000000;
            CARD = 0xE62C2C2E;
            CARD_HOVER = 0xF23A3A3C;
            SEPARATOR = 0x1FFFFFFF;
            LABEL = 0xFFFFFFFF;
            SECONDARY = 0xFF98989F;
            TERTIARY = 0xFF5C5C61;
            SWITCH_OFF = 0xFF39393D;
            TRACK = 0xFF3A3A3C;
            DIM = 0x38000000;
        } else {
            PANEL = 0xE0F2F2F7;
            SIDEBAR = 0x40FFFFFF;
            CARD = 0xF2FFFFFF;
            CARD_HOVER = 0xFFE9E9EE;
            SEPARATOR = 0x1F000000;
            LABEL = 0xFF000000;
            SECONDARY = 0xFF6C6C70;
            TERTIARY = 0xFFAEAEB2;
            SWITCH_OFF = 0xFFE3E3E8;
            TRACK = 0xFFD1D1D6;
            DIM = 0x20000000;
        }
        ACCENT_SOFT = (dark ? 0x40000000 : 0x30000000) | accent;
    }

    // ------------------------------------------------------------ text

    private static final FontDescription UI = new FontDescription.Resource(Identifier.fromNamespaceAndPath("flopvisuals", "ui"));
    private static final FontDescription UI_BOLD = new FontDescription.Resource(Identifier.fromNamespaceAndPath("flopvisuals", "ui_bold"));
    private static final Map<String, Component> REG_CACHE = new HashMap<>(), BOLD_CACHE = new HashMap<>();

    public static Component c(String s, boolean bold) {
        boolean smooth = Config.theme().smoothFont;
        Map<String, Component> cache = bold ? BOLD_CACHE : REG_CACHE;
        if (cache.size() > 1024) cache.clear();
        String key = smooth ? s : "\u0000" + s;
        return cache.computeIfAbsent(key, k -> {
            Style st = Style.EMPTY;
            if (smooth) st = st.withFont(bold ? UI_BOLD : UI);
            else if (bold) st = st.withBold(true);
            return Component.literal(s).withStyle(st);
        });
    }

    // Faces from the preview: Manrope (regular / bold / extra bold) for words, JetBrains Mono for numbers.
    public static final int REGULAR = 0, BOLD = 1, HEAVY = 2, MONO = 3;
    private static final FontDescription UI_HEAVY = new FontDescription.Resource(Identifier.fromNamespaceAndPath("flopvisuals", "ui_heavy"));
    private static final FontDescription UI_MONO = new FontDescription.Resource(Identifier.fromNamespaceAndPath("flopvisuals", "mono"));
    private static final Map<String, Component> FACES = new HashMap<>();

    public static Component face(String s, int face) {
        if (face == REGULAR || face == BOLD) return c(s, face == BOLD);
        boolean smooth = Config.theme().smoothFont;
        if (FACES.size() > 1024) FACES.clear();
        return FACES.computeIfAbsent(face + (smooth ? "" : "!") + "\u0001" + s, k -> {
            Style st = Style.EMPTY;
            if (smooth) st = st.withFont(face == HEAVY ? UI_HEAVY : UI_MONO);
            else if (face == HEAVY) st = st.withBold(true);
            return Component.literal(s).withStyle(st);
        });
    }

    public static float width(String s, int face, float scale) {
        return font().width(face(s, face)) * scale;
    }

    public static void draw(GuiGraphics g, String s, float x, float y, float scale, int colour, int face) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        if (scale != 1) g.pose().scale(scale, scale);
        g.drawString(font(), face(s, face), 0, 0, colour, false);
        g.pose().popMatrix();
    }

    /** Small uppercase label with wide letter spacing ("NOW PLAYING"), like the preview. */
    public static void tracked(GuiGraphics g, String s, float x, float y, float scale, int colour) {
        s = s.toUpperCase(java.util.Locale.ROOT);
        float gap = 0.9f * scale;
        float cx = x;
        for (int i = 0; i < s.length(); i++) {
            String ch = s.substring(i, i + 1);
            draw(g, ch, cx, y, scale, colour, BOLD);
            cx += width(ch, BOLD, scale) + gap;
        }
    }

    public static float trackedWidth(String s, float scale) {
        s = s.toUpperCase(java.util.Locale.ROOT);
        float w = 0;
        for (int i = 0; i < s.length(); i++) w += width(s.substring(i, i + 1), BOLD, scale) + 0.9f * scale;
        return Math.max(0, w - 0.9f * scale);
    }

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    public static int width(String s, boolean bold) {
        return font().width(c(s, bold));
    }

    public static int width(String s) {
        return width(s, false);
    }

    public static void text(GuiGraphics g, String s, float x, float y, int colour, boolean bold) {
        text(g, s, x, y, colour, bold, false);
    }

    public static void text(GuiGraphics g, String s, float x, float y, int colour, boolean bold, boolean shadow) {
        int ix = Math.round(x), iy = Math.round(y);
        if (Math.abs(ix - x) < 0.01f && Math.abs(iy - y) < 0.01f) {
            g.drawString(font(), c(s, bold), ix, iy, colour, shadow);
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.drawString(font(), c(s, bold), 0, 0, colour, shadow);
        g.pose().popMatrix();
    }

    public static void textScaled(GuiGraphics g, String s, float x, float y, float scale, int colour, boolean bold) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.drawString(font(), c(s, bold), 0, 0, colour, false);
        g.pose().popMatrix();
    }

    public static void textRight(GuiGraphics g, String s, float right, float y, int colour, boolean bold) {
        text(g, s, right - width(s, bold), y, colour, bold);
    }

    public static void textCenter(GuiGraphics g, String s, float cx, float y, int colour, boolean bold) {
        text(g, s, cx - width(s, bold) / 2f, y, colour, bold);
    }

    /** Cut text to fit a width, adding an ellipsis. */
    public static String fit(String s, int maxWidth, boolean bold) {
        if (width(s, bold) <= maxWidth) return s;
        String out = s;
        while (out.length() > 1 && width(out + "…", bold) > maxWidth) out = out.substring(0, out.length() - 1);
        return out + "…";
    }

    // ------------------------------------------------------------ shapes

    /** Soft drop shadow under a rounded box. */
    public static void shadow(GuiGraphics g, float x, float y, float w, float h, float r, float strength) {
        for (int i = 6; i >= 1; i--) {
            int a = Math.round(255 * strength * 0.05f * (1 - i / 7f));
            if (a <= 0) continue;
            Shapes.round(g, x - i, y - i + 2, w + 2 * i, h + 2 * i, r + i, a << 24);
        }
    }

    public static void hairline(GuiGraphics g, float x, float y, float w, int colour) {
        Shapes.round(g, x, y, w, 0.5f, 0, colour);
    }

    public static int lerpColour(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = (a >>> 24), ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = (b >>> 24), br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return Math.round(aa + (ba - aa) * t) << 24 | Math.round(ar + (br - ar) * t) << 16
            | Math.round(ag + (bg - ag) * t) << 8 | Math.round(ab + (bb - ab) * t);
    }

    /** iOS switch, 26 x 15. t = 0 off .. 1 on (animated). */
    public static void toggle(GuiGraphics g, float x, float y, float t) {
        float w = 26, h = 15;
        Shapes.round(g, x, y, w, h, h / 2, lerpColour(SWITCH_OFF, ACCENT, t));
        float kx = x + 1.5f + t * (w - h);
        shadowKnob(g, kx + 6f, y + h / 2, 6f);
        Shapes.circle(g, kx + 6f, y + h / 2, 6f, 0xFFFFFFFF);
    }

    private static void shadowKnob(GuiGraphics g, float cx, float cy, float r) {
        Shapes.circle(g, cx, cy + 0.8f, r + 0.9f, 0x22000000);
        Shapes.circle(g, cx, cy + 0.4f, r + 0.4f, 0x1A000000);
    }

    /** Thin slider track with a round white knob. t = 0..1. */
    public static void slider(GuiGraphics g, float x, float y, float w, float t, int fill, boolean active) {
        t = Math.max(0, Math.min(1, t));
        Shapes.round(g, x, y - 1.5f, w, 3, 1.5f, TRACK);
        Shapes.round(g, x, y - 1.5f, Math.max(3, w * t), 3, 1.5f, fill);
        float kx = x + w * t, r = active ? 6.5f : 6f;
        shadowKnob(g, kx, y, r);
        Shapes.circle(g, kx, y, r, 0xFFFFFFFF);
    }

    /** iOS-settings style icon tile: coloured squircle with a white symbol. */
    public static void icon(GuiGraphics g, String icon, float x, float y, float size, int colour) {
        Shapes.round(g, x, y, size, size, size * 0.26f, 0xFF000000 | colour);
        float pad = size * 0.18f;
        Identifier tex = Identifier.fromNamespaceAndPath("flopvisuals", "textures/gui/icon/" + icon + ".png");
        int s = Math.round(size - 2 * pad);
        g.pose().pushMatrix();
        g.pose().translate(x + pad, y + pad);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0f, 0f, s, s, s, s, 0xFFFFFFFF);
        g.pose().popMatrix();
    }

    /** Thin line icon from the preview (tinted with {@code colour}). */
    public static void lineIcon(GuiGraphics g, String icon, float x, float y, float size, int colour) {
        Identifier tex = Identifier.fromNamespaceAndPath("flopvisuals", "textures/gui/line/line_" + icon + ".png");
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(size / 48f, size / 48f);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0f, 0f, 48, 48, 48, 48, 0xFF000000 | (colour & 0xFFFFFF));
        g.pose().popMatrix();
    }

    /** Pill button. Returns nothing; hit testing is done by the caller. */
    public static void pill(GuiGraphics g, float x, float y, float w, float h, String label, boolean primary, float hover) {
        int fill = primary ? ACCENT : (dark ? 0xFF3A3A3C : 0xFFE5E5EA);
        if (hover > 0) fill = lerpColour(fill, primary ? 0xFFFFFFFF : (dark ? 0xFF48484A : 0xFFD8D8DD), hover * (primary ? 0.15f : 1f));
        Shapes.round(g, x, y, w, h, h / 2, fill);
        int tc = primary ? (luminance(ACCENT) > 0.6f ? 0xFF000000 : 0xFFFFFFFF) : LABEL;
        textCenter(g, label, x + w / 2, y + (h - 8) / 2f, tc, true);
    }

    public static float luminance(int c) {
        return (0.299f * ((c >> 16) & 255) + 0.587f * ((c >> 8) & 255) + 0.114f * (c & 255)) / 255f;
    }

    /** Ease towards a target, framerate independent. */
    public static float approach(float current, float target, float speed, float dt) {
        float k = 1 - (float) Math.exp(-speed * dt);
        float v = current + (target - current) * k;
        return Math.abs(v - target) < 0.001f ? target : v;
    }
}
