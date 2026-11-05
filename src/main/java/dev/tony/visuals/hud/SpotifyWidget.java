package dev.tony.visuals.hud;

import dev.tony.visuals.Config;
import dev.tony.visuals.Theme;
import dev.tony.visuals.gui.Kit;
import dev.tony.visuals.gui.Shapes;
import dev.tony.visuals.spotify.Spotify;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Apple Music style now-playing card: cover, title, artist, progress. */
public final class SpotifyWidget {
    private SpotifyWidget() {}

    private static final int W = 168;

    public static int[] size() {
        Config.Spotify s = Config.get().spotify;
        int p = Config.theme().padding + 2;
        int art = s.showArt ? 30 : 0;
        int text = 9 + 3 + 9;
        int h = Math.max(art, text) + 2 * p + (s.showProgress ? 9 : 0);
        return new int[]{W, h};
    }

    public static void draw(GuiGraphics g, int w, int h) {
        Theme t = Config.theme();
        Config.Spotify s = Config.get().spotify;
        Hud.card(g, 0, 0, w, h);
        int p = t.padding + 2;
        Spotify.Track tr = Spotify.track();

        float tx = p;
        if (s.showArt) {
            Identifier art = Spotify.art();
            if (art != null && tr != null) {
                g.pose().pushMatrix();
                g.pose().translate(p, p);
                g.pose().scale(30 / 128f, 30 / 128f);
                g.blit(RenderPipelines.GUI_TEXTURED, art, 0, 0, 0f, 0f, 128, 128, 128, 128, 0xFFFFFFFF);
                g.pose().popMatrix();
            } else {
                Kit.icon(g, "music", p, p, 30, 0x1DB954);
            }
            tx = p + 30 + 8;
        }
        float maxW = w - tx - p;
        String title = tr == null ? "Not playing" : tr.title();
        String artist = tr == null ? Spotify.status() : tr.playing() ? tr.artist() : "Paused · " + tr.artist();
        Kit.text(g, Kit.fit(title, (int) maxW, true), tx, p + 2, Theme.opaque(t.text), true, t.textShadow);
        Kit.text(g, Kit.fit(artist, (int) maxW, false), tx, p + 14, Theme.opaque(t.muted), false, t.textShadow);

        if (s.showProgress) {
            float by = h - p - 3;
            float bx = p, bw = w - 2 * p;
            float frac = tr == null || tr.durationMs() <= 0 ? 0 : tr.progressNow() / (float) tr.durationMs();
            Shapes.round(g, bx, by, bw, 2.5f, 1.25f, Theme.argb(t.text, 22));
            Shapes.round(g, bx, by, Math.max(2.5f, bw * frac), 2.5f, 1.25f, Theme.opaque(t.accent()));
        }
    }
}
