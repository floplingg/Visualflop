package dev.tony.visuals.hud;

import dev.tony.visuals.Config;
import dev.tony.visuals.Theme;
import dev.tony.visuals.gui.Kit;
import dev.tony.visuals.spotify.Lyrics;
import dev.tony.visuals.spotify.Spotify;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/** Synced lyrics card: the line being sung in bold, the lines around it fading out, scrolling smoothly. */
public final class LyricsWidget {
    private LyricsWidget() {}

    private static final int LINE = 13;
    private static float scroll = -1;
    private static int lastIdx = -2;
    private static long lastFrame;
    private static float glow;

    /** True while a song is playing and has lyrics; the card hides otherwise. */
    public static boolean hasLyrics() {
        Spotify.Track tr = Spotify.track();
        Lyrics.follow(tr);   // keep looking them up even while hidden
        return tr != null && tr.playing() && !Lyrics.lines().isEmpty();
    }

    public static int[] size() {
        Config.Lyrics l = Config.get().lyrics;
        int p = Config.theme().padding + 2;
        return new int[]{l.width, (2 * l.context + 1) * LINE + 2 * p};
    }

    public static void draw(GuiGraphics g, int w, int h) {
        Theme t = Config.theme();
        Config.Lyrics cfg = Config.get().lyrics;
        Hud.card(g, 0, 0, w, h);
        int p = t.padding + 2;
        Spotify.Track tr = Spotify.track();
        Lyrics.follow(tr);
        List<Lyrics.Line> lines = Lyrics.lines();
        float midY = h / 2f - 4;

        if (tr == null || lines.isEmpty()) {
            String msg = tr == null ? "Play a song to see lyrics" : Lyrics.status().isEmpty() ? "No synced lyrics for this song" : Lyrics.status();
            line(g, msg, w, p, midY, Theme.opaque(t.muted), false, cfg.center, t.textShadow);
            return;
        }

        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        int idx = Lyrics.current(tr.progressNow());
        if (scroll < -0.5f || Math.abs(scroll - idx) > 6) scroll = idx;
        scroll = Kit.approach(scroll, idx, 9, dt);
        if (idx != lastIdx) {
            lastIdx = idx;
            glow = 1;
        }
        glow = Kit.approach(glow, 0, 2.5f, dt);

        g.enableScissor(0, p - 2, w, h - p + 2);
        int from = Math.max(0, idx - cfg.context - 1), to = Math.min(lines.size() - 1, idx + cfg.context + 1);
        for (int i = from; i <= to; i++) {
            float y = midY + (i - scroll) * LINE;
            float dist = Math.abs(i - scroll);
            if (dist > cfg.context + 0.9f) continue;
            String text = lines.get(i).text();
            if (text.isEmpty()) text = "• • •";
            boolean cur = i == idx;
            int colour;
            if (cur) {
                colour = Kit.lerpColour(Theme.opaque(t.text), Theme.opaque(t.accent()), cfg.accentLine ? 0.55f + 0.45f * glow : 0);
            } else {
                int a = Math.max(0, Math.min(255, Math.round(255 * (1 - dist / (cfg.context + 1f)) * 0.75f)));
                colour = Theme.argb(t.muted, a * 100 / 255);
            }
            line(g, text, w, p, y, colour, cur, cfg.center, t.textShadow);
        }
        g.disableScissor();
        // Before the first line: a small "starting" hint.
        if (idx < 0 && !lines.isEmpty() && lines.get(0).timeMs() > 3000 && cfg.context == 0) {
            line(g, "• • •", w, p, midY, Theme.opaque(t.muted), false, cfg.center, t.textShadow);
        }
    }

    private static void line(GuiGraphics g, String text, int w, int p, float y, int colour, boolean bold, boolean center, boolean shadow) {
        int max = w - 2 * p;
        String s = text;
        float scale = 1;
        int tw = Kit.width(s, bold);
        if (tw > max) {
            // The sung line shrinks a little to fit; the others are cut with "…".
            if (bold) scale = Math.max(0.78f, max / (float) tw);
            if (tw * scale > max) s = Kit.fit(s, (int) (max / scale), bold);
            tw = Kit.width(s, bold);
        }
        float x = center ? (w - tw * scale) / 2f : p;
        if (scale != 1) {
            g.pose().pushMatrix();
            g.pose().translate(x, y + 4);
            g.pose().scale(scale, scale);
            Kit.text(g, s, 0, -4, colour, bold, shadow);
            g.pose().popMatrix();
        } else {
            Kit.text(g, s, x, y, colour, bold, shadow);
        }
    }
}
