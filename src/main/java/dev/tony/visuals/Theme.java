package dev.tony.visuals;

/** Shared look for menus and every HUD widget. Colours are 0xRRGGBB. */
public final class Theme {
    public int accentColor = 0x7DD3FC;
    /** Cycle the accent through the rainbow. */
    public boolean chroma = false;
    public int chromaSpeed = 5;

    public int background = 0x111114;
    public int widgetOpacity = 62;
    public int rounding = 7;
    public boolean outline = true;
    public int outlineOpacity = 16;
    /** Outline in the accent colour instead of a soft white hairline. */
    public boolean outlineAccent = false;
    /** Menus in light mode (Apple style light appearance). */
    public boolean lightMenus = false;
    /** Smooth high-resolution font (Inter) instead of Minecraft's pixel font. */
    public boolean smoothFont = true;
    /** Blur the game behind menus. */
    public boolean blurMenus = true;
    /** Thin accent strip on the left of each widget. */
    public boolean accentStrip = false;
    public int text = 0xF2F2F7;
    public int muted = 0x98989F;
    public boolean textShadow = false;
    public int padding = 4;

    /** Current accent, including chroma. */
    public int accent() {
        if (!chroma) return accentColor & 0xFFFFFF;
        float hue = (System.currentTimeMillis() % (long) (20_000 / Math.max(1, chromaSpeed))) / (20_000f / Math.max(1, chromaSpeed));
        return hsv(hue, 0.55f, 1f);
    }

    public static int hsv(float h, float s, float v) {
        h = (h % 1 + 1) % 1 * 6;
        int i = (int) h;
        float f = h - i, p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
        float r, g, b;
        switch (i) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(b * 255);
    }

    public void clamp() {
        chromaSpeed = c(chromaSpeed, 1, 10);
        widgetOpacity = c(widgetOpacity, 0, 100);
        rounding = c(rounding, 0, 10);
        outlineOpacity = c(outlineOpacity, 0, 100);
        padding = c(padding, 2, 10);
    }

    static int c(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    public static int argb(int rgb, int opacityPercent) {
        return (Math.round(Math.max(0, Math.min(100, opacityPercent)) * 2.55f) << 24) | (rgb & 0xFFFFFF);
    }

    public static int opaque(int rgb) { return 0xFF000000 | (rgb & 0xFFFFFF); }

    public static final String[] PRESETS = {"Sky", "Amber", "Sakura", "Mint", "Violet", "Mono", "Clear"};

    public static Theme preset(String name, Theme old) {
        Theme t = new Theme();
        t.chroma = old.chroma;
        t.chromaSpeed = old.chromaSpeed;
        t.lightMenus = old.lightMenus;
        t.smoothFont = old.smoothFont;
        t.blurMenus = old.blurMenus;
        switch (name) {
            case "Amber" -> { t.accentColor = 0xFFB020; t.background = 0x120C05; t.muted = 0xB09A80; }
            case "Sakura" -> { t.accentColor = 0xF9A8D4; t.background = 0x160B12; t.muted = 0xB399A8; }
            case "Mint" -> { t.accentColor = 0x6EE7B7; t.background = 0x07110D; t.muted = 0x8FB3A4; }
            case "Violet" -> { t.accentColor = 0xA855F7; t.background = 0x0A090D; t.muted = 0x8B7F9E; }
            case "Mono" -> { t.accentColor = 0xFFFFFF; t.background = 0x0A0A0A; t.muted = 0x9A9A9A; }
            case "Clear" -> { t.accentColor = old.accentColor; t.widgetOpacity = 0; t.outline = false; t.textShadow = true; }
            default -> {}
        }
        return t;
    }
}
